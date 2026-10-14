# Aegida — Samsung validation options (Oct 8, 2026)
Supports section 8b of `aegida-technical-design.md`. Samsung checks come last; this lists how to do them.

## Which Galaxy is useful
- Needs **One UI 6 (Android 14) or newer**: Auto Blocker arrived with One UI 6, and the sleeping-apps behaviour we depend on is the newer one. Ideal: a Galaxy S23-generation phone or newer, since Mom has an S23+ on One UI 8.
- A **Galaxy S8 is not useful**: its last update was Android 9, below the Android 10 `RoleManager` API Aegida needs and far below the Android level the Play build targets.
- Before using any old Galaxy: Settings > About phone > Software information (One UI version) and check that Settings > Security and privacy > Auto Blocker exists.

## Option A: Samsung Remote Test Lab (to look into)
Real Galaxy phones rented remotely in a browser (Chrome) with a free Samsung developer account. From Samsung's pages (Oct 8):
- Free to sign up; the page does not state session length or limits, but says the Partner Program gives "longer device reservations", which implies standard sessions are short. **Unconfirmed: exact session limit.**
- Labs in several countries; device list changes; the program page featured recent devices (Galaxy S26 Ultra, Z Fold8, Z Flip8, Tab S11). **Unconfirmed: whether a Galaxy S23/S24-class phone on One UI 6/7/8 is offered.**
- Not addressed on the pages I could read: installing our own APK, signing in to a Google account / Play Store, opening Settings, adb access. A Samsung forum thread titled "When I try to install the apk file to test the app in Remote Test Lab" suggests APK install exists but can have problems (thread content not readable). **All of this needs a first session to check.**
- Likely good for: the One UI settings screens (Auto Blocker, Never sleeping list, default apps, Private DNS), whether the Never-sleeping deep link opens the right screen, Samsung Internet's notification behaviour with our fixture page, and whether Auto Blocker is readable.
- Likely NOT good for: multi-day tests (sleeping after 3 days, deep sleep after about 16 days), reboots/updates, real messages, a real Google account.

First session checklist (15–30 min):
1. Reserve a recent Galaxy (S23 class or newer if offered); note model and One UI version.
2. Install a debug build of Aegida; see if the Play image/Play Protect is present.
3. Open Settings > Security and privacy > Auto Blocker; try reading it (`adb shell settings list global|secure` if adb is available) and record keys.
4. Fire the deep link `com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY` with `activity_type` 0, 1, 2.
5. Open Samsung Internet to our host-served fixture page (needs a reachable URL, since the lab phone can't see `10.0.2.2`; use a temporary public HTTPS fixture page) and see how its notifications arrive.
6. Capture `dumpsys package`/`dumpsys notification` fixtures, labelled Samsung.
7. Record what the lab could not do, in `docs/emulator-vs-device.md`.

### First session result (Oct 8, 30 min)
- Got an SM-S916U (Galaxy S23+) on Android 16 / One UI 8.0. The first reservation showed Android 13 / One UI 5.1, so check the version before starting.
- adb works through **Remote Debug Bridge**: download RDB from the web client, run `rdb.exe` from a shell where `adb` is on PATH (`$env:Path += ";$env:LOCALAPPDATA\Android\Sdk\platform-tools"`), then the device shows as `localhost:<port>`. Screenshots via `adb exec-out screencap` and taps via `adb shell input` work, so no browser automation is needed.
- **The lab phone is managed by Knox Guard and has Auto Blocker disabled**, so the lab cannot answer the Auto Blocker questions (findings #10–#12). The Never-sleeping deep link works (#13).
- Still useful for: One UI screens, deep links, `dumpsys`/`settings` captures, Samsung Internet notifications with a public fixture page.

### Second session result (Oct 14)
- Galaxy S25 (SM-S931B, EUX) on Android 16 / One UI 8.0. RDB on a new port each session; a session that would not connect had to be restarted.
- Galaxy Store installs record installer and initiating installer `com.sec.android.app.samsungapps` (3 of 3); Galaxy Store is a SYSTEM, PRIVILEGED, updated system app. Aegida asked about each as "from outside the Play Store" (findings #106).
- The lab's `com.samsung.rtlassistant` disables Auto Blocker and blocks every App info page ("not available"), so uninstall paths cannot be checked here.
- Git Bash rewrites `/data/local/tmp` in adb arguments; set `MSYS_NO_PATHCONV=1` and use `C:/` paths for local files.
- Captured (in `build/samsung-rtl/`, not committed): props, `pm list packages -i` before and after, `dumpsys package` for the three apps and Galaxy Store, `dumpsys notification`.

## Option B: paid device farms
BrowserStack, TestingBot and similar list real Galaxy phones; pricing and exact models not checked.

## Option C: a physical Galaxy
Needed for the multi-day tests (sleeping, deep sleeping, reboot/update persistence). Any Galaxy on One UI 6+; used S22/S23 is the closest match to Mom's phone.

## Fallback if none of these is available
The **observe-only first release to Mom** with the opt-in support log: guardians can see what Aegida could and couldn't read on her actual S23+ without touching it.

## Sources
- Samsung Developers, Remote Test Lab: developer.samsung.com/remote-test-lab
- Samsung Newsroom, "Samsung enhances Remote Test Lab program": news.samsung.com/global/samsung-enhances-remote-test-lab-program-to-support-developers-worldwide
- Samsung Developers, App testing: developer.samsung.com/resources/app-testing
- Samsung forum, APK install in Remote Test Lab: forum.developer.samsung.com/t/when-i-try-to-install-the-apk-file-to-test-the-app-in-remote-test-lab/29168
