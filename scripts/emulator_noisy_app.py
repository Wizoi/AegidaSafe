"""Live emulator scenario: an app that sends ads as notices (finding #92).

    python -I scripts/emulator_noisy_app.py [emulator-5554]

Emulator only (it refuses any other serial). Needs the -local debug build of Aegida and the fixture app
(fixtures/noisy-app, `./gradlew :noisyfixture:assembleDebug`) installed. The fixture posts real notices with a real channel,
importance and category, so the listener's ranking is the phone's own:

  1. five chat-like notices (category msg): not counted, no warning
  2. three ad-like notices (pop-up importance, "Special offers" channel, "File converter is ready!"): one NoisyApp warning naming the app
  3. she taps Show me, then "This app is fine": the app is on the allow list and the warning is seen
  4. three more ad-like notices: no new warning
  5. My settings, "Warn me again": the list is empty again

No guardian alerts are on, so nothing is sent anywhere.
"""
import importlib.util, os, sys, time

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location("web", os.path.join(HERE, "emulator_websites.py"))
web = importlib.util.module_from_spec(spec)
spec.loader.exec_module(web)  # refuses a non-emulator serial itself
ps, sh, PKG = web.ps, web.sh, web.PKG
FIXTURE = "com.aegida.fixture.noisy"
LISTENER = "%s/%s.AegidaListenerService" % (PKG, PKG)


def post(n, title, text, channel="general", channel_name="General", importance=3, category=None):
    extra = " --es category %s" % category if category else ""
    sh("am start -n %s/.PostActivity --ei id %d --es title '%s' --es text '%s' --es channel %s --es channelName '%s' --ei importance %d%s"
       % (FIXTURE, n, title, text, channel, channel_name, importance, extra))
    time.sleep(3)


def ad(n):
    post(n, "File converter is ready!", "Convert", channel="offers", channel_name="Special offers", importance=4)


def noisy_findings(state=None):
    rows = web.db("select state from finding where problem = 'NoisyApp' and subject = '%s'" % FIXTURE)
    return [r[0] for r in rows] if state is None else [r[0] for r in rows if r[0] == state]


def quiet_setup(c):
    for k, v in [("setup.stepsDone", "8"), ("guardians.ownerName", "Ruth"), ("guardians.alertsOn", "false")]:
        c.execute("insert or replace into setting(name, value) values (?, ?)", (k, v))


if __name__ == "__main__":
    try:
        sh("settings put system font_scale 1.0")
        sh("pm clear " + PKG)
        for pkg in (PKG, FIXTURE):
            sh("pm grant %s android.permission.POST_NOTIFICATIONS" % pkg)
        ps.start_app(6)
        sh("cmd notification disallow_listener " + LISTENER)  # the phone would restart the app on the old database
        ps.edit_db(quiet_setup)
        sh("cmd notification allow_listener " + LISTENER)
        ps.start_app(8)

        print("-- 1. chat-like notices are not counted", flush=True)
        for n in range(1, 6):
            post(n, "Mom", "Lunch at noon?", channel="chats", channel_name="Chats", category="msg")
        web.check(not web.db("select 1 from notice where packageName = '%s'" % FIXTURE), "no chat notice was written down")
        web.check(not noisy_findings(), "no warning for chats")

        print("-- 2. three ad-like notices in a day: one warning naming the app", flush=True)
        for n in range(11, 14):
            ad(n)
        rows = web.db("select signals from notice where packageName = '%s'" % FIXTURE)
        print("   signals per notice:", [r[0] for r in rows], flush=True)
        web.check(len(rows) == 3 and all(r[0] >= 2 for r in rows), "three notices written down, each with at least two signs")
        web.wait_for("the NoisyApp warning is open", lambda: noisy_findings("Open") == ["Open"], 20)
        web.wait_for("Aegida's banner for it is up", lambda: web.have(PKG, "NoisyApp"), 20)

        print("-- 3. Show me, then 'This app is fine'", flush=True)
        sh("am force-stop " + PKG)
        ps.start_app(8)
        web.check(web.tap_any("Show me"), "Home offers Show me")
        time.sleep(3)
        sh("uiautomator dump /sdcard/ui.xml")
        screen = ps.adb("exec-out", "cat", "/sdcard/ui.xml")
        web.check("Image to PDF (fixture)" in screen, "the page names the app")
        ps.shot("noisy-app-page")
        web.check(web.tap_any("This app is fine"), "tapped This app is fine")
        web.wait_for("the app is on the allow list", lambda: bool(web.db("select 1 from allowed where subject = '%s'" % FIXTURE)), 20)
        web.check(noisy_findings() == ["Acknowledged"], "the warning is marked seen")

        print("-- 4. more ads: no new warning", flush=True)
        for n in range(21, 24):
            ad(n)
        time.sleep(3)
        web.check(noisy_findings() == ["Acknowledged"], "still no open warning")

        print("-- 5. My settings, Warn me again", flush=True)
        sh("am force-stop " + PKG)
        ps.start_app(6)
        web.tap_any("My settings")
        time.sleep(3)
        for _ in range(6):
            if web.tap_any("Warn me again"):
                break
            sh("input swipe 540 1800 540 600 300")
            time.sleep(1)
        ps.shot("noisy-app-settings")
        web.wait_for("the allow list is empty again", lambda: not web.db("select 1 from allowed"), 20)
    finally:
        sh("pm clear " + PKG)
    print("%d failed" % web.FAILS)
    sys.exit(1 if web.FAILS else 0)
