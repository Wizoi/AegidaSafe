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
| 1f | Android adapters: read Private DNS, default browser, listener and notification state into a `Snapshot`; map a notification into a `NotificationEvent`; banners (`NotificationPoster`); feed download with signature check | `:platform` | next |
| 1g | Wiring: notification listener (hide scam notifications, open the site's switch), "Aegida is protecting you" foreground service, daily WorkManager job (check, feed update, dead man's switch) | `:app` | next |
| 1h | Screens: Home (pure `HomeState.of`), History, first-run setup, My settings; both themes at 200% font | `:app` | next |
| 1i | Feed publishing: separate repo with a scheduled GitHub Action and Pages; signing key as a secret; public key in the app | outside this repo | needs Kevin |
| 1j | Emulator scenarios: link check, pop-up page, website filter turned off, starved worker, offline, killed mid-check | `emulator-checks.sh` | next |

Open items inside phase 1:
- `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE` and `FOREGROUND_SERVICE_SPECIAL_USE` are approved but not yet in the manifest; add them with 1f/1g. `ManifestPolicyTest` has no rule against them.
- Dead man's switch for "Aegida hasn't checked in" through ntfy scheduled delivery (findings #23).
- Feed licences (Phishing.Database, URLhaus) are still "to verify" in About.
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
