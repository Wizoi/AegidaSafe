#!/usr/bin/env bash
# Emulator-first checks (design section 7). Run from Git Bash in the repo root:
#   scripts/emulator-checks.sh avd        create the API 36 Google Play AVD (Pixel profile; Mom's OS level)
#   scripts/emulator-checks.sh smoke      install the -local debug build and run the instrumented tests
#   scripts/emulator-checks.sh spike      check each adb/cmd mechanism the scenarios rely on (PASS/FAIL)
#   scripts/emulator-checks.sh popup-page serve the website-notification-spam fixture and open it in Chrome
#   scripts/emulator-checks.sh scenarios  feature scenarios (added with each feature)
#   scripts/emulator-checks.sh shots      light and dark screenshots at 100% and 200% font into build/emulator-shots
# It refuses any serial that is not emulator-*, so it can never touch a real phone by mistake.
set -u
SDK="${ANDROID_SDK_ROOT:-$LOCALAPPDATA/Android/Sdk}"
ADB="$SDK/platform-tools/adb.exe"; [ -f "$ADB" ] || ADB="$SDK/platform-tools/adb"
SERIAL="${SERIAL:-emulator-5554}"
case "$SERIAL" in emulator-*) ;; *) echo "refusing: $SERIAL is not an emulator"; exit 2;; esac
adb() { MSYS_NO_PATHCONV=1 "$ADB" -s "$SERIAL" "$@"; }
export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"
PKG=com.aegida.app
AVD_NAME=Aegida36
IMAGE="system-images;android-36;google_apis_playstore;x86_64"

case "${1:-}" in
avd)
  SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager.bat"; [ -f "$SDKMANAGER" ] || SDKMANAGER="$SDK/cmdline-tools/latest/bin/sdkmanager"
  AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager.bat"; [ -f "$AVDMANAGER" ] || AVDMANAGER="$SDK/cmdline-tools/latest/bin/avdmanager"
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
spike)
  # Each adb/cmd mechanism the scenarios rely on (findings #16). Needs the -local debug build installed.
  ok() { echo "PASS  $1"; }; no() { echo "FAIL  $1"; FAILS=$((FAILS+1)); }; FAILS=0
  s() { adb shell "$@" 2>&1 | tr -d '\r'; }
  [ "$(s cmd role get-role-holders android.app.role.BROWSER)" != "" ] && ok "cmd role get-role-holders" || no "cmd role get-role-holders"
  s cmd role add-role-holder android.app.role.BROWSER com.android.chrome >/dev/null && ok "cmd role add-role-holder (qualifying app)" || no "cmd role add-role-holder"
  s cmd notification post -t AegidaSpike spike "https://example.test/x" >/dev/null
  s dumpsys notification --noredact | grep -q "example.test/x" && ok "cmd notification post" || no "cmd notification post"
  s settings put global private_dns_mode hostname; s settings put global private_dns_specifier security.cloudflare-dns.com; sleep 3
  s dumpsys connectivity | grep -q "PrivateDnsServerName: security.cloudflare-dns.com" && ok "Private DNS hostname applied" || no "Private DNS hostname"
  s settings delete global private_dns_mode >/dev/null; s settings delete global private_dns_specifier >/dev/null
  [ "$(s settings get global private_dns_mode)" = "null" ] && ok "Private DNS back to absent (default)" || no "Private DNS restore"
  s cmd connectivity airplane-mode enable; [ "$(s settings get global airplane_mode_on)" = "1" ] && ok "airplane mode on" || no "airplane mode"; s cmd connectivity airplane-mode disable
  s am set-standby-bucket $PKG restricted; [ "$(s am get-standby-bucket $PKG)" = "45" ] && ok "standby bucket restricted" || no "standby bucket"; s am set-standby-bucket $PKG active
  s dumpsys deviceidle force-idle >/dev/null; [ "$(s dumpsys deviceidle get deep)" = "IDLE" ] && ok "deviceidle force-idle" || no "deviceidle"; s dumpsys deviceidle unforce >/dev/null
  s cmd appops set $PKG RUN_ANY_IN_BACKGROUND ignore; s cmd appops get $PKG RUN_ANY_IN_BACKGROUND | grep -q ignore && ok "background restriction appop" || no "background appop"; s cmd appops set $PKG RUN_ANY_IN_BACKGROUND allow
  s settings put system font_scale 2.0; [ "$(s settings get system font_scale)" = "2.0" ] && ok "font scale 200%" || no "font scale"; s settings put system font_scale 1.0
  s am start -n "$PKG/.MainActivity" >/dev/null; sleep 2; s input keyevent KEYCODE_HOME; sleep 5; s am kill $PKG; sleep 1
  [ -z "$(s pidof $PKG)" ] && ok "am kill (after 5 s in background)" || no "am kill"
  BEFORE=$(s date +%s); s settings put global auto_time 0; s cmd alarm set-time $(( (BEFORE + 86400) * 1000 )) >/dev/null
  [ $(( $(s date +%s) - BEFORE )) -ge 86000 ] && ok "clock +1 day without root (cmd alarm set-time)" || no "clock advance"; s settings put global auto_time 1
  s fallocate -l 10M /data/local/tmp/fill.bin && ok "fallocate on /data/local/tmp" || no "fallocate"; s rm -f /data/local/tmp/fill.bin
  echo "$FAILS failed"; [ "$FAILS" = 0 ]
  ;;
popup-page)
  # Serve fixtures/web/popup-spam to the emulator as http://localhost:8765 (a secure origin, so the page
  # may ask for notification permission) and open it in Chrome. Tap Allow, then Allow on Chrome's prompt.
  adb shell am set-debug-app --persistent com.android.chrome
  adb shell 'echo "_ --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
  (cd fixtures/web/popup-spam && python -m http.server 8765 >/dev/null 2>&1 &)
  adb reverse tcp:8765 tcp:8765
  adb shell am start -a android.intent.action.VIEW -d http://localhost:8765/index.html com.android.chrome
  echo "serving on 8765; stop the python http.server and run 'adb reverse --remove tcp:8765' when done"
  ;;
scenarios)
  echo "Not written yet: scenarios are added with the features they test (build step 1 onward)."
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
*) sed -n 2,9p "$0"; exit 1;;
esac
