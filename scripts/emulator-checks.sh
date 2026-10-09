#!/usr/bin/env bash
# Emulator-first checks (design section 7). Run from Git Bash in the repo root:
#   scripts/emulator-checks.sh avd        create the API 36 Google Play AVD (Pixel profile; Mom's OS level)
#   scripts/emulator-checks.sh snapshot   save a clean quick-boot snapshot (run once, before installing the app)
#   scripts/emulator-checks.sh boot       start the emulator without a window, from that snapshot if there is one
#   scripts/emulator-checks.sh smoke     install the -local debug build and run the instrumented tests
#   scripts/emulator-checks.sh spike      check each adb/cmd mechanism the scenarios rely on (PASS/FAIL)
#   scripts/emulator-checks.sh popup-page serve the website-notification-spam fixture and open it in Chrome
#   scripts/emulator-checks.sh scenarios  feature scenarios on a cleared app: pop-up warning, messages, daily feed, listed link
#   scripts/emulator-checks.sh failures   offline daily job, and a phone that never runs its daily check (reads the app's database)
#   scripts/emulator-checks.sh guardian-offline  a warning that cannot reach the guardian is kept, retried, then reported (offline: nothing is sent)
#   scripts/emulator-checks.sh drift  access given to another app after setup is noticed, asked about, and cleared when removed (offline)
#   scripts/emulator-checks.sh new-apps  an app installed by hand and a remote-control app are noticed and cleared when removed (offline, fixture apps)
#   scripts/emulator-checks.sh stalkerware  a known spying app is found with the live feed and cleared when removed (fixture app; offline after the feed is stored)
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
boot|snapshot)
  # Starts the emulator without a window. `snapshot` makes a clean quick-boot snapshot called "clean" from a cold
  # boot (do it before installing the app); `boot` then starts from it in seconds instead of 30-70 s.
  PORT="${SERIAL#emulator-}"; SNAP_DIR="$HOME/.android/avd/$AVD_NAME.avd/snapshots/clean"
  EMU="$SDK/emulator/emulator.exe"; [ -f "$EMU" ] || EMU="$SDK/emulator/emulator"
  up() { [ "$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; }
  wait_boot() { for _ in $(seq 1 120); do up && return 0; sleep 2; done; return 1; }
  started=$(date +%s)
  if [ "$1" = "snapshot" ]; then
    adb emu kill >/dev/null 2>&1; sleep 3
    ("$EMU" -avd "$AVD_NAME" -port "$PORT" -no-window -no-audio -no-boot-anim -no-snapshot-load -no-snapshot-save >"${TEMP:-/tmp}/aegida-emu.log" 2>&1 &)
    wait_boot || { echo "emulator did not boot"; exit 1; }
    echo "cold boot took $(( $(date +%s) - started )) s; letting the system settle"; sleep 30
    adb emu avd snapshot save clean | tail -2
    adb emu kill >/dev/null 2>&1
    echo "saved snapshot 'clean'; start it with: scripts/emulator-checks.sh boot"
  else
    if up; then echo "an emulator is already running"; exit 0; fi
    if [ -d "$SNAP_DIR" ]; then FROM="-snapshot clean"; else FROM="-no-snapshot-load"; echo "no 'clean' snapshot yet, so a cold boot (make one with: snapshot)"; fi
    ("$EMU" -avd "$AVD_NAME" -port "$PORT" -no-window -no-audio -no-boot-anim $FROM -no-snapshot-save >"${TEMP:-/tmp}/aegida-emu.log" 2>&1 &)
    wait_boot || { echo "emulator did not boot"; exit 1; }
    echo "booted in $(( $(date +%s) - started )) s"
  fi
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
guardian-offline)
  # The guardian outbox on a real system, with NO network so nothing is ever sent to ntfy: a warning that cannot
  # be delivered is kept, retried, and after a day becomes "couldn't reach your guardian". Guardian settings are
  # seeded into the app's database (no sqlite3 on the phone, so the file is edited here and put back).
  FAILS=0
  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  LISTED=$(sed -n '4p' feedtool/src/test/resources/feeds/phishing-domains.txt | tr -d '\r')
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'
  restore() { sh_ cmd connectivity airplane-mode disable >/dev/null; sh_ cmd alarm set-time $(( $(date +%s) * 1000 )) >/dev/null; sh_ settings put global auto_time 1; [ -n "${KEEP:-}" ] || sh_ pm clear $PKG >/dev/null; }
  trap restore EXIT
  job_id() { sh_ dumpsys jobscheduler | grep -oE "JOB #u[0-9a-z]+/[0-9]+: [a-f0-9]+ com.aegida.app" | head -1 | sed -E 's#JOB \#u[0-9a-z]+/([0-9]+):.*#\1#'; }
  DB="$TEMP/aegida-gd-db"; rm -rf "$DB"; mkdir -p "$DB"
  pull_db() { for f in aegida.db aegida.db-wal aegida.db-shm; do adb exec-out run-as $PKG cat databases/$f > "$DB/$f" 2>/dev/null; done; }
  db_query() { pull_db; python -I - "$DB/aegida.db" "$1" <<'PYEOF'
import sqlite3, sys
for row in sqlite3.connect(sys.argv[1]).execute(sys.argv[2]):
    print("|".join("" if c is None else str(c) for c in row))
PYEOF
  }
  wait_for "the first daily check ran by itself" '[ "$(db_query "select count(*) from check_run where \"trigger\" = '"'"'Daily'"'"'")" -ge 1 ] 2>/dev/null' 90

  # No network from here on: the seeded guardian must never be sent a real message. (Turning the listener off
  # raises its own warning, which would otherwise go out to the throwaway topic.)
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  echo "-- seed: alerts on, a guardian paired (a throwaway topic nobody listens to)"
  # The system rebinds the listener (and so restarts the app) right after a force-stop, which would hold the database open
  # while it is replaced; switch the listener off first.
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ am force-stop $PKG >/dev/null; sleep 2
  [ -z "$(sh_ pidof $PKG)" ] || { sh_ am force-stop $PKG >/dev/null; sleep 2; }
  pull_db
  python -I - "$DB/aegida.db" <<'PYEOF'
import sqlite3, sys, secrets
c = sqlite3.connect(sys.argv[1])
for k, v in [("guardians.alertsOn", "true"), ("guardians.ownerName", "Ruth"), ("guardians.firstTopic", "aegida_" + secrets.token_urlsafe(24).replace("-", "x")[:32]), ("guardians.firstName", "Kevin")]:
    c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))
