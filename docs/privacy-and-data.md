# What Aegida reads, stores and sends

Written for someone auditing the code. Each statement is backed by a test or a guard; the findings log
(`docs/findings.md`) records how.

## Reads on the phone
- Notifications other apps post (through Android's notification access, which the owner grants). The title and text are
  looked at for links, fake-warning patterns and ads, and are **never sent**. For a scam message or a website pop-up the
  sender, the first 200 characters and the time are kept with the warning until she taps Got it; History keeps a short line
  (app, sender, a few words with links taken out, the address) for 90 days, on the phone only. For apps that send ads as
  notices, the time and a count of ad signs per notice are kept for a week, and one example title (up to 200 characters)
  is kept until the owner has seen it.
- Which app opens web links, texts and calls; which apps have notification access, screen control or device-admin rights;
  Private DNS; every installed app with its name, the store that installed it, the app that asked for the install (both stay on the phone) and (for apps that did not ship with the phone)
  its signing-certificate fingerprint. No app data, no contacts, no messages, no location, no microphone, no camera.

## Stores on the phone
In Aegida's private storage only (excluded from backup and phone-to-phone transfer): the owner's first name, the chosen safety
picture, the guardian's name, email and phone number, a private topic name for guardian messages, a baseline of the
phone at setup, the findings, a 90-day history, queued guardian messages, and the downloaded scam list.

## Sends
- To the public ntfy.sh service, only when the owner turned guardian alerts on: short fixed sentences written with the owner's first name
  ("A message with a scam link arrived on Ruth's phone. Aegida warned Ruth."). **No message text and no whole web address. It may name the app a scam message came from and a masked address (`pa*****.example`).**
  The topic name is the only access control, so its last 40 characters are random (about 233 bits) and it is given to the guardian by email from the
  owner's own phone. A new topic starts with the owner's first name in plain letters (`Ruth-aegida-` and the 40),
  so a guardian with several topics in ntfy sees whose it is; the notes already carry the owner's first name, so ntfy.sh learns nothing new
  beyond the topic name itself. Topics given out before keep their old `aegida_` form.
- To https://wizoi.github.io/Aegida-Feed/ : an ordinary download of the signed list. Nothing about the phone is sent.
- Nothing else. The app contains no analytics, no advertising, no crash-reporting service and no accounts.

## Never
Aegida never opens a link taken from a message, never uses an accessibility service, SMS, call recording, a VPN, screen
overlays or full-screen takeovers, and never says a phone is "safe".

## Permissions (all approved individually)
Internet, post notifications, foreground service (the visible "Aegida is looking out for you" notice), run at boot, query all
packages (to notice a new or remote-control app).

## Play Data safety form (as submitted Oct 12, 2026)

Checked against what is sent above. Data shared with third parties: **none** (the note goes to the one guardian she chose, at her
request; ntfy.sh only delivers it). Encrypted in transit: **yes** (HTTPS only, cleartext blocked). Data collected, each **optional**
(only when guardian messages are on) and for **app functionality**:

| Type | Why |
|---|---|
| Personal info → Name | her first name is the title of every note |
| App activity → Installed apps | a note can name an app (a scam message's app, an app sending ads, an app she said is fine) |
| App activity → Other actions | notes report what she did ("confirmed the change", "hasn't dealt with a warning", "said an app is fine", "said a link is fine" (findings #155; the address masked), "picked a different guardian") |
| Web browsing → Web browsing history | declared to be cautious: a note can carry a masked address (`pa*****.example`) |

Fingerprint of what is sent: `1c965bc12a3f5e55` (`DataSafetyFormTest`). When the test fails, what a guardian can receive or the
hosts Aegida contacts changed: re-check the form in Play Console, update this section, then record the new fingerprint.
