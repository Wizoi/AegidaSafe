# Aegida — decision history (Oct 7–8, 2026)
*The current spec is `product-brief.md` and the storyboard in `../design/`. This file records how decisions were reached, newest changes last within each topic. Originally called "Phone Angel".*

## Naming and roles
- Original working name "Kevin's Phone Helper" / "Mom's Phone Guard" → **Phone Angel** (Phone Guardian was the alternative), so it isn't tied to Kevin's name. Alert recipients are **guardians**.
- Name check on "Phone Angel": no exact-name product found, but "Angel" security names are crowded (Guardian Angel, Angel Watch) and an ANGEL PHONES trademark exists for phone accessories. "Phone Guardian" is taken by an existing VPN app.
- Other names considered: SecurityWard (generic, "ward" suggests a hospital), Bellwether (crowded, pending BELLWETHER trademark in software), Larkwatch, Wrenwatch, Porchlight, Kinwatch and others (unchecked or only quick-checked).
- **Final name: Aegida** (Kevin, Oct 7, 3:00 pm). No existing app or company found in a first-pass search; "Aegis" is a crowded neighbour. "AegidaSafe" was floated; suggestion is to keep the brand "Aegida" and optionally use "Aegida Safe" as the Play Store title. Formal clearance (USPTO, Play Console, domain, GitHub) still to do. Repo: `AegidaSafe` (private, github.com/Wizoi/AegidaSafe).
- **Logo: locked on idea #1, the sunflower shield** (Kevin, Oct 7, 3:02 pm), chosen from six ideas (sunflower shield, letter A shield, cradled sun, keyhole sunflower, porch-light arch, Æ tile). Full-colour, app-icon and single-colour notification SVGs were produced.

## Setup
- Originally imagined as Kevin setting up in person with a QR code. Kevin lives states away, so **Mom sets up herself**: she enters guardians (one required, optional second), and the app drafts the invitation email in her own mail app (ntfy install links for Android and iPhone plus her alert link). She presses Send. Sending from her Gmail automatically was ruled out earlier (OAuth/app-password burden).
- **Auto Blocker:** first a required first safety switch (earlier, Kevin: "auto-blocker should be enabled by default"). **Changed Oct 8 (Kevin): recommended, not required.** Reason: the app can't turn it on or read it, it exists only on Samsung phones, and Maximum restrictions hides links in messages. Setup guides one tap with "Show me" / "Not now"; the answer is stored as what Mom said, never shown as verified, and the guardian summary says if she chose "Not now". Brief, design doc and storyboard Setup board updated.
- **Distribution** changed from ADB/sideload to **Google Play closed testing (alpha)**, published by Kevin.
- Kevin chose to keep the 10 safety pictures and the alert length as designed.

## Strictness and approvals
- Earlier rule "new app is unapproved until Kevin approves" was **dropped**: Kevin doesn't want to approve every app. New apps pass if Play Protect and the threat feeds are clean; only flagged apps prompt.
- No approve/remove from a guardian's phone (needs infrastructure we don't have).
- Added **remote-control app detection** (AnyDesk and similar).

## Alerts
- On Mom's phone: **heads-up banner** (not full-screen, not overlay).
- Escalation: Kevin after 24 hours; the **second guardian** only if still open 24 hours after the first alert. (First answer was "every alert," then changed.)
- **Guardian ntfy alerts:** first drafted as triggered by a "Tell Kevin" button; Kevin preferred **no button** ("start with it using ntfy automatically after initial pairing"). Settled design: automatic, via an opt-in setup switch Mom turns on herself ("Let Aegida tell my guardians about serious problems"), persistent notice, unique icon, off switch, coarse events only, written in her name. Fallback to the button design if Play objects. The Play monitoring policy is a gray area (2020 text found; adult-to-adult tracking barred even with consent).
- Added Oct 8: guardians get "Aegida hasn't checked in" if the app is silent for 2+ days (covers Samsung putting it to sleep).

## Scam coverage
- Deep research report produced (SMS/links/QR, calls, malicious apps and accounts, free data sources); see `../research/`. Report title predates the rename: "Phone Angel extra scam protections."
- **Always-on safe-call card dropped** as too restrictive; other checks are the backstop.
- **Manual "Check this" (share/scan) demoted**: Kevin doubts Mom would use it. Replaced in v1 by automatic link checks on message notifications plus a guided website filter (Private DNS). Manual version is a later add-on.

