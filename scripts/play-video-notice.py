"""Records the short video Play asks for with the FOREGROUND_SERVICE_SPECIAL_USE declaration (findings #68).

    python -I scripts/play-video-notice.py [emulator-5554]

Emulator only. Same starting point as scripts/play-video.py (setup finished, one real daily check, nothing flagged).
Records the screen while it shows Aegida's persistent notice in the notification shade, then a message with a scam link
arriving and Aegida warning about it straight away. Writes build/play-video/aegida-foreground-notice.mp4, a captions
file with the real timings (.srt) and a screenshot per key moment. No ffmpeg here, so captions are a separate file.
"""
import importlib.util, os, subprocess, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("pv", os.path.join(HERE, "play-video.py"))
pv = importlib.util.module_from_spec(spec)
spec.loader.exec_module(pv)  # refuses a non-emulator serial itself

ps, sh, adb, PKG, OUT = pv.ps, pv.sh, pv.adb, pv.PKG, pv.OUT
FILE = "/sdcard/aegida-foreground-notice.mp4"
NOTICE = "Aegida is looking out for you"


def clear_old_notifications():
    """The emulator keeps earlier test notifications; take them off the shade (Aegida's own notice cannot be cleared)."""
    sh("cmd statusbar expand-notifications")
    time.sleep(3)
    sh("uiautomator dump /sdcard/ui.xml")
    xml = adb("exec-out", "cat", "/sdcard/ui.xml")
    if "Clear all" in xml:
        ps.tap_text("Clear all")
    sh("cmd statusbar collapse")
    time.sleep(2)


if __name__ == "__main__":
    sh("settings put system font_scale 1.0")
    sh("cmd uimode night no")
    ps.clean_status_bar(True)
    rec = None
    try:
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
        pv.wait_visible("the calm Home", "No known problems found")
        clear_old_notifications()

        sh("rm -f " + FILE)
        rec = subprocess.Popen([ps.ADB, "-s", ps.SERIAL, "shell", "screenrecord", "--time-limit", "60", "--size", "720x1600",
                                "--bit-rate", "3000000", FILE], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        time.sleep(2)
        t0 = time.time()
        now = lambda: time.time() - t0
        cues = []

        def cue(start, text):
            cues.append([start, None, text])

        def end_last(t):
            cues[-1][1] = t

        def snap(name):
            with open(os.path.join(OUT, "check-notice-" + name + ".png"), "wb") as f:
                f.write(adb("exec-out", "screencap", "-p", binary=True))

        cue(0.5, "Aegida is installed and set up. It keeps a visible notice on while it works.")
        time.sleep(4)
        end_last(now())

        sh("cmd statusbar expand-notifications")
        pv.wait_visible("Aegida's notice in the shade", NOTICE)
        cue(now(), "The notice says Aegida is on. It is visible, and the user can dismiss it.")
        snap("1-notice")
        time.sleep(7)
        end_last(now())

        sh("cmd notification post -S bigtext -t 'Bank' scam1 'Your account is locked. Verify at https://%s/login'" % ps.listed_domain())
        cue(now(), "A message with a scam link arrives.")
        time.sleep(3)
        pv.wait_visible("Aegida's warning", "looks like a scam")
        end_last(now())
        cue(now(), "Aegida checks it as it arrives and warns the user straight away. This cannot wait: she could tap the link first.")
        snap("2-warning")
        time.sleep(9)
        end_last(now())
        total = now()
    finally:
        sh("pkill -SIGINT screenrecord")  # SIGINT lets screenrecord close the file properly
        if rec:
            try:
                rec.wait(timeout=20)
            except subprocess.TimeoutExpired:
                rec.kill()
        sh("cmd statusbar collapse")
        ps.clean_status_bar(False)
        sh("settings put global auto_time 1")
    time.sleep(2)
    mp4 = os.path.join(OUT, "aegida-foreground-notice.mp4")
    with open(mp4, "wb") as f:
        f.write(adb("exec-out", "cat", FILE, binary=True))
    sh("rm -f " + FILE)
    with open(os.path.join(OUT, "aegida-foreground-notice.srt"), "w", encoding="utf-8", newline="\n") as f:
        for i, (start, end, text) in enumerate(cues, 1):
            f.write("%d\n%s --> %s\n%s\n\n" % (i, pv.stamp(start + pv.LEAD), pv.stamp((end or total) + pv.LEAD), text))
    print("recorded %.0f s of steps, wrote %s (%.1f MB)" % (total, mp4, os.path.getsize(mp4) / 1e6))
