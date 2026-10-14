"""
Emulator check of findings #111, #112 and #134 (docs/scenarios/health.md H3, H4): the late check of notices that came while the
listener was not bound, and Home's "can't see your notices" step, without relying on onListenerDisconnected.

Android on the emulator rebinds the listener within seconds and has no command to unbind it while access stays on, so step 2 uses
the debug-only DebugListenerReceiver (app/src/debug) to clear the in-process "bound now" flag and seed "last seen bound". Offline
from the guardian's side: alerts are off, nothing is sent. Screenshots go to build/emulator-checks/listener/. Refuses any serial
that is not emulator-*.

Step 2 needs the emulator up for over an hour and Aegida installed or updated over an hour ago: the step is never raised in the
first hour after either (health H4e, H4g). On a fresh emulator the script checks that it is not raised, then says to rerun it
later with --no-install (pm clear keeps the install time).

    python scripts/emulator_listener.py [serial] [--no-install]
"""
import os, re, sqlite3, subprocess, sys, tempfile, time
import xml.etree.ElementTree as ET

ARGS = [a for a in sys.argv[1:] if not a.startswith("--")]
SERIAL = ARGS[0] if ARGS else "emulator-5554"
INSTALL = "--no-install" not in sys.argv
if not SERIAL.startswith("emulator-"):
    sys.exit("refusing: %s is not an emulator" % SERIAL)
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
PKG = "com.aegida.app"
LISTENER = "%s/%s.AegidaListenerService" % (PKG, PKG)
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHOTS = os.path.join(ROOT, "build", "emulator-checks", "listener")
TMP = tempfile.mkdtemp(prefix="aegida-listener-")
LOST_TEXT = "Aegida can't see your notices right now. Tap, then turn Aegida off and on again."
FAILS = 0


def adb(*args, binary=False):
    p = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True)
    return p.stdout if binary else p.stdout.decode("utf-8", "replace").replace("\r", "")


def sh(cmd):
    return adb("shell", cmd).strip()


def check(cond, msg):
    global FAILS
    print(("  PASS " if cond else "  FAIL ") + msg, flush=True)
    if not cond:
        FAILS += 1
    return cond


