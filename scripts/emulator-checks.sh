#!/usr/bin/env bash
# Emulator-first checks (design section 7). Run from Git Bash in the repo root:
#   scripts/emulator-checks.sh avd        create the API 36 Google Play AVD (Pixel profile; Mom's OS level)
#   scripts/emulator-checks.sh smoke      install the -local debug build and run the instrumented tests
#   scripts/emulator-checks.sh spike      check each adb/cmd mechanism the scenarios rely on (PASS/FAIL)
#   scripts/emulator-checks.sh popup-page serve the website-notification-spam fixture and open it in Chrome
#   scripts/emulator-checks.sh scenarios  feature scenarios on a cleared app: pop-up warning, messages, daily feed, listed link
#   scripts/emulator-checks.sh failures   offline daily job, and a phone that never runs its daily check (reads the app's database)
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

# Helpers shared by the scenario commands.
FAILS=0
ok() { echo "PASS  $1"; }; no() { echo "FAIL  $1"; FAILS=$((FAILS+1)); }
sh_() { adb shell "$@" 2>&1 | tr -d '\r'; }
# `cmd notification post` is word-split by adb's shell, so the whole command goes over as one string.
post() { adb shell "cmd notification post -S bigtext -t '$1' '$2' '$3'" >/dev/null 2>&1; }
tags() { sh_ dumpsys notification --noredact | grep "NotificationRecord" | sed -E 's/.*pkg=([^ ]+) .*id=([-0-9]+) tag=([^ ]*).*/\1 \2 \3/'; }
have() { tags | grep -qF "$1"; }
wait_for() { for _ in $(seq 1 "${3:-20}"); do eval "$2" && { ok "$1"; return 0; }; sleep 1; done; no "$1"; return 1; }

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
  # Feature scenarios on the emulator (findings #32, #33). Starts from a cleared app so it is repeatable.
  # Needs the -local debug build: gradlew installDebug -PlocalBuild (done below).
  FAILS=0
  LISTED=$(sed -n '4p' feedtool/src/test/resources/feeds/phishing-domains.txt | tr -d '\r')

  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'

  echo "-- a website sends a fake virus warning (Chrome, host-served fixture page)"
  adb shell am set-debug-app --persistent com.android.chrome >/dev/null
  adb shell 'echo "_ --disable-fre --no-default-browser-check --no-first-run" > /data/local/tmp/chrome-command-line'
  (cd fixtures/web/popup-spam && python -m http.server 8765 >/dev/null 2>&1 &)
  adb reverse tcp:8765 tcp:8765 >/dev/null
  sh_ am force-stop com.android.chrome >/dev/null
  sh_ am start -a android.intent.action.VIEW -d http://localhost:8765/index.html com.android.chrome >/dev/null
  sleep 8
  for _ in 1 2 3; do  # the first tap after launch can be lost; Chrome may ask for permission first
    adb shell input tap 216 644; sleep 3
    sh_ uiautomator dump /sdcard/ui.xml >/dev/null; adb shell input tap 540 1395 >/dev/null 2>&1 && true
    have "com.aegida.app 1000 PopupSpam:SiteNotifications:http://localhost:8765" && break
  done
  wait_for "Aegida's banner for the website warning is shown" 'have "com.aegida.app 1000 PopupSpam:SiteNotifications:http://localhost:8765"' 10
  have "com.android.chrome -1 p#http://localhost:8765/#1aegida-fixture" && no "Chrome's fake virus warning was hidden" || ok "Chrome's fake virus warning was hidden"

  echo "-- messages"
  post Delivery lookalike1 "Your parcel is held. Pay the fee at paypa1-help.example/claim"
  wait_for "a look-alike link gets a banner" 'have "com.aegida.app 1000 Link:LookalikeLink:paypa1-help.example"' 10
  have "com.android.shell 2020 lookalike1" && ok "the look-alike message itself was left in place" || no "the look-alike message itself was left in place"
  post Friend shared1 "Church sale https://sites.google.com/view/church-sale and bit.ly/3abc"
  post Mum plain1 "Lunch menu https://www.example.org/menu"
  sleep 4
  tags | grep -q "Link:.*sites.google.com\|Link:.*bit.ly\|Link:.*example.org" && no "links on shared hosts and plain links are not flagged" || ok "links on shared hosts and plain links are not flagged"

  echo "-- the daily job fetches the live feed, then a listed link is caught"
  sh_ settings put global auto_time 0
  sh_ cmd alarm set-time $(( ($(date +%s) + 90000) * 1000 )) >/dev/null   # WorkManager will not run periodic work early
  JID=$(sh_ dumpsys jobscheduler | grep -oE "JOB #u[0-9a]+/[0-9]+: [a-f0-9]+ com.aegida.app" | head -1 | sed -E 's#JOB \#u[0-9a-z]+/([0-9]+):.*#\1#')
  sh_ cmd jobscheduler run -f $PKG "$JID" >/dev/null
  wait_for "the app downloaded and stored the live feed" 'sh_ run-as '$PKG' ls files/feed | grep -q feed.bin' 60
  sh_ cmd alarm set-time $(( $(date +%s) * 1000 )) >/dev/null; sh_ settings put global auto_time 1
  post Bank listed1 "Your account is locked. Verify at https://$LISTED/login"
  wait_for "a link from the real scam list gets a warning" "have \"com.aegida.app 1000 Link:ScamLink:$LISTED\"" 10
  have "com.android.shell 2020 listed1" && ok "the scam message itself was left in place" || no "the scam message itself was left in place"

  adb reverse --remove tcp:8765 >/dev/null 2>&1
  powershell -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='python.exe'\" | Where-Object { \$_.CommandLine -match 'http.server 8765' } | ForEach-Object { Stop-Process -Id \$_.ProcessId }" >/dev/null 2>&1
  echo "$FAILS failed"; [ "$FAILS" = 0 ]
  ;;
