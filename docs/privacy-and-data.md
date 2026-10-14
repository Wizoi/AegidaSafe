# What Aegida reads, stores and sends

Written for someone auditing the code. Each statement is backed by a test or a guard; the findings log
(`docs/findings.md`) records how.

## Reads on the phone
- Notifications other apps post (through Android's notification access, which the owner grants). The title and text are
  looked at for links and fake-warning patterns and are **not stored, logged or sent**. Only the host, the website or
  channel, the notification key and the app package go into a finding.
- Which app opens web links, texts and calls; which apps have notification access, screen control or device-admin rights;
  Private DNS; every installed app with its name, the store that installed it and (for apps that did not ship with the phone)
  its signing-certificate fingerprint. No app data, no contacts, no messages, no location, no microphone, no camera.

## Stores on the phone
In Aegida's private storage only (excluded from backup and phone-to-phone transfer): the owner's first name, the chosen safety
picture, the guardian's name, email and phone number, a random private topic name for guardian messages, a baseline of the
phone at setup, the findings, a 90-day history, queued guardian messages, and the downloaded scam list.

## Sends
- To the public ntfy.sh service, only when the owner turned guardian alerts on: short fixed sentences written with the owner's first name
  ("A message with a scam link arrived on Ruth's phone. Aegida warned Ruth."). **No message text and no whole web address. It may name the app a scam message came from and a masked address (`pa*****.example`).**
  The topic name is the only access control, so it is random (238 bits) and is given to the guardian by email from the
  owner's own phone.
- To https://wizoi.github.io/Aegida-Feed/ : an ordinary download of the signed list. Nothing about the phone is sent.
- Nothing else. The app contains no analytics, no advertising, no crash-reporting service and no accounts.

## Never
Aegida never opens a link taken from a message, never uses an accessibility service, SMS, call recording, a VPN, screen
overlays or full-screen takeovers, and never says a phone is "safe".

## Permissions (all approved individually)
Internet, post notifications, foreground service (the visible "Aegida is looking out for you" notice), run at boot, query all
packages (to notice a new or remote-control app).