c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
PYEOF
  adb push "$DB/aegida.db" /data/local/tmp/seed.db >/dev/null
  sh_ "chmod 666 /data/local/tmp/seed.db; run-as $PKG sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db"
  sh_ am start -n "$PKG/.MainActivity" >/dev/null; sleep 4
  # A force-stop unbinds the listener; allow it again so the system binds it to the restarted app.
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sleep 10

  echo "-- offline: a listed scam link arrives and the guardian cannot be told"
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  post Bank listed1 "Your account is locked. Verify at https://$LISTED/login"
  wait_for "Ruth is warned" "have \"com.aegida.app 1000 Link:ScamLink:$LISTED\"" 15
  wait_for "the undelivered message is kept in the outbox" '[ "$(db_query "select count(*) from outbox where outboxProblem = '"'"'ScamLink'"'"'")" = 1 ] 2>/dev/null' 15
  Q_HIST="select count(*) from history where historyProblem = 'ScamLink'"
  wait_for "history says she was warned and the guardian was not reached" '[ "$(db_query "$Q_HIST")" = 2 ]' 15

  echo "-- a day later, still offline"
  sh_ settings put global auto_time 0; sh_ cmd alarm set-time $(( ($(date +%s) + 25 * 3600) * 1000 )) >/dev/null
  sh_ cmd jobscheduler run -f $PKG "$(job_id)" >/dev/null
  Q_TRIES="select max(attempts) from outbox"
  wait_for "the oldest message was retried and failed again (the rest wait their turn)" '[ "$(db_query "$Q_TRIES")" = 2 ]' 60
  wait_for "'couldn't reach your guardian' becomes a problem" '[ "$(db_query "select count(*) from finding where problem = '"'"'GuardianUnreachable'"'"' and state = '"'"'Open'"'"'")" = 1 ] 2>/dev/null' 30
  have "com.aegida.app 1000 Delivery:GuardianUnreachable" && ok "she is told to call her guardian" || no "she is told to call her guardian"

  echo "$FAILS failed"; [ "$FAILS" = 0 ]
  ;;
