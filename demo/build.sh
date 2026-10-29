#!/usr/bin/env bash
set -euo pipefail

APP="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$APP/.." && pwd)"
JRE25="/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre"
BT="$(cygpath -m "$ROOT/work/sdk-dl/bt35/android-15")"
AJAR="$(cygpath -m "$ROOT/work/sdk-dl/plat35/android-35/android.jar")"

cd "$APP"
rm -rf build
mkdir -p build/gen build/classes build/dex

"$BT/aapt2.exe" compile --dir res -o build/res.zip
"$BT/aapt2.exe" link -o build/base.apk -I "$AJAR" \
    --manifest AndroidManifest.xml -R build/res.zip \
    --auto-add-overlay --min-sdk-version 29 --target-sdk-version 35 \
    --java build/gen

javac -nowarn -encoding UTF-8 -source 8 -target 8 -cp "$AJAR" -d build/classes \
    $(find src ../vivo-OriginUI/lib/src -name '*.java') $(find build/gen -name '*.java')

"$JRE25/bin/java" -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
    --min-api 29 --lib "$AJAR" --output build/dex \
    $(find build/classes -name '*.class')

python - <<'PY'
import zipfile
with zipfile.ZipFile('build/base.apk') as zin, zipfile.ZipFile('build/unaligned.apk', 'w', zipfile.ZIP_DEFLATED) as zout:
    for item in zin.infolist():
        zout.writestr(item, zin.read(item.filename))
    zout.write('build/dex/classes.dex', 'classes.dex')
PY

"$BT/zipalign.exe" -f -p 4 build/unaligned.apk build/aligned.apk
"$JRE25/bin/java" -jar "$BT/lib/apksigner.jar" sign \
    --ks debug.keystore --ks-pass pass:android --key-pass pass:android \
    --out build/VivoUILab.apk build/aligned.apk
"$JRE25/bin/java" -jar "$BT/lib/apksigner.jar" verify --print-certs build/VivoUILab.apk | head -3
ls -lh build/VivoUILab.apk
