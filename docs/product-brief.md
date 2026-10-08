# Aegida — product brief (version 1)
*Formerly working-titled "Phone Angel." Current spec as of Oct 8, 2026. Decision history is in `decisions.md`; how it is built is in `aegida-technical-design.md`. Storyboard: https://claude.ai/artifact/X8MFEDC2skyGQdt9gePmbS (sources in `../design/`). Research: `../research/extra-scam-protections-report.md` (written before the rename).*

## Brand
- **Name: Aegida** (from "aegis," a shield). Play Store title may add "Safe" (for example "Aegida Safe") for clarity; in-app and brand name stay "Aegida." A first-pass public search found no existing app or company with the name; do a USPTO search (software and security services), Play Console, domain and GitHub checks before committing. Neighbouring names to avoid confusion with: Aegis (many products).
- **Logo: locked on idea #1, the sunflower shield** (teal shield, eight-petal sunflower with brown centre; it echoes the safety picture Mom picks). SVGs exist for the full-colour logo, the app icon (cream background) and a single-colour notification icon (`../design/logo/`). Palette: teal #0F6B73, sunflower yellow #F4B400, brown #6B3E1B, cream #FFF4CC, no red. To check: petals blur at notification size; may need a simpler 8-petal or 6-petal mono version.

## Goal
An Android app for Mom's phone (Samsung Galaxy S23+, One UI 8.x / Android 16; she is 80+, retired, no work profile, and doesn't reliably follow prompts) that keeps her phone set up safely, hides scam alerts, spots dangerous apps and links, and tells her family guardians when something serious happens. Kevin (developer, and a guardian) lives states away, so everything works without him being there.

## Principles
- **Normal app, no factory reset, no Device Owner mode.** Android requires her tap for changes; the app opens the exact screen and checks the result.
- **Built for an 80+ user.** One action per screen, big text, plain sentences, no red, her safety picture and "Aegida" on every real screen, "Call a guardian" always visible. One rule: it never asks for money, passwords, or codes.
- **Strict where it counts, quiet elsewhere.** Default apps, safety switches, remote-control apps and scam-list matches are treated as problems. Normal new apps pass automatically if Play Protect and the threat lists are clean (listed in a digest). No manual approval by anyone.
- **Alerts on her phone are heads-up banners**, repeated for critical items. No full-screen takeover and no overlays (Play restricts full-screen to alarm/calling apps; overlays are what scam apps do).
- **Guardians are informed, not asked.** No approve/remove from a guardian's phone (no infrastructure for it).
- **Never claim what it can't verify.** A setting Aegida can't read (for example Samsung Auto Blocker) is recommended and guided, never shown as "on" or "all good."

## Distribution
Google Play Store, closed testing (dev/alpha) track, published by Kevin. Mom's Google account is a tester; she installs from an opt-in link. Play installs avoid sideload hurdles (restricted settings, 2027 developer verification). Play Console needs a `QUERY_ALL_PACKAGES` declaration (or narrower `<queries>`) and disclosure of notification-listener use.

## First run (Mom does it herself)
1. **Guardians:** her guardians' names and emails (one required, optional second such as a sibling).
2. **Safety picture:** pick one of 10 bundled images; shown on every genuine screen and notification.
3. **Invite email:** the app drafts an email in her own mail app to the guardians; she presses Send. It includes ntfy install links (Android and iPhone) and her private alert link (a long random ntfy.sh topic). Guardians install ntfy and tap the link. A test alert has a "Got it" button so her phone can confirm they are connected (ntfy action button, to verify).
4. **Opt-in to automatic alerts:** "Let Aegida tell my guardians about serious problems," shown with an example alert. Visible, with a persistent "Aegida is protecting you" notification, unique icon, and an off switch.
5. **Safety switches:** Samsung **Auto Blocker is recommended**, not required. Apps can't switch it on or read it, so Aegida explains it in one plain sentence and opens the page; she can tap it on or choose "Not now," and Aegida never shows it as verified. Other switches as the research suggests (Play Protect, spam protection, Chrome Enhanced Protection, Advanced Protection optional). Aegida also asks her to put itself on Samsung's "Never sleeping" list so the phone doesn't stop it (one guided tap).
6. **Website filter:** guided one-time Private DNS setup (Cloudflare `security.cloudflare-dns.com` or Quad9) with a rescue step.
7. **Snapshot:** saves today's setup as "known good" (default apps, install source and signatures of apps, risky permissions, security switches). A summary goes to guardians, including whether Auto Blocker was left as "Not now."

