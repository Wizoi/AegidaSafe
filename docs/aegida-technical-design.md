# Aegida — technical design (draft 3, Oct 8, 2026)

Companion to `product-brief.md` (what the app does) and `decisions.md` (why). It describes the app as built; ideas that are not built are in `ideas.md`, without promise. This file is **how it is built so it stays easy to change and cheap to check**. The structure and rules are taken from what cost time in Kevin's **SmugView** (Android: Kotlin, Compose, Room, Hilt, WorkManager; 899 unit tests) and **TagPup** (Python; layers + guard tests), including their retros.

**Ground rules (Kevin, Oct 8):**
1. No access to the owner's phone. **Emulator first**; Kevin's own phone (a **Google Pixel**) only for known emulator gaps.
2. Most of the app is plain Android (default-app roles, packages, listeners, Private DNS) and behaves the same on a Pixel and a Galaxy. **Samsung-specific validations go to the end.** Options (see `samsung-validation-options.md`): Samsung Remote Test Lab (to look into), a Galaxy on One UI 6+ if Kevin finds one (a Galaxy S8 is too old), or the observe-only release. Until then they are researched from documentation and handled by defensive design (section 8b).
3. **Samsung Auto Blocker is a recommendation, not a requirement.** Aegida guides one tap, never reads it as verified, and keeps the owner's answer ("Not now" included) as what the owner said. Safety switches Aegida *can* read (Private DNS, default apps, special-access grants) are the ones it enforces and uses in drift alerts.

Draft 3: Samsung items researched and moved to the end; Auto Blocker recommended; battery-management consequences added.

## 1. What carries over, and why

