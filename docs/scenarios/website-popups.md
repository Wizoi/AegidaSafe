# Scenarios: website pop-ups

A website sends notifications through the browser. A fake virus warning is hidden. A new website is asked about once.
`GAP` means nothing covers it yet.

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| WP1 | "A website says my phone has a virus" | The pop-up is hidden; a warning on Home that it was hidden | A note naming the site, masked | Repeats are all hidden; the owner is told once | Chrome's real channel format | CheckRunnerTest#aFakeVirusPopUpIsHiddenAndOwnerAndTheGuardianAreTold; CheckRunnerTest#everyRepeatedPopUpIsHiddenButOwnerIsOnlyAlertedOnce | emulator_websites.py | popup-baseline-sites |
| WP2 | "Chrome's own site notification" | Caught as a website notification | n/a | Real shape from the emulator | Emulator dump | DetectorsTest#theRealChromeSiteNotificationIsCaught | emulator_websites.py | site-banner-opens-switch |
| WP3 | "A website I never saw before sends a notice" | A question: "Was that you?" The notice is put aside, not deleted | At once, when alerts are on: the site as a question, masked (not after a day) | Question left a day | Emulator | DetectorsTest#aSiteThatAppearedAfterSetupIsAskedAboutAndNotSimplyBlocked; CheckOutcomeTest#aFakeWarningIsRemovedAndANewWebsiteIsSnoozed; FindingActionsTest#aNewWebsiteIsToldToTheGuardianAsAQuestionAndHidden | emulator_websites.py (site B) | new-website-is-snoozed-not-deleted |
| WP4 | "I tap Yes, it was me" | The site is remembered and its notices stay | Nothing | A baseline from before websites were recorded | Emulator | WebsiteBaselineTest#sayingItWasHersAddsTheSiteAndKeepsTheOthers; WebsiteBaselineTest#aBaselineFromBeforeWebsitesWereRecordedTakesHerYesToo | emulator_websites.py (site A) | website-yes-adds-the-site; website-question-can-be-answered |
| WP5 | "A site I already had at setup" | Left alone unless it turns scary | Nothing | Scary words later | Emulator | DetectorsTest#aSiteSheAlreadyHadWhenSetupEndedIsLeftAloneUnlessItTurnsScary; WebsiteBaselineTest#theBaselineStartsWithNoWebsitesSoEachIsAskedAboutOnce | emulator_websites.py | website-recorded-site-left-alone; website-baseline-starts-empty |
| WP6 | "A new site with fake virus words" | Called fake, not asked about | Note | n/a | Invented words | DetectorsTest#aNewSiteWithFakeVirusWordsIsCalledFakeNotAskedAbout | emulator_websites.py | GAP |
| WP7 | "I tap No" | The site's notice is deleted; quiet for a week | Nothing | Notice comes back: deleted, not put aside again | Emulator | CheckOutcomeTest#aWebsiteSheSaidNoToIsDeletedWhenItsNoticeComesBackNotPutAsideAgain; WebsiteSitesTest#aWebsiteTheOwnerSaidNoToIsQuietForAWeek | emulator_websites.py (site B again) | website-no-sticks |
| WP8 | "I never answered the question" | After a day the notice stops being put aside and is deleted | Nothing | Clock set ahead | Emulator | CheckOutcomeTest#aQuestionLeftUnansweredForADayStopsBeingPutAsideAndIsDeleted; AlertPipelineTest#aQuestionFirstSeenUnderAClockThatWasAheadIsTreatedAsSeenNowAndDeletedADayLater | emulator_websites.py (jump_minutes) | first-seen-put-right-once |
| WP9 | "What the site said is in History" | The site and its words, no link | n/a | Site masked for the guardian | Room database | HistoryDetailTest#aWebsiteWarningSaysWhichWebsiteAndWhatItSaid; HostMaskTest#keepsTheFirstTwoLettersAFixedRunOfStarsAndTheEnding | none | host-mask-hides-the-name; history-keeps-what-it-was |
| WP10 | "A pop-up title with a link in it" | The link is shown as (link) | Masked | n/a | Invented text | LinkTextScenariosTest#aWebsitePopUpTitleNeverShowsALinkAsText | none | popup-title-links-removed |
| WP11 | "A site I said Yes to later posts a real scare ('Retailer hacked')" | The notice stays where it is; no warning | Nothing | Site had at setup; allowed before the list was recorded; "malware found in 40 apps"; was hidden and the guardian told before findings #109 | Invented headline (a real news-site shape still wanted) | DetectorsTest#aNewsHeadlineFromASiteSheSaidYesToIsLeftInPlace | GAP | known-site-news-is-not-a-fake-warning |
| WP12 | "Samsung Internet or another browser" | Same as Chrome | Same | Website channels are known only in Chrome's format | Samsung dump needed | GAP | GAP | GAP |
| WP13 | "The word pop-ups splits to ups" | Nothing: a website notice is not link-checked, and in a text "pop-ups" is not the parcel company | Nothing | "blocked pop-ups.Tap", pop-ups.com; was warned in a text before findings #108 | Invented words | LinkSignsTest#theWordPopUpsIsNotTheParcelCompany; DetectorsTest#ownAndSiteNotificationsAreNotLinkChecked | none | ups-in-an-english-word-is-not-the-brand |
| WP14 | "The phone put the notice to sleep and it came back" | Left in place if the site was answered Yes | Nothing | Snooze returning, no model of it | Needs a fake notification shade | GAP | emulator_websites.py (partly) | GAP |
| WP15 | "A site I said Yes to starts saying my phone has a virus" | Still hidden and called fake (must never stay as news) | A note naming the site, masked | "Your device has been hacked", "Battery damaged by virus. Clean now", "Trojan detected on this phone", "You are infected"; site allowed before the list was recorded; "3 viruses found! Remove now", "Virus found", "Malware detected. Remove immediately", "Your Samsung/Galaxy S25/iPhone is infected" (findings #119; the rule is now WP17, findings #126); must-never news stays: "Retailer hacked", "New Android malware found in 40 apps", "Hackers stole card data", "New virus found in bats", "Your favourite retailer was hacked" | Invented words | DetectorsTest#aSiteSheSaidYesToThatWarnsAboutHerPhoneIsStillHidden; DetectorsTest#aSiteSheSaidYesToSayingVirusFoundOrYourPhoneIsInfectedIsStillHidden; DetectorsTest#aNewsHeadlineFromASiteSheSaidYesToIsLeftInPlace | emulator_websites.py | known-site-about-her-phone-still-hidden; scare-words-order; scare-words-count; scare-words-phone-makers |
| WP16 | "A website I never saw before posts 'Retailer hacked'" | Called fake, not asked about (unchanged: "Was that you?" would offer to allow a scare) | Note | n/a | Invented headline | DetectorsTest#aNewSitePostingAScareHeadlineIsStillCalledFake | none | GAP |
| WP17 | "A site I said Yes to sends news with a scare word, news that speaks to me, or a fake virus warning" | News stays; news that speaks to her is hidden and she is told "a scary news story" (A rows); a fake warning is hidden (S, A and H rows below) | Fake: a note naming the site, masked; news and news to the reader: nothing (Kevin, Oct 14) | 44 news rows, 11 news-to-the-reader rows, 59 fake rows (H20, H21, H38 moved to A1-A3) | Real-style headlines and pop-up wordings (below) | DetectorsTest#aNewsStoryFromASiteSheSaidYesToStaysWhereItIs; DetectorsTest#newsThatSpeaksToTheReaderFromASiteSheSaidYesToIsHiddenWithoutTellingTheGuardian; DetectorsTest#aFakeVirusWarningFromASiteSheSaidYesToIsHidden; DetectorsTest#aFakeThatAsksAboutHerPhoneFirstIsHidden | none | known-site-news-is-not-a-fake-warning; scary-news-is-hidden; scary-news-not-told-to-the-guardian; scary-news-has-no-guardian-sentence; scare-words-order; scare-words-order-starts-a-clause; scare-words-command-follows; scare-words-count; scare-words-count-at-clause-end; scare-words-bracketed-number; scare-words-brackets-split; scare-words-your-named-phone; scare-words-your-retailer-is-news; scare-words-phone-makers; scare-words-your-system; scare-words-you-are-infected; scare-words-you-may-have; scare-words-you-got; scare-words-found-on-this-phone; scare-words-question-is-news-to-the-reader; scare-words-delete-these-apps; scare-words-battery; scare-words-gate-battery; scare-words-gate-will-be-damaged; scare-words-gate-needs-cleaning; scare-words-look-alike-letters; scare-words-digits-for-letters; scare-words-it-after-a-question; scare-words-your-device-after-a-question; scare-words-answer-with-a-condition; scare-words-question-mark-ends-a-clause; scare-words-question-mark-after-another-mark; scare-words-question-about-her-phone-only; scare-words-it-has-a-virus; scare-words-gate-it-may-be-damaged |

## WP17: news or a fake warning? (findings #126)

Only for a website she already had or said Yes to: a scare word (virus, malware, hacked, infected, ...) from such a site is a fake
warning about HER phone (hidden, the guardian gets a note naming the site, masked), news that speaks to the reader (hidden, she is
told, nothing sent), or news (left where it is, nothing sent). A site she never had is called fake on any scare word (WP6, WP16);
this table does not change that path. Repeats, snooze, No and the failure states are those of WP1-WP8 and are unchanged; this
table is only about the words.

**Kevin, Oct 14 (findings #133):** real tech news that speaks to the reader ("Delete these apps now", "Check your phone for this
spyware", "Malware can take over your Android", "How to tell if your phone is hacked") is hidden from the shade as before, but the
guardian is NOT sent a note. It is its own problem, `Problem.SiteScaryNews`: she sees "A website you allowed sent a scary news story
about phones. Aegida hid it. Tap to stop that website." (Needs a tap; the tap opens that site's switch, as for a fake warning; told
at most once a week, each story still hidden); History says "A website sent a scary news story" with the site and its words; it has
no guardian sentence, so neither the first note nor the one-day reminder is sent. A fake pop-up from such a site (a count of viruses,
"your phone is infected", orders to clean, remove or download, a damaged battery) is still `Problem.SiteNotifications` ("A website
was sending you warnings. They are not real.") and the guardian gets the note. "Your system" counts as her phone.

**The rule (one rule):** before reading, look-alike letters are undone (NFKC, so maths-bold letters read plain; Cyrillic and Greek
look-alikes from the one shared map in `LookAlikes`, also used by LinkSigns; and a word with digits for letters, "V1rus", reads as
the scare word it spells). The same plain words go to the scare-word gate. Title and text are then read one clause at a time
(split at `. ! ? : ; , ( ) [ ] |` and a spaced dash; "(3)" stays a number).

It is a **fake warning** when one clause:
- **counts what it found:** a number right before virus/viruses/malware/trojan(s)/spyware, then found or detected, or at the end of
  the clause ("13 viruses found", "(3) viruses were found", "infected with 13 viruses", "Your Motorola has 3 viruses"); "two new
  viruses found" and "200 infected" are news;
- **says her phone is in a bad state:** "your" or "this" before a phone word (phone, device, mobile, tablet, browser, chrome,
  smartphone, cellphone, or a name in the phone list in Texts: system, the makers' names, galaxy, pixel, iphone, android ...), with
  infected / hacked / compromised / damaged / cleaning after it ("Your phone will be damaged", "Your Samsung needs cleaning"), or
  "on/in this phone" with found or detected in the clause; or "your <name> is ... infected" where the name has a phone word or a model
  number ("your new Pixel 9"). "Your pharmacy", "your bank" and "your favourite retailer" are not phones, so they are news;
- **says she is:** "you are / you're / you've been / you got infected or hacked", "you (may/might/could) have a virus", "you've got
  a virus", "you have 3 viruses";
- **orders her:** the clause starts (after "please") with tap, click, press, clean, remove, delete, download, install, scan, fix or
  repair, **and a command word follows** (to + another order word, here, ok, now, it, them, the, this, these, your, up, all,
  immediately, cleaner, or a virus word). "Click fraud", "Tap water", "Press freedom", "Clean energy", "Fix for", "Scan of" and "Tap to
  read the full story" are not orders. "Delete" with no virus word, "it" or "them" ("Delete these apps now") is news to the reader;
- **a damaged battery:** "battery" and "damaged" in one clause.

A claim inside a question or condition ("Is your phone infected?", "Have you been hacked?", "How to tell if your phone is hacked")
is not a claim. **Right after a question about her phone** (a clause ending in "?" with "your phone", "this phone", "your
Android", ... in it, or starting with a phone word: "Is your phone slow?", "Phone running hot?"), the next clause speaks of her phone
when it starts with "it" or "your device"/"your phone": it is a fake warning when a bad state or "(may) have / has a virus" follows
and the clause has no condition word ("Is your phone slow? It may have a virus", findings #138). The gate also lets "<phone word>
...? it may be damaged" through. "It could be", "It probably isn't", "Experts say it may ..." (not first) and "It may have a virus if
..." are not claims, so such a story stays news to the reader. It is **news to the reader** when, with no fake sign, a clause still speaks to her phone or to her (any "your phone",
"this phone", "your Android", a question about her, or a "Delete these apps" order). Anything else is **news**.

Tests: DetectorsTest#aNewsStoryFromASiteSheSaidYesToStaysWhereItIs (every S row),
DetectorsTest#newsThatSpeaksToTheReaderFromASiteSheSaidYesToIsHiddenWithoutTellingTheGuardian (every A row) and
DetectorsTest#aFakeVirusWarningFromASiteSheSaidYesToIsHidden (H1-H55) and DetectorsTest#aFakeThatAsksAboutHerPhoneFirstIsHidden (H56-H62); each case carries its ID and a failure lists the IDs.
"Today" is the result on the code before findings #126 (ABOUT_HER_PHONE as one regex) for S1-S33 and H1-H38, on the code
before findings #133 (the #126 rule) for the rows added by #133, and on the code before findings #138 for S44, A8-A11 and H56-H62.

### Must stay (news)

| ID | Title | Text | Source kind | Why it is news | Today |
|---|---|---|---|---|---|
| S1 | New malware found: what to know | Security researchers say the app was downloaded a million times | news style (tech) | reports, no order | hidden (wrong) |
| S2 | Malware found. | Researchers say 40 apps on the Play Store carried it | news style (tech) | reports about apps | hidden (wrong) |
| S3 | Bird flu virus found | Officials confirm the virus in a dairy herd in Texas | news style (health) | about a herd | stays |
| S4 | Your favourite retailer has been infected with ransomware | Here is what shoppers should know | news style (crime) | "your" + a two-word name with no phone word | hidden (wrong) |
| S5 | Malware found on Google Play | Google has removed the apps from its store | news style (tech) | about a store | hidden (wrong) |
| S6 | Trojan detected in banking apps | Researchers warn the malware targets 60 banks | news style (tech) | about apps | hidden (wrong) |
| S7 | Mpox virus detected in county | Health officials urge vaccination | news style (health) | about a county | hidden (wrong) |
| S8 | Retailer hacked | Millions of customer records stolen in data breach | news style (crime) | third party | stays |
| S9 | New Android malware found in 40 apps | Researchers say the virus spreads by text | news style (tech) | about apps | stays |
| S10 | New virus found in bats | Scientists say there is no risk to people | news style (health) | about bats | stays |
| S11 | Your favourite retailer was hacked | What it means for shoppers | news style (crime) | as S4 | stays |
| S12 | Hospital systems infected with ransomware | Patients are being sent to other hospitals | news style (crime) | a hospital's systems | stays |
| S13 | West Nile virus found in mosquitoes in three counties | Residents are asked to use insect repellent | news style (health) | about mosquitoes | stays |
| S14 | City water system hacked | Officials say drinking water was not affected | news style (crime) | a city's system | stays |
| S15 | Measles virus detected in wastewater | The health department asks parents to check vaccination records | news style (health) | about wastewater | hidden (wrong) |
| S16 | Pharmacy chain hacked, prescriptions delayed | Customers are told to call their store | news style (crime) | third party | stays |
| S17 | Police: man hacked ex-partner's email | He faces three charges | news style (crime) | third party | stays |
| S18 | Spyware found on journalists' phones | The group says the attack used a flaw in messaging apps | news style (tech) | other people's phones | hidden (wrong) |
| S19 | Banking trojan spreads through fake delivery texts | Experts say the malware asks for card numbers | news style (tech) | reports | stays |
| S20 | Norovirus outbreak: 200 infected on cruise ship | The ship returned to port early | news style (health) | "200 infected" counts people, not viruses found | stays |
| S21 | Two new viruses found in deer | Scientists say they pose no risk to humans | news style (health) | about deer | stays |
| S22 | 3 new strains of the virus detected in Europe | Scientists are tracking how fast they spread | news style (health) | a number, but not right before the virus word | hidden (wrong) |
| S23 | Election office computers infected with malware, officials say | No votes were changed | news style (crime) | an office's computers | stays |
| S24 | Teen charged after school website hacked | Police say no student data was taken | news style (crime) | third party | stays |
| S25 | Bird flu virus detected in milk samples | FDA says pasteurised milk carries no live virus | news style (health) | about milk | hidden (wrong) |
| S26 | Car maker infected by ransomware, plants shut | Production should restart next week | news style (crime) | third party | stays |
| S27 | Fake shopping apps carry spyware, researchers warn | The apps have been removed from the store | news style (tech) | "removed" is not an order | stays |
| S28 | Google to remove 40 infected apps from Play Store | The apps showed ads and stole contacts | news style (tech) | "remove" does not start the clause (hostile: an order word inside news) | stays |
| S29 | Microsoft says staff email was hacked | Customer data was not touched | news style (crime) | third party | stays |
| S30 | How a virus found in cats is changing vaccines | Researchers at the university explain | news style (health) | about cats | stays |
| S31 | Zika virus found in local mosquitoes; spraying set for Tuesday | Stay indoors at dusk, officials say | news style (health) | about mosquitoes | stays |
| S32 | Bank's app hit by trojan; customers told to change passwords | The bank says no money was taken | news style (crime) | third party | stays |
| S33 | Clean-up continues after malware attack on port | Ships are moving again | news style (crime) | "clean-up" is one word, not the order "clean" (hostile) | stays |
| S34 | Click fraud malware found in 60 apps | Researchers say the apps made fake ad taps | news style (tech) | "Click" is a noun here: no command word follows (hostile) | hidden (wrong) |
| S35 | Tap water tested for virus | The city says the water is fine to drink | news style (health) | "Tap water" (hostile) | hidden (wrong) |
| S36 | Clean energy firm hacked | Customer records may have been taken | news style (crime) | "Clean energy" (hostile) | hidden (wrong) |
| S37 | Press freedom group says reporters' phones hacked | The group asks for an inquiry | news style (crime) | "Press freedom"; other people's phones (hostile) | hidden (wrong) |
| S38 | Retailer hacked | Tap to read the full story | news style (crime) | "Tap to read" is not an order to fix anything | hidden (wrong) |
| S39 | Your pharmacy was hacked | What customers should know | news style (crime) | "your" + a name that is not a phone (the one-word rule is gone) | hidden (wrong) |
| S40 | Fix for Android malware released by Google | The update reaches phones this month | news style (tech) | "Fix for" (hostile) | hidden (wrong) |
| S41 | Scan of sewage finds polio virus | Health officials ask parents to check vaccinations | news style (health) | "Scan of" (hostile) | hidden (wrong) |
| S42 | Your bank was hacked: what to do next | Experts explain | news style (crime) | "your bank" is not a phone | hidden (wrong) |
| S43 | Experts tell users to remove the apps now | The malware hides in fake games | news style (tech) | "remove the" does not start the clause (guards the clause-start rule) | stays |
| S44 | Is the new Pixel any good? It was hacked in a contest | Researchers say the flaw is fixed | news style (tech; hostile: a question about a phone, then "it") | the question is not about HER phone (no "your"), so "it" is that phone | stays |

### Hide, but tell only her (news that speaks to the reader; Kevin, Oct 14)

Hidden; she sees "A website you allowed sent a scary news story about phones. Aegida hid it. Tap to stop that website."; the
guardian gets nothing (`Problem.SiteScaryNews`, no guardian sentence, so no reminder either).

| ID | Title | Text | Source kind | Why it is news to the reader | Today |
|---|---|---|---|---|---|
| A1 | Is your phone infected? Here's how to check | | news style (was H20) | a question about her phone, no claim | hidden, guardian told (wrong now) |
| A2 | Have you been hacked? Here's how to check | | news style (was H21) | a question about her | hidden, guardian told (wrong now) |
| A3 | Delete these apps now: malware found on Google Play | | news style (was H38) | "Delete these apps", no virus word in that clause | hidden, guardian told (wrong now) |
| A4 | Check your phone for this spyware | Researchers found it in 12 apps | news style (tech) | "your phone" with no bad state; "check" is not an order word | hidden, guardian told (wrong) |
| A5 | Malware can take over your Android | Here is how it spreads | news style (tech) | "your Android" with no bad state | hidden, guardian told (wrong) |
| A6 | How to tell if your phone is hacked | Five signs to look for | news style (tech) | "your phone is hacked" inside "how ... if" (hostile: a claim's words in a condition) | hidden, guardian told (wrong) |
| A7 | New spyware found that can infect your phone | Researchers say it hides in fake games | news style (tech) | "found" is not "on your phone"; no bad state after "your phone" (hostile) | hidden, guardian told (wrong) |
| A8 | Is your phone hacked? It could be, here's how to tell | | news style (tech; hostile: "it" after a question) | "It could be" has no bad state after it | hidden, not told |
| A9 | Is your phone listening to you? It probably isn't, but spyware can | | news style (tech) | "It probably isn't" denies; decided news to the reader, not news, because the question is about her phone (without "spyware" it never passes the gate and stays where it is) | hidden, not told |
| A10 | Is your phone slow? Experts say it may have a virus | | news style (tech; hostile) | "it" is not the first word: experts are reporting, not the pop-up claiming | hidden, not told |
| A11 | Is your phone slow? It may have a virus if it does these 5 things | | news style (tech; hostile) | a condition ("if") in the clause after the question | hidden, not told |

### Must hide (a fake warning about her phone)

| ID | Title | Text | Source kind | Why it is about her phone | Today |
|---|---|---|---|---|---|
| H1 | Viruses found - tap to fix | | scam style | order "tap" after a spaced dash | missed |
| H2 | Virus found, clean up now | | scam style | order "clean" | missed |
| H3 | 13 viruses found | Download cleaner | scam style | count; order "download" | missed |
| H4 | Security alert: virus found | Click OK | scam style | order "click" | missed |
| H5 | (3) viruses were found | | scam style | count in brackets | missed |
| H6 | System infected | Remove virus immediately | scam style | order "remove" | missed |
| H7 | Your Samsung Galaxy A15 is infected | | scam style | your + named phone (galaxy, model number) | missed |
| H8 | Your Galaxy S25 Ultra 5G is infected | | scam style | your + phone word | missed |
| H9 | Your Samsung is now infected | | scam style | your + one-word name + "is now infected" | missed |
| H10 | Your Samsung may be infected | | scam style | your + one-word name + "may be infected" | missed |
| H11 | Your iPhone has been hacked | | scam style | your + phone word | missed |
| H12 | 3 viruses found! Remove now | Tap here | scam style | count; orders | hidden |
| H13 | Virus found | Tap to remove | scam style | order | hidden |
| H14 | Malware detected. Remove immediately | | scam style | order | hidden |
| H15 | Your phone is infected | Tap here | scam style | your phone | hidden |
| H16 | Your device has been hacked | Tap to fix | scam style | your device | hidden |
| H17 | Security warning | Battery damaged by virus. Clean now | scam style | battery damaged; order | hidden |
| H18 | Alert | Trojan detected on this phone | scam style | this phone | hidden |
| H19 | Warning | You are infected | scam style | you are infected | hidden |
| H20 | (moved to A1, Kevin, Oct 14) | | | | |
| H21 | (moved to A2, Kevin, Oct 14) | | | | |
| H22 | Your Android is infected with 4 viruses | Clean now | scam style | your android; order | hidden |
| H23 | (2) viruses detected | Your phone may be damaged | scam style | count; your phone | hidden |
| H24 | Your phone is infected with (5) viruses | Scan now to remove them | scam style | your phone; order | hidden |
| H25 | Virus alert! | Your device is infected. Tap to clean | scam style | your device; order | hidden |
| H26 | Warning! Your mobile has been hacked | Install the cleaner now | scam style | your mobile; order | hidden |
| H27 | Malware found | Tap to scan and remove | scam style | order | hidden |
| H28 | Your Pixel 8 is infected | | scam style | your pixel | hidden |
| H29 | Spyware detected! | Click here to delete it | scam style | order | hidden |
| H30 | You have a virus | Download the cleaner to fix it | scam style | you have a virus; order | missed |
| H31 | Battery 28% damaged by a virus | | scam style | battery damaged (a percentage between) | missed |
| H32 | Your iPhone has been infected with viruses | | scam style | your iphone | hidden |
| H33 | Infected! | Fix your device now | scam style | order; your device | hidden |
| H34 | Hacked! | Your Chrome browser has been hacked | scam style | your chrome | missed |
| H35 | WARNING: VIRUS FOUND! | Clean up your phone now | scam style (capitals, hostile) | order; your phone | hidden |
| H36 | Your phone has been hacked! | Press OK to clean it | scam style | your phone; order "press" | hidden |
| H37 | 2 viruses detected | Please remove them now | scam style | count; order after "please" | hidden |
| H38 | (moved to A3, Kevin, Oct 14) | | | | |
| H39 | Your system is heavily damaged by four viruses | | scam style | "your system" is her phone (Kevin, Oct 14) + damaged | missed |
| H40 | Your Samsung needs cleaning | | scam style | your + phone maker + "needs cleaning" (the gate now has it) | missed |
| H41 | Your Motorola has 3 viruses | | scam style | a count at the clause end | missed |
| H42 | Android infected with 13 viruses! | | scam style | a count at the clause end | missed |
| H43 | You may have a virus | | scam style | you may have a virus | missed |
| H44 | You've got a virus | | scam style | you've got a virus | missed |
| H45 | Your battery is 87% damaged | | scam style | battery damaged (the gate now allows words between) | missed |
| H46 | Your phone will be damaged | | scam style | your phone + damaged (the gate now has "will be damaged") | missed |
| H47 | Vіrus found | Tap to remove | hostile (Cyrillic і) | look-alike letters undone; order | missed |
| H48 | 𝐕𝐢𝐫𝐮𝐬 found | Tap to remove | hostile (maths-bold letters) | NFKC; order | missed |
| H49 | V1rus found | Tap to remove | hostile (1 for i) | digits for letters undone; order | missed |
| H50 | Your phone has 3 v1ruses | | hostile (1 for i) | digits for letters undone before the rule; count at the end | missed |
| H51 | Virus found (tap to remove) | | scam style | brackets split clauses; order | missed |
| H52 | Your Xiaomi got hacked | | scam style | your + phone maker + got hacked | hidden |
| H53 | Your system is infected | Clean it now | scam style | your system; order | hidden |
| H54 | Your new Pixel 9 is infected | | scam style | "your <name> is infected" with a phone word in the name | hidden |
| H55 | Your Motorola is infected | | scam style | your + a maker in the phone list (the one-word rule is gone) | hidden |
| H56 | Is your phone slow? It may have a virus | | scam style (asks first) | "it" right after a question about her phone, "may have a virus" | hidden, not told (wrong) |
| H57 | Is your phone acting strange? It is infected with 3 viruses | | scam style (asks first) | "it" + infected; also a count at the clause end | hidden |
| H58 | Phone running hot? It may be damaged | | scam style (asks first, no "your") | a question starting with a phone word, "it may be damaged" (the gate now has it) | nothing (wrong) |
| H59 | Is your phone slow? | Your device may have a virus | scam style (question in the title) | "your device" first in the clause after the question | hidden, not told (wrong) |
| H60 | Is your phone running slow? | It has been hacked | scam style (question in the title) | "it" + hacked | hidden, not told (wrong) |
| H61 | Is your Android slow!? | It might have a virus | scam style (hostile: "!?") | still a question | hidden, not told (wrong) |
| H62 | Is your phone slow? It has a virus | | scam style (asks first) | "it has a virus" | hidden, not told (wrong) |

Not covered (GAP): "300 viruses found in sewage" would be hidden by the count rule; no such headline was found, so it is not a
row. A fake that asks first but puts more than one clause between ("Is your phone slow? Don't worry, it may just
have a virus") or a reporting word before "it" is news to the reader: hidden, the guardian not told (A10). A fake in a script with no Latin look-alike (all-Cyrillic words) is not read.