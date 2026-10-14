# Aegida — product brief
*What the app does, as built (version 0.3.9, Oct 2026). Formerly working-titled "Phone Angel." Decision history is in `decisions.md`; how it is built is in `aegida-technical-design.md`. Ideas that are not built are listed, without promise, in `ideas.md`. Storyboard: https://claude.ai/artifact/X8MFEDC2skyGQdt9gePmbS (sources in `../design/`). Research: `../research/extra-scam-protections-report.md` (written before the rename).*

## Brand
- **Name: Aegida** (from "aegis," a shield). The Play Store title is "Aegida" (decisions.md, Play release). Name research is in `name-clearance.md`.
- **Logo: the sunflower shield** (teal shield, eight-petal sunflower with a brown center). SVGs for the full-color logo, the app icon (cream background) and a single-color notification icon are in `../design/logo/`. Palette: teal #0F6B73, sunflower yellow #F4B400, brown #6B3E1B, cream #FFF4CC, no red.

## Goal
An Android app for an older person's phone (designed first for a Samsung Galaxy S23+ on One UI 8 / Android 16, owned by someone over 80 who does not reliably follow prompts). It hides fake website warnings, spots scam links, spying apps and remote-control apps, notices when an important setting changes, and, only if the owner turns it on, tells one family member the owner chose (the "guardian") when something serious happens. The guardian may live far away, so everything works without anyone being there.

## Principles
- **Normal app, no factory reset, no Device Owner mode.** Android requires the owner's tap for changes; the app opens the exact screen and checks the result where it can read it.
- **Built for an older user.** One action per screen, big text, plain sentences, no red, the owner's safety picture and "Aegida" on every real screen, "Call a guardian" always in reach. It never asks for money, passwords or codes.
- **Strict where it counts, quiet elsewhere.** Default apps, special access, remote-control apps and scam-list matches are problems. Ordinary new apps from Play pass without a question. No manual approval by anyone.
- **Alerts on the owner's phone are heads-up banners.** No full-screen takeover and no overlays (Play restricts full-screen to alarm and calling apps; overlays are what scam apps do).
- **Guardians are informed, not asked.** Nothing can be approved or removed from a guardian's phone.
- **Never claim what it can't verify.** A setting Aegida can't read (for example Samsung Auto Blocker) is recommended and guided, never shown as on. Results say "looks like a scam" or "no known problems found," never "safe."

## Distribution
Google Play, closed testing, published by Kevin. Play installs avoid sideload hurdles (restricted settings, developer verification). The Play Console has the `QUERY_ALL_PACKAGES`, notification-access and foreground-service declarations.

## First run (the owner does it without help)
A welcome screen, then six steps (seven on a Samsung):
1. **About you and your guardian:** the owner's first name and one guardian's name, email and phone number.
2. **Safety picture:** pick one of 10 bundled pictures; it is shown on every genuine screen and on Aegida's banners.
3. **Tell your guardian:** Aegida drafts an email in the owner's own mail app; the owner presses Send. It has ntfy install links (Android and iPhone) and the owner's private alert link (a long random ntfy.sh topic).
4. **Alerts:** "Let Aegida tell your guardian about serious problems," Yes or No. Off at any time in My settings.
5. **Notification access and banners:** the two permissions Aegida needs to read notices and to show warnings. Skipping asks first.
6. **Block scam websites:** guided Private DNS setup with Cloudflare's security filter (`security.cloudflare-dns.com`).
7. **Samsung only, Auto Blocker:** "One more switch to try," with Show me, "I turned it on" and Not now. Aegida can't read the switch, so it keeps only what the owner said.

The baseline (how the phone is set up today) is taken quietly when setup finishes.

## Using the app
- **Home:** "Your phone looks good." with the last-checked time and "No known problems found.", or "1 thing needs a tap" with one big **Fix my phone** button. If no check could run it says so instead of "looks good".
- **History:** a plain list of what Aegida did, newest first, kept on the phone for 90 days. Tapping an entry shows what happened, what was done and whether the guardian was told.
- **My settings:** guardian (change it, or send the invitation again), safety picture, first name, the alerts switch, website protection, the list of things the owner said are fine (with "Warn me again"), when the scam lists were last updated, and where the lists come from.
- **Changing the guardian:** allowed. With alerts on, the old guardian gets one short message first; a popup asks the owner to tell them too.