| Lesson (source) | What it becomes in Aegida |
|---|---|
| **One owner per concern; a guard test enforces each owner** (TagPup: `db.py`, `processes.py`, `paths.py`; each ended a whole class of bug) | Single-owner rules for notifications, ntfy sends, link checking, feed storage, time, and settings (section 4). Each has a test that fails the build. |
| **Layers; imports only go down; a composition root builds the objects** (TagPup `runtime`) | Gradle modules, so the compiler enforces layers instead of a convention (section 2). One `AppGraph` builds everything (Hilt at the edge only). |
| **"The second fix for one symptom is a question: who owns this?"** (both repos; SmugView's "new" dot patched twice) | Written into CLAUDE.md for Aegida on day one. Each alert condition has exactly one detector owning "is this a problem?" (section 5). |
| **Name where each side gets its data** (SmugView: tree vs flat album index disagreed and *was* the bug) | Drift compares *baseline* vs *current snapshot*, both produced by the same `Snapshotter`. No second reading path. |
| **A test's data is made by the code that makes the real thing** (TagPup fixtures; SmugView "ID shapes must be real") | Fixtures are recorded from a real device or emulator (`adb` dumps, real notification payloads), not invented (section 7). |
| **Every fix gets a test that fails on the old code first** | Same rule. |
| **Design for how it fails** (offline, 429, process death, racing background work) | Failure table in section 6; every detector returns `Unknown`, never silently "fine". |
| **One kind of failure, one text** (SmugView `UserMessages`/`Problem`) | `Problem` enum with every sentence the owner or a guardian can see in one file. No raw exception text. |
| **Pure function decides screen state** (`GridView.of`) | `HomeState.of(findings, now)` is pure; Compose only draws it. |
| **Dead columns and silent writes are bugs** ("a write reports what it changed") | Every write returns a `Result(changed, attempted, skipped, errors)`; an alert send reports delivered / not delivered. |
| **Test time is a budget** (`settle()` cost 162 of 184 s) | Fast JVM core; Robolectric only where needed; per-class time check when the suite passes 5 min. |
| **Phone-only checks are named, everything else runs on emulator first** (SmugView retro: 7 of 12 phone checks could have run on the emulator) | Section 8 lists the *known emulator gaps*; everything else is scripted on the emulator. |
| **Agents get a time box; the lead runs the full suite; commit before launching agents** | Copied as working rules. |

## 2. Module structure (compile-time layering)

```
:app               Android entry points: Activity, Compose UI, Services, Receivers,
                   WorkManager workers, AppGraph (composition root). Depends on all.
   |
   +--> :alerts      AlertPipeline, ntfy sender, reminders. Pure JVM
   |      |
   |    :detectors   One detector per concern (section 5). Pure JVM
   |      |
   +--> :platform    Android adapters implementing the :core ports: PackageSource,
   |      |          SettingsSource, NotificationSource, Clock,
   |      |          HttpClient, NotificationPoster, OemSource (Samsung-only readings;
   |      |          NotPresent on a Pixel)
   +--> :store       Room database, DAOs, migrations, feed file (the only SQL);
          |          exposes only :core ports (AegidaStore), never Room types
          v
:core              Pure Kotlin/JVM, no Android imports: models, Baseline, Snapshot,
                   Finding, Severity, UrlRules, Domain matching, HomeState, Result,
                   user text (Texts/Problem) and the ports (Clock, HttpClient,
                   OemSource, CheckLog, ...)

:guards            Guard tests only (section 4); ships nothing
```

`:detectors` and `:alerts` depend only on `:core`, never on `:platform`/`:store`: a JVM module cannot depend on an Android library, so the interfaces they need live in `:core` and the Android modules implement them (findings #3, Oct 8). User text lives in `:core` because `NotificationPoster` in `:platform` needs it too.

Rules (all enforced, see section 4):
- Dependencies point down only (`LayerTest` checks every `build.gradle.kts`). `:core`, `:detectors` and `:alerts` are JVM modules, so any Android import there fails to compile.
- `:detectors` never touches Android types: it receives `Snapshot` objects from `:platform` adapters. This is what makes ~90% of the logic runnable as fast JVM tests.
- `:app` contains no decisions, only wiring and drawing. A ViewModel facade plus per-concern controllers, as in SmugView; new logic goes in the controller, never back in the facade.
- Port/adapter used **only where it pays** (TagPup option C): the seams are the ones that need a fake in tests — Clock, PackageSource, SettingsSource, NotificationSource, HttpClient, NtfySender, OemSource. Everything else is a plain class.
- **Everything Samsung-specific lives behind `OemSource`** (one owner). Each reading returns `Present(value)`, `NotPresent` (e.g. no Auto Blocker on a Pixel) or `Unreadable`. Detectors never assume a Samsung feature exists. This also lets Pixel/emulator testing exercise the "not present" path for real.

Stack (matches SmugView so tooling and habits transfer): Kotlin, Compose + Material 3, Hilt, Room (explicit migrations, exported schemas, no destructive fallback), WorkManager, OkHttp, Kotlinx serialization, JUnit4 + Mockito/Mockk, kotlinx-coroutines-test, Robolectric, work-testing, room-testing.

## 3. Core model

- **Snapshot**: immutable reading of the phone at a moment: default apps (browser, SMS, dialer, assistant), installed apps (name, installer, and the signing-certificate fingerprint for apps that did not ship with the phone), special-access grants (notification listener, screen control through accessibility, device admin), Private DNS mode/host, whether Aegida's own listener is on and it can post notifications, and OEM readings (`OemSource`: Auto Blocker and the Samsung never-sleeping list, both `Unreadable` on a Samsung and `NotPresent` elsewhere today), `takenAt`.
- **Baseline**: a `Snapshot` taken when setup finished, plus the approved diffs since (changes the owner confirmed). Stored as one row set with a schema version. Auto Blocker is stored as **what the owner told Aegida** (`UserSaidOn`/`NotNow`/`NotAsked`), never as a verified fact.
- **Finding** = `(detectorId, kind, subject, severity, evidence, firstSeen, lastSeen, state)`. Identity is `(detectorId, kind, subject)`, so a repeated check updates one finding instead of making many alerts. States: `Open → Acknowledged | Resolved | Escalated`.
- **Severity**: `Info`, `NeedsTap` (banner on the owner's phone), `Urgent` (remote-control and spying apps, new special access: the guardian message is never backed off), each mapped once to banner behavior and guardian message.
- **Verdict wording**: `NoKnownProblems`, `Warning`, `Unknown`. The string "safe" does not exist in the codebase (a test greps for it).

## 4. Guard tests (each fails the build, TagPup style)

All run on the JVM against a list of shipped source files (`ShippedSources`), like TagPup's `shipped_sources.py`.

| Single owner | Only here | Test |
|---|---|---|
| Time | `Clock` interface; no `System.currentTimeMillis()`, `Instant.now()` outside `:platform` | `ClockSingleOwnerTest` |
| HTTP | `HttpClient` in `:platform`; no raw `OkHttpClient`/`URL.openConnection` elsewhere. Nothing may fetch a URL taken from a message or notification | `NetworkSingleOwnerTest` (also asserts no `INTERNET` use for message-derived URLs) |
| ntfy | `NtfySender` only; all payloads built by `AlertPipeline` from a `Finding` | `NtfySingleOwnerTest` |
| SQL / Room | Only `:store` | `SqlSingleOwnerTest` |
| Notifications posted | `NotificationPoster` (channels, icon, persistent notice, heads-up flags) | `NotificationSingleOwnerTest` |
| Secrets | No keys in source or APK; the feed signing key is a GitHub Actions secret only | `NoSecretsTest` (regex scan of source and `BuildConfig`) |
| Layers | `:core` has no `android.*`; `:detectors` no `android.*`; `:app` has no `if (severity …)` logic | `LayerTest` + Gradle module graph |
| OEM code | Samsung package names, intent actions and `Build.MANUFACTURER` checks only in `OemSource` | `OemSingleOwnerTest` |
| Banned capabilities | No `AccessibilityService`, `READ_SMS`/`RECEIVE_SMS`, `RECORD_AUDIO`, `BIND_VPN_SERVICE`, `SYSTEM_ALERT_WINDOW`, `USE_FULL_SCREEN_INTENT`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (Play restricts it) in the manifest | `ManifestPolicyTest` in `:app`, `ManifestSourcePolicyTest` (this is the Play policy line from the brief, made executable) |
| Banned words | "safe", "secure", "protected from" in user text | `CopyRulesTest` |
| User text | Every string the owner or a guardian sees lives in `Problem`/`Texts`; no `localizedMessage` | `UserTextTest` |
| Dead state | Every Room column is read somewhere (SmugView's dead-column lesson) | `NoDeadColumnsTest` |

## 5. Detectors and the pipeline

```
 platform adapters --> Snapshot --\
 notification listener -> LinkEvent -+--> Detector(s) --> Finding(s) --> FindingStore --> AlertPipeline
 feed sync (daily) --> FeedStore ---/                                                      |-> owner: heads-up banner
                                                                                           |-> guardian: ntfy
                                                                                           '-> History (90 days)
```

Detector contract (one interface, trivially fakeable):
```
interface Detector { val id: DetectorId; fun evaluate(input: Inputs): List<Finding> }
```
Pure, deterministic, given `Inputs(snapshot, baseline, feeds, linkEvent?, now)`.

| Detector | Owns the question | Build order |
|---|---|---|
| `LinkDetector` | "Is this URL known-bad or a look-alike?" (the signed scam list from Phishing.Database, plus look-alike signs). Never fetches the URL | 1 |
| `PopupSpamDetector` | Browser notification-permission/channel grants for unknown sites; cancels via listener | 1 |
| `DnsDetector` | Private DNS configured to the chosen provider (the readable "safety switch"; storyboard scenario 4 uses it) | 1 |
| `HealthDetector` | "Is Aegida itself still able to look out?" (listener enabled, banners allowed, daily check ran, feed fresh) | 1 |
| `DeliveryDetector` | "Can Aegida still reach the guardian?" (a message held for a day) | 1 |
| `DriftDetector` | Differences between baseline and snapshot (default apps, special access). Auto Blocker only if a real reading exists | 2 |
| `NewAppDetector` | An app installed after setup from outside Play; no manual approval. Not raised: Play apps, apps from setup, system apps, and on a Samsung apps the Galaxy Store installed and asked for (`Snapshot.makerStores`, system apps only; findings #106) | 2 |
| `RemoteControlDetector` | Known remote-access packages, by package name | 2 |
| `StalkerwareDetector` | Echap indicator list, by package name and signing certificate (CC BY 4.0 credit in My settings) | 3 |
| `NoisyAppDetector` | An app that keeps posting ad-like notices (`notification-ad-spam-design.md`) | after 3 |

**One owner for "is something wrong?"**: only detectors create `Finding`s. The UI, the notification listener, and the workers call `Evaluator.run(trigger)`; they never make findings themselves. (This is SmugView's "which single place owns *is something new here?*" answer, applied up front.)

**Triggers**: notification posted (listener), app start, daily WorkManager. A new app or changed setting is noticed at the next of these, not the moment it happens. All triggers funnel to one `Evaluator`, which is idempotent: running twice yields the same findings.

**AlertPipeline** (the only path to people):
1. Dedup by finding identity.
2. Owner: heads-up banner (`NotificationPoster`), history row.
3. Guardian: ntfy POST if the opt-in is on. Payload is a fixed sentence with the owner's first name; no message text, no whole web address (an app name and a masked address at most). Repeats of the same kind are backed off 1, 2, 4, then 7 days; urgent problems never are.
4. Reminder: a finding still Open after 24 h reminds the same guardian once and becomes `Escalated` (still open, still on Home). Checked on each run.
Every step returns a `Result`; a failed send is retried with backoff and shown in History as "couldn't reach <guardian>" (never silently dropped).

## 6. Failure design (decided before building)

| Situation | Behavior |
|---|---|
| Phone offline at check time | Detectors use the cached lists. `HealthDetector` raises `Problem.ListsOld` ("Aegida couldn't update its scam lists for a few days") once the lists are older than 3 days (`LISTS_OLD`), so Home does not say "no known problems found" without it. No `Verdict.Unknown` is produced by any detector today |
| ntfy unreachable | Queue, retry with backoff, History line; the owner is told only if it persists over 24 h |
| Process killed mid-check | Evaluator is idempotent; WorkManager retries |
| Feed download fails or is malformed | Keep last good copy; checksum + size sanity check before swapping |
| Notification listener revoked | Becomes a `NeedsTap` finding ("website protection is off") with one-tap fix |
| **OEM puts Aegida to sleep (Samsung sleeping/deep sleeping, aggressive Doze)** | See 8b. Design response: the persistent foreground notice; `HealthDetector` notices a missed daily check (`lastCheckedAt`) and the guardian is told if the phone goes quiet for 2+ days ("Aegida hasn't checked in"). Never claims "all good" from a stale check |
| Two triggers at once | Evaluator serialised by a single-writer mutex |
| Guardian changed | With alerts on, the old guardian gets one short message (one attempt), then their topic and "hasn't checked in" message are cancelled; the change is not blocked |
| Unknown package/URL not in any feed | `NoKnownProblems`, never "safe" |
| Samsung feature absent (Pixel) or unreadable | `OemSource` returns `NotPresent`/`Unreadable`; shown as "can't check this on your phone", never as a problem or as OK |
| Owner chose "Not now" for Auto Blocker | Recorded as `NotNow`; never shown as on; never a drift alert (nothing to drift from) |

## 7. Validation and test strategy

**Order of evidence: JVM → Robolectric → emulator → Kevin's Pixel → (last) a Samsung phone.** A check moves to the next tier only if the tier before it cannot show the thing. Target: JVM + Robolectric under ~3 minutes (the SmugView 134 s mark).

| Tier | How | Share |
|---|---|---|
| `:core`, `:detectors` | Plain JVM tests with fake `Inputs`, millisecond each | bulk |
| `:store` | Room in-memory + `MigrationTestHelper` against exported schemas; a test per migration | moderate |
| `:alerts` | Fake `NtfySender`, fake `Clock`; reminder timelines driven by advancing the clock | moderate |
| `:app` | Robolectric + Compose tests; `HomeState.of` tested as a pure function, screens only draw | small |
| Manifest/policy/layer guards | JVM source scans (section 4) | small, fast |
| **Emulator (scripted, the default for anything on-device)** | `androidTest` + `scripts/emulator-checks.sh`; see below | moderate |
| Kevin's Pixel | Only the known emulator gaps in section 8a | few |
| Samsung (Remote Test Lab or a Galaxy, if found) | Section 8b, done last | few |

### Emulator-first harness

`scripts/emulator-checks.sh` (modelled on SmugView's: it **refuses any serial that is not `emulator-*`**, so a script can never touch the real phone by mistake). Subcommands: `avd` (create), `smoke`, `scenarios`, `shots`. AVD: Pixel profile, **Google Play system image, Android 16 (API 36)**, matching the owner's OS level (SmugView found API 37 emulators unstable on Kevin's machine, so use 36; verify). The Play image matters: it has Play Services, Play Protect, and Chrome.

Scenario scripts the emulator can drive through `adb` (each is a repeatable test, not a manual poke; verify each command works on API 36 in the first spike):

| Scenario | Emulator mechanism |
|---|---|
| Baseline snapshot, drift on default browser/SMS/dialer roles | `cmd role add-role-holder` / `remove-role-holder`, install a second browser APK |
| New app arrives (clean, flagged, remote-control) | `adb install` of **test fixture APKs we build and sign with our own test key**; test feed contains that key's cert hash. Flagged and "AnyDesk-like" apps are our fixtures, never real malware |
| Special-access grants (notification listener, overlay, device admin) | `cmd notification allow_listener`, `appops set`, device-admin set via `dpm` where permitted |
| Link checking from message notifications | `cmd notification post` and a small fixture "messages" app posting notifications with known-bad and clean URLs; listener reads, cancels, raises finding |
| Fake virus pop-up (website notification spam) | Chrome on the emulator opens a **local fixture page served from the host** through `adb reverse` as `http://localhost:8765` (a secure origin, so it may ask for notification permission; `emulator-checks.sh popup-page`) that requests notification permission and sends notifications; verifies detection and cancel. Notification shape recorded in findings #18 |
| Private DNS check and "switch turned off" drift | `settings put global private_dns_mode` / `private_dns_specifier`; turn it off after baseline and watch the reminders |
| Offline / airplane / metered | `cmd connectivity airplane-mode`, host-side network shaping |
| Daily worker, 24-hour reminder | `cmd jobscheduler run` / WorkManager test driver; 24 h waits done with the fake `Clock` in JVM tests, plus one emulator run with the clock advanced (`settings put global auto_time 0` + `cmd alarm set-time`; the Play image has no root, so `date` fails: findings #17) |
| Battery restrictions (stock Android) | `cmd deviceidle`, `am set-standby-bucket` (rare/restricted), `cmd appops` background restrictions; verifies `HealthDetector` notices a starved worker |
| ntfy end-to-end | Real ntfy.sh topic from the emulator, a second client (host `curl` subscribe, or ntfy app on the emulator) as the guardian |
| Samsung-absent path | `OemSource` returns `NotPresent` on the emulator and Pixel; asserts no crash and the right wording, including the Auto Blocker "recommended" setup step and "Not now" |
| Both themes, font scale 200%, all tabs | `ThemeSweepSmokeTest`-style sweep with `cmd uimode night` and `settings put system font_scale 2.0` (large text is the norm for the owner) |
| Process death, low storage | `am kill`, `fallocate` on the data partition |
| First run flow with 10 safety pictures, invitation email | UI test; email draft checked as an Intent (not sent) |

### Fixtures from the real thing
Capture `dumpsys package`, `dumpsys notification`, `settings get`, role holders, real notification payloads, real feed files **from the emulator first, then diff against Kevin's Pixel once**; differences go in `emulator-vs-device.md` (the running list of gaps, and where section 8a comes from). Samsung fixtures are added only when a Samsung device is available and are labelled as such. Tests parse fixtures with the *same* adapters production uses. A fixture that no real device produces is a review finding (TagPup #246/#247, #590).

**Snapshots vs hand-built scenarios** (SmugView rule): recorded payloads for *parsing* tests (feed formats: Phishing.Database, Echap; ntfy responses); hand-written scenarios for *logic* tests (new app arrives with a remote-control cert; DNS switched off after baseline).

**Every fix:** write the test, run it on the old code and see it fail, then fix. No `git stash`; comment out the fix or use a worktree.

**Mutation checks** on the high-stakes logic (`DriftDetector`, the reminder, link matching): break the root cause, not a guard another guard also catches; the test must go red.

**Feed contract tests**: URL-match tests include the traps real data has (punycode, userinfo `@` tricks, trailing dots, `www.` vs apex, URL shorteners, uppercase hosts, IDN homographs). Property-style test: matching is monotonic (adding a feed entry never removes a finding).

**Time**: all tests use fake clocks; test dates relative to now (SmugView #9).

**Per-class time check** when the suite passes 5 minutes or a class passes 60 s.

**Findings log**: `findings.md`, a numbered row per bug/review item *before* fixing it (TagPup: "findings that lived only in a conversation were lost"), with: premise checked, count over the whole set for any "all/none" claim, test that failed first, commit.

## 8. Device validation

### 8a. Known emulator gaps → Kevin's Pixel
Because the owner's phone is unavailable, the only real-device checks are things an emulator cannot show. Each gap is recorded with a reason; if a gap closes it moves back to the emulator list.

| Gap | Why the emulator can't show it | Check on the Pixel |
|---|---|---|
| Heads-up banner look, sound, Do Not Disturb, Android 15/16 OTP/sensitive-content redaction of notifications | Needs real SystemUI/policy and real message apps | Send real SMS/RCS and messaging-app links to the Pixel |
| Real messaging apps (Google Messages, WhatsApp) notification payload shapes | No real accounts on the emulator | Record payloads once into fixtures |
| Real Chrome behavior with real notification-spam sites | Emulator Chrome differs; real spam sites risky on an emulator | Fixture page first; real known-spam site only if needed |
| Private DNS guided flow wording | Settings UI differs by build | Walk the guided steps on the Pixel |
| Real push latency and Doze with the phone idle overnight | Emulator doesn't model Doze/real push | Guardian alert while the Pixel sits idle overnight; check `lastCheckedAt` after 3 days |
| Play-installed build (not sideloaded), update flow, closed-test opt-in | Needs Play Console | Install via the internal/closed track |

### 8b. Samsung-specific validations — LAST
How to get a Samsung to test on: `samsung-validation-options.md` (Samsung Remote Test Lab, a Galaxy on One UI 6+, or the observe-only release; a Galaxy S8 is too old). Researched from documentation (Oct 8). Nothing here is verified on a device yet.

| Item | What documentation says | Design response now | Validate later |
|---|---|---|---|
| **Auto Blocker** | A Samsung-only feature (Settings > Security and privacy > Auto Blocker). Blocks installs from unauthorized sources (sideloaded apps also can't update), blocks USB commands, Message Guard (blocks malware images in messaging apps), blocks non-official software updates. "Maximum restrictions" adds: App protection scanning, **blocks device admin apps and work profiles**, blocks 2G, blocks auto-reconnect to insecure Wi-Fi, **hides links and previews in messages**, blocks shared albums, strips location from shared pictures. On by default for devices shipped with One UI 6.1.1; updated devices keep their prior setting. Samsung's page does not say apps can read or control it. | **Decided (Kevin): recommended, not required.** Aegida installs from **Play**, so the base setting does not block it. Setup explains it in one sentence, opens the page, and offers "Show me" or "Not now"; the answer is stored as what the owner said, never as verified. Read it through `OemSource` only if a reading exists. With Maximum restrictions on, links are hidden in messages, so link checks have nothing to check and that must not read as "no known problems" | Find whether the setting is readable (`Settings.Secure`/`Global` key or via `dumpsys`), what its toggles look like on One UI 8, and that a Play-installed Aegida is not blocked or updated oddly. If it is readable, add a drift check |
| **Sleeping / deep sleeping apps** | Apps **not opened for about 3 days** become "sleeping": jobs, alarms and foreground services are restricted. Unused for a long time (page says 16 days) they become **deep sleeping**: run only while open and cannot send notifications. Users exempt apps in Settings > Battery > Background usage limits ("Never sleeping"). Samsung documents a deep link: action `com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY` with extra `activity_type` (0 sleeping, 1 deep sleeping, 2 never sleeping). Per dontkillmyapp.com, there is no developer-side fix, firmware updates and reboots can re-add apps to the lists, and automatically requesting battery exemptions risks a Play policy violation. Samsung says foreground services should work on Android 14+ (One UI 6+) if the app follows Android's foreground-service policy. | **This is the main risk: the owner may never open Aegida, so Samsung would put it to sleep after ~3 days.** Built: (1) the persistent "Aegida is looking out for you" foreground service, the main defense; (2) `HealthDetector` + guardian "hasn't checked in" alert for 2+ quiet days. Not built: a setup step for the Never sleeping list (`ideas.md`). Never the restricted direct-request battery permission | Does a foreground service plus a bound notification listener survive 3, 7 and 16 days unopened? (Needs a phone you own; a remote lab session is too short.) Does the deep link open the right screen on One UI 8? Does "Never sleeping" hold after a reboot or update? |
| **Samsung Internet / Samsung Messages** | Samsung's docs describe an enterprise policy for blocking notifications by URL (`NotificationsBlockedForUrls`, Windows only) and nothing about notification-spam handling in the phone browser, so no behavior could be confirmed from documentation. | Whether the owner uses Chrome or Samsung Internet is a setup reading (default browser role). The pop-up guard targets whichever browser holds the role. Samsung Internet is `Unknown` until validated | How Samsung Internet posts website notifications (channels? one group?), whether the listener sees them and can cancel them; Samsung Messages notification shapes |
| One UI default-apps and Private DNS screens | Menu names and layouts vary by model and software version (per Samsung's own pages) | Guided steps use generic wording first; screenshots added only after seeing the screen | Walk setup on a Samsung and fix the wording |

**Residual risk until validated:** a wrong Samsung assumption can leave a check silently not working. The mitigations that apply regardless: readings can be `Unknown` and are never shown as fine; the guardian is told if the app goes quiet.

## 9. Build, release, CI

- Gradle with Android Studio JBR; check success by `BUILD SUCCESSFUL`, not exit code through a pipe (SmugView).
- CI (GitHub Actions) runs only the daily feed build (`build-feed.yml`). Unit tests, guard tests and emulator scenarios run locally before each commit. Release is a manual step.
- Versioning: versionCode monotonic; `-PlocalBuild` marks device test builds. targetSdk kept within Play's one-year rule.
- Release hygiene: R8 on, mapping files kept per versionCode outside the repo.
- Secrets: none in the APK. A daily GitHub Action downloads the keyless feeds, builds one signed file and publishes it on GitHub Pages (`feed-publishing.md`); the app downloads the file and verifies the signature with the public key. The signing key is a GitHub Actions secret. There is no server.
- Privacy by construction: the app sends nothing but ntfy alerts and feed downloads; the Play data-safety form is derived from the `NetworkSingleOwnerTest` allowlist.

## 10. Working rules for the Aegida repo (CLAUDE.md, short on purpose)

1. New code goes where section 2 puts it; check the module table before adding a class.
2. Record every review finding in `findings.md` before fixing it.
3. The second fix for one symptom is a question: which single place should own this?
4. Check the premise against real data (emulator or phone dump, real feed) before writing a fix; "all/none" claims need a count.
5. A fix gets a test that fails on the old code first.
6. Test data comes from the code that makes the real thing.
7. Design the failure states (offline, killed, racing, revoked, put to sleep) before building.
8. Ask before one-way choices: Room migration, new default the owner didn't choose, changing what "alert" or "resolved" means, anything that sends to guardians.
9. Look at every screen in both themes and at 200% font size.
10. **Emulator first.** Script it. The Pixel is only for gaps listed in `emulator-vs-device.md`; a symptom seen on a device is a finding in the same step, with an emulator reproduction attempted before it is deferred. Scripts refuse non-emulator serials.
11. **Samsung-specific code lives only in `OemSource`**, returns `NotPresent`/`Unreadable`, and nothing Samsung-specific is claimed as working until it has been validated on a Samsung.
12. Never show a setting as on, off or fine unless Aegida read it; otherwise it is "you told me" or "can't check".
13. Agents get a 45-minute box; the lead runs the full suite; commit before launching background agents and keep their work in worktrees.
14. Publishing to Play, `git push` and destructive git operations each need explicit confirmation.
15. Never commit secrets; scan staged diffs.

## 11. Build order mapped to modules

1. `:core` + `:detectors` (`LinkDetector`, `PopupSpamDetector`, `DnsDetector`, `HealthDetector`) + `:store` + listener + foreground service + `AlertPipeline` + Home/History screens. Gate: guard tests green; emulator scenarios for link check, pop-up page, DNS, starved-worker pass; fixtures captured.
2. `DriftDetector`, `NewAppDetector`, `RemoteControlDetector` (fixture APKs); the 24-hour reminder and repeat back-off.
3. `StalkerwareDetector` with the Echap list; credit in My settings.
4. Pixel gap pass (section 8a); internal then closed-test build via Play.
5. **Last: Samsung validations (section 8b)**: Samsung Remote Test Lab first session (checklist in `samsung-validation-options.md`), a physical Galaxy for the multi-day sleeping tests if Kevin finds one.
Ideas that are not built: `ideas.md`.

## 12. Open technical questions

- **Emulator spike first (day 1):** confirm each `adb`/`cmd` mechanism in section 7 works on the API 36 Play image (roles, `allow_listener`, `post` notifications, Private DNS settings, Chrome notification permission from a host-served page, standby buckets). Anything that doesn't moves to the gap table.
- ~~Exact way to read a browser's per-site notification grants without Accessibility~~ Answered by the spike (findings #18): Chrome posts each site on its own channel `web:<origin>;<n>`; the listener sees the origin and `ACTION_CHANNEL_NOTIFICATION_SETTINGS` opens that site's switch. Confirm on the Pixel; Samsung Internet still unknown.
- Will a foreground service + notification listener keep Aegida alive under Samsung's sleeping rules when the owner never opens it? Documentation says foreground services are supported on One UI 6+ but is silent on WorkManager; unknown until tested on a Samsung.
- Is Auto Blocker readable at all? Only answerable on a Samsung; until then it stays "you told me".
- Samsung Remote Test Lab: session length, which Galaxy models and One UI versions, whether our own APK installs, adb access (see `samsung-validation-options.md`). Look into it when step 5 comes up.
- ~~Signed combined feed hosting and key custody~~ Answered: GitHub Pages, key as a GitHub Actions secret (`feed-publishing.md`).
- Hilt vs hand-wired graph: Hilt matches SmugView; a hand-wired `AppGraph` is smaller. Default: Hilt, as Kevin already knows it.
- Play policy answer on automatic consented alerts (blocks release beyond closed testing, not development).
- Whether to set up `.claude` agents (worker/reviewer) as in TagPup.
- Physical Galaxy: Kevin to look; it must be on One UI 6+ (Galaxy S8 is too old).

*Resolved:* Auto Blocker is recommended, not required (Kevin, Oct 8). Repo is github.com/Wizoi/AegidaSafe.

## Sources
Local (read Oct 8): SmugView `CLAUDE.md`, `.agents/AGENTS.md`, `docs/DESIGN.md`, `docs/review/2026-10-02-retro-whole-app-review.md`, `app/build.gradle.kts`; TagPup `CLAUDE.md`, `docs/ARCHITECTURE.md`, `docs/RETRO_PHASE_9.md`.
Web (Oct 8): Samsung, "Protect your Galaxy device with the Auto Blocker feature" (samsung.com/latin_en/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/); Samsung Developers, "App management" (developer.samsung.com/mobile/app-management.html); Don't kill my app, Samsung (dontkillmyapp.com/samsung); Samsung Developers, Samsung Internet "NotificationsBlockedForUrls" policy (developer.samsung.com/browser/policy/notifications-blocked-for-urls.html); Samsung Developers, Remote Test Lab (developer.samsung.com/remote-test-lab).
