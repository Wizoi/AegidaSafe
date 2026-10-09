#!/usr/bin/env bash
# Builds a tiny app that does nothing, with the package name given, signed with the debug test key
# (never a real vendor's key; a fixture only stands in for one, design section 7).
#   scripts/make-fixture-apk.sh com.anydesk.anydeskandroid   ->  build/fixture-apps/com.anydesk.anydeskandroid.apk
set -eu
PKG="${1:?package name}"
SDK="${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}"
BT=$(ls -d "$SDK"/build-tools/*/ | sort -V | tail -1)
JAR=$(ls -d "$SDK"/platforms/android-3*/ | sort -V | tail -1)android.jar
OUT=build/fixture-apps; WORK="$OUT/work-$PKG"
rm -rf "$WORK"; mkdir -p "$WORK"
cat > "$WORK/AndroidManifest.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="$PKG">
    <uses-sdk android:minSdkVersion="29" android:targetSdkVersion="34" />
    <application android:hasCode="false" android:label="Fixture ${PKG##*.}" />
</manifest>
XML
"${BT}aapt2.exe" link -o "$WORK/unsigned.apk" --manifest "$WORK/AndroidManifest.xml" -I "$JAR" >/dev/null
"${BT}apksigner.bat" sign --ks "$HOME/.android/debug.keystore" --ks-pass pass:android --key-pass pass:android \
    --out "$OUT/$PKG.apk" "$WORK/unsigned.apk"
rm -rf "$WORK"
echo "$OUT/$PKG.apk"