failures)
  # The design's failure states (section 6) on a real system: the daily job offline, and a phone whose daily
  # check never runs. Asserts on what the app recorded in its own database, so it needs no screen (findings #39).
  FAILS=0
  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'

  restore() { sh_ cmd connectivity airplane-mode disable >/dev/null; sh_ cmd alarm set-time $(( $(date +%s) * 1000 )) >/dev/null; sh_ settings put global auto_time 1; }
  trap restore EXIT
  advance_hours() { sh_ settings put global auto_time 0; sh_ cmd alarm set-time $(( ($(date +%s) + $1 * 3600) * 1000 )) >/dev/null; }
  job_id() { sh_ dumpsys jobscheduler | grep -oE "JOB #u[0-9a-z]+/[0-9]+: [a-f0-9]+ com.aegida.app" | head -1 | sed -E 's#JOB \#u[0-9a-z]+/([0-9]+):.*#\1#'; }
  run_job() { sh_ cmd jobscheduler run -f $PKG "$(job_id)" >/dev/null; }
  # Reads the app's own database (debug build, run-as) and runs one query; prints the rows.
  db_query() {
    local dir="$TEMP/aegida-failures-db"; rm -rf "$dir"; mkdir -p "$dir"
    for f in aegida.db aegida.db-wal aegida.db-shm; do adb exec-out run-as $PKG cat databases/$f > "$dir/$f" 2>/dev/null; done
    python -I - "$dir/aegida.db" "$1" <<'PYEOF'
import sqlite3, sys
for row in sqlite3.connect(sys.argv[1]).execute(sys.argv[2]):
    print("|".join("" if c is None else str(c) for c in row))
PYEOF
  }
  Q_DAILY="select count(*) from check_run where \"trigger\" = 'Daily'"
  Q_OPEN="select group_concat(problem) from (select problem from finding where state = 'Open' order by problem)"
  feed_stat() { sh_ run-as $PKG stat -c '%s-%Y' files/feed/feed.bin 2>/dev/null; }

  echo "-- baseline: the app's own first check (right after install) fetches the live feed"
  # The app runs a first full check by itself when opened (findings #36), so wait for that; counts are relative.
  wait_for "the first daily check ran by itself and stored the live feed" '[ "$(db_query "$Q_DAILY")" -ge 1 ] 2>/dev/null && [ -n "$(feed_stat)" ]' 90
  FEED_BEFORE=$(feed_stat); DAILY_BEFORE=$(db_query "$Q_DAILY")

  echo "-- offline: the daily job runs with no network"
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  advance_hours 50; run_job
  wait_for "the daily check still ran while offline" '[ "$(db_query "$Q_DAILY")" -gt "$DAILY_BEFORE" ] 2>/dev/null' 60
  [ "$(feed_stat)" = "$FEED_BEFORE" ] && ok "the last good feed was kept, not replaced or deleted" || no "the last good feed was kept"
  [ -z "$(db_query "$Q_OPEN")" ] && ok "one offline day raises no false alarm" || no "one offline day raises no false alarm (open: $(db_query "$Q_OPEN"))"
  sh_ cmd connectivity airplane-mode disable >/dev/null

  echo "-- starved: the phone stops running the daily job for days (put to sleep)"
  advance_hours 120
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "a missed daily check and old lists are both noticed" '[ "$(db_query "$Q_OPEN")" = "ListsOld,MissedCheck" ]' 40
  have "com.aegida.app 1000 Health:MissedCheck" && no "info-level findings do not pop up a banner" || ok "info-level findings do not pop up a banner"

  echo "$FAILS failed"; [ "$FAILS" = 0 ]
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
*) sed -n 2,10p "$0"; exit 1;;
esac
