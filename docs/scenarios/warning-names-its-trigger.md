# Scenarios: every warning names what set it off (finding #164)

Kevin (Oct 21): every warning must show the owner what triggered it (the app, website, setting or sender) on Home, on the page
"Fix my phone" opens, and in History, so she can tell whether it was hers and fix it. Field report: on Kevin's Pixel (0.4.1) a
"new notifications" question named neither the app nor the site, on Home or after Fix my phone. `GAP` means nothing covers it yet.

## Audit (Oct 22, by reading the code at 8641838, checked with a throwaway JVM probe over the real detectors)

Where each side gets its words:

- **Banner** (the Android notice Aegida posts): `NotificationPoster.show` = `Problem.forMom` only. Its tap opens
  `FixActions.settingsIntentFor` (an Android screen) when there is one, else Aegida's Home; it never opens Aegida's own page.
- **Home**: `HomeScreen` draws "1 thing needs a tap", then `Problem.forMom` of the first open finding, then the button. Nothing
  from the finding's evidence is drawn.
- **Page after Fix my phone** (`MainContent.fixFinding`): `Fix.ConfirmChange` / `Fix.ConfirmEvent` open `ConfirmPage`, whose only
  "what" line is `Texts.Home.confirmApp(problem, evidence[APP_LABEL])` (null for every urgent kind, R17, and for any finding
  without `APP_LABEL`); `Fix.Acknowledge` opens `AdvicePage` (`AdviceLines.of`); `Fix.AppNotices` opens `NoisyAppPage`
  (`AdviceLines.ofNoisyApp`); `Fix.Settings` / `Fix.SettingsThenAcknowledge` open an Android screen directly; `Fix.WebsiteFilterGuide`
  opens Aegida's filter guide.
- **History**: `HistoryRows` title = `Problem.whatHappened`; the line under it = `HistoryDetail.of(finding)`, written once when the
  warning is first shown, from the evidence as it was then (`AlertPipeline.announce`; a repeat keeps the stored evidence).
  For every app kind except the noisy app, the words are `confirmApp(...)`, so urgent kinds get no words and no hidden key.

Marks: **yes** names the trigger; **no** does not; **setting** the sentence itself names the phone setting, which is the trigger;
**Android** Aegida does not name it but the Android screen it opens does; **n/a** not shown there.

