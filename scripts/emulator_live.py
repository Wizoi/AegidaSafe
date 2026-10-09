"""
Live end-to-end run on the emulator against the real ntfy.sh (findings #69).

The emulator is the owner's phone; this script is the guardian: it reads the throwaway topics from the computer, the
same server and the same messages a guardian's ntfy app would get. Real messages are sent to two random topics that
nobody else knows. Needs the Aegida36 emulator running (scripts/emulator-checks.sh boot). Refuses any serial that is
not emulator-*.

    python scripts/emulator_live.py [serial]
"""
import json, os, re, secrets, sqlite3, subprocess, sys, tempfile, time, urllib.request
import xml.etree.ElementTree as ET

SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
if not SERIAL.startswith("emulator-"):
    sys.exit("refusing: %s is not an emulator" % SERIAL)
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
PKG = "com.aegida.app"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TMP = tempfile.mkdtemp(prefix="aegida-live-")
FAILS = 0
LISTED = [l.strip() for l in open(os.path.join(ROOT, "feedtool/src/test/resources/feeds/phishing-domains.txt"), encoding="utf-8") if l.strip() and not l.startswith("#")]


def adb(*args, check=False, binary=False):
    p = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True)
    out = p.stdout if binary else p.stdout.decode("utf-8", "replace").replace("\r", "")
    return out


def sh(cmd):
    return adb("shell", cmd).strip()


def ok(msg):
    print("PASS ", msg, flush=True)


def no(msg):
    global FAILS
    FAILS += 1
    print("FAIL ", msg, flush=True)


def check(cond, msg):
    ok(msg) if cond else no(msg)