drift)
  # Special access that appears after setup (findings #45). The phone's own Accessibility Menu stands in for a stranger's app. Offline, so nothing is sent. Setup is marked finished
  # in the app's database (see guardian-offline for why the file is edited with the listener switched off).
  FAILS=0
  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'
  restore() {
    sh_ cmd connectivity airplane-mode disable >/dev/null; sh_ settings delete secure enabled_accessibility_services >/dev/null
    sh_ settings delete secure enabled_notification_listeners >/dev/null; [ -n "${KEEP:-}" ] || sh_ pm clear $PKG >/dev/null
  }
  trap restore EXIT
  DB="$TEMP/aegida-drift-db"; rm -rf "$DB"; mkdir -p "$DB"
  pull_db() { for f in aegida.db aegida.db-wal aegida.db-shm; do adb exec-out run-as $PKG cat databases/$f > "$DB/$f" 2>/dev/null; done; }
  db_query() { pull_db; python -I - "$DB/aegida.db" "$1" <<'PYEOF'
import sqlite3, sys
for row in sqlite3.connect(sys.argv[1]).execute(sys.argv[2]):
    print("|".join("" if c is None else str(c) for c in row))
PYEOF
  }
  Q_OPEN="select group_concat(problem || ':' || subject) from finding where state = 'Open'"
  Q_BASE="select value from setting where name = 'baseline.taken'"
  wait_for "the first daily check ran by itself" '[ "$(db_query "select count(*) from check_run where \"trigger\" = '"'"'Daily'"'"'")" -ge 1 ] 2>/dev/null' 90

  echo "-- setup is finished: the next check takes the baseline"
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ am force-stop $PKG >/dev/null; sleep 2
  [ -z "$(sh_ pidof $PKG)" ] || { sh_ am force-stop $PKG >/dev/null; sleep 2; }
  pull_db
  python -I - "$DB/aegida.db" <<'PYEOF'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1])
c.execute("insert or replace into setting(name, value) values ('setup.stepsDone', '8')")
c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
PYEOF
  adb push "$DB/aegida.db" /data/local/tmp/seed.db >/dev/null
  sh_ "chmod 666 /data/local/tmp/seed.db; run-as $PKG sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db"
  sh_ am start -n "$PKG/.MainActivity" >/dev/null; sleep 4
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sleep 10
  wait_for "the baseline was taken" '[ "$(db_query "$Q_BASE")" = true ]' 30
  [ -z "$(db_query "$Q_OPEN")" ] && ok "nothing is flagged on the phone as it was at setup" || no "nothing is flagged at setup (open: $(db_query "$Q_OPEN"))"

  echo "-- an app is given control of the screen and another can read notifications"
  sh_ settings put secure enabled_accessibility_services "com.android.systemui.accessibility.accessibilitymenu/com.android.systemui.accessibility.accessibilitymenu.AccessibilityMenuService"
  sh_ settings put secure enabled_notification_listeners "$PKG/$PKG.AegidaListenerService:org.spy.app/org.spy.app.Listener"
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "both are noticed" '[ "$(db_query "$Q_OPEN")" = "AccessibilityAdded:com.android.systemui.accessibility.accessibilitymenu,ListenerAdded:org.spy.app" ] || [ "$(db_query "$Q_OPEN")" = "ListenerAdded:org.spy.app,AccessibilityAdded:com.android.systemui.accessibility.accessibilitymenu" ]' 40
  have "com.aegida.app 1000 Drift:AccessibilityAdded:com.android.systemui.accessibility.accessibilitymenu" && ok "she gets a banner for the screen control" || no "she gets a banner for the screen control"

  echo "-- the access is switched off again"
  sh_ settings delete secure enabled_accessibility_services >/dev/null
  sh_ settings put secure enabled_notification_listeners "$PKG/$PKG.AegidaListenerService"
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the questions are gone" '[ -z "$(db_query "$Q_OPEN")" ]' 40
  have "com.aegida.app 1000 Drift:AccessibilityAdded" && no "the banners were cleared" || ok "the banners were cleared"

  echo "$FAILS failed"; [ "$FAILS" = 0 ]
  ;;