## App screens
- Added: home (all good / needs a tap), **history of events** (kept 90 days, confirmed by Kevin), **My settings** where Mom can change guardians, picture, name, the alerts switch, and website protection. Added safety rule: any guardian change notifies the existing guardians.

## Storyboard
- Final structure: Overview + Scenario 0 First run, 1 Using the app, 2 Fake virus warnings, 3 A new app changes her defaults, 4 A safety switch is turned off, 5 Remote-control apps, 6 A scam text is caught, plus a Logo ideas board. Boards show the end state, not the review history.
- Oct 8: Setup step 5 now shows Auto Blocker as "Recommended" with a "Not now" button, and scenario 4's turned-off switch is now **Private DNS / website protection** (something Aegida can actually read) instead of Auto Blocker. Overview next steps updated (emulator first, Samsung checks last).
- Sharing note: the published canvas was found set to "Anyone with the link" after a share change; sharing is controlled from the page's Share menu.

## Technical design (Oct 8)
- Kevin asked for a technical design reusing the rework lessons from **TagPup** and **SmugView**. Result: `aegida-technical-design.md` (draft 3).
- Key choices: Gradle modules so the compiler enforces layers (`:core` pure JVM → `:store`/`:platform` → `:detectors` → `:alerts` → `:app`); one owner per concern with a failing guard test for each (time, HTTP, ntfy, SQL, notifications, secrets, OEM code, banned manifest capabilities, banned words like "safe"); only detectors create findings; single idempotent Evaluator; failure states designed before building; findings log and CLAUDE.md working rules copied from the two repos.
- **Testing decision (Kevin):** no access to Mom's phone. **Emulator first** (scripted, Play image API 36, scripts refuse non-emulator serials, own signed fixture APKs for flagged/remote-control apps, host-served page for website notification spam); **Kevin's Pixel only for known emulator gaps** (tracked in `emulator-vs-device.md`).
- **Samsung decision (Kevin):** One UI is mostly a launcher; default-app and reset logic is plain Android. **Samsung-specific validations go last**; Kevin will try to find an old Samsung phone (a Galaxy S8 is too old: Android 9) and Samsung Remote Test Lab is "an item to look into" (`samsung-validation-options.md`). Until then research from documentation. Samsung-only code lives behind one `OemSource` and returns `NotPresent`/`Unreadable` rather than guessing.
- **Research findings (docs, unverified on device):** (1) Samsung puts apps **not opened for ~3 days** to sleep (jobs, alarms, foreground services restricted) and long-unused apps (~16 days) into deep sleep with no notifications; Mom may never open Aegida, so this is the biggest Samsung risk. Responses: foreground service, a setup deep-link to Samsung's "Never sleeping" list, a "hasn't checked in" guardian alert. (2) **Auto Blocker** blocks installs from unauthorized sources (Play is fine); its Maximum restrictions block device-admin apps and **hide links in messages**, which would starve link checks; Samsung doesn't say apps can read it. (3) Nothing found in documentation about how Samsung Internet handles website notification spam.
- Oct 8: repo created at `C:\src\kidzi\GitHub\AegidaSafe`, private GitHub repository `Wizoi/AegidaSafe`. Docs, research and design artifacts copied into it.

