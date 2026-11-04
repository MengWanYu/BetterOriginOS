#!/usr/bin/env bash
set -euo pipefail

MOD="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$MOD/.." && pwd)"
JRE25="/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre"
BT="$(cygpath -m "$ROOT/work/sdk-dl/bt35/android-15")"
AJAR="$(cygpath -m "$ROOT/work/sdk-dl/plat35/android-35/android.jar")"

cd "$MOD"
rm -rf build
mkdir -p build/stubs build/classes build/dex build/gen

# 1. compile-time Xposed API stubs (never packaged; LSPosed provides the real ones)
javac -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$AJAR" -d build/stubs \
    $(find stubs -name '*.java')

# 2. resources + manifest + assets + generated R.java
"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/base.apk -I "$AJAR" \
    --manifest AndroidManifest.xml -A assets -R build/res.zip \
    --auto-add-overlay --min-sdk-version 29 --target-sdk-version 35 \
    --java build/gen

# 3. module classes + R
# 输出同时落到 build-log.txt：这份日志以前是手写的，过期好几天还会把老错误当新错误读。
javac -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$AJAR;build/stubs" -d build/classes \
    $(find src -name '*.java') $(find build/gen -name '*.java') 2>&1 | tee build-log.txt

# 4. dex
"$JRE25/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 29 --lib "$AJAR" --output build/dex \
    $(find build/classes -name '*.class')

# 5. inject classes.dex
python - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk') as zin, zipfile.ZipFile('build/unaligned.apk', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write('build/dex/classes.dex', 'classes.dex')
PY

# 6. align + sign
"$BT/zipalign.exe" -f -p 4 build/unaligned.apk build/aligned.apk

if [ ! -f debug.keystore ]; then
    keytool -genkeypair -keystore debug.keystore -alias androiddebugkey \
        -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

"$JRE25/bin/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/BetterOriginOS.apk build/aligned.apk

"$JRE25/bin/java" -jar "$BT/lib/apksigner.jar" verify --print-certs build/BetterOriginOS.apk | head -5
ls -lh build/BetterOriginOS.apk
