#!/usr/bin/env bash
set -euo pipefail

MOD="$(cd "$(dirname "$0")" && pwd)"
cd "$MOD"

# Toolchain (override with environment variables):
#   JAVA / JAVAC / KEYTOOL    Java 11+ (d8/apksigner need it)
#   ANDROID_BUILD_TOOLS       build-tools dir (aapt2, lib/d8.jar, zipalign, lib/apksigner.jar)
#   ANDROID_JAR               platforms/android-35/android.jar
JAVA="${JAVA:-java}"
JAVAC="${JAVAC:-javac}"
KEYTOOL="${KEYTOOL:-keytool}"
PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null 2>&1 || PYTHON=python

if [ -z "${ANDROID_BUILD_TOOLS:-}" ]; then
    : "${ANDROID_HOME:?set ANDROID_HOME (or ANDROID_BUILD_TOOLS + ANDROID_JAR)}"
    ANDROID_BUILD_TOOLS="$(ls -d "$ANDROID_HOME"/build-tools/* | sort -V | tail -1)"
fi
if [ -z "${ANDROID_JAR:-}" ]; then
    : "${ANDROID_HOME:?set ANDROID_HOME (or ANDROID_BUILD_TOOLS + ANDROID_JAR)}"
    ANDROID_JAR="$ANDROID_HOME/platforms/android-35/android.jar"
fi

conv() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s' "$1"; fi; }
BT="$(conv "$ANDROID_BUILD_TOOLS")"
AJAR="$(conv "$ANDROID_JAR")"
AAPT2="$BT/aapt2"; [ -f "$BT/aapt2.exe" ] && AAPT2="$BT/aapt2.exe"
ZIPALIGN="$BT/zipalign"; [ -f "$BT/zipalign.exe" ] && ZIPALIGN="$BT/zipalign.exe"

CP_SEP=":"
case "$(uname -s)" in
    MINGW*|MSYS*|CYGWIN*) CP_SEP=";";;
esac

rm -rf build
mkdir -p build/stubs build/classes build/dex build/gen

# 1. compile-time Xposed API stubs (never packaged; LSPosed/Vector provides the real ones)
"$JAVAC" -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$AJAR" -d build/stubs \
    $(find stubs -name '*.java')

# 2. resources + manifest + assets + generated R.java
"$AAPT2" compile --dir res -o build/res.zip
"$AAPT2" link -o build/base.apk -I "$AJAR" \
    --manifest AndroidManifest.xml -A assets -R build/res.zip \
    --auto-add-overlay --min-sdk-version 29 --target-sdk-version 35 \
    --java build/gen

# 3. module classes + R
"$JAVAC" -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$AJAR${CP_SEP}build/stubs" -d build/classes \
    $(find src -name '*.java') $(find build/gen -name '*.java') 2>&1 | tee build-log.txt

# 4. dex
"$JAVA" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 29 --lib "$AJAR" --output build/dex \
    $(find build/classes -name '*.class')

# 5. inject classes.dex
"$PYTHON" - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk') as zin, zipfile.ZipFile('build/unaligned.apk', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write('build/dex/classes.dex', 'classes.dex')
PY

# 6. align + sign
"$ZIPALIGN" -f -p 4 build/unaligned.apk build/aligned.apk

if [ ! -f debug.keystore ]; then
    "$KEYTOOL" -genkeypair -keystore debug.keystore -alias androiddebugkey \
        -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$JAVA" -jar "$BT/lib/apksigner.jar" sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/BetterOriginOS.apk build/aligned.apk

"$JAVA" -jar "$BT/lib/apksigner.jar" verify --print-certs build/BetterOriginOS.apk | head -5
ls -lh build/BetterOriginOS.apk
