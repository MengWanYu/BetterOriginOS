#!/bin/bash
# usage: jdx.sh <APK> <fully.qualified.ClassName> <OutFile.java>
J="/c/Users/Administrator/Desktop/jadx/jadx-gui-1.5.5-with-jre-win/jre/bin/java.exe"
APK=$(cygpath -m "$1"); CLS="$2"; OUT=$(cygpath -m "$3"); TMP=$(cygpath -m "work/y7s-a11/tmp")
"$J" -Xmx4g -cp "C:\Users\Administrator\Desktop\jadx\jadx-1.5.5\lib\jadx-1.5.5-all.jar" jadx.cli.JadxCLI \
  --single-class "$CLS" --single-class-output "$OUT" -d "$TMP" "$APK"
