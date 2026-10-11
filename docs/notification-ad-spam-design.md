# Ads that arrive as notifications: design (draft for Kevin, Oct 11, 2026)

Status: **built (Oct 11); not released. Changed after review (findings #93): a notice scores only with a sign in the notice itself (promo category, ad word in the channel, pushy or scary words), the other signs only add; "news" and broad words dropped; the short window is 1 hour, not 24 (Kevin).** Findings #92. Kevin's answers: keep the numbers, a new table, nothing for ads inside apps, an owner allow list in My settings with the guardian told on each allow.

## What happened

Mom sent five screenshots. Four show full-screen ads shown by a free "Image to PDF & PDF..." app: one with a fake security scan
("We may have found 1 exposed password and 2 email leaks", from "TrendingCyber", with "Scan Now"), one quiz that leads to a "START SCAN"
button, and an install ad for another app. One shows a big heads-up notification "File converter is ready!" with a "Convert" button, over a
NewsBreak notice. The pattern is a free utility app earning money from ads, some of them scare ads, and pushing them as notifications.

What Aegida can and cannot do about it is set by its own rules (no Accessibility service, no overlays, no VPN):

| Part | Can Aegida see it? |
|---|---|
| The "File converter is ready!" notice | **Yes.** A notification listener sees every notice another app posts. |
| An ad drawn inside the other app's window | **No.** That would need Accessibility or screen capture, both banned. |
| Which app Mom has been using when an ad appears | Only with the usage-access special permission, which is a further ask of Mom; not proposed. |

So the design below is about **notices**, the part Aegida can really see. For ads inside an app it can only advise (uninstall the app, or use a
DNS that blocks ad domains; that is a separate decision).

## What Android gives a notification listener (checked against the SDK 36 `android.jar`)

- `Notification.category` (set by the sending app; values include `promo`, `recommendation`, `msg`, `call`, `email`, `alarm`, `navigation`, `progress`,
  `service`, `status`, `transport`, `reminder`, `event`, `social`). A spam app may leave it empty, so a `promo` category is proof but a missing one is not.
- `NotificationListenerService.Ranking.getChannel()` for a posted notice: the channel id, **name**, description, group and importance. This is the same
  channel data finding #84 found we can read only for a notice that has been posted, not by listing an app's channels.
- `Ranking.getImportance()` (what the phone actually does with it: 4 and up is a heads-up), `getLastAudiblyAlertedMillis()`, `isConversation()`,
  `getUserSentiment()` (the phone's own learning from what the owner dismisses), `isAmbient()`.
- Flags such as `FLAG_ONGOING_EVENT` and `FLAG_FOREGROUND_SERVICE` (steady notices that are not spam, like a music player).
- `ApplicationInfo.category` (a developer's own label: image, productivity, game, news...), often unset.
- `Settings.ACTION_APP_NOTIFICATION_SETTINGS` with the app's package opens that app's notification page, so Aegida can send Mom straight to the switch.
  `ACTION_APPLICATION_DETAILS_SETTINGS` opens its App info page (Uninstall is there).
- There is **no public API** for the Promotions, News, Social and Suggested bundles that Google's notification organizer (Android 16 QPR2, Pixel,
  English only) produces; its classifier is not in the open-source part ([Android Authority](https://androidauthority.com/android-16-bundled-notifications-3508409)).
  Samsung phones do not have it. So Aegida needs its own rule.

## What the rules of Google Play say (the app is probably in breach)

From Google Play's Ads policy ([support.google.com/googleplay/android-developer/answer/9857753](https://support.google.com/googleplay/android-developer/answer/9857753)),
as read on Oct 11, 2026: ads must not imitate the interface of any app feature, including operating-system notifications or warnings; "Ads may only be
displayed inside of the app serving them"; and full-screen interstitials that cannot be closed after 15 seconds are not allowed. A fake "security scan" ad
and a notification that is an ad in disguise both fit those. Mom or Kevin can use "Flag as inappropriate" on the app's Play page; that is the only place
to complain. I did not find in the page I read the sentence that system-level notices may only be used for an app's "integral features", so I am not relying on it.

## How people find the spammy app today (research)

Guides ([Guiding Tech](https://www.guidingtech.com/find-pop-up-ads-app-android/), [Bitdefender](https://www.bitdefender.com/en-gb/blog/hotforsecurity/do-ads-keep-popping-up-on-your-phone-how-to-stop-it))
all say the same, by hand: look at the Recents screen when an ad shows; list the apps that may "draw over other apps"; turn off the app's notifications;
uninstall it. Adware is most common in flashlight, battery, cleaner and file-tool apps. Nothing there is automatic. Aegida's value is doing the
"which app" part from the notices and giving Mom one button to the right Settings page.

## Proposed rule: tell a spammy app from a real one

Count notices **per app, over time**, and only ever flag an app whose notices look like ads. A real app can send many notices (messages, calls, a
delivery app, a bank), so the rule has to need more than a count.

Never counted, never flagged (the allow side):
- System apps and the phone's default messages, phone, and email apps.
- Notices with category `msg`, `call`, `email`, `alarm`, `navigation`, `progress`, `service`, `status`, `transport`, `reminder`, `event`, `social`.
- Conversation notices; ongoing and foreground-service notices; apps whose own notices she has dealt with before ("Was that you?" yes).

Signals (each one is worth one point; each is a plain yes or no on the notice):
1. `category` is `promo` or `recommendation`.
2. The channel name or description has an ad word ("promo", "offer", "deal", "ads", "sale", "marketing", "news", "recommend", "discover").
3. The notice is a heads-up (importance 4 or more) from an app that is not in the allow side.
4. The words push an action ("ready!", "convert", "clean now", "boost", "scan now", "free", "install", "win", "limited") or scare ("virus", "infected"); the scary
   list already exists in `PopupSpamDetector`.
5. The app's own category is a tool or game (productivity, image, game), where a steady flow of notices is unusual.
6. The phone's own sentiment for the app is negative.

Flag an app when, in the last 24 hours, it has posted **3 or more counted notices and at least 2 of the signals above on at least 2 of them**, or **6 or more
over 7 days with at least 1 signal on most**. These numbers are guesses and must be set from real data (below), and tuned so a real messaging, news or
banking app is never flagged. A single notice never flags anything.

What is stored: **counts and signals per app per day, never the words.** The finding keeps the app's name, the number of notices, and the signals. No title
or text is kept beyond the warning page for one example notice, cleared when she taps Got it (the same rule as finding #87).

## What Aegida does when it flags an app

1. A new problem, `NoisyApp`, needs a tap, never urgent. Owner sentence: "The “Image to PDF” app keeps sending you ads as notices." Button "Show me":
   the app's notification page, with plain words: "Turn off Allow notifications for this app. You can also remove the app."
2. A second button on the page: "Remove this app" opens its App info page, where Uninstall is.
3. Optional (second version, consent): "Let Aegida hide this app's notices", a list she picks. Aegida then removes them as they arrive, or snoozes them. The app
   keeps trying, so this is a help and not a cure.
4. The guardian is told once, with the app's name like the other app notes: "An app (“Image to PDF”) kept sending ads as notices on Ruth's phone. Aegida
   showed Ruth how to turn them off." Backed off like other non-urgent notes.
5. History keeps a line: the app and how many notices.

## Open questions and risks

- **The numbers.** The thresholds above are guesses. A rule that flags a bank or a chat app would be worse than none. Premise to check on real data: count,
  on a real phone, the notices per app per day and which would be flagged (CLAUDE.md: a premise about "all" needs a count). Proposed: a one-time
  `dumpsys notification` read on Kevin's own phone, plus an emulator fixture app that posts real notices with those categories and channels (`cmd notification post`
  cannot set a category or a channel, so a small fixture app is needed). Mom's phone only with her say-so.
- **Where to keep the counts.** A new table needs a Room migration (Kevin's OK, as in finding #87). The alternative, one row per app in the existing `setting`
  table, needs no migration but is untidy; I would ask for the migration.
- **Spammers who do not announce themselves.** An app can use category `msg` to dodge the allow-side check. Keep it simple: a flagged app is only a suggestion to Mom.
- **Ads inside the app** are out of reach (see the first table). Mom's other help is the how-it-works page: never tap "Scan Now" in an ad, and what to do.

## Decisions for Kevin

1. Build the `NoisyApp` check on these signals? (Recommended, after the real-data count.)
2. New table with a migration, or `setting` rows?
3. Guardian note with the app name, as proposed?
4. Is the "hide this app's notices" option wanted, or only the advice?
5. Do you want me to try the idea on a fixture app on the emulator first, and may I read one `dumpsys notification` from your own phone?