## What it checks
1. **Fake website warnings.** "Your phone is infected" pop-ups are website notifications, not viruses. With notification access, Aegida hides a website notification with scare words at once. A website notifying for the first time is snoozed and the owner is asked "Was that you?"; Yes remembers the site. The button opens that site's own notification switch (`ACTION_CHANNEL_NOTIFICATION_SETTINGS`).
2. **Scam links in messages.** Links in message notifications are read as text, never opened, and checked against the signed scam list (Phishing.Database) and look-alike signs (look-alike letters, brand names in the wrong place, characters browsers ignore). The warning names the app and the sender and shows a few words with the link taken out.
3. **"Was that you?" changes.** Compared with the baseline: the default browser, text and phone apps, the assistant, which apps have notification access, screen control (accessibility) or device-admin rights, "Yes, that was me" accepts the change.
4. **Website protection turned off.** Private DNS no longer set to the filter chosen in setup; one tap opens the guided fix.
5. **New apps from outside Play.** An app installed after setup from somewhere other than Play is asked about.
6. **Remote-control apps.** Known remote-access apps (AnyDesk, TeamViewer QuickSupport, RustDesk, AirDroid and similar), by package name. Urgent: the guardian message is never held back.
7. **Spying apps.** Echap's stalkerware indicators (CC BY 4.0), matched by package name and signing certificate.
8. **Apps that send ads as notices.** An app that keeps posting ad-like notices is named, with buttons to its notification settings and App info. Nothing is hidden. The owner can say an app is fine; the guardian is told.
9. **Aegida itself.** Notification access lost, banners blocked, the daily check not run, the scam list more than 3 days old, or a guardian message that could not be delivered for a day.

Checks run when a notification arrives, when the app opens, and once a day. The scam list is one signed file downloaded daily from `https://wizoi.github.io/Aegida-Feed/` and verified on the phone.

## Alerts to guardians (ntfy)
- Sent only if the owner said Yes. Written with the owner's first name, fixed sentences only: no message text, no whole web address, no contacts, no location. A message may name the app a scam came from and a masked address (`pa*****.example`).
- A warning still open after 24 hours reminds the same guardian once. Repeats of the same kind are backed off (1, 2, 4, then 7 days); urgent problems are never held back.
- "Hasn't checked in": a scheduled ntfy message, pushed back by every daily check, reaches the guardian if the phone goes quiet for 2 days.
- Sending is an HTTPS POST to the topic, queued and retried when offline. Receiving is the free ntfy app (Android or iPhone). The topic name is the only access control, so it is long and random, one per install.
- **Play policy risk:** Play's monitoring policy names parental and enterprise uses; an adult choosing their own guardian is not clearly covered. The consent design (explicit Yes/No, off any time, persistent notice, normal icon, fixed sentences) is the answer if Play asks (`play-support-question.md`).

## Not doing
Factory reset or Device Owner mode; reading SMS or one-time codes; AccessibilityService; call recording; VPN-based traffic inspection; overlays; full-screen takeovers; root or ADB tricks; embedding caller-ID data; opening links from messages; sending email for the owner; saying "this is safe"; requiring Auto Blocker.

## Existing options (why build our own)
Built-in protections Aegida guides toward: Samsung Auto Blocker, Android Advanced Protection, Play Protect, Google Messages scam detection. Open source for reference: AndroDR, PrivacyScanner, MVT. Nothing found packages a baseline, change alerts to family, fake-pop-up hiding and a one-button fix for a non-technical owner. Play Store "cleaner/booster/security" apps are often the scam.

## Still to verify (Samsung items come last)
- Play policy text for notification access and the monitoring policy.
- **On a Samsung phone:** whether Auto Blocker is readable at all (no public API found); whether Maximum restrictions hides links in notifications; whether Aegida survives Samsung's sleeping and deep-sleeping apps with a foreground service and notification listener; how Samsung Internet posts website notifications. Options are in `samsung-validation-options.md`.

## Sources
- Android `RoleManager` — developer.android.com/reference/android/app/role/RoleManager
- Play policy on `QUERY_ALL_PACKAGES` — support.google.com/googleplay/android-developer/answer/10158779
- Play stalkerware/monitoring announcement — support.google.com/googleplay/android-developer/answer/10065487
- Play SMS/Call Log policy — support.google.com/googleplay/android-developer/answer/10208820
- Advanced Protection — security.googleblog.com/2025/05/advanced-protection-mobile-devices.html
- Samsung Auto Blocker — samsung.com/latin_en/support/mobile-devices/protect-your-galaxy-device-with-the-new-auto-blocker-feature/
- Samsung app management (sleeping apps) — developer.samsung.com/mobile/app-management.html ; dontkillmyapp.com/samsung
- Stalkerware indicators — github.com/AssoEchap/stalkerware-indicators
- Phishing.Database — github.com/Phishing-Database/Phishing.Database
- AndroDR — github.com/yasirhamza/AndroDR
- ntfy — docs.ntfy.sh ; github.com/binwiederhier/ntfy
- FTC senior-scam data — ftc.gov data spotlight, Aug 2025
