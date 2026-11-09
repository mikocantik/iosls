#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TOOLS="$ROOT/tools"
SDK="$TOOLS/sdk"
BT="$SDK/build-tools/36.0.0"
AJAR="$SDK/platforms/android-36/android.jar"
JDK="$TOOLS/jdk17"
OUT="$ROOT/build"
KS="$TOOLS/signing/iosls.jks"

export JAVA_HOME="$JDK"
export PATH="$JDK/bin:$PATH"

[ -x "$JDK/bin/javac" ] || { echo "ERROR: JDK missing at $JDK"; exit 1; }
[ -x "$BT/aapt2" ] || { echo "ERROR: build-tools missing at $BT"; exit 1; }
[ -f "$AJAR" ] || { echo "ERROR: android.jar missing at $AJAR"; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/stubs" "$OUT/classes" "$OUT/dex" "$OUT/res" "$OUT/apk"

echo "[1/6] compile xposed stubs"
find "$ROOT/stubs" -name '*.java' > "$OUT/stub-src.txt"
"$JDK/bin/javac" -nowarn -source 11 -target 11 -cp "$AJAR" -d "$OUT/stubs" @"$OUT/stub-src.txt"

echo "[2/6] compile module"
find "$ROOT/module/src" -name '*.java' > "$OUT/src.txt"
"$JDK/bin/javac" -nowarn -source 11 -target 11 -cp "$AJAR:$OUT/stubs" -d "$OUT/classes" @"$OUT/src.txt"

echo "[3/6] dex"
find "$OUT/classes" -name '*.class' > "$OUT/class.txt"
"$BT/d8" --min-api 31 --lib "$AJAR" --output "$OUT/dex" $(cat "$OUT/class.txt")

echo "[4/6] resources"
"$BT/aapt2" compile --dir "$ROOT/module/res" -o "$OUT/res"
"$BT/aapt2" link -o "$OUT/apk/base.apk" -I "$AJAR" \
  --manifest "$ROOT/module/AndroidManifest.xml" \
  --min-sdk-version 31 --target-sdk-version 36 \
  -A "$ROOT/module/assets" \
  "$OUT/res"/*.flat

echo "[5/6] package"
"$ROOT/.venv/bin/python" "$ROOT/tools/add_dex.py" "$OUT/apk/base.apk" "$OUT/dex/classes.dex" "$OUT/apk/app.apk"
"$BT/zipalign" -f -p 4 "$OUT/apk/app.apk" "$OUT/apk/aligned.apk"

echo "[6/6] sign"
mkdir -p "$TOOLS/signing"
if [ ! -f "$KS" ]; then
  "$JDK/bin/keytool" -genkeypair -keystore "$KS" -alias iosls -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass ioslsdev -keypass ioslsdev \
    -dname "CN=iOS LS XOS, O=iosls, C=ID" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:ioslsdev --key-pass pass:ioslsdev \
  --out "$OUT/iosls-xos-0.1.0.apk" "$OUT/apk/aligned.apk"
"$BT/apksigner" verify --print-certs "$OUT/iosls-xos-0.1.0.apk" | head -3
ls -lh "$OUT/iosls-xos-0.1.0.apk"