new-apps)
  # Apps that arrive after setup (findings #46): one installed by hand, and one that looks like a remote-control app.
  # Fixture apps are built by scripts/make-fixture-apk.sh and signed with the debug test key. Offline. Offline, so nothing is sent. Setup is marked finished
  # in the app's database (see guardian-offline for why the file is edited with the listener switched off).
  FAILS=0
  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'
  restore() {
    sh_ cmd connectivity airplane-mode disable >/dev/null; sh_ pm uninstall org.fixture.sideloaded >/dev/null; sh_ pm uninstall com.anydesk.anydeskandroid >/dev/null
    [ -n "${KEEP:-}" ] || sh_ pm clear $PKG >/dev/null
  }
  trap restore EXIT
  DB="$TEMP/aegida-drift-db"; rm -rf "$DB"; mkdir -p "$DB"
  pull_db() { for f in aegida.db aegida.db-wal aegida.db-shm; do adb exec-out run-as $PKG cat databases/$f > "$DB/$f" 2>/dev/null; done; }
  db_query() { pull_db; python -I - "$DB/aegida.db" "$1" <<'PYEOF'
import sqlite3, sys
for row in sqlite3.connect(sys.argv[1]).execute(sys.argv[2]):
    print("|".join("" if c is None else str(c) for c in row))
PYEOF
  }
  Q_OPEN="select group_concat(problem || ':' || subject) from finding where state = 'Open'"
  Q_BASE="select value from setting where name = 'baseline.taken'"
  wait_for "the first daily check ran by itself" '[ "$(db_query "select count(*) from check_run where \"trigger\" = '"'"'Daily'"'"'")" -ge 1 ] 2>/dev/null' 90

  echo "-- setup is finished: the next check takes the baseline"
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ am force-stop $PKG >/dev/null; sleep 2
  [ -z "$(sh_ pidof $PKG)" ] || { sh_ am force-stop $PKG >/dev/null; sleep 2; }
  pull_db
  python -I - "$DB/aegida.db" <<'PYEOF'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1])
c.execute("insert or replace into setting(name, value) values ('setup.stepsDone', '8')")
c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
PYEOF
  adb push "$DB/aegida.db" /data/local/tmp/seed.db >/dev/null
  sh_ "chmod 666 /data/local/tmp/seed.db; run-as $PKG sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db"
  sh_ am start -n "$PKG/.MainActivity" >/dev/null; sleep 4
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sleep 10
  wait_for "the baseline was taken" '[ "$(db_query "$Q_BASE")" = true ]' 30
  [ -z "$(db_query "$Q_OPEN")" ] && ok "nothing is flagged on the phone as it was at setup" || no "nothing is flagged at setup (open: $(db_query "$Q_OPEN"))"

  echo "-- an app is installed by hand and a remote-control app is installed"
  bash scripts/make-fixture-apk.sh org.fixture.sideloaded >/dev/null; bash scripts/make-fixture-apk.sh com.anydesk.anydeskandroid >/dev/null
  adb install build/fixture-apps/org.fixture.sideloaded.apk >/dev/null; adb install build/fixture-apps/com.anydesk.anydeskandroid.apk >/dev/null
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "both are noticed" '[ "$(db_query "$Q_OPEN")" = "NewAppOutsidePlay:org.fixture.sideloaded,RemoteControlApp:com.anydesk.anydeskandroid" ] || [ "$(db_query "$Q_OPEN")" = "RemoteControlApp:com.anydesk.anydeskandroid,NewAppOutsidePlay:org.fixture.sideloaded" ]' 40
  have "com.aegida.app 1000 NewApp:NewAppOutsidePlay:org.fixture.sideloaded" && ok "she gets a banner for the app installed by hand" || no "she gets a banner for the app installed by hand"
  have "com.aegida.app 1000 RemoteControl:RemoteControlApp:com.anydesk.anydeskandroid" && ok "she gets a banner for the remote-control app" || no "she gets a banner for the remote-control app"

  echo "-- both are uninstalled again"
  sh_ pm uninstall org.fixture.sideloaded >/dev/null; sh_ pm uninstall com.anydesk.anydeskandroid >/dev/null
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the questions are gone" '[ -z "$(db_query "$Q_OPEN")" ]' 40
  have "com.aegida.app 1000 NewApp" && no "the banners were cleared" || ok "the banners were cleared"

  echo "$FAILS failed"; [ "$FAILS" = 0 ]
  ;;
