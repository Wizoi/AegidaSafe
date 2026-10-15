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
| 1e | Storage: findings, 90-day history, setup and guardians, feed file | `:store` | done |
| 1f | Android adapters: Snapshotter (Private DNS, default browser, listener, banner permission), notification mapper, banner poster with the brand icon, feed download and verified update | `:platform`, `:core` | done |
| 1g | Wiring: `CheckRunner` and `DailyCheck` (`:alerts`, JVM-tested), notification listener that hides fake website warnings, the "Aegida is looking out for you" foreground service, daily WorkManager job, boot receiver, Hilt graph | `:alerts`, `:app` | done, proven on the emulator (findings #32) |
| 1h | Screens: Home, History, My settings and the eight first-run steps, in the storyboard's ten safety pictures; 71 UI tests per variant (light and dark, normal and 200% font); walked on the emulator and looked at in all four display variants (findings #36-#38) | `:app` | done; guardian change built (see below) |
| 1i | Feed publishing: daily workflow (`build-feed.yml`) builds, signs, verifies and pushes `feed.bin`/`feed.sig` to the public Aegida-Feed Pages site. First run green Oct 8; live feed verified with the committed public key (findings #28, #29) | `.github/`, `:feedtool`, Aegida-Feed | done |
| 1j | Emulator scenarios: `emulator-checks.sh scenarios` 10/10 and `failures` 7/7 (offline daily job keeps the last feed and raises no alarm; a phone that never runs its check gets MissedCheck and ListsOld). Guardian transport built and JVM-tested (Oct 8): scheduled and cancellable ntfy messages, dead man's switch (due in 2 days, pushed back by each daily check), alerts switch, outbox with back-off and retry, `GuardianUnreachable` after 24 h. Done since: public ntfy.sh honours sequence ids for replace and cancel (live test, findings #44); outbox on the emulator, offline (`guardian-offline` 0 failed, #43); 37 mutation checks; a check killed mid-way no longer loses a warning (#42). Heartbeat arming and replace, and guardian change from the real app against live ntfy.sh, were run with `scripts/emulator_live.py` reading the topics as the guardian (findings #71); guardian messages from Mom's phone were fine (#103) | `emulator-checks.sh`, `emulator_live.py` | done |

Open items inside phase 1:
- **The owner's picture on banners:** decided (Kevin) and built: the shield over the owner's safety picture as a faint watermark, drawn by `PictureBanner` and set as the banner's large icon (JVM/Robolectric-tested). No longer blocks the posting thread (findings #51, #61). Kevin's real-phone checks were all fine (#103), but no look at the picture on a banner was recorded.
- **Event warnings that repeat:** back-off built (phase 2, `AlertBackoffTest`, decisions Oct 8).
- **Home wording:** approved by Kevin. The storyboard says "Everything is as it should be."; Home says "No known problems found." instead, as CLAUDE.md requires.
- Dead man's switch: built; replace/cancel confirmed live (findings #44) and armed from the real app against ntfy.sh (#71).
- About screen shows each feed source's licence from the feed itself. Phishing.Database is MIT (verified); URLhaus is excluded until abuse.ch answers (findings #27).
- Look-alike gap: a name written entirely in Cyrillic or Greek letters that imitates a brand is only caught when its letters map to Latin ones (`LinkSigns` homoglyph table). Check against real internationalised domains from the feed before widening it.

## Phase 2. Drift, new apps, remote-control apps
- [x] Snapshotter reads default browser/text/phone apps and who has notification access, screen control or device-admin rights and every installed app with its installer and signing certificate (`QUERY_ALL_PACKAGES` approved Oct 8)
- [x] `DriftDetector` with a baseline taken after setup and "Was that you?" (JVM-tested, emulator `drift` scenario, findings #45)
- [x] `NewAppDetector` (apps installed from outside Play after setup) and `RemoteControlDetector` (known remote-access apps by package name), JVM-tested and walked on the emulator with fixture APKs signed by the debug test key (findings #46). Open test gaps: a second-browser fixture to see default-browser drift, a fixture device-admin app. Not built: matching remote-control apps by signing certificate, and delivering that list in the signed feed
- [x] Escalation (decided Oct 8): the same guardian is reminded once at 24 h if the warning is still open; repeat back-off 1/2/4/7 days. Fake-clock tests (`AlertBackoffTest`); a second guardian is not built
- [x] Emulator scenarios: `drift` (notification access, screen control), `new-apps` (installed by hand, remote-control app). Not yet: default browser/text/phone app changes, device-admin and overlay grants (need fixtures)

## Phase 3. Stalkerware list
- [x] Echap indicators (CC BY 4.0) in the signed feed v2 (610 packages, 470 certificate hashes), refreshed daily by the workflow; `StalkerwareDetector`; credit in the feed NOTICE and in My settings. Walked on the emulator against the live feed (findings #47). Decided (Kevin, Oct 14): keep warning the owner plainly and telling the guardian (findings #47)

## Phase 4. Pixel pass and Play
- [x] Gaps in `emulator-vs-device.md` checked on Kevin's Pixel (Kevin's Oct 9 retest of the Play build; he keeps it running; findings #69 to #78, #103, #107); results in that file
- [x] Internal Play build (0.2.0 on Oct 8, 0.3.0 next); all Console forms and declarations (`QUERY_ALL_PACKAGES`, notification access, foreground service, Data safety, privacy page) done
- [x] Closed-test release: Internal and alpha (closed) tracks live; 0.3.9 (versionCode 12) released on both (Oct 12)
- [x] Play Developer Support about automatic consented alerts: decided not to ask for now (decisions, Oct 9; draft in `play-support-question.md`)
- [x] Name clearance: Kevin's hand check of USPTO looked clean; no trademark filed for now (`name-clearance.md`)

## Phase 5. Samsung (last)
- [x] Second Remote Test Lab session (S25, One UI 8.0, Oct 14): Galaxy Store installs read and now trusted (findings #106)
- [ ] Unmanaged Galaxy on One UI 6+ for Auto Blocker on/off and Maximum restrictions (both lab phones had it disabled, findings #2, #11)
- [ ] Multi-day sleeping checks (3, 7, 16 days unopened) on a phone Kevin owns (findings #1)
- [ ] Samsung Internet notification behavior with a public fixture page

## Notes
Ideas that are not built, and not promised, are listed in `ideas.md`.
- **Code naming:** the person with the phone is the *owner* in code (decided Oct 8).
- **Blank owner name:** setup will not continue without a name, so a blank only appears on a database from before that rule; guardian messages then say "Aegida". Left as is.
- **Changing the guardian:** decided (Kevin): allowed, with a popup asking the owner to let the previous guardian know; with alerts on, the old guardian also gets one short message (decisions, review R12). Built: GuardianChange (new private topic for the new guardian, old heartbeat cancelled, new guardian told if alerts are on) and the My settings screen with the popup. Walked live on the emulator against ntfy.sh (findings #71).
