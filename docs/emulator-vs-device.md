# Emulator versus device

The running list of things the emulator cannot show, and what was seen on a real device. A gap that
closes moves back to the emulator list. Section 8 of the technical design is where this comes from.

## Known gaps (check on Kevin's Pixel)

The Pixel pass ran Oct 8 to 14 on Kevin's Pixel 11 (Android 17), with Play builds from 0.2.0 on. Kevin confirmed
the real-phone checks were all fine (findings #103, Oct 13); where no details were recorded, the row says so.

| Gap | Why the emulator can't show it | Checked on Pixel? | Result |
|---|---|---|---|
| Heads-up banner look, sound, Do Not Disturb, Android 15/16 sensitive-notification redaction | Needs real SystemUI and real message apps | Yes (Oct 8-13) | Banners and Home seen in the run-throughs (findings #73, #74, #78, #80); the website "No" and ads-as-notices warnings seen on the phone (#103). Do Not Disturb and redaction were not recorded separately |
| Real messaging app notification shapes (Google Messages, WhatsApp) | No real accounts on the emulator | Yes (Oct 14) | One real day of notices read from the Pixel: 239 notices from 27 apps, the default messages app never counted, no app scored as ad-like (#107). Words are not in that history, so link checks on real texts rest on the emulator runs |
| Real Chrome behavior with real notification-spam sites | Emulator Chrome differs; real spam sites risky on an emulator | Yes (Oct 13) | The website "No" behaviour was fine on a real phone (#103). Real spam sites were not used; the emulator runs use a fixture page (#32) |
| Private DNS guided-flow wording | Settings UI differs by build | Yes (Oct 8) | No public link opens the Private DNS screen on Android 17; the step copies the address and opens Network settings, with brand-neutral steps (#69) |
| Real push latency and Doze with the phone idle overnight | Emulator doesn't model Doze or real push | Partly (Oct 8-13) | Guardian messages arrived from Mom's phone (the "set up" note, #103) and live against ntfy.sh (#71). No overnight timing was recorded; the guardian's "hasn't checked in" note (#23) is the backstop |
| Play-installed build, update flow, closed-test opt-in | Needs Play Console | Yes (Oct 9-13) | Play build 0.2.0 run through on the Pixel (#78); the upgrade from an older version kept History and My settings (#103); upgrade with data also covered by `UpgradeWithDataTest` (#113) |

## Samsung-only (last; see samsung-validation-options.md)

| Item | Checked? | Result |
|---|---|---|
| Sleeping / deep-sleeping apps with a foreground service and notification listener | No | Open: needs a real Galaxy for 3, 7 and 16 unopened days (findings #1). The lab sessions are too short |
| Never-sleeping deep link opens the right screen | Yes, Remote Test Lab S23+ One UI 8.0 (Oct 8) | Works; screen is titled "Never auto sleeping apps" (findings #13) |
| Apps from the Galaxy Store | Yes, Remote Test Lab S25 One UI 8.0 (Oct 14) | Installer and initiating installer are `com.sec.android.app.samsungapps` (3 of 3); Galaxy Store is a system app. Aegida asked about each as from outside the Play Store (#106); the Galaxy Store is now trusted through `OemSource.appStores()` |
| Auto Blocker readable? | Yes, lab (Oct 8) | Signature-level permissions, no settings key (findings #10), so Aegida records only what she says. The Auto Blocker setup step was fine on a real Samsung that has it (#103) |
| Auto Blocker on/off settings diff, and does Maximum restrictions hide links in notices? | No | Open (findings #2, #11): both lab phones had Auto Blocker disabled (Knox Guard on the S23+, the lab's `com.samsung.rtlassistant` on the S25). Needs an unmanaged Galaxy on One UI 6+ |
| Uninstall paths (App info) | No | The S25 lab blocks every App info page (#106); not checkable in the lab |
| Samsung Internet website-notification behavior | No | Needs a public fixture page in a lab session or a real Galaxy |
| One UI default-apps and Private DNS screen wording | Partly | The Private DNS steps are brand-neutral and cover Samsung's Connections path (#69); not walked on One UI |

## What the Remote Test Lab can and cannot do

- Can: adb through Remote Debug Bridge, screenshots and taps over adb, installing our build and store apps, `dumpsys` and
  `settings` captures, One UI screens and deep links (samsung-validation-options.md, sessions 1 and 2).
- Cannot: Auto Blocker (disabled on both phones), App info pages (S25), multi-day sleeping, reboots and updates, real
  messages or a real Google account.

## Differences seen between emulator and device

(Add a row each time a fixture captured on the emulator differs from the same capture on a device.)

| Date | What | Emulator | Device |
|---|---|---|---|
| 2026-10-14 | Installer of a store app | No Galaxy Store; the maker's-store case is built from the Samsung capture (`GALAXY_STORE` in the test kit) | Galaxy Store (`com.sec.android.app.samsungapps`) on the S25 lab phone (#106) |
| 2026-11-13 | Sideloaded install, then notification access | On Android 13 and newer, an APK installed by hand (adb, a file) shows Android's own "Restricted setting" screen before notification access can be turned on, with none of Aegida's words (finding #180). A tester told to sideload sees this and needs the three-dot "Allow restricted settings" step | A Play install does not show it. Note only; nothing in Aegida changes |