stalkerware)
  # A known spying app (a do-nothing fixture named like one on Echap's list, findings #47) is found using the LIVE signed
  # feed the app downloads by itself first; then the phone goes offline. Fixture built by scripts/make-fixture-apk.sh. Offline, so nothing is sent. Setup is marked finished
  # in the app's database (see guardian-offline for why the file is edited with the listener switched off).
  FAILS=0
  export JAVA_HOME="${JAVA_HOME:-/c/Program Files/Android/Android Studio/jbr}"; export ANDROID_SERIAL="$SERIAL"
  ./gradlew.bat installDebug -PlocalBuild --console=plain 2>&1 | grep -q "BUILD SUCCESSFUL" || { echo "install failed"; exit 1; }
  sh_ pm clear $PKG >/dev/null
  sh_ pm grant $PKG android.permission.POST_NOTIFICATIONS
  sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null
  sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the 'Aegida is protecting you' notice is up" 'have "com.aegida.app 1 null"'
  restore() {
    sh_ cmd connectivity airplane-mode disable >/dev/null; sh_ pm uninstall com.spyzee >/dev/null
    [ -n "${KEEP:-}" ] || sh_ pm clear $PKG >/dev/null
  }
  trap restore EXIT
  DB="$TEMP/aegida-drift-db"; rm -rf "$DB"; mkdir -p "$DB"
  pull_db() { for f in aegida.db aegida.db-wal aegida.db-shm; do adb exec-out run-as $PKG cat databases/$f > "$DB/$f" 2>/dev/null; done; }
  db_query() { pull_db; python -I - "$DB/aegida.db" "$1" <<'PYEOF'
import sqlite3, sys
for row in sqlite3.connect(sys.argv[1]).execute(sys.argv[2]):
    print("|".join("" if c is None else str(c) for c in row))
PYEOF
  }
  Q_OPEN="select group_concat(problem || ':' || subject) from finding where state = 'Open'"
  Q_BASE="select value from setting where name = 'baseline.taken'"
  wait_for "the first daily check ran by itself and stored the live feed" '[ "$(db_query "select count(*) from check_run where \"trigger\" = '"'"'Daily'"'"'")" -ge 1 ] 2>/dev/null && sh_ run-as '$PKG' ls files/feed | grep -q feed.bin' 90

  echo "-- setup is finished: the next check takes the baseline"
  sh_ cmd connectivity airplane-mode enable >/dev/null; sleep 5
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ am force-stop $PKG >/dev/null; sleep 2
  [ -z "$(sh_ pidof $PKG)" ] || { sh_ am force-stop $PKG >/dev/null; sleep 2; }
  pull_db
  python -I - "$DB/aegida.db" <<'PYEOF'
import sqlite3, sys
c = sqlite3.connect(sys.argv[1])
c.execute("insert or replace into setting(name, value) values ('setup.stepsDone', '8')")
c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
PYEOF
  adb push "$DB/aegida.db" /data/local/tmp/seed.db >/dev/null
  sh_ "chmod 666 /data/local/tmp/seed.db; run-as $PKG sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db"
  sh_ am start -n "$PKG/.MainActivity" >/dev/null; sleep 4
  sh_ cmd notification disallow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sh_ cmd notification allow_listener "$PKG/$PKG.AegidaListenerService" >/dev/null; sleep 10
  wait_for "the baseline was taken" '[ "$(db_query "$Q_BASE")" = true ]' 30
  [ -z "$(db_query "$Q_OPEN")" ] && ok "nothing is flagged on the phone as it was at setup" || no "nothing is flagged at setup (open: $(db_query "$Q_OPEN"))"

  echo "-- an app named like a known spying app is installed (installed by hand, so it is not also a new-app question)"
  bash scripts/make-fixture-apk.sh com.spyzee >/dev/null
  adb install build/fixture-apps/com.spyzee.apk >/dev/null
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "it is noticed, and only as a spying app" '[ "$(db_query "$Q_OPEN")" = "StalkerwareApp:com.spyzee" ]' 40
  have "com.aegida.app 1000 Stalkerware:StalkerwareApp:com.spyzee" && ok "she gets a banner" || no "she gets a banner"

  echo "-- it is uninstalled"
  sh_ pm uninstall com.spyzee >/dev/null
  sh_ am force-stop $PKG; sh_ am start -n "$PKG/.MainActivity" >/dev/null
  wait_for "the warning is gone" '[ -z "$(db_query "$Q_OPEN")" ]' 40
  have "com.aegida.app 1000 Stalkerware" && no "the banner was cleared" || ok "the banner was cleared"

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
*) sed -n 2,12p "$0"; exit 1;;
esac
