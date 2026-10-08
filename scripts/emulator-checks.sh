#!/usr/bin/env bash
# Emulator-first checks (design section 7). Run from Git Bash in the repo root:
#   scripts/emulator-checks.sh avd        create the API 36 Google Play AVD (Pixel profile; Mom's OS level)
#   scripts/emulator-checks.sh smoke      install the -local debug build and run the instrumented tests
#   scripts/emulator-checks.sh scenarios  the adb/cmd scenario checks (filled in by the emulator spike)
#   scripts/emulator-checks.sh shots      light and dark screenshots at 100% and 200% font into build/emulator-shots
# It refuses any serial that is not emulator-*, so it can never touch a real phone by mistake.
set -u
SDK="${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"; [ -x "$ADB" ] || ADB="$SDK/platform-tools/adb"
SERIAL="${SERIAL:-emulator-5554}"
case "$SERIAL" in emulator-*) ;; *) echo "refusing: $SERIAL is not an emulator"; exit 2;; esac
adb() { MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" "$@"; }
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
PKG=com.aegida.app
AVD_NAME=Aegida36
IMAGE="system-images;android-36;google_apis_playstore;x86_64"

case "${1:-}" in
avd)
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager.bat"; [ -x "$SDKMANAGER" ] || SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager.bat"; [ -x "$AVDMANAGER" ] || AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
  "$SDKMANAGER" "$IMAGE" || exit 1
  echo no | "$AVDMANAGER" create avd -n "$AVD_NAME" -k "$IMAGE" -d pixel_7 --force || exit 1
  echo "start it with: \"$SDK/emulator/emulator\" -avd $AVD_NAME -no-snapshot-save &"
  ;;
smoke)
  export ANDROID_SERIAL="$SERIAL"; mkdir -p build
  ./gradlew.bat installDebug installDebugAndroidTest -PlocalBuild | tee build/smoke-gradle.log
  grep -q "BUILD SUCCESSFUL" build/smoke-gradle.log || { echo "gradle install failed"; exit 1; }
  adb shell am instrument -w "$PKG.test/androidx.test.runner.AndroidJUnitRunner"
  ;;
scenarios)
  echo "Not written yet. The emulator spike (design section 12) confirms each mechanism first and records it in docs/findings.md."
  exit 1
  ;;
shots)
  OUT=build/emulator-shots; mkdir -p "$OUT"
  adb shell am start -n "$PKG/.MainActivity" >/dev/null; sleep 4
  for scale in 1.0 2.0; do
    adb shell settings put system font_scale "$scale"
    for mode in no yes; do
      adb shell cmd uimode night "$mode"; sleep 3
      adb exec-out screencap -p > "$OUT/home-font$scale-night-$mode.png"
    done
  done
  adb shell settings put system font_scale 1.0
  adb shell cmd uimode night auto
  echo "wrote $OUT"
  ;;
*) sed -n 2,7p "$0"; exit 1;;
esac