def wait_for(msg, fn, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        try:
            v = fn()
        except Exception:
            v = None
        if v:
            ok(msg)
            return v
        time.sleep(1)
    no(msg)
    return None


# ---- the guardian side: read a topic from ntfy.sh
def topic_events(topic, scheduled=False):
    url = "https://ntfy.sh/%s/json?poll=1&since=all%s" % (topic, "&scheduled=1" if scheduled else "")
    lines = urllib.request.urlopen(url, timeout=20).read().decode("utf-8").strip().splitlines()
    return [e for e in (json.loads(l) for l in lines if l.strip()) if e.get("event") == "message"]


def delivered(topic):
    return [e for e in topic_events(topic) if not (e.get("sequence_id") == "heartbeat")]


def pending_heartbeats(topic):
    return [e for e in topic_events(topic, scheduled=True) if e.get("sequence_id") == "heartbeat" and e["time"] > time.time()]


def texts(events):
    return [e.get("message", "") for e in events]


# ---- the phone side
def db_rows(sql):
    d = os.path.join(TMP, "db")
    os.makedirs(d, exist_ok=True)
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    return sqlite3.connect(os.path.join(d, "aegida.db")).execute(sql).fetchall()


def banners():
    out = sh("dumpsys notification --noredact")
    return [m for m in re.findall(r"pkg=com\.aegida\.app .*?tag=([^ ]*)", out)]


def job_id():
    m = re.search(r"JOB #u[0-9a-z]+/(\d+): [a-f0-9]+ com\.aegida\.app", sh("dumpsys jobscheduler"))
    return m.group(1) if m else None


def run_daily():
    sh("settings put global auto_time 0")  # the job runs even if it is not due (-f)
    sh("cmd jobscheduler run -f %s %s" % (PKG, job_id()))


def restart_app():
    sh("am force-stop " + PKG)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(6)


def rebind_listener():
    sh("cmd notification disallow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
    sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
    time.sleep(8)


def ui():
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    return ET.fromstring(xml.encode("utf-8")) if xml.strip().startswith("<?xml") or "<hierarchy" in xml else None


def center(node):
    l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
    return (l + r) // 2, (t + b) // 2


def find_text(text, root=None):
    root = root if root is not None else ui()
    if root is None:
        return None
    for n in root.iter("node"):
        if n.get("text") == text or n.get("content-desc") == text:
            return n
    return None


def tap_text(text, timeout=15):
    end = time.time() + timeout
    while time.time() < end:
        n = find_text(text)
        if n is not None:
            x, y = center(n)
            sh("input tap %d %d" % (x, y))
            time.sleep(2)
            return True
        time.sleep(1)
    no("could not find '%s' on screen" % text)
    return False


def post(tag, text, title="Bank"):
    sh("cmd notification post -S bigtext -t '%s' '%s' '%s'" % (title, tag, text))


def seed(topic, owner, guardian, alerts_on=True):
    sh("cmd notification disallow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
    sh("am force-stop " + PKG)
    time.sleep(2)
    d = os.path.join(TMP, "seed")
    os.makedirs(d, exist_ok=True)
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    c = sqlite3.connect(os.path.join(d, "aegida.db"))
    for k, v in [("setup.stepsDone", "8"), ("guardians.alertsOn", "true" if alerts_on else "false"), ("guardians.ownerName", owner),
                 ("guardians.firstTopic", topic), ("guardians.firstName", guardian), ("guardians.firstEmail", "guardian@example.org")]:
        c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))
    c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
    adb("push", os.path.join(d, "aegida.db"), "/data/local/tmp/seed.db")
    sh("chmod 666 /data/local/tmp/seed.db; run-as %s sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db" % PKG)
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(5)
    rebind_listener()


def fixture(pkg):
    path = os.path.join(ROOT, "build", "fixture-apps", pkg + ".apk")
    subprocess.run(["bash", os.path.join(ROOT, "scripts", "make-fixture-apk.sh"), pkg], cwd=ROOT, capture_output=True)
    adb("install", path)


def open_findings():
    return sorted(r[0] + ":" + r[1] for r in db_rows("select problem, subject from finding where state in ('Open','Escalated')"))


# ======================================================================================
env = dict(os.environ, ANDROID_SERIAL=SERIAL)
print("-- build and install", flush=True)
r = subprocess.run([os.path.join(ROOT, "gradlew.bat"), "installDebug", "-PlocalBuild", "--console=plain"], cwd=ROOT, env=env, capture_output=True, text=True)
check("BUILD SUCCESSFUL" in r.stdout, "installed the -local debug build")
sh("pm clear " + PKG)
sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
sh("am start -n %s/.MainActivity" % PKG)
wait_for("the first daily check ran and stored the live feed", lambda: db_rows("select count(*) from check_run where \"trigger\"='Daily'")[0][0] >= 1 and "feed.bin" in sh("run-as %s ls files/feed" % PKG), 90)

TOPIC = "aegida_live_" + secrets.token_hex(10)
TOPIC2 = "aegida_live_" + secrets.token_hex(10)
print("-- seed: setup finished, alerts on, owner 'José' (accent), guardian topic", TOPIC, flush=True)
seed(TOPIC, "José", "Sam")
wait_for("the baseline was taken", lambda: db_rows("select value from setting where name='baseline.taken'") == [("true",)], 40)
check(open_findings() == [], "nothing is flagged on the phone as it was at setup (%s)" % open_findings())

try:
    print("-- 1. the heartbeat: armed by the daily check, replaced (not added) by the next one", flush=True)
    run_daily()
    first = wait_for("a 'hasn't checked in' message is waiting two days out", lambda: pending_heartbeats(TOPIC), 60)
    time.sleep(4)
    run_daily()
    time.sleep(8)
    now_pending = pending_heartbeats(TOPIC)
    check(len(now_pending) == 1 and first and now_pending[0]["id"] != first[0]["id"], "the next daily check replaced it: still exactly one waiting")
    if now_pending:
        gap = now_pending[0]["time"] - time.time()
        check(abs(gap - 172800) < 600, "it is due in about 2 days (%.1f h)" % (gap / 3600))
        check("hasn't checked in" in now_pending[0]["message"] and "José" in now_pending[0]["message"], "its words use her name: %r" % now_pending[0]["message"])

    print("-- 2. a new app from outside Play, and she says it was hers", flush=True)
    fixture("org.fixture.sideloaded")
    restart_app()
    wait_for("a new-app question is open", lambda: open_findings() == ["NewAppOutsidePlay:org.fixture.sideloaded"], 40)
    msgs = wait_for("the guardian is told", lambda: [t for t in texts(delivered(TOPIC)) if "outside the Play Store" in t], 40)
    if msgs:
        check("José" in msgs[0], "in her name: %r" % msgs[0])
    ev = [e for e in delivered(TOPIC) if "outside the Play Store" in e.get("message", "")]
    check(ev and ev[0].get("title") == "José", "the title decodes to the accented name: %r" % (ev[0].get("title") if ev else None))
    if tap_text("Fix my phone") and tap_text("Yes, that was me"):
        wait_for("the question is gone", lambda: open_findings() == [], 30)
        wait_for("the guardian hears she said it was hers (calm words)", lambda: [t for t in texts(delivered(TOPIC)) if "Nothing more to do" in t], 40)
    sh("pm uninstall org.fixture.sideloaded")

    print("-- 3. a remote-control app (urgent), and she says it was hers", flush=True)
    fixture("com.anydesk.anydeskandroid")
    restart_app()
    wait_for("a remote-control question is open", lambda: open_findings() == ["RemoteControlApp:com.anydesk.anydeskandroid"], 40)
    ev = wait_for("the guardian is told, urgently", lambda: [e for e in delivered(TOPIC) if "control" in e.get("message", "") and "far away" in e.get("message", "")], 40)
    check(ev and ev[0].get("priority") == 5, "priority is urgent (5): %r" % (ev[0].get("priority") if ev else None))
    if tap_text("Fix my phone") and tap_text("Yes, that was me"):
        wait_for("the guardian's follow-up does NOT say everything is fine",
                 lambda: [t for t in texts(delivered(TOPIC)) if "If you were not expecting it, please call her" in t], 40)
    sh("pm uninstall com.anydesk.anydeskandroid")

    print("-- 4. a known spying app (Echap list, live signed feed), then removed", flush=True)
    fixture("com.spyzee")
    restart_app()
    wait_for("it is flagged", lambda: open_findings() == ["StalkerwareApp:com.spyzee"], 40)
    wait_for("the guardian is told", lambda: [t for t in texts(delivered(TOPIC)) if "secretly spy" in t], 40)
    sh("pm uninstall com.spyzee")
    restart_app()
    wait_for("the warning clears when the app is gone", lambda: open_findings() == [], 40)

    print("-- 5. a scam link while the phone is offline: kept, then delivered when the phone is back", flush=True)
    sh("cmd connectivity airplane-mode enable")
    time.sleep(6)
    before = len(delivered(TOPIC)) if False else None
    post("scam1", "Your account is locked. Verify at https://%s/login" % LISTED[3])
    wait_for("she is warned on the phone", lambda: any("Link:ScamLink" in b for b in banners()), 20)
    wait_for("the message waits in the outbox", lambda: db_rows("select count(*) from outbox")[0][0] == 1, 20)
    sh("cmd connectivity airplane-mode disable")
    time.sleep(8)
    run_daily()
    wait_for("the outbox emptied and the guardian got it", lambda: db_rows("select count(*) from outbox")[0][0] == 0 and [t for t in texts(delivered(TOPIC)) if "scam link" in t], 60)

    print("-- 6. a second scam the same day: she is warned again but the guardian is not messaged again", flush=True)
    n = len([t for t in texts(delivered(TOPIC)) if "scam link" in t])
    post("scam2", "Parcel held. Pay at https://%s/pay" % LISTED[4])
    wait_for("she is warned again", lambda: len([b for b in banners() if "Link:ScamLink" in b]) >= 2, 20)
    time.sleep(10)
    check(len([t for t in texts(delivered(TOPIC)) if "scam link" in t]) == n, "no second scam message to the guardian (back-off)")

    print("-- 7. she changes her guardian: the old one hears it, the new one is told, the old heartbeat goes", flush=True)
    seed(TOPIC, "José", "Sam")  # re-seed owner/guardian cleanly (keeps findings), alerts on
    run_daily()
    wait_for("the old guardian's heartbeat is waiting", lambda: pending_heartbeats(TOPIC), 60)
    # drive the real screens: My settings, Pick a different guardian
    if tap_text("My settings") and tap_text("Pick a different guardian"):
        for label, value in (("Their name", "Ann"), ("Their email", "ann@example.org")):
            node = find_text(label)
            if node is not None:
                x, y = center(node)
                sh("input tap %d %d" % (x, y))
                time.sleep(1)
                sh("input text '%s'" % value.replace("@", "\\@"))
                sh("input keyevent KEYCODE_BACK")
                time.sleep(1)
            else:
                no("could not find the '%s' field" % label)
        if tap_text("Save") and tap_text("I will"):
            time.sleep(4)
            wait_for("the old guardian was told she changed her guardian", lambda: [t for t in texts(delivered(TOPIC)) if "changed her guardian" in t], 40)
            check(not pending_heartbeats(TOPIC), "the old guardian's waiting 'hasn't checked in' message was cancelled")
            new_topic = db_rows("select value from setting where name='guardians.firstTopic'")[0][0]
            check(new_topic != TOPIC, "the new guardian has a new private topic")
            wait_for("the new guardian got the 'set up' message", lambda: [t for t in texts(delivered(new_topic)) if "is set up on" in t], 40)
            wait_for("the new guardian's heartbeat is waiting", lambda: pending_heartbeats(new_topic), 40)
            sh("input keyevent KEYCODE_BACK")

            print("-- 8. alerts switched off: the waiting message is cancelled and nothing more is sent", flush=True)
            restart_app()
            if tap_text("My settings"):
                node = None
                for n in ui().iter("node"):
                    if n.get("checkable") == "true":
                        node = n
                if node is not None:
                    time.sleep(3)  # ntfy ignores a cancel in the same second as the arm
                    x, y = center(node)
                    sh("input tap %d %d" % (x, y))
                    wait_for("the new guardian's waiting message is cancelled", lambda: not pending_heartbeats(new_topic), 40)
                    k = len(delivered(new_topic))
                    post("scam3", "Last chance https://%s/x" % LISTED[5], title="Bank3")
                    time.sleep(10)
                    check(len(delivered(new_topic)) == k, "with alerts off nothing new reaches the guardian")
                else:
                    no("could not find the alerts switch")
finally:
    print("-- cleanup", flush=True)
    for p in ("org.fixture.sideloaded", "com.anydesk.anydeskandroid", "com.spyzee"):
        sh("pm uninstall " + p)
    sh("cmd connectivity airplane-mode disable")
    sh("settings put global auto_time 1")
    if not os.environ.get("KEEP"):
        sh("pm clear " + PKG)

print("%d failed" % FAILS)
sys.exit(1 if FAILS else 0)
