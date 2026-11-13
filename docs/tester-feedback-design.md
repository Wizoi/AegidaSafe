# Design: changes after the Testers Community report (Nov 13)

Source: [reviewers/com.aegida.app_feedback.pdf](reviewers/com.aegida.app_feedback.pdf), a week on the External Testing track.
It found no crashes or bugs and suggested a walkthrough, store-listing keywords, a "Rate your app" button and a "Share app"
option, plus generic notes (feedback, updates, a forum, privacy education). Two reviewers (Fable 5.1 and Opus 5.5 high) read it
against the code, the listing and the site on their own; their findings were merged, then both reviewed the merged design and
their corrections are folded in below. Rows are findings #172-#180.

Status: decided by Kevin (Nov 13, end of file). Release A is being built; release B waits for its own scenario review.

## Releases

- **0.4.3 (release A): words, listing, site, one page and two quiet buttons.** T1, T3, T6, T7, T8, T9, T10, T11, then T2.
  Nothing here changes what a guardian receives, except T8's one line in the invitation.
- **Release B: guardian details.** T4 and T5. Each changes what is stored or sent about the guardian, needs K1, K2 and K7, and
  gets its own scenario review before code.

## What we do not build, and why

| Suggestion | Decision | Why (the owner is 80+, cannot follow technical prompts) |
|---|---|---|
| Interactive tour, coach marks, spotlights, "Skip" | No | Setup already is the walkthrough: Welcome, then one step per screen with "Step N of M", Back, one action, plain words, checks that read the real setting, resume after a kill (SetupFlow.kt, setup/*.kt, first-run.md FR1-FR16). Overlays are a new kind of screen to learn and "Skip" is a prompt she cannot judge. What was missing is a closing "what you will see" page (T2). |
| "Rate your app" as a prompt | No timed prompt or dialog; a quiet button at the bottom of My settings instead (Kevin, K8, T11) | A star prompt that appears by itself is an uninvited pop-up of the kind Aegida teaches her to distrust, and the In-App Review library is a new dependency. A button she chooses to tap is not. Families are also asked on the website (T7). |
| "Share app" | A quiet button at the bottom of My settings (Kevin, K4, T10) | Both reviewers leaned no (a link from an older friend saying "install this" looks like the pattern Aegida warns about); Kevin chose a quiet, text-only share she starts herself. The message names Aegida and the store page, nothing about her. |
| Community forum or social media | No | A public place where older people talk about scams draws "recovery" scammers and fake "Aegida support". |
| In-app feedback form | No | A text box is a prompt she cannot follow, and anything typed could hold message words that must never leave the phone. The contact email goes on the website (T7). |
| Regular updates | Already done | Releases 0.1.0-0.4.2; the scam list updates daily. |
| Privacy education | Already done, but wrong in places | T1 corrects what the pages say Aegida keeps. |

## T1 (#172). Say truthfully what stays on the phone

The setup step (`Texts.Setup.ACCESS_BODY`, Texts.kt:244), the listing (full-description.txt:21) and the site
(how-it-works.html:263) say Aegida does not keep message words. What the code keeps, all on the phone, nothing sent:
- With a warning, until she taps (Got it, Yes, This link is fine, This app is fine, all through `markSeen` → `withoutMessage()`,
  FindingActions.kt): the title and text, each up to 200 characters (LinkDetector.kt:88).
- In History, for 90 days (`HistoryDetail`): the sender, up to 100 characters with links taken out (`Texts.SAYS_MAX`) and
  "The link went to" the whole site name (HistoryDetail.kt:78-82). Website pop-ups: up to 100 characters each of title and text.
- For apps that send ads: one example title, up to 200 characters, until she has seen it (NoisyAppDetector.kt:39-40).
  privacy-and-data.md line 11 says "only the time and a count"; it is corrected in the same change.

The detailed part of the site (how-it-works.html:338) already tells the truth. New wording, one meaning everywhere:
- Setup: "This is how Aegida spots fake warnings and scam links. It never sends what your messages say. When it warns you, it keeps who sent the message and its first words on this phone, so you can see what it was."
- Listing (written to "you"): "It never sends what they say. When it warns you about one, it keeps who sent it and its first words on this phone, so you can see what it was. History keeps a short line for 90 days."
- Site summary line: the same as the listing, in the page's voice.

The 90 days are typed twice today (`DailyCheck.HISTORY_KEPT`, DailyCheck.kt:145, and `Texts.History.KEPT`, Texts.kt:199); a test
ties them, and the new listing and site sentences are checked by the same test.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T1.1 | Reads the access step in setup | The new sentence | n/a | SetupScreensTest |
| T1.2 | must never: the listing, the site or Texts say Aegida does not keep message words | n/a | n/a | PlayListingTest + a Texts test: no "does not keep" / "doesn't keep" |
| T1.3 | must never: a page says History is kept for anything but the real time | n/a | n/a | Test tying `Texts.History.KEPT`, the listing and the site to `HISTORY_KEPT` |
| T1.4 | Taps This link is fine / This app is fine | The message words leave the warning; the History line stays | The usual note | FindingActionsTest: no message keys after allow |
| T1.5 | Opens a History line after a warning | No more than the setup sentence promised (sender, first words, site name) | n/a | HistoryDetailTest |
| T1.6 | 200% font, both themes | The access step scrolls; its buttons stay reachable (setup steps scroll, they do not pin) | n/a | SetupScreensTest variants |

## T2 (#173). A closing "What you will see" page, also in My settings

Setup ends today with "All set. Aegida is looking out for you now. You don't need to do anything." The Finish step becomes one
scrolling page showing her safety picture tile and these lines, then the existing Done:

- "Aegida works quietly. You don't need to open it."
- "When something needs you, you will see a notice with your picture. Tap it."
- "When Aegida finds nothing, Home says: No known problems found."
- "If you are ever unsure, tap Call a guardian."

When banners or notification access are off, the second line becomes "Home will show you what needs a tap." It never promises a
notice she cannot get. When no picture can be drawn, the tile is left out and the line says "a notice from Aegida".

The same page opens from My settings as "What you will see", at the top of My settings. "How does this work?" stays at the
bottom: it leaves the app for the browser and should not be the first thing on the page. One composable, two entry points;
from My settings, Done only goes back.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T2.1 | Finishes setup | "All set.", then the page with her picture; Done goes to Home; the baseline is taken once, as today | First message if alerts on (FR1) | SetupFlowTest, WhatYouSeeAndRateTest; mutant setup-ends-with-all-set |
| T2.2 | Opens it from My settings weeks later | The same page with her current picture; Done returns to My settings | Nothing | Robolectric |
| T2.3 | must never: Done on the My settings copy runs the setup-finish path or takes a baseline again | Back to My settings | Nothing | Robolectric: setup state unchanged |
| T2.4 | Banners or notification access off | "Home will show you what needs a tap."; never "you will see a notice" | Nothing | Robolectric with notices off |
| T2.5 | No picture saved (old database) or it cannot be drawn | No tile; "a notice from Aegida" | Nothing | Robolectric |
| T2.6 | Changed her picture since setup | The new picture | Nothing | Robolectric |
| T2.7 | Opens it while a warning is open | Nothing resolved; Home unchanged | Nothing | JVM |
| T2.8 | must never: a demo notification, anything sent, an overlay or spotlight, several pages | n/a | Nothing | By construction: `WhatYouSeePage` takes only the picture, the notices flag, the title and Done; no sender or poster can reach it. One page (WhatYouSeeAndRateTest) |
| T2.9 | Killed on the Finish page | Reopens on Finish (SetupCommands.finishStep saves Finish; FR7) | Nothing | FirstRunScenariosTest |
| T2.10 | Guardian phone blank | Until T4 ships: "tap Call a guardian" (opens the keypad, as today); after: "If you are ever unsure, ask Sam." | Nothing | Revisited in release B |
| T2.11 | 200% font, both themes | Lines scroll; Done pinned (AegidaScreen `pinned`) | n/a | SetupScreensTest variants |

## T3 (#174). Setup words that confuse

| Text | Now | New | Why |
|---|---|---|---|
| `YOUR_NAME` (Texts.kt:222) | "How shall I address you?" | "Your first name" | Read as a postal address; My settings says "My first name" |
| `ALERTS_BODY` (Texts.kt:238) | "...fake warnings to Ruth's phone..." | Her own first name, with the possessive rule guardian notes already use (Problem.kt templates); "your phone" when blank | An owner named Helen wonders who Ruth is. `AlertsStep` needs the name passed in (state.guardians.ownerName) |
| `PICTURE_BODY` (Texts.kt:229) | "You'll see it every time it's really me. If you don't see it, it isn't me." | "Aegida shows this picture on its warnings. If a warning does not show it, it did not come from Aegida." | The app speaks as "Aegida". "Every time" was not true: the always-on "looking out for you" notice has no picture (NotificationPoster.statusNotification) |
| `COULDNT_CHECK` (Texts.kt:87) | "I couldn't check today." | "Aegida couldn't check today." | Same voice as the next line |
| `INVITE_BODY` (Texts.kt:232) | "I'll write an email to %s..." | "Aegida will write an email to %s in your own email app. You press Send." | Same voice |
| `WELCOME_BODY` (Texts.kt:217) | "...keeps you and a guardian you trust aware." | "...It quietly looks out for scams and, if you say so, lets a guardian you trust know." | Said before she chooses Yes or No |

Buttons in her own voice ("I sent it", "I turned it on", "No, I'll tell them myself", "I will", "Warn me again") stay. No regex
guard over all of Texts (its exception list would be bigger than the rule); each changed string gets its own test.

| ID | Situation | Owner sees | Test |
|---|---|---|---|
| T3.1 | Typed "Helen" | "...fake warnings to Helen's phone..." | Robolectric |
| T3.2 | hostile: name ending in s, an emoji, a very long name, another alphabet | The guardian notes' possessive form; wraps at 200% | JVM on the formatter |
| T3.3 | Old database with a blank name | "...to your phone..." | JVM |
| T3.4 | Each changed string | The new words; never "I" or "me" for Aegida | One test per string |

## T6 (#177). Store listing

- Short description (80 max): "Spots scam links and fake virus pop-ups. Can tell one family member you choose." (79). "virus" is what a worried relative searches; the description already says the pop-ups are not viruses.
- A short "For families" paragraph near the top, no offsetting cuts (the description is about 2,400 of 4,000 characters): "Setting this up for a parent or grandparent? You can sit with them for the five-minute setup. If they say yes, a family member or friend they choose gets a short note in the free ntfy app when something serious happens, never the words of their messages." It must not say "safety picture" (the listing bans "safety").
- Screenshots: retake My settings after a full setup (the current one shows "My safety picture: Not set"); add one of a warning that needs a tap, from the debug seeds, with a made-up sender such as the site's bank-login-check.example, never a real bank or brand.
- Title stays "Aegida" (K3).
- PlayListingTest's banned words today are safe, safely, safety, secure, secured, 100%, guarantee(s/d), protected (PlayListingTest.kt:26). Add "protect" and "protects" (not "protection": the site names "Website protection").

| ID | Situation | Test |
|---|---|---|
| T6.1 | must never: a banned word in the listing or the site | PlayListingTest, with "protect(s)" added |
| T6.2 | Limits: title 30, short 80, full 4000 | PlayListingTest (existing) |
| T6.3 | must never: a screenshot shows a real person's data, a real bank, or a state the app cannot reach | Review; emulator debug seeds |
| T6.4 | The screenshots match the shipped app (no "Not set") | Review before publishListing |

## T7 (#178). Website: two lines for families

In how-it-works.html, Questions for guardians: "If Aegida helps your family, a short review on Google Play helps other families
find it." and "Questions or a problem? Email" the store contact address (already tied to the privacy page by PlayListingTest).
Never in the app, the invitation or a guardian note.

## T8 (#179). Invitation email says what Aegida is (K5)

The invitation asks a guardian to install an app and paste a name, from a parent's email, and never says what Aegida is. Add one
line before step 1: "What Aegida is:" and `PublicPages.HOW_IT_WORKS` (the one owner of that address).

| ID | Situation | Guardian gets | Test |
|---|---|---|---|
| T8.1 | Invitation written in setup, sent again, or for a new guardian after a change | The line with the bare page address | JVM on Invite.BODY formatting |
| T8.2 | must never: the link carries her name, the channel or a query | The bare address | JVM |
| T8.3 | No email app | `INVITE_NO_EMAIL_APP`, as today | existing |

## T9 (#180). Small corrections

- first-run.md: a row for the welcome screen after a quit on the first step (`welcomed` is `rememberSaveable`, starting from `stepsDone > 0`, SetupFlow.kt:40; the welcome showing again is fine and should be written down).
- first-run.md FR13 says "I changed my name later in My settings", but My settings cannot change the name (SettingsScreen.kt shows it only; its test calls `saveAbout` twice, which only Back in setup reaches). Reword it to what is true until T5 ships.
- emulator-vs-device.md: a sideloaded APK on Android 13+ shows "Restricted setting" before notification access, with no Aegida words. Play installs do not. Note only.

## T10 (#181). "Tell a friend about Aegida" at the bottom of My settings (Kevin, K4)

A quiet button below "How does this work?". It opens Android's share sheet (`Intent.ACTION_SEND`, `text/plain`, through
`Intent.createChooser`) with fixed text and nothing else: no permission, no library, nothing fetched.

- Button: "Tell a friend about Aegida"
- Text, with no link (a link from a friend saying "it's free" is the pattern Aegida teaches people not to tap; Opus round 3):
  "I use Aegida on my phone. It looks for scam links and fake warnings and explains them in plain words. It's free. Look for Aegida in the Play Store."
- The chooser title comes from `Texts`.
- **Shown only once Aegida is public on Play.** While it is in closed testing, a friend who is not a tester would get "not found".
  Both T10 and T11 hide behind one switch, `PublicPages.LISTED_ON_PLAY` (false now), turned on in the first production release.
- The store page gets one owner, `PublicPages.PLAY_STORE` ("https://play.google.com/store/apps/details?id=com.aegida.app"), and the
  `market://` form is built from the same id. T11 uses both; T10 uses neither.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T10.1 | Taps it (switch on) | The phone's share sheet with the fixed text; she picks an app and sends | Nothing | Robolectric: the intent's type, text and no extras besides the text |
| T10.0 | Switch off (closed testing, today) | No share button and no rate button | Nothing | Robolectric with `LISTED_ON_PLAY` false |
| T10.2 | must never: the text holds her name, the guardian's name, email or phone, the channel, a warning or anything from her phone | n/a | n/a | JVM: the text equals the fixed string |
| T10.9 | must never: the shared text holds a link or web address | n/a | n/a | JVM: no "http", "://" or "www." in the text |
| T10.3 | must never: the text says safe, protect(s), secure or guarantee, or names a feature that is not built | n/a | n/a | The existing Texts banned-word guard (it covers every string in `Texts`; add protect/protects there if missing) |
| T10.4 | Backs out of the share sheet | My settings as before | Nothing | No Aegida code runs on return (the chooser is Android's); the tap itself is T10.1 |
| T10.5 | Taps it twice | The share sheet once each time; nothing counted or remembered | Nothing | Robolectric |
| T10.6 | No app can share text | Android's own chooser says no app can do it (`createChooser` always opens); no Aegida error | Nothing | Note only |
| T10.10 | hostile: a scammer is in her recent chats, so the share sheet suggests them | The usual share sheet; the text holds nothing of hers (T10.2) | Nothing | Covered by T10.2 |
| T10.7 | must never: shown anywhere but the bottom of My settings, or as a pop-up, banner or reminder | n/a | n/a | Robolectric: Home and setup have no share button |
| T10.8 | 200% font, both themes | The button wraps and stays readable | n/a | SettingsScreen variants |

## T11 (#182). "Rate Aegida on Google Play" at the bottom of My settings (Kevin, K8)

A quiet button below T10, shown only when `PublicPages.LISTED_ON_PLAY` is on (T10). Above it, one plain line: "Reviews on Google
Play show your Google name." It opens Aegida's own page in the Play Store app (`ACTION_VIEW` on `market://details?id=com.aegida.app`,
package `com.android.vending`, `FLAG_ACTIVITY_NEW_TASK`): `startSafely(market) || startSafely(web)`, where the fallback is the same
page in the browser (`PublicPages.PLAY_STORE`). No In-App Review library, no stars drawn by Aegida, never a prompt that appears by itself.

Accepted risk (Opus round 3): the store page has an Uninstall button, so a scammer could script "tap the bottom button, then
Uninstall". Uninstalling is reachable other ways too, and the guardian's "hasn't checked in" note after 2 days catches it when alerts are on.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T11.1 | Taps it | Aegida's page in the Play Store app | Nothing | Robolectric: the intent's data and package |
| T11.2 | No Play Store app (sideloaded, a phone without Play) | The same page in the browser | Nothing | Robolectric with the store intent unresolved |
| T11.3 | Neither can open | Nothing happens; no error | Nothing | Robolectric |
| T11.4 | must never: a dialog, timer, count of opens, or reminder asks her to rate | n/a | n/a | SettingsScreenTest (Home and setup have no rate button); a rating prompt would need the In-App Review library, a new dependency that needs Kevin's OK |
| T11.5 | must never: the address is anything but Aegida's own store page (one owner, `PublicPages.PLAY_STORE`) | n/a | n/a | JVM |
| T11.6 | Comes back from Play | My settings as she left it | Nothing | No Aegida code runs on return; the tap itself is T11.1 |
| T11.8 | Offline | The Play app's own offline page; the browser fallback does not run | Nothing | Robolectric (the store intent resolves) |
| T11.9 | Uninstalls from the store page (accepted risk) | n/a | When alerts are on: the existing "hasn't checked in" note after 2 days | Existing dead-man tests |
| T11.10 | must never: Aegida suggests what to write, or fills in her name | The line about her Google name only | Nothing | Review |
| T11.7 | 200% font, both themes | Readable, wraps | n/a | SettingsScreen variants |

## Release B

### T4 (#175). "Call a guardian" with no phone number opens an empty keypad

`FixActions.callGuardian` (FixActions.kt:73-77) keeps digits and "+" and falls back to a bare `ACTION_DIAL` when nothing is left.
The About step labels the field "(one tap to call)" but does not require it. The phone stays stored as typed.

Kevin, Nov 13 (K1): many owners will not know the guardian's number by heart, so the phone stays optional. Setup offers to pick it
from her contacts; without a phone, Aegida relies on the invitation email and the guardian notes, and never shows a call button
that cannot call.

- **Pick from my contacts.** The phone field's main button opens the phone's own contact picker for phone numbers
  (`ACTION_PICK` on `ContactsContract.CommonDataKinds.Phone.CONTENT_URI`). Android grants Aegida only the one row she picks: no
  contacts permission, no new library. The number fills the field; the contact's name fills the guardian's name only if that is
  still empty. Typing stays below it. Email is not read from the contact (that would need the contacts permission). To be checked
  on the emulator and later the Galaxy: several numbers on one contact, and Samsung's contacts app.
- **No phone.** "Call a guardian" is not shown on Home, the question page or the T2 page. The T2 line becomes "If you are ever
  unsure, ask" and the guardian's name. Nothing nags her to add one; My settings has a quiet "Add Sam's phone" that uses the same
  picker and follows T5's phone rule (K7).
- The button may say the guardian's name ("Call Sam") when it fits at 200%, else "Call a guardian".
- privacy-and-data.md and the privacy page gain one line: if she picks a contact, Aegida keeps that one number on the phone, never sends it.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T4.1 | Phone saved, taps Call | The dialer with the number filled in | n/a | existing FixActions test |
| T4.2 | Picks the guardian from contacts in setup | The number in the field; the name too if it was empty | Nothing new | Robolectric with a stubbed picker result |
| T4.3 | Backs out of the contact list | The field as it was | Nothing | Robolectric |
| T4.4 | Contact with several numbers | Android asks which one; the one picked is saved | Nothing | Emulator, then Galaxy |
| T4.5 | No contacts app, or the picker cannot open (`startSafely` false) | The typed field only; no button that does nothing | Nothing | Robolectric |
| T4.6 | must never: Aegida asks for the contacts permission or reads anything but the picked row | n/a | n/a | Manifest test: no READ_CONTACTS |
| T4.7 | No phone, alerts on | No call button; "If you are ever unsure, ask Sam."; the guardian notes work as today | Notes as today | Robolectric |
| T4.8 | No phone, alerts off | No call button; the same line | Nothing | Robolectric |
| T4.9 | Upgraded from 0.4.2 with a blank phone | As T4.7/T4.8; nothing asked again in setup | Nothing | JVM |
| T4.10 | No dialer app (a tablet) with a phone saved | The number shown as words; never a button that does nothing | n/a | Robolectric |
| T4.11 | hostile: guardian name long or shaped like a web address | "Call a guardian"; never shown as a link | n/a | JVM on the label |
| T4.12 | hostile: picked contact's name is a web address or very long | Saved as typed text, never a link; she can change it | n/a | JVM |
| T4.13 | Old database with letters in the phone | Digits dialed, as today | n/a | JVM |
| T4.14 | must never: the phone is sent to anyone | n/a | Nothing | Existing outbox text guards |

### T5 (#176). Fix a spelling without replacing the guardian

Today her name is shown but not editable, and the guardian's details change only through "Pick a different guardian"
(GuardianChange.kt: tells the old guardian they were replaced, cancels their waiting message, makes a new channel). That rule
exists because a coached change must not be silent (GuardianChange.kt:12-15), and the channel is the password
(how-it-works.html:401). So:

- "Fix a spelling" on the name card covers only her first name and the guardian's name. It reuses `saveAbout`'s path, sends
  nothing, and keeps the channel; notes use the new name (FindingTelling `guardianName(settings)`); the channel keeps the old
  spelling ("Ruht-aegida-...").
- The guardian's email: only through "Pick a different guardian", now prefilled with the current details. A wrong address means a
  stranger may hold the channel, so a new channel is right; the old guardian's "replaced" note harms no one.
- The guardian's phone (adding or changing): K7. Recommended: saved, and the current guardian gets one short note, never the
  number: "Ruth changed your phone number in Aegida. If you did not expect this, please call Ruth." A coached owner could
  otherwise make "Call a guardian" dial the scammer.

| ID | Situation | Owner sees | Guardian gets | Test |
|---|---|---|---|---|
| T5.1 | Fixes "Ruht" to "Ruth" | My first name: Ruth | Later notes say Ruth; same channel | JVM + Robolectric |
| T5.2 | must never: "Fix a spelling" offers the email or phone | Only the two names | n/a | Robolectric |
| T5.3 | must never: a spelling fix tells the old guardian they were replaced, makes a new channel or cancels the waiting message | n/a | Nothing extra | JVM: no NtfySender call, topic unchanged |
| T5.4 | Fixes the guardian's email through "Pick a different guardian" (prefilled) | The existing change flow | Old channel: the "replaced" note, heartbeat cancelled; new invitation at the new address | GuardianChangeTest |
| T5.5 | Adds or changes the guardian's phone (K7 yes) | Saved | Current guardian: the short note without the number | JVM |
| T5.6 | Name fixed while the "hasn't checked in" note is waiting | Nothing | The next re-arm uses the new name; same channel | JVM (DeadMansSwitch) |
| T5.7 | Name fixed while a warning is open | n/a | The 24-hour reminder uses the new name | JVM (OldWarningReminder) |
| T5.8 | Name fixed while alerts are off | Saved | Nothing | JVM |
| T5.9 | repeat: saves with nothing changed, or fixes it back | No question; the original | Nothing | JVM |
| T5.10 | hostile: blank name, look-alike letters, another alphabet | Save waits on blank (FR5 rules); others saved | Channel unchanged (FR13) | JVM |
| T5.11 | failure: killed while saving | Either the old or the new details, never a mix | Nothing | Test on the guardian settings store (checked before naming) |

## Kevin's decisions

| | Question | Recommended | Reviewers |
|---|---|---|---|
| K1 | Must setup require the guardian's phone? | **Decided (Kevin, Nov 13): optional.** Offer "Pick from my contacts"; without a phone, no call button and rely on the invitation and guardian notes (T4) | Fable: require. Opus: optional. |
| K2 | May she fix her own name after setup, with later notes using it? | **Decided (Kevin, Nov 13): as recommended.** Yes; the channel keeps its spelling | Both yes |
| K3 | Title | **Decided (Kevin, Nov 13): as recommended.** Keep "Aegida"; the short description carries the search words | Both |
| K4 | Share button | **Decided (Kevin, Nov 13): yes, a quiet button at the bottom of My settings (T10)** | Both leaned no |
| K5 | One line linking the how-it-works page in the invitation | **Decided (Kevin, Nov 13): as recommended.** Yes | Both |
| K6 | Version | **Decided (Kevin, Nov 13): as recommended.** 0.4.3 for release A; release B gets its own number | Both |
| K7 | Adding or changing the guardian's phone tells the current guardian (without the number) | **Decided (Kevin, Nov 13): as recommended.** Yes | Opus raised it; Fable's T5 concern is the same risk |
| K8 | Rate button | **Decided (Kevin, Nov 13): yes, a quiet button at the bottom of My settings, never a prompt (T11)** | Both rejected a prompt |

## For the production-access form (only what ships)

- "Testers found no crashes or bugs. Setup already walks the owner through one step at a time, so instead of an interactive tour we added a closing 'What you will see' page, also in Settings, showing what Aegida's notices look like and what to tap." (true once T2 ships)
- "In response to their store-listing feedback we rewrote the short description, added a paragraph and screenshots for families setting the app up for an older relative, and made the privacy wording exact about what stays on the phone." (mention screenshots only once retaken)
- "Following their suggestion we added 'Tell a friend' and 'Rate Aegida' buttons at the bottom of Settings. We made them buttons the user chooses to tap, never pop-up prompts, because the app is used by older adults whom it teaches to be wary of unexpected prompts."
