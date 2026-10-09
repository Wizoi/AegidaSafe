"""Captures the Play store phone screenshots from the emulator (finding #68).

    python -I scripts/play-screenshots.py [emulator-5554]

Needs the -local debug build installed on the Aegida36 emulator (not the Pixel: it refuses any serial that is not
emulator-*). It starts from a cleared app, takes the welcome screen, seeds a finished setup for a made-up owner
("Ruth") and guardian ("Sam"), then takes Home, a warning, History and My settings in the light theme at 100% font
with a clean status bar. Writes build/play-shots/*.png; look at each one, then copy the good ones into
app/src/main/play/listings/en-US/graphics/phone-screenshots/ (Play wants 2 to 8, sides 320..3840 px).
"""
import os, re, sqlite3, subprocess, sys, tempfile, time
import xml.etree.ElementTree as ET

SERIAL = sys.argv[1] if len(sys.argv) > 1 else "emulator-5554"
if not SERIAL.startswith("emulator-"):
    sys.exit("refusing: %s is not an emulator" % SERIAL)
SDK = os.environ.get("ANDROID_SDK_ROOT") or os.path.join(os.environ["LOCALAPPDATA"], "Android", "Sdk")
ADB = os.path.join(SDK, "platform-tools", "adb.exe")
PKG = "com.aegida.app"
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "build", "play-shots")
os.makedirs(OUT, exist_ok=True)


def adb(*args, binary=False):
    p = subprocess.run([ADB, "-s", SERIAL, *args], capture_output=True)
    return p.stdout if binary else p.stdout.decode("utf-8", "replace").replace("\r", "")


def sh(cmd):
    return adb("shell", cmd).strip()


def shot(name):
    time.sleep(2)
    with open(os.path.join(OUT, name + ".png"), "wb") as f:
        f.write(adb("exec-out", "screencap", "-p", binary=True))
    print("wrote", name + ".png", flush=True)


def tap_text(*wanted, timeout=15):
    """Tap the first of the given texts that is on screen (uiautomator), waiting up to `timeout` seconds."""
    end = time.time() + timeout
    while time.time() < end:
        sh("uiautomator dump /sdcard/ui.xml")
        xml = adb("exec-out", "cat", "/sdcard/ui.xml")
        if "<hierarchy" in xml:
            root = ET.fromstring(xml)
            for text in wanted:
                for node in root.iter("node"):
                    if text in (node.get("text"), node.get("content-desc")):
                        l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
                        sh("input tap %d %d" % ((l + r) // 2, (t + b) // 2))
                        time.sleep(2)
                        return text
        time.sleep(1)
    sys.exit("could not find any of %s on screen" % (wanted,))


def clean_status_bar(on):
    if on:
        sh("settings put global sysui_demo_allowed 1")
        for extra in ("-e command enter", "-e command clock -e hhmm " + time.strftime("%H%M"), "-e command battery -e level 100 -e plugged false",
                      "-e command network -e wifi show -e level 4 -e mobile show -e datatype none -e level 4",
                      "-e command notifications -e visible false"):
            sh("am broadcast -a com.android.systemui.demo " + extra)
    else:
        sh("am broadcast -a com.android.systemui.demo -e command exit")


LISTED_DOMAIN = None  # a domain on the bundled scam list, read from the feed test fixture


def listed_domain():
    path = os.path.join(ROOT, "feedtool/src/test/resources/feeds/phishing-domains.txt")
    lines = [l.strip() for l in open(path, encoding="utf-8") if l.strip() and not l.startswith("#")]
    return lines[3]


def edit_db(edit):
    """Stop the app, copy its database out, let `edit(connection)` change it, and put it back."""
    sh("am force-stop " + PKG)
    time.sleep(2)
    d = tempfile.mkdtemp(prefix="aegida-shots-")
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    c = sqlite3.connect(os.path.join(d, "aegida.db"))
    edit(c)
    c.commit(); c.execute("pragma wal_checkpoint(truncate)"); c.execute("pragma journal_mode=delete"); c.close()
    adb("push", os.path.join(d, "aegida.db"), "/data/local/tmp/seed.db")
    sh("chmod 666 /data/local/tmp/seed.db; run-as %s sh -c 'rm -f databases/aegida.db-wal databases/aegida.db-shm; cp /data/local/tmp/seed.db databases/aegida.db'; rm /data/local/tmp/seed.db" % PKG)


def finish_setup(c):
    for k, v in [("setup.stepsDone", "8"), ("guardians.alertsOn", "true"), ("guardians.ownerName", "Ruth"),
                 ("guardians.firstTopic", "screenshots-only-not-a-real-topic"), ("guardians.firstName", "Sam"),
                 ("guardians.firstEmail", "sam@example.org")]:
        c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))


def clear_noise(c):
    for table in ("history", "outbox"):
        c.execute("delete from " + table)


def start_app(wait=6):
    sh("am start -n %s/.MainActivity" % PKG)
    time.sleep(wait)


def run_daily_check():
    sh("settings put global auto_time 0")
    sh("cmd alarm set-time %d" % int((time.time() + 25 * 3600) * 1000))
    time.sleep(2)
    m = re.search(r"JOB #u[0-9a-z]+/(\d+): [a-f0-9]+ com\.aegida\.app", sh("dumpsys jobscheduler"))
    if not m:
        sys.exit("the daily job is not scheduled")
    sh("cmd jobscheduler run -f %s %s" % (PKG, m.group(1)))
    time.sleep(25)
    sh("settings put global auto_time 1")  # the phone gets its real time back from the network
    time.sleep(3)


if __name__ == "__main__":
    sh("settings put system font_scale 1.0")
    sh("cmd uimode night no")
    sh("wm size 1080x2160")  # Play: the long side may be at most twice the short side (the emulator is 2.22:1)
    time.sleep(3)
    clean_status_bar(True)
    try:
        sh("pm clear " + PKG)
        sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)  # pm clear resets it; setup asks Mom for it
        start_app()
        shot("1-welcome")
        sh("am force-stop " + PKG)
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        edit_db(finish_setup)
        start_app(10)
        run_daily_check()
        edit_db(clear_noise)
        start_app(8)
        shot("2-home")
        # My settings first: before any test message is posted, so the status bar is clean
        tap_text("My settings")
        shot("5-my-settings")
        tap_text("Home")
        sh("cmd notification disallow_listener %s/%s.AegidaListenerService" % (PKG, PKG))  # force a clean rebind
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        time.sleep(8)
        sh("cmd notification post -S bigtext -t 'Bank' scam1 'Your account is locked. Verify at https://%s/login'" % listed_domain())
        time.sleep(8)
        sh("cmd statusbar expand-notifications")  # the warning reaches Mom as a notification
        shot("3-warning")
        sh("cmd statusbar collapse")
        time.sleep(2)
        tap_text("History")
        shot("4-history")
    finally:
        clean_status_bar(False)
        sh("wm size reset")
        sh("settings put global auto_time 1")
