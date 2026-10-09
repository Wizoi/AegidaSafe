"""Live emulator scenario: website notifications (findings #81, Kevin Oct 9).

    python -I scripts/emulator_websites.py [emulator-5554]

Emulator only (it refuses any other serial). Needs the -local debug build installed and the Aegida36 emulator running.
Uses the real Chrome and two throwaway host-served pages (fixtures/web/popup-spam on ports 8791 and 8792, calm wording):

  1. setup is marked finished; the first check records an empty website list (Android will not list the sites, findings #84)
  2. site A notifies for the first time: Aegida hides it and asks "Was that you?"
  3. she says yes: site A is added to the baseline; it notifies again and is left alone
  4. site B (another new site) notifies: Aegida hides it and asks too

No guardian alerts are on, so nothing is sent anywhere.
"""
import importlib.util, os, re, sqlite3, subprocess, sys, tempfile, time
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
spec = importlib.util.spec_from_file_location("shots", os.path.join(HERE, "play-screenshots.py"))
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)  # refuses a non-emulator serial itself
PKG, sh, adb = ps.PKG, ps.sh, ps.adb
FIXTURE = os.path.join(ROOT, "fixtures", "web", "popup-spam")
SITE_A, SITE_B = "http://localhost:8791", "http://localhost:8792"
FAILS = 0


def ok(msg):
    print("PASS ", msg, flush=True)


def no(msg):
    global FAILS
    FAILS += 1
    print("FAIL ", msg, flush=True)


def check(cond, msg):
    ok(msg) if cond else no(msg)


def tags():
    """'pkg id tag' for every notification the phone holds (dumpsys, unredacted)."""
    out = []
    for line in sh("dumpsys notification --noredact").splitlines():
        m = re.search(r"NotificationRecord.*?pkg=(\S+) .*?id=(-?\d+) tag=(\S*)", line)
        if m:
            out.append("%s %s %s" % m.groups())
    return out


def have(*parts):
    return any(all(p in t for p in parts) for t in tags())


def wait_for(msg, fn, timeout=40):
    end = time.time() + timeout
    while time.time() < end:
        if fn():
            ok(msg)
            return True
        time.sleep(2)
    no(msg)
    return False


def db(sql):
    d = tempfile.mkdtemp(prefix="aegida-web-")
    for f in ("aegida.db", "aegida.db-wal", "aegida.db-shm"):
        with open(os.path.join(d, f), "wb") as fh:
            fh.write(adb("exec-out", "run-as", PKG, "cat", "databases/" + f, binary=True))
    c = sqlite3.connect(os.path.join(d, "aegida.db"))
    try:
        return c.execute(sql).fetchall()
    finally:
        c.close()


def baseline_sites():
    rows = db("select value from setting where name = 'baseline.websites'")
    return set(rows[0][0].split("\n")) - {""} if rows else None


def tap_any(*texts):
    """Taps the first of the given texts that is on screen right now; False if none is."""
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    if "<hierarchy" not in xml:
        return False
    for text in texts:
        for node in ET.fromstring(xml).iter("node"):
            if text in (node.get("text"), node.get("content-desc")):
                l, t, r, b = map(int, re.findall(r"\d+", node.get("bounds")))
                sh("input tap %d %d" % ((l + r) // 2, (t + b) // 2))
                return True
    return False


def open_site(site):
    sh("am force-stop com.android.chrome")
    sh("am start -a android.intent.action.VIEW -d '%s/index.html?calm' com.android.chrome" % site)
    time.sleep(6)


def seen(site):
    return have("localhost:%s" % site.rsplit(":", 1)[1])


def subscribe(site):
    """Opens the page and taps through Chrome's first-run promo, the page's Allow and Chrome's permission prompt."""
    open_site(site)
    for _ in range(10):
        tap_any("No thanks", "Allow")
        time.sleep(3)
        if seen(site):
            return True
    return False


def notify_again(site):
    open_site(site)
    for _ in range(6):
        if tap_any("Again"):
            time.sleep(3)
            return
        tap_any("No thanks")
        time.sleep(2)


def say_yes():
    sh("am force-stop " + PKG)
    ps.start_app(8)
    for _ in range(8):
        if tap_any("Yes, that was me"):
            return
        tap_any("Fix my phone")
        time.sleep(2)


def quiet_setup(c):
    for k, v in [("setup.stepsDone", "8"), ("guardians.ownerName", "Ruth"), ("guardians.alertsOn", "false")]:
        c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))


if __name__ == "__main__":
    servers = []
    try:
        sh("settings put system font_scale 1.0")
        for port in (8791, 8792):
            servers.append(subprocess.Popen([sys.executable, "-m", "http.server", str(port)], cwd=FIXTURE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
            adb("reverse", "tcp:%d" % port, "tcp:%d" % port)
        sh("am set-debug-app --persistent com.android.chrome")
        sh("echo '_ --disable-fre --no-default-browser-check --no-first-run' > /data/local/tmp/chrome-command-line")
        sh("pm clear " + PKG)
        sh("pm clear com.android.chrome")
        sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        ps.start_app(6)
        wait_for("the Aegida notice is up", lambda: have(PKG, " 1 "))

        print("-- 1. setup finishes: the baseline starts with no websites (Android will not list them, findings #84)", flush=True)
        # With the listener allowed the phone restarts the app the moment it is stopped, on the old database.
        sh("cmd notification disallow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        ps.edit_db(quiet_setup)
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        ps.start_app(8)
        ps.run_daily_check()  # the first check after setup takes the baseline
        wait_for("the baseline records an empty website list", lambda: baseline_sites() == set(), 60)

        print("-- 2. site A notifies for the first time: hidden, and she is asked", flush=True)
        check(subscribe(SITE_A), "site A is subscribed")
        wait_for("Aegida's question about site A is shown", lambda: have(PKG, "NewWebsiteNotifications", "localhost:8791"), 30)
        check(not have("com.android.chrome", "localhost:8791"), "site A's own notification was hidden")

        print("-- 3. she says yes: site A is remembered and left alone", flush=True)
        say_yes()
        wait_for("site A is in the baseline", lambda: SITE_A in (baseline_sites() or set()), 30)
        rows = db("select state from finding where problem = 'NewWebsiteNotifications' and subject = '%s'" % SITE_A)
        check(bool(rows) and rows[0][0] != "Open", "the question is no longer open (%s)" % (rows[0][0] if rows else "missing"))
        notify_again(SITE_A)
        check(have("com.android.chrome", "localhost:8791"), "site A's next notification was left in place")

        print("-- 4. site B is another new site: asked too, and not added by itself", flush=True)
        check(subscribe(SITE_B), "site B is subscribed")
        wait_for("Aegida's question about site B is shown", lambda: have(PKG, "NewWebsiteNotifications", "localhost:8792"), 30)
        check(not have("com.android.chrome", "localhost:8792"), "site B's own notification was hidden")
        check(SITE_B not in (baseline_sites() or set()), "site B is not in the baseline")
    finally:
        for server in servers:
            server.terminate()
        for port in (8791, 8792):
            adb("reverse", "--remove", "tcp:%d" % port)
        sh("am force-stop com.android.chrome")
        sh("pm clear " + PKG)
    print("%d failed" % FAILS)
    sys.exit(1 if FAILS else 0)
