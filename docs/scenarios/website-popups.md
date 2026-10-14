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
| WP15 | "A site I said Yes to starts saying my phone has a virus" | Still hidden and called fake (must never stay as news) | A note naming the site, masked | "Your device has been hacked", "Battery damaged by virus. Clean now", "Trojan detected on this phone", "You are infected"; site allowed before the list was recorded; "3 viruses found! Remove now", "Virus found", "Malware detected. Remove immediately", "Your Samsung/Galaxy S25/iPhone is infected" (findings #119; the rule is now WP17, findings #126); must-never news stays: "Retailer hacked", "New Android malware found in 40 apps", "Hackers stole card data", "New virus found in bats", "Your favourite retailer was hacked" | Invented words | DetectorsTest#aSiteSheSaidYesToThatWarnsAboutHerPhoneIsStillHidden; DetectorsTest#aSiteSheSaidYesToSayingVirusFoundOrYourPhoneIsInfectedIsStillHidden; DetectorsTest#aNewsHeadlineFromASiteSheSaidYesToIsLeftInPlace | emulator_websites.py | known-site-about-her-phone-still-hidden; scare-words-order; scare-words-count; scare-words-your-one-word |
| WP16 | "A website I never saw before posts 'Retailer hacked'" | Called fake, not asked about (unchanged: "Was that you?" would offer to allow a scare) | Note | n/a | Invented headline | DetectorsTest#aNewSitePostingAScareHeadlineIsStillCalledFake | none | GAP |
| WP17 | "A site I said Yes to sends news with a scare word, or a fake virus warning" | News stays; a fake warning is hidden (the S and H rows below) | Fake: a note naming the site, masked; news: nothing | 33 news rows, 38 fake rows | Real-style headlines and pop-up wordings (below) | DetectorsTest#aNewsStoryFromASiteSheSaidYesToStaysWhereItIs; DetectorsTest#aFakeVirusWarningFromASiteSheSaidYesToIsHidden | none | known-site-news-is-not-a-fake-warning; scare-words-order; scare-words-order-starts-a-clause; scare-words-count; scare-words-your-one-word; scare-words-your-named-phone; scare-words-your-retailer-is-news; scare-words-you-are-infected; scare-words-battery |

## WP17: news or a fake warning? (findings #126)

Only for a website she already had or said Yes to: a scare word (virus, malware, hacked, infected, ...) from such a site is a fake
warning about HER phone (hidden, the guardian gets a note naming the site, masked) or news (left where it is, nothing sent). A site
she never had is called fake on any scare word (WP6, WP16); this table does not change that path. Repeats, snooze, No and the
failure states are those of WP1-WP8 and are unchanged; this table is only about the words.

**The rule (one rule):** it is about her phone when it **speaks to her or her phone, or tells her to act**; a report about someone
else is news. Title and text are read one clause at a time (split at `. ! ? : ; , ( ) [ ] |` and a spaced dash):

- **her phone:** "your" or "this" right before a phone word (phone, device, mobile, android, iphone, galaxy, pixel, tablet, browser,
  chrome, smartphone, cellphone); or "your <name> is / has been / may be / is now ... infected / hacked / compromised / damaged"
  where the name is one word ("your Samsung") or has a phone word or a model number in it ("your Samsung Galaxy A15"). "Your
  favourite retailer" is two words with neither, so it is news.
- **her:** "you are / you're / you have been / you've been infected or hacked", "you have a virus", "you have 3 viruses".
- **an order:** the clause starts (after "please") with tap, click, press, clean, remove, delete, download, install, scan, fix or
  repair. "Google to remove 40 apps" does not start with it; "clean-up" is one word, not "clean".
- **a count of what it found:** a number right before virus/viruses/malware/trojan(s)/spyware, then found or detected
  ("13 viruses found", "(3) viruses were found"); "two new viruses found" and "200 infected" are news.
- **a damaged battery:** "battery" and "damaged" in one clause.

Tests: DetectorsTest#aNewsStoryFromASiteSheSaidYesToStaysWhereItIs (every S row) and
DetectorsTest#aFakeVirusWarningFromASiteSheSaidYesToIsHidden (every H row); each case carries its ID. "Today" is the result on the
code before findings #126 (ABOUT_HER_PHONE as one regex), from running those tests on it.

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
| H20 | Is your phone infected? Here's how to check | | agreed exception: news style, but it speaks to her phone (reviewer and lead agree it stays hidden) | your phone | hidden |
| H21 | Have you been hacked? Here's how to check | | same class as H20 (news style that speaks to her) | you been hacked | missed |
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
| H38 | Delete these apps now: malware found on Google Play | | news style headline that orders her (unsure; kept hidden, since hiding a news story costs less than showing a fake warning) | order "delete" | hidden |

Not covered (GAP): a scare word in look-alike letters ("vіrus" with a Cyrillic і) is not a scare word to `ScareWords.REGEX`, so a
known site's one is left alone (unchanged by #126). "300 viruses found in sewage" would be hidden by the count rule; no such
headline was found, so it is not a row.