## Using the app
- **Home:** green check "Your phone looks good" with last-checked time, or "1 thing needs a tap" with one big **Fix my phone** button that walks through items in order.
- **History:** plain list of what Aegida did, newest first (green = handled, amber = needs her), stored locally for about 90 days (confirmed). Tapping an event shows what happened, what was done, and who was told.
- **My settings:** she can change guardians, safety picture, her first name, the automatic-alerts switch, and website protection. **Any guardian change notifies the existing guardians** and the new guardian gets an invitation email, so a scammer who talks her into adding them is noticed.

## Features
1. **Fake virus warning guard.** The "phone is infected" pop-ups are website notification spam, not a virus. With notification access, Aegida recognises scareware wording and unallowed site notifications, hides them (`cancelNotification`), shows one calm card, and opens that site's own notification switch (`ACTION_CHANNEL_NOTIFICATION_SETTINGS`). Domains are checked with Safe Browsing. Clearing Chrome/Google app data stays as a fallback.
2. **Strict baseline and drift detection.** Snapshot defaults (browser, home, SMS, dialer, assistant, call screening), installed apps (installer via `getInstallSourceInfo`, signing cert, APK hash), enabled accessibility services, device admins, security posture. Changes are detected within about a minute; attribution comes from the new role holder or from install time and source. Fix = one-tap restore plus guided uninstall.
3. **New-app checks.** Every new app is checked against Play Protect, threat feeds and behaviour rules. Flagged apps (threat-list match, remote-control app, grabs a default role, accessibility/admin/overlay powers) prompt Mom and alert guardians.
4. **Remote-control app detection.** Known packages (AnyDesk, TeamViewer QuickSupport, RustDesk, AirDroid, Chrome Remote Desktop and similar) plus behaviour (accessibility, screen capture, overlay). Mom is told to hang up and not read out codes; louder alarm-style sound; "fix it" card on unlock; urgent ntfy alert.
5. **Scam text and link checks.** Links in message notifications are checked automatically (local blocklist from Phishing.Database and URLhaus, Safe Browsing v5 hash-prefix lookups, lookalike-domain and pressure-wording heuristics). Wording is "looks like a scam" or "no known problems found," never "safe." Never fetch the link. A website filter (Private DNS) backs it up. A manual share-sheet "Check this" is an optional later add-on; she is unlikely to use it unprompted. (If Samsung Auto Blocker's Maximum restrictions hide links in messages, there is nothing to check; Aegida says nothing rather than "no known problems.")
6. **Daily threat-feed check** (Wi-Fi, charging, plus on every install). Match on package + signing cert + APK hash. Sources: Play Protect state (Play Integrity), Safe Browsing, MalwareBazaar (free Auth-Key), Echap stalkerware-indicators (CC-BY 4.0, bundle and refresh), URLhaus, Phishing.Database.
7. **Escalation.** An unfixed item alerts Kevin after 24 hours; the second guardian is told if it is still open 24 hours after the first guardian's alert.
8. **Still watching.** If Aegida can't run (Samsung puts rarely opened apps to sleep after about 3 days) or hasn't checked in for 2+ days, guardians are told "Aegida hasn't checked in."

## Alerts to guardians (ntfy)
- Sent automatically after pairing, only because Mom opted in during setup. Written in her name, coarse events only (e.g. "AnyDesk installed," "default browser changed"). No message content, call numbers, or location. Short enough to read aloud safely.
- Sending side is built into the app (HTTPS POST to the topic). Receiving side is the official ntfy app (free), Android or iPhone. Free ntfy.sh tier limits apply; the topic name acts as the password (long, random, one per install).
- **Play policy risk:** the stalkerware/monitoring policy (a 2020 announcement is the text found) allows behaviour-monitoring apps only if never hidden, with a persistent notification and unique icon, and bars tracking other adults even with consent. Whether a self-enrolled protective app is allowed is a gray area, unconfirmed. Mitigations are built in (opt-in, visible, coarse, off switch). Fallback: switch to a "Tell Kevin" button Mom presses. Ask Play Developer Support before any release beyond the closed test.

## Not doing
Factory reset/Device Owner mode; reading SMS or OTPs (Play SMS policy; Android redacts OTPs); AccessibilityService; call recording; an always-on safe-call card on every unknown call (too restrictive); VPN-based traffic inspection; overlays; root/ADB tricks; embedding Hiya/Truecaller/Tellows data; fetching unknown links; sending alerts from Mom's Gmail; saying "this is safe"; requiring Auto Blocker.

## Existing options (why build our own)
Built-in protections to verify and guide: Samsung Auto Blocker, Android Advanced Protection, Play Protect, in-call protections, Google Messages Scam Detection. Open source for reference: AndroDR (best reference and IOC source), PrivacyScanner, MVT (Kevin-only deep check). Nothing packages strict baseline, drift alerts to family, fake-pop-up blocking and a one-button fix for a non-technical parent. Trust built-in features and code you can read; Play Store "cleaner/booster/security" apps are often the scam.

## Build order
1. Link checks on message notifications + guided website filter.
2. Extended drift checks (Play Protect via Play Integrity, default SMS app, spam role holder, Private DNS state, guidance checklist for Auto Blocker/Scam detection).
3. Bundled Echap stalkerware list.
Later: remote-access + banking warning (Usage Access, heavier review), opt-in call screening, weekly guardian digest, manual "Check this."
Testing order is in the technical design: emulator first, Kevin's Pixel for known emulator gaps, Samsung-specific checks last (Samsung Remote Test Lab or an old Samsung phone if Kevin finds one).

## To verify (Samsung items come last)
- Play Protect `listHarmfulApps()` on Android 16; Play Integrity quotas and pricing.
- Play policy text for notification listener, usage access, and the monitoring policy; Safe Browsing non-commercial terms for a Play app; Phishing.Database and abuse.ch data licences.
- ntfy action buttons for the "Got it" reply.
- Name clearance for "Aegida" (trademark, Play Console, domain, GitHub).
- **Samsung, on a Samsung phone:** whether Auto Blocker is readable at all (no public API found); whether Maximum restrictions hides links in notifications; whether Aegida survives Samsung's sleeping/deep-sleeping apps with a foreground service and notification listener; whether the "Never sleeping" deep link opens the right screen on One UI 8; how Samsung Internet posts website notifications; Samsung Smart Call versus a third-party call-screening role; whether Android 16 in-call blocks are active on Samsung builds.

## Sources
- Android `RoleManager` — developer.android.com/reference/android/app/role/RoleManager
- Play policy on `QUERY_ALL_PACKAGES` — support.google.com/googleplay/android-developer/answer/10158779
- Play stalkerware/monitoring announcement — support.google.com/googleplay/android-developer/answer/10065487
- Play SMS/Call Log policy — support.google.com/googleplay/android-developer/answer/10208820
- Android 16 in-call protections — androidauthority.com/android-16-phone-call-protections-3526068
- Advanced Protection — security.googleblog.com/2025/05/advanced-protection-mobile-devices.html
- Samsung Auto Blocker — samsung.com/latin_en/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/
- Samsung app management (sleeping apps) — developer.samsung.com/mobile/app-management.html ; dontkillmyapp.com/samsung
- Safe Browsing — developers.google.com/safe-browsing/reference
- MalwareBazaar / URLhaus — bazaar.abuse.ch/api/ ; urlhaus.abuse.ch/api/
- Stalkerware indicators — github.com/AssoEchap/stalkerware-indicators
- AndroDR — github.com/yasirhamza/AndroDR
- ntfy — docs.ntfy.sh ; github.com/binwiederhier/ntfy
- FTC senior-scam data — ftc.gov data spotlight, Aug 2025