| Problem | Raised by: subject; evidence | Banner | Home | Page after Fix my phone | History |
|---|---|---|---|---|---|
| NoisyApp | NoisyAppDetector: package; PACKAGE, APP_LABEL (if the app list has it), NOTICES, MESSAGE_TITLE, MESSAGE_AT | no ("Tap to see which one") | no | yes: NoisyAppPage "It came from the “X” app." plus an example | yes: "The “X” app sent N notices that look like ads." |
| ScamLink | LinkDetector: link host; NOTIFICATION_KEY, PACKAGE, HOST, MESSAGE_*, APP_LABEL (if known) | no | no | yes: AdvicePage app, time, sender, words with links taken out | yes: app, sender, words, "The link went to host." (whole on the phone) |
| LookalikeLink | as ScamLink | no | no | yes: as ScamLink | yes: as ScamLink |
| SiteNotifications | PopupSpamDetector: site origin; NOTIFICATION_KEY, PACKAGE, CHANNEL_ID, SITE, MESSAGE_*, ALARMING | no | no | Android: the site's own notification switch in Chrome (named by the site) | yes: "The website was host. It said: ..." |
| SiteScaryNews | as SiteNotifications | no | no | Android: as SiteNotifications | yes: as SiteNotifications |
| NewWebsiteNotifications | as SiteNotifications (no APP_LABEL) | no | **no** | **no**: ConfirmPage looks only for APP_LABEL, which this detector never sets; the probe printed `confirmApp=null` | yes: "The website was host. It said: ..." |
| WebsiteFilterOff | DnsDetector: chosen host; HOST | setting ("Website protection") | setting | setting: Aegida's filter guide | setting ("Website protection was off") |
| ListenerOff | HealthDetector: "aegida"; none | setting | setting | Android: notification access | setting |
| ListenerNotConnected | HealthDetector: "aegida"; none | setting | setting | Android: notification access | setting |
| NotificationsBlocked | HealthDetector: "aegida"; none | setting | setting | Android: Aegida's notification settings | setting |
| GuardianUnreachable | DeliveryDetector: "guardian"; none | setting ("your guardian") | setting ("your guardian") | the dialer with the first guardian's number | setting ("your guardian") |
| BrowserChanged | DriftDetector: new package; APP_LABEL only if the cached app list has the package | no | **no** | yes if APP_LABEL: "The app is called: “X”"; **no** when the app arrived less than 10 minutes before (the app list is kept 10 minutes) | yes if APP_LABEL; otherwise no, and never filled in later |
| SmsAppChanged | as BrowserChanged | no | **no** | as BrowserChanged | as BrowserChanged |
| DialerChanged | as BrowserChanged | no | **no** | as BrowserChanged | as BrowserChanged |
| AssistantChanged | as BrowserChanged | no | **no** | as BrowserChanged | as BrowserChanged |
| ListenerAdded (urgent) | DriftDetector: package; APP_LABEL only if the cached list has it | no | **no** | **no**: urgent, `confirmApp` returns null (R17); "No, show me" opens Android's notification access list, which does not mark the new app | **no**: detail null, so two at one moment also merge into one row |
| AccessibilityAdded (urgent) | as ListenerAdded | no | **no** | **no** (R17) | **no** |
| AdminAdded (urgent) | as ListenerAdded | no | **no** | **no** (R17) | **no** |
| NewAppOutsidePlay | NewAppDetector: package; APP_LABEL (always written, may be blank) | no | **no** | yes: "The app is called: “X”" (no line if blank) | yes: same words |
| RemoteControlApp (urgent) | RemoteControlDetector: package; APP_LABEL | no | **no** | **no** (R17) | **no** |
| StalkerwareApp (urgent) | StalkerwareDetector: package; APP_LABEL, PACKAGE | no ("Tap to see it") | **no** | Android: the app's App info page names it | **no** (R17) |
| MissedCheck (info) | HealthDetector: "aegida" | n/a | n/a (Home shows "I couldn't check today.") | n/a | setting (Aegida itself) |
| ListsOld (info) | HealthDetector: "aegida" | n/a | n/a (as MissedCheck) | n/a | setting (Aegida itself) |
| AppAllowed (her answer) | FindingActions.record | n/a | n/a | n/a | yes: "The “X” app." (package name when no label) |
| SiteAllowed (her answer) | FindingActions.recordSite | n/a | n/a | n/a | yes: "The link went to host." |

