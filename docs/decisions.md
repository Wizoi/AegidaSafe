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

## Open / next session
- Create the Gradle project and run the **emulator spike**: confirm each `adb`/`cmd` scenario works on the API 36 Play image.
- Kevin: look for an old Samsung phone on One UI 6+; look into Samsung Remote Test Lab.
- Ask Play Developer Support about automatic consented alerts.
- Clear the name Aegida (trademark, Play Console, domain, GitHub).
- Storyboard "Logo ideas" board still shows all six ideas; optionally mark #1 as chosen.