def wait_for(msg, fn, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        try:
            v = fn()
        except sqlite3.DatabaseError:  # the app has not created its database yet
            v = None
        if v:
            check(True, msg)
            return v
        time.sleep(1)
    check(False, msg)
    return None


def db_rows(sql):
    d = os.path.join(TMP, "db")
    os.makedirs(d, exist_ok=True)
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    c = sqlite3.connect(os.path.join(d, "aegida.db"))
    try:
        return c.execute(sql).fetchall()
    finally:
        c.close()


def open_findings():
    return sorted(r[0] + ":" + r[1] for r in db_rows("select problem, subject from finding where state in ('Open','Escalated')"))


def banners():
    return re.findall(r"pkg=com\.aegida\.app .*?tag=([^ ]*)", sh("dumpsys notification --noredact"))


def node_with(text):
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    if "<hierarchy" not in xml:
        return None
    for n in ET.fromstring(xml.encode("utf-8")).iter("node"):
        if text in (n.get("text") or "") or text in (n.get("content-desc") or ""):
            return n
    return None


def tap(node):
    l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
    sh("input tap %d %d" % ((l + r) // 2, (t + b) // 2))


def broadcast(extra):
    sh("am broadcast -a com.aegida.app.debug.LISTENER_STATE -p %s %s" % (PKG, extra))


def reopen():
    """Home then back to Aegida: resuming runs a check (AppStart)."""
    sh("input keyevent KEYCODE_HOME")
    time.sleep(1)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(4)


def shot(name):
    os.makedirs(SHOTS, exist_ok=True)
    path = os.path.join(SHOTS, name + ".png")
    with open(path, "wb") as fh:
        fh.write(adb("exec-out", "screencap", "-p", binary=True))
    print("  shot " + path, flush=True)


def seed_setup_done():
    sh("am force-stop " + PKG)
    sh("cmd notification disallow_listener " + LISTENER)
    sh("am force-stop " + PKG)
    time.sleep(2)
    d = os.path.join(TMP, "seed")
    os.makedirs(d, exist_ok=True)
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    c = sqlite3.connect(os.path.join(d, "aegida.db"))
    for k, v in [("setup.stepsDone", "8"), ("guardians.alertsOn", "false"), ("guardians.ownerName", "Ruth")]:
        c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))
    c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
    adb("push", os.path.join(d, "aegida.db"), "/data/local/tmp/seed.db")
    sh("chmod 666 /data/local/tmp/seed.db; run-as %s sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db" % PKG)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(5)
    sh("cmd notification allow_listener " + LISTENER)
    time.sleep(6)


# ======================================================================================
env = dict(os.environ, ANDROID_SERIAL=SERIAL)
if INSTALL:
    print("-- build and install", flush=True)
    r = subprocess.run([os.path.join(ROOT, "gradlew.bat"), "installDebug", "-PlocalBuild", "--console=plain"], cwd=ROOT, env=env, capture_output=True, text=True)
    check("BUILD SUCCESSFUL" in r.stdout, "installed the -local debug build")
sh("pm clear " + PKG)
sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
sh("cmd notification allow_listener " + LISTENER)
sh("am start -n %s/.MainActivity" % PKG)
wait_for("the first daily check ran", lambda: db_rows("select count(*) from check_run where \"trigger\"='Daily'")[0][0] >= 1, 90)
seed_setup_done()
wait_for("the baseline was taken", lambda: db_rows("select value from setting where name='baseline.taken'") == [("true",)], 40)
wait_for("a check saw the listener bound and recorded it", lambda: db_rows("select value from setting where name='listener_last_seen_bound'"), 20)
check(open_findings() == [], "nothing open after setup (%s)" % open_findings())

print("-- 2. access on, not bound, last seen bound two hours ago: Home says it cannot see her notices", flush=True)
uptime = float(sh("cat /proc/uptime").split()[0])
m = re.search(r"lastUpdateTime=(\S+ \S+)", sh("dumpsys package " + PKG))
installed_ago = time.time() - time.mktime(time.strptime(m.group(1), "%Y-%m-%d %H:%M:%S")) if m else 0
STEP2_DONE = uptime > 3700 and installed_ago > 3700
if not STEP2_DONE:
    broadcast("--ez bound false --el lastSeenBound %d" % int((time.time() - 2 * 3600) * 1000))
    time.sleep(2)
    reopen()
    time.sleep(3)
    check("ListenerNotConnected:aegida" not in open_findings(),
          "not raised within the first hour after the emulator started (%d s) or Aegida was installed (%d s ago)" % (uptime, installed_ago))
    print("  STEP 2 NOT RUN: rerun with --no-install once the emulator has been up and Aegida installed for over an hour", flush=True)
    # Back to "seen bound just now", so notices left in the shade by earlier runs are not checked late by the next bind.
    broadcast("--el lastSeenBound %d" % int(time.time() * 1000))
    sh("cmd notification disallow_listener " + LISTENER)
    time.sleep(2)
    sh("cmd notification allow_listener " + LISTENER)
    time.sleep(6)
else:
    two_hours_ago = int((time.time() - 2 * 3600) * 1000)
    broadcast("--ez bound false --el lastSeenBound %d" % two_hours_ago)
    time.sleep(2)
    reopen()
    wait_for("ListenerNotConnected is open", lambda: "ListenerNotConnected:aegida" in open_findings(), 20)
    check(any("ListenerNotConnected" in b for b in banners()), "a banner was posted (%s)" % banners())
    check(node_with(LOST_TEXT) is not None or node_with("can't see your notices") is not None, "Home shows the step")
    for mode in ("no", "yes"):
        sh("cmd uimode night " + mode)
        for scale in ("1.0", "2.0"):
            sh("settings put system font_scale " + scale)
            time.sleep(3)
            theme = "light" if mode == "no" else "dark"
            shot("home-%s-%s" % (theme, "200" if scale == "2.0" else "100"))
            sh("cmd statusbar expand-notifications")
            time.sleep(2)
            shot("banner-%s-%s" % (theme, "200" if scale == "2.0" else "100"))
            sh("cmd statusbar collapse")
            time.sleep(1)
    sh("settings put system font_scale 1.0")
    sh("cmd uimode night no")
    time.sleep(3)
    step = node_with("can't see your notices")
    if check(step is not None, "the step can be tapped"):
        tap(step)
        time.sleep(3)
        top = sh("dumpsys activity activities | grep -m1 -E 'topResumedActivity|mResumedActivity'")
        check("NotificationAccess" in top or "notification" in top.lower(), "the tap opened notification access (%s)" % top.strip())
        sh("input keyevent KEYCODE_BACK")
        time.sleep(2)
    print("-- re-allowing clears it", flush=True)
    # Back to "seen bound just now", so notices left in the shade by earlier runs are not checked late by the next bind.
    broadcast("--el lastSeenBound %d" % int(time.time() * 1000))
    sh("cmd notification disallow_listener " + LISTENER)
    time.sleep(2)
    sh("cmd notification allow_listener " + LISTENER)
    wait_for("ListenerNotConnected cleared once bound again", lambda: "ListenerNotConnected:aegida" not in open_findings(), 30)

print("-- 1. a look-alike scam text posted while not bound is warned about once, late", flush=True)
sh("cmd notification disallow_listener " + LISTENER)
time.sleep(3)
sh("cmd notification post -S bigtext -t 'Delivery' 'late%d' 'Your parcel is held. Pay the fee at paypa1-help.example/claim'" % int(time.time()))
time.sleep(2)
check(not any("paypa1" in f for f in open_findings()), "not heard while access was off")
posted_before = db_rows("select count(*) from check_run where \"trigger\"='NotificationPosted'")[0][0]
sh("cmd notification allow_listener " + LISTENER)
wait_for("the late warning appears", lambda: any("LookalikeLink:paypa1-help.example" in f for f in open_findings()), 30)
check(sum("paypa1-help.example" in b for b in banners()) == 1, "one banner (%s)" % banners())
late = db_rows("select count(*) from check_run where \"trigger\"='NotificationPosted'")[0][0] - posted_before
# One per notice posted while not bound: the text, plus the group summary Android adds when the shell already has a notice
# in the shade from an earlier run.
check(late in (1, 2), "one late check per notice that came while not bound (%d)" % late)
for _ in range(2):
    sh("cmd notification disallow_listener " + LISTENER)
    time.sleep(2)
    sh("cmd notification allow_listener " + LISTENER)
    time.sleep(6)
again = db_rows("select count(*) from check_run where \"trigger\"='NotificationPosted'")[0][0] - posted_before
check(again == late, "two more rebinds checked nothing again (%d late checks in all)" % again)
check(sum("paypa1-help.example" in b for b in banners()) == 1, "still one banner")
check(len([f for f in open_findings() if "paypa1" in f]) == 1, "one finding")

sh("settings put system font_scale 1.0")
sh("cmd uimode night no")
print("\n%s (%d failed)%s" % ("ALL PASS" if FAILS == 0 else "FAILURES", FAILS, "" if STEP2_DONE else "; step 2 not run"))
sys.exit(1 if FAILS else 0)
