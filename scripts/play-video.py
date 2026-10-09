"""Records the short video Play asks for with the QUERY_ALL_PACKAGES declaration (findings #68).

    python -I scripts/play-video.py [emulator-5554]

Emulator only (it refuses any other serial). Starts from a cleared app with setup finished and one real daily check
done, records the screen while it installs two harmless do-nothing fixture apps (scripts/make-fixture-apk.sh, signed
with the debug test key): one named like a known remote-control app and one named like a known spying app. Aegida
notices each from the list of installed apps and says so; both are removed and Aegida goes back to "No known
problems found". Writes build/play-video/aegida-query-all-packages.mp4, a captions file with the real timings
(.srt, upload it with the video) and a screenshot per key moment so the content can be checked without playing it.
There is no ffmpeg here, so captions are a separate file rather than burned in.
"""
import importlib.util, os, re, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("shots", os.path.join(HERE, "play-screenshots.py"))
ps = importlib.util.module_from_spec(spec)
spec.loader.exec_module(ps)  # refuses a non-emulator serial itself

ROOT, PKG, sh, adb = ps.ROOT, ps.PKG, ps.sh, ps.adb
OUT = os.path.join(ROOT, "build", "play-video")
os.makedirs(OUT, exist_ok=True)
REMOTE, SPY = "com.anydesk.anydeskandroid", "com.spyzee"
REMOTE_FILE = "/sdcard/aegida-query-all-packages.mp4"
LEAD = 2.0  # seconds between starting the recorder and the steps clock (t0), so captions line up with the video


def visible(*texts):
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    return any(t in xml for t in texts)


def wait_visible(label, *texts, timeout=60):
    end = time.time() + timeout
    while time.time() < end:
        if visible(*texts):
            return
        time.sleep(2)
    sys.exit("never saw %s on screen (%s)" % (label, texts))


def make_fixture(pkg):
    path = os.path.join(ROOT, "build", "fixture-apps", pkg + ".apk")
    subprocess.run(["bash", os.path.join(ROOT, "scripts", "make-fixture-apk.sh"), pkg], cwd=ROOT, capture_output=True)
    if not os.path.isfile(path):
        sys.exit("could not build the fixture app " + pkg)
    return path


def relaunch():
    sh("am force-stop " + PKG)
    sh("am start -n %s/.MainActivity" % PKG)


def stamp(t):
    ms = int(t * 1000)
    return "%02d:%02d:%02d,%03d" % (ms // 3600000, ms // 60000 % 60, ms // 1000 % 60, ms % 1000)


if __name__ == "__main__":
    apps = {pkg: make_fixture(pkg) for pkg in (REMOTE, SPY)}
    for pkg in (REMOTE, SPY):
        sh("pm uninstall " + pkg)
    sh("settings put system font_scale 1.0")
    sh("cmd uimode night no")
    ps.clean_status_bar(True)
    rec = None
    try:
        # Setup finished, one real daily check done (it also downloads the scam list), nothing flagged.
        sh("pm clear " + PKG)
        sh("pm grant %s android.permission.POST_NOTIFICATIONS" % PKG)
        ps.start_app()
        sh("am force-stop " + PKG)
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        ps.edit_db(ps.finish_setup)
        ps.start_app(10)
        ps.run_daily_check()
        ps.edit_db(ps.clear_noise)
        ps.start_app(8)
        sh("cmd notification disallow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        sh("cmd notification allow_listener %s/%s.AegidaListenerService" % (PKG, PKG))
        time.sleep(8)
        wait_visible("the calm Home", "No known problems found")

        sh("rm -f " + REMOTE_FILE)
        rec = subprocess.Popen([ps.ADB, "-s", ps.SERIAL, "shell", "screenrecord", "--time-limit", "85", "--size", "720x1600",
                                "--bit-rate", "3000000", REMOTE_FILE], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)
        t0 = time.time()
        now = lambda: time.time() - t0
        cues = []

        def cue(start, text):
            cues.append([start, None, text])

        def end_last(t):
            cues[-1][1] = t

        def snap(name):
            with open(os.path.join(OUT, "check-" + name + ".png"), "wb") as f:
                f.write(adb("exec-out", "screencap", "-p", binary=True))

        cue(0.5, "Aegida checks, on the phone only, whether any installed app is a known remote-control or spying app.")
        snap("1-calm")
        time.sleep(7)
        end_last(now())

        adb("install", apps[REMOTE])
        cue(now(), "A remote-control app is installed from outside Google Play.")
        time.sleep(3)
        relaunch()
        wait_visible("the remote-control warning", "from far away")
        end_last(now())
        cue(now(), "Aegida found it by reading the list of installed apps (QUERY_ALL_PACKAGES). A queries list cannot see an unknown app.")
        snap("2-remote-control")
        time.sleep(8)
        end_last(now())

        # One warning at a time: with two open, Home shows only a count, not the sentence for each.
        sh("pm uninstall " + REMOTE)
        adb("install", apps[SPY])
        cue(now(), "The first app is removed. Now a known spying app is installed. Aegida matches it against a published list, on the phone.")
        time.sleep(3)
        relaunch()
        wait_visible("the spying-app warning", "spying app")
        snap("3-spying-app")
        time.sleep(8)
        end_last(now())

        sh("pm uninstall " + SPY)
        cue(now(), "That app is removed and Aegida checks again. The list of apps never leaves the phone.")
        time.sleep(2)
        relaunch()
        wait_visible("the calm Home again", "No known problems found")
        snap("4-calm-again")
        time.sleep(6)
        end_last(now())
        total = now()
    finally:
        sh("pkill -SIGINT screenrecord")  # SIGINT lets screenrecord close the file properly
        if rec:
            try:
                rec.wait(timeout=20)
            except subprocess.TimeoutExpired:
                rec.kill()
        for pkg in (REMOTE, SPY):
            sh("pm uninstall " + pkg)
        ps.clean_status_bar(False)
        sh("settings put global auto_time 1")
    time.sleep(2)
    mp4 = os.path.join(OUT, "aegida-query-all-packages.mp4")
    with open(mp4, "wb") as f:
        f.write(adb("exec-out", "cat", REMOTE_FILE, binary=True))
    sh("rm -f " + REMOTE_FILE)
    with open(os.path.join(OUT, "aegida-query-all-packages.srt"), "w", encoding="utf-8", newline="\n") as f:
        for i, (start, end, text) in enumerate(cues, 1):
            f.write("%d\n%s --> %s\n%s\n\n" % (i, stamp(start + LEAD), stamp((end or total) + LEAD), text))
    print("recorded %.0f s of steps, wrote %s (%.1f MB)" % (total, mp4, os.path.getsize(mp4) / 1e6))
