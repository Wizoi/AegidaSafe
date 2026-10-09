# Aegida — build plan

The checklist behind `aegida-technical-design.md` section 11. Updated as work lands; the evidence for each
item is in `findings.md` and the commit log. Statuses: done, doing, next, needs Kevin, blocked.

Order of evidence for everything: JVM, Robolectric, emulator, Kevin's Pixel, last a Samsung (CLAUDE.md).

## Phase 0. Foundations (done)
- [x] Docs, research, design committed; repo at github.com/Wizoi/AegidaSafe
- [x] Gradle skeleton: `:core :detectors :alerts :platform :store :app :guards :feedtool`, layered, guard tests with self-tests
- [x] Emulator: API 36 Play image, AVD `Aegida36`, `scripts/emulator-checks.sh` (refuses non-emulator serials)
- [x] Emulator spike: 13 mechanisms confirmed (`emulator-checks.sh spike`), findings #16-#20
- [x] First Samsung Remote Test Lab session (S23+, One UI 8.0): findings #10-#15

## Phase 1. Link checks and the website filter
Gate (design section 11): guard tests green; emulator scenarios for link check, pop-up page, website filter and a starved worker pass; fixtures captured.

| | Piece | Where | Status |
|---|---|---|---|
| 1a | Read links from text without fetching; look-alike signs | `:core` url | done |
| 1b | Signed feed format, sanity checks, builder (real feeds) | `:core` feed, `:feedtool` | done |
| 1c | Link, PopupSpam, Dns, Health detectors; Evaluator records runs | `:detectors` | done |
| 1d | AlertPipeline: alert once, opt-in guardians, resolve fixed settings | `:alerts` | done |
| 1e | Storage: findings, 90-day history, setup and guardians, feed file | `:store` | done (commit pending) |
| 1f | Android adapters: Snapshotter (Private DNS, default browser, listener, banner permission), notification mapper, banner poster with the brand icon, feed download and verified update | `:platform`, `:core` | done |
| 1g | Wiring: `CheckRunner` and `DailyCheck` (`:alerts`, JVM-tested), notification listener that hides fake website warnings, the "Aegida is looking out for you" foreground service, daily WorkManager job, boot receiver, Hilt graph | `:alerts`, `:app` | done, proven on the emulator (findings #32) |
| 1h | Screens: Home, History, My settings and the eight first-run steps, in the storyboard's ten safety pictures; 71 UI tests per variant (light and dark, normal and 200% font); walked on the emulator and looked at in all four display variants (findings #36-#38) | `:app` | done; guardian change built (see below) |
| 1i | Feed publishing: daily workflow (`build-feed.yml`) builds, signs, verifies and pushes `feed.bin`/`feed.sig` to the public Aegida-Feed Pages site. First run green Oct 8; live feed verified with the committed public key (findings #28, #29) | `.github/`, `:feedtool`, Aegida-Feed | done |
| 1j | Emulator scenarios: `emulator-checks.sh scenarios` 10/10 and `failures` 7/7 (offline daily job keeps the last feed and raises no alarm; a phone that never runs its check gets MissedCheck and ListsOld). Guardian transport built and JVM-tested (Oct 8): scheduled and cancellable ntfy messages, dead man's switch (due in 2 days, pushed back by each daily check), alerts switch, outbox with back-off and retry, `GuardianUnreachable` after 24 h. Done since: public ntfy.sh honours sequence ids for replace and cancel (live test, findings #44); outbox on the emulator, offline (`guardian-offline` 0 failed, #43); 37 mutation checks; a check killed mid-way no longer loses a warning (#42). Still to do: heartbeat arming and guardian change from the real app against live ntfy, with a phone subscribed | `emulator-checks.sh` | mostly done |

Open items inside phase 1:
- **Her picture on banners:** decided (Kevin) and built: the shield over her picture as a faint watermark, drawn by `PictureBanner` and set as the banner's large icon (JVM/Robolectric-tested). Not yet looked at on the emulator or a phone.
- **Event warnings that repeat:** back-off built (phase 2, `AlertBackoffTest`, decisions Oct 8).
- **Home wording:** approved by Kevin. The storyboard says "Everything is as it should be."; Home says "No known problems found." instead, as CLAUDE.md requires.
- Dead man's switch: built and its replace/cancel behaviour confirmed live (findings #44); the app-side arming still needs a live run with a subscribed phone.
- About screen shows each feed source's licence from the feed itself. Phishing.Database is MIT (verified); URLhaus is excluded until abuse.ch answers (findings #27).
- Look-alike gap: a name written entirely in Cyrillic or Greek letters that imitates a brand is only caught when its letters map to Latin ones (`LinkSigns` homoglyph table). Check against real internationalised domains from the feed before widening it.

## Phase 2. Drift, new apps, remote-control apps
- [x] Snapshotter reads default browser/text/phone apps and who has notification access, screen control or device-admin rights (installed apps with installer and signing cert: still to do, needs `QUERY_ALL_PACKAGES`, which Kevin has not approved yet)
- [x] `DriftDetector` with a baseline taken after setup and "Was that you?" (JVM-tested, emulator `drift` scenario, findings #45)
- [x] `NewAppDetector` (apps installed from outside Play after setup) and `RemoteControlDetector` (known remote-access apps by package name), JVM-tested and walked on the emulator with fixture APKs signed by the debug test key (findings #46). Still to do: a second-browser fixture to see default-browser drift, a fixture device-admin app, signing-certificate hashes and the signed-feed delivery of the remote-control list
- [x] Escalation (decided Oct 8): the same guardian is reminded once at 24 h if the warning is still open; repeat back-off 1/2/4/7 days. Fake-clock tests (`AlertBackoffTest`); a second guardian is a later feature
- [x] Emulator scenarios: `drift` (notification access, screen control), `new-apps` (installed by hand, remote-control app). Not yet: default browser/text/phone app changes, device-admin and overlay grants (need fixtures)

## Phase 3. Stalkerware list
- [x] Echap indicators (CC BY 4.0) in the signed feed v2 (610 packages, 470 certificate hashes), refreshed daily by the workflow; `StalkerwareDetector`; credit in the feed NOTICE and in My settings. Walked on the emulator against the live feed (findings #47). Open: the owner-safety question when a spying app was planted by someone close to her (findings #47)

## Phase 4. Pixel pass and Play
- [x] Gaps in `emulator-vs-device.md` checked on Kevin's Pixel (Kevin's Oct 9 retest of the Play build; he keeps it running; findings #69 to #78)
- [x] Internal Play build (0.2.0 on Oct 8, 0.3.0 next); all Console forms and declarations (`QUERY_ALL_PACKAGES`, notification access, foreground service, Data safety, privacy page) done
- [ ] Closed-test release: 0.2.0 sent for review on Oct 9; 0.3.0 follows it
- [ ] Ask Play Developer Support about automatic consented alerts before any release beyond the closed test
- [ ] Name clearance: trademark, Play Console, domain, GitHub

## Phase 5. Samsung (last)
- [ ] Unmanaged Galaxy on One UI 6+ for Auto Blocker on/off and Maximum restrictions (lab phone has it disabled, findings #11)
- [ ] Multi-day sleeping checks (3, 7, 16 days unopened) on a phone Kevin owns
- [ ] Samsung Internet notification behaviour with a public fixture page
- [ ] First release to Mom is observe-only for a few days

## Later
Remote-access and banking warning, opt-in call screening, weekly guardian digest, manual "Check this".
- **Code naming:** the person with the phone is the *owner* in code (decided Oct 8).
- **Blank owner name:** setup will not continue without a name, so a blank only appears on a database from before that rule; guardian messages then say "Aegida". Left as is.
- **Changing the guardian:** decided (Kevin): allowed, with a popup telling her to let the previous guardian know; nothing is sent automatically. Built: GuardianChange (new private topic for the new guardian, old heartbeat cancelled, new guardian told if alerts are on) and the My settings screen with the popup; not yet walked on the emulator.