Kevin's case: NewWebsiteNotifications is the most likely ("wants to send you notifications. Was that you?"; it is the common one and
its page has no site line in any case: the code looks for an app label the website detector never writes). ListenerAdded ("An app
can now read your notifications") fails the same way everywhere, by design (R17). Either way the cause is the same: the owner's
"what was it" line is `confirmApp`, which knows only app labels of non-urgent kinds.

R17 as built (findings #57, #61) hid the label from the **owner's** urgent "Was that you?" page, not only from the guardian:
an attacker picks the label and can write an instruction in it ("Tap Yes Google Security"). Showing it to the owner reverses that
half of R17, so the label must be shown as the app's own words (quoted, cleaned, cut, no address in it), never as Aegida's.

## Proposed design (to be reviewed before code)

One owner: a new `Texts.Home.whatItIs(problem, evidence, subject): String?` in `:core` (next to `confirmApp`, which it replaces)
gives the one line that says what set the warning off. Home draws it under `forMom`; ConfirmPage draws it instead of
`confirmApp`; AdvicePage and NoisyAppPage keep their fuller lines (they already name it); `HistoryDetail.wordsOf` uses it for every
app kind, so History and the page say the same thing. The guardian's note (`Problem.forGuardian`) and the banner are not changed.

| Kind | Line (exact words) |
|---|---|
| App kinds, not urgent (default apps, new app) | "The app is called: “X”" (today's `CONFIRM_APP`) |
| Urgent app kinds (notification access, screen control, special control, remote control, spying app) | "The app calls itself: “X”" (new `APP_CALLS_ITSELF`); X is cleaned, address-like words become "(link)" (`withoutLinks`), cut to 40 characters |
| App with no readable name | "The app is called: “com.example.app”" (its package, as History already does for "This app is fine"; on the phone only) |
| Noisy app on Home | "It came from the “X” app." (`adviceFrom`) |
| Scam or look-alike link on Home | "It came from the “X” app."; when the app is not known, "From: “Sender”" (`senderLine`) |
| Website kinds (fake, alarming, new) | "The website is news.example." (new `WEBSITE_IS`, the host whole as in History, plain text) |
| Settings and health kinds | none: the sentence already names the setting |

Also proposed: when a check sees a package in a role, the notification-access, screen-control or admin lists, or a new app that is
not in the kept app list, `AndroidSnapshotter` reads the list again at once (the 10-minute cache is why a just-installed app has no
name). History rows written before the update keep their empty detail (History is never rewritten).

## Scenarios

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| WN1 | "A website I never used wants to send me notifications" (Kevin's report) | Home: the question and "The website: news.example"; Fix my phone (or a tap on the banner): the same question page with the same line above Yes and No; History: "The website: news.example." and what it said | As today: the question with the site masked (ne*****.example) | Site with a port (localhost:8765); an IP address; a site with a path in its channel id | Chrome's channel id on the emulator (emulator_websites.py fixture) | WhatItIsTest#aNewWebsiteQuestionNamesTheWebsiteWithItsPort; WhatItIsTest#aWebsiteWithLettersFromAnotherAlphabetOrALookAlikeNameShowsAsUnusual; WhatItIsTest#aVeryLongWebsiteNameIsCutShort; HomeScreenTest#homeNamesTheWebsiteANewWebsiteQuestionIsAbout; HomeScreenTest#aTappedQuestionBannerOpensItsQuestionPage; HistoryDetailTest#aWebsiteWarningSaysWhichWebsiteAndWhatItSaid | PASS Oct 22 (Aegida36, 0.4.1-local): Home, question page and History show "The website: localhost:8791", light/dark, 100%/200%; build/emulator-checks/warning-names/web-* | whatitis-unusual-site; whatitis-lookalike-site; whatitis-site-port-kept; home-draws-what-it-is; confirm-draws-what-it-is |
| WN2 | "A website sent me fake virus warnings" | Home: the warning and "The website is fake.example."; Fix my phone opens that site's switch (Chrome names it); History as today | As today: the site masked | Before setup (no list yet) | Chrome fixture popup | WhatItIsTest#aWebsiteSendingFakeWarningsNamesTheWebsite | GAP | whatitis-site-port-kept |
| WN3 | "A website I allowed sent alarming news" | Home: the warning and "The website is news.example."; Fix my phone opens its switch; History as today | Nothing (as today) | Repeats within the week are quiet | Chrome fixture popup | WhatItIsTest#aWebsiteSendingFakeWarningsNamesTheWebsite; HistoryDetailTest#aWebsiteWarningShowsTheWordsOfItsTextToo | GAP | whatitis-site-port-kept |
| WN4 | "An app can now read my notifications" | Home: the urgent question and "The app calls itself: “Pixel Watch”"; Fix my phone: the same line above Yes and No; History: the same words, so the row closes green when she answers or it goes | As today: no app name (urgent) | Two apps at once; the app installed a minute before; the app removed before she looks | DriftDetector over a Snapshot from the emulator (an app given notification access with adb) | WhatItIsTest#anAppThatCanNowReadNotificationsShowsTheNameItCallsItselfAndWhereItCameFrom; HomeScreenTest#homeNamesTheAppAnUrgentWarningIsAboutAndKeepsTheButtonOnScreen; HistoryDetailTest#anUrgentAppsHistoryLineNamesItAndWhereItCameFromButNotTheDay | PASS Oct 22 (fixture app org.fixture.watcher as a second listener): Home and question page "The app calls itself: “Fixture watcher”" + "It came from outside the Play Store." + "Added today."; build/emulator-checks/warning-names/listener-* | whatitis-urgent-calls-itself; home-draws-what-it-is; history-no-day |
| WN5 | "An app can now see and press things on my screen" | As WN4, with "The app calls itself: “X”" | As today: no name | Two at once | Emulator, an accessibility service enabled with adb | WhatItIsTest#eachUrgentAppKindSaysTheAppCallsItself | GAP | whatitis-urgent-calls-itself |
| WN6 | "An app was given special control over my phone" | As WN4 | As today: no name | | Emulator, a test device admin | WhatItIsTest#eachUrgentAppKindSaysTheAppCallsItself | GAP | whatitis-urgent-calls-itself |
| WN7 | "An app that lets someone control my phone is on it" | As WN4, with the remote-control app's own name | As today: no name | Installed from Play | Remote-control list, fixture APK | WhatItIsTest#eachUrgentAppKindSaysTheAppCallsItself | GAP | whatitis-urgent-calls-itself |
| WN8 | "A spying app is on my phone" | Home: the warning and "The app calls itself: “X”"; Fix my phone opens its App info page; History names it | As today: no name | Renamed app caught by its certificate (shows the name it has now) | Echap feed, fixture APK | WhatItIsTest#eachUrgentAppKindSaysTheAppCallsItself; HistoryDetailTest#anAppProblemNamesTheAppAndAnUrgentOneAsTheNameItCallsItself | GAP | whatitis-urgent-calls-itself |
| WN9 | "My browser, text or phone app was changed" | Home: the question and "The app is called: “Firefox”"; page as today; History as today | As today: no name | The assistant; changed back before she looks | Emulator, default app changed with adb | WhatItIsTest#aChangedDefaultAppSaysTheAppIsCalledAndTheDayItWasAdded | GAP | whatitis-urgent-calls-itself |
| WN10 | "A new app came from outside the Play Store" | Home: the question and "The app is called: “X”"; page and History as today | As today: no name | | Fixture APK on the emulator | WhatItIsTest#aNewAppFromOutsideThePlayStoreSaysSo | PASS Oct 22 (fixture APK): "The app is called: “Fixture sideloaded”" + facts on Home and page; build/emulator-checks/warning-names/app-* | whatitis-source-outside |
| WN11 | "An app keeps sending me ads as notices" | Home: the warning and "It came from the “Temu” app."; page and History as today | As today: the app's name in the note | | Notices counted on the emulator | WhatItIsTest#aNoisyAppSaysWhichAppItCameFrom | GAP | home-draws-what-it-is |
| WN12 | "A message has a scam link" | Home: the warning and "It came from the “Messages” app."; Show me page and History as today | As today: app and masked address | App unknown: "From: “Sender”" instead; neither known: no line | Notice posted with cmd notification on the emulator | WhatItIsTest#aScamLinkSaysTheAppItCameInOrItsSenderWhenTheAppIsNotKnown; AdviceLineTest#theAdvicePageSaysTheAppTheTimeWhoItIsFromAndWhatItSaysWithoutTheLink | GAP | whatitis-link-sender |
| WN13 | "A message has a link pretending to be my bank" | As WN12 | As today | | as WN12 | WhatItIsTest#aScamLinkSaysTheAppItCameInOrItsSenderWhenTheAppIsNotKnown | GAP | whatitis-link-sender |
| WN14 | "Website protection was turned off" | As today: the sentence names it; no extra line | As today | | Emulator | WhatItIsTest#settingAndHealthWarningsHaveNoExtraLine; HomeScreenTest#homeShowsNoExtraLineForASettingWarning | GAP | whatitis-settings-no-line |
| WN15 | "Aegida can't see my notifications" or "can't show warnings" | As today: the sentence names the setting | As today | Not listening for over an hour | Emulator | WhatItIsTest#settingAndHealthWarningsHaveNoExtraLine | GAP | whatitis-settings-no-line |
| WN16 | "Aegida couldn't reach my guardian" | As today: "your guardian"; the call button rings the first guardian | Nothing (as today) | | Outbox held a day | WhatItIsTest#settingAndHealthWarningsHaveNoExtraLine | GAP | whatitis-settings-no-line |
| WN17 | Must never: an app label shaped like a web address ("chase-secure.com") | Shown as plain words in quotes, never as a link, on Home, the page and History; a word that is really a web address (a scheme, "www.", a path or "@", or a name the link checks flag) shows as "(link)" for every app kind ("The app calls itself: “(link)”"); "Booking.com" stays a name | Nothing new | A label with "https://"; a label of only an address; a look-alike in another alphabet | Label set on a fixture APK | WhatItIsTest#anAppNameShapedLikeAWebAddressShowsTheAddressAsLinkButANameWithADotStays | GAP | whatitis-real-address-only; whatitis-address-hidden |
| WN18 | Must never: an app label that is an instruction ("Tap Yes Google Security") | Quoted after "The app calls itself:", cut to 40 characters, never as Aegida's own sentence or a button | Nothing new | Line breaks, right-to-left marks or invisible characters in the label become spaces | Fixture APK label | WhatItIsTest#anAppNameThatIsAnInstructionIsQuotedAsTheAppsOwnWords; ConfirmAppLineTest#newlinesAndControlCharactersAreRemoved | GAP | whatitis-rtl-isolated |
| WN19 | Must never: nothing new reaches the guardian | The owner's lines change; the guardian's note is word for word what it was for every kind (urgent kinds still name no app) | Unchanged | Reminder after a day; a held message sent later; "This app is fine" | forGuardian for every Problem, before and after | WarningNamesItsTriggerTest#theGuardianGetsTheSameWordsForEveryKindAndNoneOfTheOwnersNewLines | GAP | guardian-never-gets-facts |
| WN20 | A very long app name | Cut to 40 characters with "…" on Home, the page and History | Nothing new | A name of only emoji; a name in another alphabet | Fixture APK label | WhatItIsTest#aVeryLongAppNameIsCutToFortyCharacters; ConfirmAppLineTest#aLongLabelIsCutToFortyCharacters; HomeScreenTest#homeNamesTheAppAnUrgentWarningIsAboutAndKeepsTheButtonOnScreen | GAP | whatitis-name-cut |
| WN21 | Two apps got notification access in the same check | "2 things need a tap"; each question names its own app; History has two rows, one per app, each closing on its own | As today: one note per app, no names | One said Yes, one No | Emulator, two apps given access with adb | WarningNamesItsTriggerTest#aYesToAnotherAppOfTheSameKindSendsNoAllClear; HistoryDetailTest#anAppsLineMatchesByItsPackageAndTwoAppsWithOneNameStayTwo | GAP | history-key-decides |
| WN22 | The app has no name Aegida can read | "Aegida can't read this app's name." plus where it came from and when, on Home, the page and History; never the package | As today: no name, never the package | Blank label; the app list read before the app arrived; no android:label at all (Android then gives the package or class name, #169) | Snapshot with the app missing from the app list | WhatItIsTest#anAppWithNoNameAegidaCanReadSaysSoAndNeverShowsItsPackage; ConfirmAppLineTest#anEmptyOrBlankLabelSaysAegidaCannotReadTheName | GAP | whatitis-unreadable-name |
| WN23 | The app was removed before she looked | The warning closes at the next check; History keeps the name it had when it warned, and the closing line names it too, so the row turns green | As today | Removed while the page is open | Emulator, app uninstalled with adb | WarningNamesItsTriggerTest#anAppRemovedBeforeSheLookedClosesWithALineThatNamesIt | GAP | history-key-decides |
| WN24 | A warning from before the update | Home and the page show the line from the stored evidence when it has one; History rows already written stay as they were | Nothing | Urgent rows written before have no name and stay amber (HC13) | Database from 0.4.1 | WarningNamesItsTriggerTest#anUrgentWarningToldBeforeTheUpdateStillGetsItsAllClearAfterHerYes; WarningNamesItsTriggerTest#aQuestionToldBeforeTheUpdateWithTheOldWordsStillGetsItsAllClear; OutboxDeliveryAllClearTest#aNoteQueuedBeforeTheUpdateIsDeliveredButSheSaysYesAndNoAllClearIsSent; HistoryDetailTest#aWebsiteLineFromBeforeTheUpdateMatchesTheNewLineForTheSameWebsiteOnly | GAP | allclear-older-line; allclear-older-line-only-nameless; history-key-decides; history-site-key |
| WN25 | The same app warns again after she said No | Same name each time; History rows for that app close together | As today | Question again after a week (websites) | Room store | AlertPipelineTest#anAppThatRenamesItselfAfterTheWarningKeepsTheNameItWarnedWith; HistoryDetailTest#anAppsLineMatchesByItsPackageAndTwoAppsWithOneNameStayTwo | GAP | repeat-keeps-first-evidence; history-key-decides |
| WN26 | Banner on the lock screen (ask Kevin) | Banner words unchanged: no app or site name on the lock screen, where others can read it and where a label could carry an instruction | Nothing | Tapping a "Was that you?" banner opens Android's screen today, not the question; proposed: open the question page in Aegida | NotificationPoster | NotificationPosterTest#aWasThatYouBannerStaysGenericAndOpensAegidasQuestionForThatWarning; HomeScreenTest#aTappedQuestionBannerOpensItsQuestionPage; HomeScreenTest#aTappedBannerForAClosedWarningOpensHome | PARTLY Oct 22: banner tap with Aegida alive in the background opened Home, not that question (finding #165); with fresh state (am start with the banner's extras) it opens the question page; a stale one opens Home | banner-opens-question; banner-routes-to-question |
| WN27 | Urgent label shown to the owner (ask Kevin: reverses the owner half of R17) | "The app calls itself: “X”" (quoted, cleaned, address words removed, cut) | Unchanged: never the label | | Texts.Home.whatItIs | WhatItIsTest#eachUrgentAppKindSaysTheAppCallsItself; ConfirmAppLineTest#anUrgentProblemShowsTheLabelOnlyAsTheNameTheAppCallsItself | GAP | whatitis-urgent-calls-itself; guardian-never-gets-facts |
| WN28 | Facts the app cannot fake: where it came from (Kevin, Oct 22) | Step 2 draws it; step 1 stores it: every app warning (new app, remote control, spying app, default app changed, notification access, screen control, special control, noisy app, and the app a scam message came in) keeps `appSource` = play / maker_store / outside / system, decided exactly as the new-app question decides it | Nothing (evidence never leaves the phone) | From Play; from the Galaxy Store (installed and asked for by it); a file opened by hand; came with the phone | testkit app shapes from the Pixel and Galaxy dumps; real detectors | AppFactsEvidenceTest#aNewAppFromOutsideThePlayStoreSaysItCameFromOutsideAndWhenItWasAdded; AppFactsEvidenceTest#aRemoteControlAppFromPlaySaysItCameFromThePlayStore; AppFactsEvidenceTest#aSpyingAppFromTheMakersStoreSaysItCameFromTheMakersStore; AppFactsEvidenceTest#eachChangedDefaultAppAndEachNewAccessSaysWhereTheAppCameFromAndWhen; AppFactsEvidenceTest#aNoisyAppSaysWhereItCameFromAndWhen; AppFactsEvidenceTest#aScamLinkSaysWhereTheAppItCameInCameFrom | GAP | new-app-facts; remote-app-facts; stalker-app-facts; drift-app-facts; noisy-app-facts; link-app-facts; newapp-maker-store-ok |
| WN29 | When the app was added | Step 1 stores `appAdded` (first install time, epoch millis) for apps that did not ship with the phone; one that came with the phone has none (its date is the factory's, and "came with the phone" says it) | Nothing | Installed a minute ago; years ago; system app | `PackageInfo.firstInstallTime`, read from the same lookup as the certificates (no extra call) | AndroidSnapshotterTest#whenAnAppWasFirstInstalledIsReadAndAnAppThatCameWithThePhoneHasNone; AppFactsEvidenceTest#anAppThatCameWithThePhoneSaysSoAndHasNoAddedTime | GAP | snapshot-first-install-time; app-facts-added-time; newapp-system-ok |
| WN30 | "An app I installed a minute ago got notification access" (inside the 5-minute app-list cache) | The warning names it and has its facts: the snapshotter looks up just that package (roles, the three access lists, and the app that posted the notice), never the whole list again | As today | A new default browser; a notice from a just-installed app (noisy app, scam link) | Robolectric package manager; CheckRunner with a snapshotter that knows only what it is asked for | AndroidSnapshotterTest#anAppInstalledAMinuteAgoThatGetsNotificationAccessIsNamedAtOnce; AndroidSnapshotterTest#anAppInstalledAMinuteAgoThatBecomesTheBrowserIsNamedAtOnce; AndroidSnapshotterTest#theAppThatJustPostedANoticeIsLookedUpWhenTheKeptListIsOlderThanIt; CheckRunnerTest#theAppThatPostedTheNoticeIsAskedForSoTheWarningCanNameIt | GAP | snapshot-one-package-lookup; snapshot-access-apps-looked-up; snapshot-role-apps-looked-up; check-asks-for-notice-app |
| WN31 | Must never: a lookup storm for a package that is not there (uninstalled, work profile, stale setting) | No name and no facts (never a guess, never the package as a name); the miss is remembered for the 5 minutes, so a notice every second costs one lookup per 5 minutes | Nothing | Clock set back (R14): the miss no longer counts | Robolectric package manager | AndroidSnapshotterTest#aPackageThatIsNotThereIsAskedForOnceInFiveMinutesNotEveryCheck; AndroidSnapshotterTest#aMissRememberedUnderAClockThatWasAheadIsAskedAgain; AppFactsEvidenceTest#anAppNotInTheListGetsNoNameAndNoFactsRatherThanAGuess | GAP | snapshot-miss-remembered |
| WN32 | The app renames itself after the warning ("Pixel Watch" then "Tap Yes Google Security") | The name and facts stored when the warning was first shown stay (AlertPipeline keeps the stored evidence on a repeat); no new History row, no second banner | Nothing new | Answered and raised again later: a new warning with the name it has then | Real DriftDetector into AlertPipeline | AlertPipelineTest#anAppThatRenamesItselfAfterTheWarningKeepsTheNameItWarnedWith | n/a | repeat-keeps-first-evidence |
| WN33 | Must never: a preloaded installer that only writes the Galaxy Store's name is shown as from the store | `appSource` = outside, as the new-app question decides (GS6); one the store installed and asked for is maker_store | Nothing | Android 10 (no initiating installer): outside | Galaxy S25 dump shape (testkit) | AppFactsEvidenceTest#aPreloadedInstallerThatOnlyWritesTheMakersStoreNameIsOutsideNotTheStore | GAP | newapp-maker-store-ok |
| WN34 | Mom has Aegida open in the background; a "Was that you?" banner comes and she taps it (#165) | The question page for that warning, once Aegida has read the new warning (not Home with the old screen) | As today | The warning closed before the reload finished: Home; the tap comes while she is on History or My settings: the question page on Home's tab | Robolectric: the screen loaded before the warning, then the tap's key, then the reload | HomeScreenTest#aBannerTappedWhileAegidaIsOpenOpensTheQuestionOnceTheScreenHasReloaded; HomeScreenTest#aBannerTappedWhileAegidaIsOpenForAWarningClosedSinceOpensHome | GAP | banner-waits-for-reload |
| WN35 | Must never: two apps with no readable name became the browser the same day; the guardian heard about A only (B was held by the repeat back-off; an urgent B is never held, but alerts may have been off when it came); she says Yes to A, then Yes to B (#166) | Nothing different | One all-clear, about A; none for B (B was never told) | Lines written by 0.4.1 with no words (urgent names were hidden): still count for her Yes, as WN24 | AlertPipeline over the real detail lines | WarningNamesItsTriggerTest#aYesToAnAppTheGuardianHeardAboutThenToOneHeldBackSendsOnlyOneAllClear | n/a | allclear-line-names-the-app |
| WN36 | An app added years ago is the one asked about (#167) | "Added on October 3, 2023." (the year when it is not this year in her zone); this year: "Added on October 3." | Nothing | New Year's Eve in her zone vs UTC | WhatItIs with relative dates | WhatItIsTest#anAppAddedInAnEarlierYearSaysTheYear | n/a | whatitis-added-year |
| WN37 | Must never: an old History line for "paypal.com.secure-login.net" matches the warning about paypal.com (#168) | Its row stays as it was (no wrong green) | No all-clear from it | "The website was paypal.com." still matches paypal.com; "paypal.com:8080" does not | Lines as 0.4.1 wrote them | HistoryDetailTest#aWebsiteLineFromBeforeTheUpdateMatchesTheNewLineForTheSameWebsiteOnly | n/a | history-site-key-end |
| WN38 | An app has no name of its own (no android:label); Android hands back its package or class name (#169) | "Aegida can't read this app's name." plus the facts; never "com.evil.app" | As today: no name | Label equal to the package; label equal to the Application class | Robolectric package manager with a real ApplicationInfo and no label (the framework's own loadLabel fallback) | AndroidSnapshotterTest#anAppWithNoNameOfItsOwnHasNoLabelRatherThanItsPackageOrClassName | GAP | snapshot-no-fallback-label |

## Step 2 as built (Oct 22): the words

One owner, `Texts.Home.whatItIs(problem, evidence, now, zone, locale)` (logic in `core/text/WhatItIs.kt`), replaced `confirmApp` and
`adviceFrom`. Home draws its lines under the sentence, the question page draws them above Yes and No, the advice and noisy-app pages start
with them, and `HistoryDetail` builds every app and website line from them (with no day: a History line is written once and compared
later). The guardian's note (`Problem.forGuardian`, `FindingTelling`) is not touched (WN19 pins it for every kind).

| Kind | Lines (exact words) |
|---|---|
| Website kinds | "The website: news.example" (the host as written, a port kept, cut to 40 with "…"); a host with an `xn--` label, letters outside a-z, or mixed or look-alike letters (LinkSigns): "It came from a website with an unusual name." |
| App kinds, not urgent | "The app is called: “Firefox”" |
| Urgent app kinds | "The app calls itself: “Pixel Watch”" |
| Facts after an app's name | "It came from the Play Store." / "It came from the Galaxy Store." / "It came from outside the Play Store." / "It came with the phone."; then "Added today." / "Added yesterday." / "Added on October 3." (none for an app that came with the phone, none in History) |
| No readable name | "Aegida can't read this app's name." plus the facts; never the package |
| Noisy app | "It came from the “Image to PDF” app." plus the facts |
| Scam or look-alike link | "It came from the “Messages” app.", else "From: “Chase Bank”" |
| Settings and health kinds | no line |

An app's name: line breaks, direction marks and invisible characters become spaces; a word shows as "(link)" only when it is really a web
address (a scheme, "www.", a path or "@", or a name the link checks flag: another alphabet, mixed or look-alike letters), so "Booking.com"
stays a name and "chase-secure.com" does not; right-to-left words are wrapped in Unicode isolate marks; cut to 40 with "…".

History matching: an app or website line carries a hidden key (the package, or the website's host); two keyed lines match on the key
alone, so a line written before step 2 still matches; a key-less site line from before ("The website was news.example.") matches when it
names the key's host. The all-clear: a told-line with no words matches only when the line before step 2 would have had none (an urgent
app, or one with no name); otherwise a missing line still proves nothing (findings #116).

The banner: words unchanged (`Problem.forMom`). A "Was that you?" banner's tap opens Aegida (extras: the finding's key) and the question
page for that warning; a warning closed since opens Home. If Aegida was already open in the background, the request waits for a
reload that starts after the tap, then routes (#165, WN34).

## Step 1 as built (Oct 22): the data

- `InstalledApp.firstInstalled` (`PackageInfo.firstInstallTime`, null for system apps and for fixtures); `AndroidSnapshotter` fills it
  from the `GET_SIGNING_CERTIFICATES` lookup it already made for certificates.
- One derivation, `InstalledApp.sourceIn(makerStores)` (`AppSource`: `play`, `maker_store`, `outside`, `system`), used by
  `NewAppDetector` to decide and by `InstalledApp.facts` / `Snapshot.factsOf` for the evidence. The stored values must never be renamed.
- Evidence keys `appSource` and `appAdded` (epoch millis as text), plus `package`, on every finding that names an app, when the app is
  in the snapshot. Not in the list: no label, no facts.
- `Snapshotter.take(alsoApps)`: `AndroidSnapshotter` looks up, one package at a time, any role, access-list or notice package missing
  from the kept list, adds it to the list, and remembers a miss for the 5 minutes (a negative age counts as stale, R14).
