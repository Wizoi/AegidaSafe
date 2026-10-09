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
| 1g | Wiring: `CheckRunner` and `DailyCheck` (`:alerts`, JVM-tested), notification listener that hides fake website warnings, the "Aegida is protecting you" foreground service, daily WorkManager job, boot receiver, Hilt graph | `:alerts`, `:app` | done, proven on the emulator (findings #32) |
| 1h | Screens: Home, History, My settings and the eight first-run steps, in the storyboard's ten safety pictures; 71 UI tests per variant (light and dark, normal and 200% font); walked on the emulator and looked at in all four display variants (findings #36-#38) | `:app` | done; guardian change built (see below) |
| 1i | Feed publishing: daily workflow (`build-feed.yml`) builds, signs, verifies and pushes `feed.bin`/`feed.sig` to the public Aegida-Feed Pages site. First run green Oct 8; live feed verified with the committed public key (findings #28, #29) | `.github/`, `:feedtool`, Aegida-Feed | done |
| 1j | Emulator scenarios: `emulator-checks.sh scenarios` 10/10 and `failures` 7/7 (offline daily job keeps the last feed and raises no alarm; a phone that never runs its check gets MissedCheck and ListsOld). Guardian transport built and JVM-tested (Oct 8): scheduled and cancellable ntfy messages, dead man's switch (due in 2 days, pushed back by each daily check), alerts switch, outbox with back-off and retry, `GuardianUnreachable` after 24 h. Done since: public ntfy.sh honours sequence ids for replace and cancel (live test, findings #44); outbox on the emulator, offline (`guardian-offline` 0 failed, #43); 37 mutation checks; a check killed mid-way no longer loses a warning (#42). Still to do: heartbeat arming and guardian change from the real app against live ntfy, with a phone subscribed | `emulator-checks.sh` | mostly done |

Open items inside phase 1:
- **Her picture on banners:** decided (Kevin): the shield with her picture as a watermark. Needs rendering to a bitmap for `setLargeIcon`. To build.
- **Event warnings that repeat:** a scam message acknowledged and then repeated warns Mom and the guardian again (tested, `FindingActionsTest`); decided (Kevin): needs a back-off so guardians are not told constantly; build with escalation in phase 2.
- **Home wording:** approved by Kevin. The storyboard says "Everything is as it should be."; Home says "No known problems found." instead, as CLAUDE.md requires.
- Dead man's switch: built and its replace/cancel behaviour confirmed live (findings #44); the app-side arming still needs a live run with a subscribed phone.
- About screen shows each feed source's licence from the feed itself. Phishing.Database is MIT (verified); URLhaus is excluded until abuse.ch answers (findings #27).
- Look-alike gap: a name written entirely in Cyrillic or Greek letters that imitates a brand is only caught when its letters map to Latin ones (`LinkSigns` homoglyph table). Check against real internationalised domains from the feed before widening it.

## Phase 2. Drift, new apps, remote-control apps
- [ ] Snapshotter reads roles, installed apps (installer, signing cert), special-access grants
- [ ] `DriftDetector`, `NewAppDetector`, `RemoteControlDetector`, with fixture APKs signed by our own test key (a second browser, an AnyDesk-like app)
- [ ] Escalation: guardian 1 at 24 h, guardian 2 24 h after the first alert (WorkManager, fake-clock tests)
- [ ] Emulator scenarios for drift, new app, remote control, device-admin and overlay grants (findings #19, #20)

## Phase 3. Stalkerware list
- [ ] Bundled and refreshed Echap indicators (CC-BY 4.0), `StalkerwareDetector`, About-screen attribution

## Phase 4. Pixel pass and Play
- [ ] Gaps in `emulator-vs-device.md` checked on Kevin's Pixel
- [ ] Internal, then closed-test Play build (`QUERY_ALL_PACKAGES` declaration, notification-listener and foreground-service disclosures)
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
- **Blank owner name:** guardian messages fall back to "Aegida" (reads badly); choose better wording.
- **Changing the guardian:** decided (Kevin): allowed, with a popup telling her to let the previous guardian know; nothing is sent automatically. Built: GuardianChange (new private topic for the new guardian, old heartbeat cancelled, new guardian told if alerts are on) and the My settings screen with the popup; not yet walked on the emulator.