## Gradle skeleton (Oct 8)
- Skeleton built: `:core`, `:detectors`, `:alerts` (pure JVM), `:platform`, `:store` (Android libraries), `:app`, plus `:guards` for the guard tests. Stack as SmugView. The local repo was found nested in an unrelated outer repo at `C:\src\kidzi\GitHub`; Kevin moved that outer `.git` aside and AegidaSafe is now its own repo.
- **Approved by Kevin (Oct 8):**
  - Layering correction (findings #3): ports and `Texts` live in `:core`; `:detectors`/`:alerts` depend only on `:core`. Design section 2 and CLAUDE.md updated.
  - Application ID **`com.aegida.app`** (permanent after the first Play upload).
  - **`INTERNET` permission** approved for build step 1 (ntfy sending); added when the first sender is wired in, not before.
  - Download the **API 36 Google Play** system image and create the `Aegida36` AVD for the emulator spike.
  - Push `main` to github.com/Wizoi/AegidaSafe.

## Build step 1 choices (Oct 8, Kevin)
- **Feeds:** a scheduled GitHub Action downloads the feeds daily, builds one compact file, signs it and publishes it on GitHub Pages; the app downloads that one file and verifies the signature. No server. The signing key lives only as a GitHub secret. (Chosen over the app fetching keyless feeds directly, and over a bundled-only list.)
- **Persistent notice:** a foreground service of type `specialUse` shows "Aegida is protecting you" (adds `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`; needs a Play Console justification). Chosen over listener-only.
- **Website notification fix:** Aegida hides the notification, shows one calm card and opens that site's own switch for Mom to tap. It does **not** fire Chrome's "Unsubscribe" action itself (findings #18).
- **`POST_NOTIFICATIONS`** approved (needed for any banner on Android 13+); granted by Mom in setup.

## Feed publishing (Oct 8, Kevin)
- Public repo **Wizoi/Aegida-Feed** (named after "feed", the code's word; renamed from Aegida-Lists before first push). Served from GitHub Pages at `https://wizoi.github.io/Aegida-Feed/`.
- The **daily build runs in the private AegidaSafe repo** (`.github/workflows/build-feed.yml`), signs there, and pushes only `feed.bin` and `feed.sig` to Aegida-Feed's `gh-pages` branch with a write-only-to-that-repo deploy key. No private code or signing key near the public repo. (Chosen over a public workflow reading the private repo, and over copying the builder, which would let the link-reading code drift from the phone's copy.)
- **URLhaus left out** of the public feed until abuse.ch confirms in writing that republishing is allowed (findings #27). Phishing.Database is MIT; its notice ships with the feed. The builder keeps URLhaus as an option.
- The workflow also runs on a push to `main` that touches the builder, the feed or link code in `:core`, the version catalog or the workflow itself (Kevin, Oct 8), as CardNight's Pages deploy does; docs and app-only pushes do not publish. The verify gate still applies.
- One-time key and secret setup is Kevin's, so private keys never pass through chat: `docs/feed-publishing.md`.

## Storage (Oct 8)
- Room schema **version 1 is extended in place** while nothing has shipped (only the emulator has ever had it). From the first Play build on, every schema change is a tested Migration, asked first (CLAUDE.md).

## Pushing while the initial app is built (Kevin, Oct 8)
- `git push` to Wizoi/AegidaSafe and Wizoi/Aegida-Feed no longer needs a yes each time **while the initial app is being built**. Before every push: full build green and the staged-diff secrets scan clean (CLAUDE.md). Still asked first: publishing to Play, destructive git (force-push, reset, history rewrites), changing repo visibility or settings, anything that sends to guardians, and any new permission or secret.

## Build tooling (Kevin, Oct 8)
- Annotation processing is **KSP**, not kapt (Room, Hilt, Hilt WorkManager), unlike SmugView; the Gradle **configuration cache** is on. Measurements in findings #41. The standing **mutation checks** (`scripts/mutants.json`, 27) and the **emulator snapshot** (`emulator-checks.sh snapshot` / `boot`) are part of the routine; Windows shell traps are in `docs/windows-notes.md`.

## Open items settled (Kevin, Oct 8)
- **Changing the guardian:** allowed. The person holding the phone is in charge and there is no way to authorise remotely. Changing it shows a plain popup telling her to let the previous guardian know. (Supersedes the earlier "any guardian change notifies the existing guardians"; nothing is sent automatically.)
- **Her picture on banners:** the Aegida shield, with her safety picture as a watermark/small overlay.
- **Repeated event warnings:** need a back-off so the guardian is not told constantly; details with escalation in phase 2.
- **Home wording:** "No known problems found." is approved.

## Phase 2 decisions (Kevin, Oct 8)
- **`QUERY_ALL_PACKAGES` approved**, so new-app and remote-control detection can list every installed app. Needs the Play Console declaration (phase 4).
- **The 24 hour step:** a warning still not dealt with after 24 hours reminds the *same* guardian once ("hasn't dealt with a warning yet"). A second guardian is a later feature with its own setup step. The finding becomes `Escalated`, which still counts as open and stays on Home.
- **Repeat back-off (built):** a repeat of the same kind of warning still warns the owner but messages the guardian only after 1, 2, 4, then 7 days since the last message about that kind (by what was told in the last 7 days). Urgent problems are never held back.
- **"She said it was hers" follow-up (Kevin, Oct 8):** when the owner taps "Yes, that was me" on a was-that-you question, a guardian who was told about it gets one short message saying so. A guardian who was never told (default app changes) hears nothing; nothing is sent with the alerts switch off. Plain "Got it" on a scam warning sends nothing.
- **Changing the guardian now messages the old guardian (Kevin, Oct 8, review R12):** this replaces "nothing is sent automatically". When the owner changes her guardian with alerts on, the guardian being replaced gets one short message first ("... changed her guardian in Aegida. You will not get messages about her phone from now on. If you did not expect this, please call her."), then their link stops working and their "hasn't checked in" message is cancelled. The popup says Aegida will send it and that she should tell them herself too. Not sent when alerts are off or there was no guardian. One attempt only: the old topic is no longer the guardian's, so the outbox would drop a retry.

## Naming (Kevin, Oct 8)
- In code the person who holds the phone is the **owner** (`ownerName`, `OwnerNotifier`, `Outcome.OwnerWarned`), not "Mom". What people read uses the name she saved. Product docs may still say Mom as the persona.

## Play release (Kevin, Oct 8)
- **Play store title is "Aegida"**, not "Aegida Safe" (the Console title was "Aegida Safe" from app creation; `publishListing` will change it). Keeps the store page under the never-say-safe rule; `PlayListingTest` enforces it.
- **The always-on notice reads "Aegida is looking out for you"**, not "Aegida is protecting you" (Kevin, Oct 8; the earlier wording is in Build step 1 above). "Protecting" promises more than Aegida can; the welcome text already says "looks out for scams". Same reason the store text and the app avoid "watches".
- **Free, MIT licence.** Distribution starts on Internal testing (versionCode 1, 0.1.0 uploaded Oct 8); the same service account as SmugView publishes through Gradle Play Publisher.
- **Nothing public says the code is open while the repo is private** (Kevin, Oct 9). The store description and the privacy page do not mention open source or the MIT licence; `PlayListingTest.nothingPublicClaimsOpenSourceWhileTheRepoIsPrivate` fails if they do. When the repo is opened: delete that test, add "Aegida is free and open source under the MIT licence. Anyone can read how it works." back to the end of `full-description.txt`, and the licence sentence back to `docs/site/privacy.html`, then publish the listing and push.
- **R8 mapping files are git-ignored** (25 MB each; the bundle carries them for Play), not committed as the old `.gitignore` comment said. Keep a copy per release outside the repo.

## Setup and settings (Kevin, Oct 9)
- **Skipping the notification step asks first, and is not a special message to the guardian.** "Skip this step?" with Go back / Skip anyway. The two permission warnings are not raised until setup is finished; if she skipped, they are raised then, and the existing guardian sentence goes out (only if she chose to tell her guardian). Nothing new is sent.
- **No guardian confirmation.** No "Got it" button and no "Sam has confirmed" status: the guardian can read the message whenever they get to it.
- **Scam-list freshness is a visible line in Settings** ("Lists updated <date>."), not a hidden gesture.
- **The back-off after a guardian change** uses a new key-value setting (`guardians.pairedAt`), not a History column, so no Room migration.

- **Guardian messages use the owner's name, never "her"; only serious ones say "please call"** (Kevin, Oct 9: options B and D). Updates that are small go out as 0.0.1 steps: 0.3.1 next.

- **Not asking Play Support about the guardian alerts beforehand** (Kevin, Oct 9). Risk accepted: Play's monitoring policy names parental and enterprise uses, and an adult picking her own guardian is not clearly covered, so a policy notice is possible later. The consent design (explicit Yes/No in setup, off any time, persistent notice, normal icon, fixed sentences only) is the answer to give if it comes; see `docs/play-support-question.md`.

- **Guardian messages stay on the public ntfy.sh** (Kevin, Oct 9). No token or account: the random topic name is the password. ntfy limits per visitor (IP), and Aegida sends a handful of small messages per phone a day, so family scale is far inside the limits (the figures read were the server defaults; ntfy.sh's own settings were not confirmed). Risk accepted: a free public service with no guarantee. If Aegida grows to hundreds of users, or alerts stop arriving, move to a self-hosted ntfy: change `Endpoint.NTFY_HOST` and the allowed hosts, and re-send invitations (the topic link includes the host).

- **Website notifications (Kevin, Oct 9):** a website that sends a notification for the first time is hidden and the owner is asked "Was that you?"; Yes remembers it, and the guardian is told that it was asked. Android does not let Aegida list the sites that already have notifications (findings #84), so a site the owner already had is asked about once too. Words like "virus" are still hidden at once.

## Open / next session
- Create the Gradle project and run the **emulator spike**: confirm each `adb`/`cmd` scenario works on the API 36 Play image.
- Kevin: look for an old Samsung phone on One UI 6+; look into Samsung Remote Test Lab.
- Ask Play Developer Support about automatic consented alerts.
- Clear the name Aegida (trademark, Play Console, domain, GitHub).
- Storyboard "Logo ideas" board still shows all six ideas; optionally mark #1 as chosen.
