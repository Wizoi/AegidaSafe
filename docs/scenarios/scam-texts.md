# Scenarios: scam texts

A text message or chat arrives with a link in it. Aegida reads the link on the phone, and never opens it.
Each row names the test that covers it. `GAP` means nothing covers it yet. `ScenarioTablesTest` (in `:guards`) fails the build
if a named test, mutation check or emulator script does not exist. Source of the gaps: [test-review.md](../test-review.md) and findings #97.

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| ST1 | "A text came with a link that is on the scam list" | A warning on Home naming the app; the text stays where it is | A note naming the app and a masked address (two letters, then stars) | Repeat the same day: owner warned, guardian not told again | Real feed file | DetectorsTest#aListedLinkIsAScamLink; CheckRunnerTest#aTextMessageWithAScamLinkIsWarnedAboutButLeftInPlace | emulator_live.py (scam link, back-off) | link-uses-feed; scam-text-not-hidden |
| ST2 | "The link looks like PayPal but a letter is Russian" | Same warning | Same note | Mixed alphabets; all-Cyrillic name is not mixed | Invented names | LinkSignsTest#mixedAlphabetsInOneNameAreALookalike; LinkSignsTest#anAllCyrillicNameIsNotMixed | emulator_live.py (look-alike message) | links-removed-by-the-link-finder |
| ST3 | "The link says paypa1 with a number one" | Warning | Note | Brand word on another domain | Invented names | LinkSignsTest#brandWordsOnOtherDomainsAreLookalikes; DetectorsTest#aLookalikeIsFlaggedEvenWhenNotListed | emulator_live.py (look-alike message) | scam-warning-names-the-app |
| ST4 | "A real bank link" | Nothing | Nothing | The brand's own sites; words that only contain a brand | Invented names | LinkSignsTest#theBrandsOwnSitesAreNot; LinkSignsTest#wordsThatMerelyContainABrandAreNot | none | brand-own-subdomain-not-flagged |
| ST5 | "The link is a shortener (bit.ly)" | Nothing: the real target cannot be read without opening it | Nothing | Shortener on a subdomain | Invented names | DetectorsTest#plainAndShortenedLinksMakeNoFinding; LinkSignsTest#shortenersAreSeenEvenOnSubdomains | emulator_live.py (shared links) | shortener-seen-on-subdomain |
| ST6 | "The link is hidden far down a long message" | Warning; the message is cut short and the link is never shown as text | Note | Very long message | Invented text | DetectorsTest#aVeryLongMessageIsCutShortBeforeItIsKept; AdviceLineTest#aLongMessageIsCutShortAndALinkNeverShowsAsText | none | link-scan-uncapped |
| ST7 | "Show me opens the page about the text" | App, time, who it is from, what it said, no link | n/a | Nothing known about the message | Invented text | AdviceLineTest#theAdvicePageSaysTheAppTheTimeWhoItIsFromAndWhatItSaysWithoutTheLink; AdviceLineTest#theAdvicePageStillWorksWhenNothingAboutTheMessageIsKnown | none | scam-address-never-shown-as-text |
| ST8 | "The app that sent it has a sneaky name" | The name is shown in quotes and cannot carry an instruction | A label that is an address reaches the guardian masked | App named like a web address | Invented labels | AdviceLineTest#anAppLabelCannotSmuggleAnInstruction; LinkTextScenariosTest#anAppNamedLikeAWebAddressReachesTheGuardianMasked | none | guardian-label-masks-addresses |
| ST9 | "A link written with odd characters" | Warning; address replaced by the word (link) | Masked address | Zero-width and ideographic dots; ending such as evil.Top | Invented text | LinkTextScenariosTest#aCharacterBrowsersIgnoreIsRemovedSoTheGuardianStillGetsAUsefulMask; LinkTextScenariosTest#onThePhoneARealDomainEndingIsALinkEvenAfterATitle | none | dot-look-alikes-are-dots; site-endings-are-always-links |
| ST10 | "After I looked, the words of the message are gone" | History keeps the app, not the sender and words | n/a | Seen finding pruned later | Room database | HistoryDetailTest#aScamMessageSaysTheAppTheSenderTheWordsWithoutTheLinkAndTheMaskedAddress | none | message-words-cleared-when-seen; seen-findings-pruned |
| ST11 | "A friend's message in a chat app" | Link in a chat message is seen | Note | Chat apps by name | Emulator | CheckRunnerTest#anOrdinaryNotificationChangesNothing | emulator_live.py (chat message) | chat-messages-read |
| ST12 | "Samsung Messages, Google Messages, Samsung Internet text" | Same as any text | Same | Real Samsung notification shapes | Samsung dump needed | needs a Samsung (real Samsung Messages, Google Messages on a Samsung and Samsung Internet notification shapes) | GAP | needs a Samsung (no test yet) |
| ST13 | "A real brand link that looks odd (apple.co, paypal.me, amazon.de, google.co.uk)" | Nothing (no false warning) | Nothing | Brand short links (amzn.to, a.co, g.co, youtu.be) and country sites; was warned before findings #108 | The brands' own domains, by name | LinkSignsTest#aRealBrandShortOrCountryLinkIsNotALookalike | none | brand-own-short-and-country-sites |
| ST14 | "An all-Cyrillic look-alike of apple" | Warning | Note | аррӏе.com and аррӏе.рф (palochka for l); was missed before findings #108 | Invented names | LinkSignsTest#anAllCyrillicAppleIsALookalike | none | palochka-reads-as-l |
| ST15 | "A message that arrived while Aegida was not listening" | Checked once when Aegida is bound again (finding #111): the usual warning, late; at most the newest 20; never a second warning for one heard before | The usual note | Listener unbound by an update or low memory; stopped without being told: the notices posted after the last one it heard are checked once (health H3f); a new install checks none (health H3g) | FakeNotificationShade (Android docs; no emulator trace yet) | HealthScenariosTest#aScamTextThatArrivedWhileAegidaWasNotListeningIsCheckedOnceWhenItIsBoundAgain; HealthScenariosTest#aScamTextSheAlreadyDealtWithIsNotWarnedAboutAgainWhenAegidaIsBoundAgain | emulator_listener.py (step 1) | late-check-only-after-the-unbind |
| ST16 | "A link starts with a real brand site but goes somewhere else (amazon.de-login..., paypal.me.verify-account...)" | Warning (must never pass as the brand) | Note | Brand word on an ending not listed (amazon.xyz, paypal.ru, apple.com.co) | Invented names | LinkSignsTest#aBrandWordOnAnUnlistedEndingOrBeforeAnotherDomainIsStillALookalike | none | brand-domain-must-end-the-host |
| ST17 | "The words pop-ups or sign-ups in a link or a sentence" | Nothing (not the parcel company) | Nothing | pop-ups.com, "blocked pop-ups.Tap", check-ups; was warned before findings #108 | Invented names | LinkSignsTest#theWordPopUpsIsNotTheParcelCompany | none | ups-in-an-english-word-is-not-the-brand |
| ST18 | "A parcel scam with ups next to a hyphen (ups-redelivery, track-ups)" | Warning | Note | ups after another ups (pop-ups-ups); an English "-ups" word with more after it (pick-ups-redelivery, set-ups-redelivery: the "-ups" word is excused only when nothing in the name is about parcels, ST19 and findings #127); pop-ups, sign-ups, check-ups, pick-ups alone stay quiet | Invented names | LinkSignsTest#upsBeforeOrAfterAHyphenIsStillTheParcelCompany; LinkSignsTest#aParcelScamWithPickUpsOrSetUpsBeforeMoreWordsStillImitatesUps | none | ups-parcel-words-keep-the-brand |
| ST19 | "A parcel scam name with ups inside it, or an ordinary name with ups in it" | Scam (U rows): a look-alike link warning (any brand sign alone is enough: LinkDetector turns BrandLookalike into Problem.LookalikeLink); ordinary (O rows): nothing | Scam: a note, as ST18; ordinary: nothing | the U and O rows below | Invented names in the shapes of reported parcel-scam domains | LinkSignsTest#aParcelScamNameWithUpsInItImitatesUps; LinkSignsTest#anOrdinaryNameWithUpsInItIsNotTheParcelCompany | none | ups-in-an-english-word-is-not-the-brand; ups-parcel-words-keep-the-brand; ups-joined-to-a-parcel-word; ups-and-downs-is-not-the-brand; ups-parcel-word-in-the-same-label; ups-joined-at-either-end; ups-with-a-number; ups-own-supply-chain-site; ups-english-words-starting-ups; ups-english-words-ending-ups; ups-touch-ups; ups-express-joined-to-ups; ups-express-alone-is-not-parcels |
| ST20 | "My daughter sent a share.google link" (Google's own share links) | Nothing | Nothing | store.apple, about.amazon, news.microsoft: a brand's own top-level domain is the brand's site (9 of the 21 brand words are IANA endings) | Kevin's Pixel 11, 0.3.10: `Link:LookalikeLink:share.google` on two messages from his wife (findings #155) | LinkSignsTest#aBrandsOwnTopLevelDomainIsTheBrandsSite | none | brand-own-ending-owned |
| ST21 | "A link with the brand's ending in the middle (google.evil.com, share.google.evil.com)" | Warning (must never pass as the brand) | Note | apple.verify-id.example | Invented names | LinkSignsTest#aBrandTopLevelDomainUsedAsASubdomainIsStillALookalike | none | brand-ending-only-as-last-label |
| ST22 | "An ending that only looks like the brand's (share.goog1e), or another brand on Google's ending (paypal.google)" | Warning | Note | Look-alike letters in the ending | Invented names | LinkSignsTest#aLookAlikeOfABrandEndingIsNotTheBrand | none | brand-ending-as-written |
| ST23 | "A real look-alike (paypa1-help.com, google-security.com)" | Warning (must never be quiet) | Note | | Invented names | LinkSignsTest#realLookalikesAreStillWarnedAbout; DetectorsTest#aLookalikeIsFlaggedEvenWhenNotListed | none | brand-own-subdomain-not-flagged |
| ST24 | "I said the link is fine, and the same website comes again" | Nothing for that very name; another name on its domain (login.paypa1-help.com) or a look-alike of it (paypa1-he1p.com) is still warned | Nothing; the others: the usual note | The same message twice | Invented names | LinkDetectorScenariosTest#onlyTheWebsiteSheSaidIsFineIsQuiet; EvaluatorTest#aSiteSheSaidIsFineIsNotALookalikeAgainButTheScamListStillWarns | none | allowed-site-skips-lookalike; allowed-site-exact-name |
| ST25 | "An app on my list has the same name as a website" | The app entry does not quiet the website | As usual | | Invented names | EvaluatorTest#anAllowedAppDoesNotQuietALinkWithTheSameName | none | allowed-sites-only-links |
| ST26 | "A website I said is fine turns up on the scam list" | The scam warning, as always (must never be quiet) | The usual scam note | | Real feed shape | LinkDetectorScenariosTest#aSiteSheSaidIsFineIsStillWarnedWhenItIsOnTheScamList; EvaluatorTest#aSiteSheSaidIsFineIsNotALookalikeAgainButTheScamListStillWarns | none | scam-list-before-allowed-site |
| ST27 | "Show me on a look-alike warning" | The message, what to do, Got it, and "This link is fine" | n/a | Light and dark, 200% font | Invented text | AdvicePageTest#aLookalikeLinkWarningOffersThisLinkIsFine | none | none fits (a button left out is caught by the test, not a rule) |
| ST28 | "Show me on a scam-list warning" | Got it only: never "This link is fine", and the action refuses it from any path (must never) | Nothing new | | Invented text | AdvicePageTest#aLinkOnTheScamListNeverOffersThisLinkIsFine; LinkIsFineTest#aLinkOnTheScamListCanNeverBeSaidToBeFine | none | scam-link-never-allowable |
| ST29 | "I tap This link is fine" | The warning closes. History: "You said a link is fine" with "The link went to paypal-help.com." My settings lists "Links to paypal-help.com" (plain words, not a link) with Warn me again. A second tap does nothing more | "Ruth told Aegida that a link to a website (pa*****.com) is fine, so Aegida will stop warning that it pretends to be a company. If that surprises you, it may be a good time to ask Ruth." Never the whole address | Coached by a scammer: the guardian is always told | Invented names | LinkIsFineTest#sayingALookalikeLinkIsFinePutsTheSiteOnTheListAndTellsTheGuardianOnlyAMaskedAddress; SettingsScreenTest#aWebsiteSheSaidIsFineIsListedByNameAndTakingItBackIsOneTap | none | site-allowed-note-masked; site-allowed-tells-guardian |
| ST30 | "I tap This link is fine with no internet" | Same as ST29 | The note is queued as its own kind and sent later | Killed half way after the note but before the list is written: the warning is still open, so a second tap sends a second note (the guardian is never left untold, as for apps) | Fake outbox | LinkIsFineTest#theLinkIsFineNoteIsQueuedWhenOfflineAsItsOwnKind | none | site-allowed-tells-guardian |
| ST31 | "Alerts to my guardian are off and I tap This link is fine" | Same as ST29 | Nothing | | Invented names | LinkIsFineTest#withAlertsOffTheLinkIsFineStillWorksAndNothingIsSent | none | none fits (the opt-in is guarded by pipeline-guardian-opt-in) |
| ST32 | "Warn me again" on a website in My settings | It leaves the list; the next message with that link warns at once | Taking it back is not sent (as for apps); the next warning's note is sent as usual | | Invented names | LinkIsFineTest#warnMeAgainForAWebsiteTakesItOffTheListAndTheNextLinkWarns | none | allowed-site-skips-lookalike |
| ST33 | "I picked a different guardian" after saying a link is fine | The list stays as it is | The new guardian is not told about earlier answers | | n/a | accepted (the list holds no guardian, as for apps, and the note goes to whoever is the guardian when she taps) | none | none fits (nothing to break) |
| ST34 | "A link with a Russian letter hidden in a Latin name (pаypal.com)" | The warning has Got it only; "This link is fine" is never offered, and the action refuses it (must never) | Nothing new | A scammer on the phone coaching her to allow it; the brand in plain letters with a Russian letter elsewhere (paypal-sеcure.com) | Invented names | LinkIsFineTest#aNameThatMixesAlphabetsCanNeverBeSaidToBeFine | none | other-alphabet-never-allowed |
| ST35 | "I said paypa1-help.com is fine, then a link to www.paypa1-help.com comes" | Warned again, with the button again: only the very name is on the list | The usual note | Two look-alike names in one message: each is its own warning, and only the one she said is fine goes on the list | Invented names | LinkDetectorScenariosTest#onlyTheWebsiteSheSaidIsFineIsQuiet | none | allowed-site-exact-name |
| ST36 | "A website name in another alphabet on my list" | Cannot happen: only a name in plain Latin letters can be said to be fine (ST37), so the list never holds one | n/a | | n/a | accepted (the list can only ever hold plain-letter names, ST34 and ST37) | none | none fits (covered by other-alphabet-never-allowed) |
| ST37 | Must never: "A website name all in Russian letters that reads as Apple (аррӏе.com, written xn--80ak6aa92e.com), or paypal in front of it" | Got it only; "This link is fine" is never offered, and the action refuses it | Nothing new | Any name with a letter outside plain a-z (a whole-alphabet look-alike is not "mixed", so ST34 alone missed it; findings #156) | Invented names | LinkIsFineTest#aNameAllInAnotherAlphabetCanNeverBeSaidToBeFine | none | other-alphabet-never-allowed; letter-swap-never-allowed |
| ST38 | Must never: "A brand spelled with a number or swapped letters (paypa1-help.com, rnicrosoft-support.com)" | Got it only; "This link is fine" is never offered, and the action refuses it | Nothing new | A scammer coaching her: "tap This link is fine" | Invented names | LinkIsFineTest#aBrandSpelledWithSwappedLettersCanNeverBeSaidToBeFine | none | letter-swap-never-allowed |
| ST39 | "A brand's name in plain letters on another site (paypal-help.com, google-blog.example)" | "This link is fine" is offered: this is the false alarm the button is for (share.google before #155) | The usual note; the masked note when she taps it (ST29) | | Invented names | LinkIsFineTest#sayingALookalikeLinkIsFinePutsTheSiteOnTheListAndTellsTheGuardianOnlyAMaskedAddress; AdvicePageTest#aLookalikeLinkWarningOffersThisLinkIsFine | none | plain-brand-word-may-be-allowed |

Decided for ST24-ST33 (findings #155): the list holds the very name that was warned about (`paypal-help.com`), not its registrable
domain. Aegida has no public-suffix list, so "the domain" of `a.b.co.uk` cannot be worked out safely, and a wider entry would quiet
names she never saw. Only a look-alike warning (`Problem.LookalikeLink`, a guess Aegida can get wrong) offers "This link is fine"; a
link on the scam list (`Problem.ScamLink`) never does, nor a name with any letter outside plain a-z (ST34, ST37), nor a brand spelled with swapped letters (ST38), and a later
scam-list match for an allowed name still warns.

Decided for ST37-ST39 (findings #156): "This link is fine" is offered only when the brand's name is in the link as written, in plain
letters (`LinkSigns.namesBrandPlainly`). The false alarms the button is for are a brand's real word on a site Aegida does not know
(share.google before #155, a shop's "google-reviews" page). No ordinary site spells a brand with a number for a letter (paypa1),
"rn" for "m", or letters of another alphabet: those are made to fool her, so they are the ones a scammer would coach her to allow,
and the guardian, who sees only a masked address, could not tell. The rule lives in one place, `FindingKey.mayBeAllowed` (with `Problem.allowableFrom`).

## ST19: is "ups" the parcel company? (findings #127)

Only text messages and chats are link-checked (a website's own notification is not, WP13). The UPS brand sign alone makes a
warning: `LinkDetector` turns `BrandLookalike` into `Problem.LookalikeLink`, so "warned" below is what Mom sees (a look-alike link
warning, the guardian gets a note) and "nothing" means no warning at all. ups.com and its subdomains are UPS's own and never warned.

**The rule (one rule):** each name between dots is split into words at hyphens; look-alike letters are undone first (up5 -> ups,
parce1 -> parcel; the Cyrillic/Greek map is the shared one in `LookAlikes`). "ups" joined to a parcel word ("upsdelivery") reads as
"ups" and that word, and "ups" with a number ("ups24") is the brand. Then, **within that one name between dots** (Kevin, Oct 14: a
parcel word in another label does not count, so track.check-ups-clinic.com is a clinic):
- "ups" ending an English "-ups" word (pop-ups, check-ups, sign-ups, pick-ups, set-ups, touch-ups, mash-ups, ...) is excused unless
  the name has a parcel word (track, tracking, delivery, deliveries, redelivery, deliver, redeliver, parcel(s), package(s), shipping,
  shipment, courier, mychoice; "express" only when joined to "ups", as in upsexpress-help: findings #138);
- in a name with a parcel word, "ups" joined to either end of a word is the brand too (myups-redelivery, upsglobal-track,
  pushups-tracking), unless the word is English: ending in "ups" (cups, groups, soups, ...) or starting with "ups" (upstate, upset,
  upstairs, upstream, upside, ...);
- "ups-and-downs" is never the brand.
UPS's own sites are never warned: ups.com, ups-scs.com and theupsstore.com and their subdomains (Kevin, Oct 14). "Today" is the
result before #127 for U1-U19 and O1-O19, before #133 for the rows #133 added, and before #138 for U27-U29 and O30-O32.

| ID | Host | Kind | Mom sees | Why | Today |
|---|---|---|---|---|---|
| U1 | ups-redelivery.com | parcel-scam style | warned | ups stands alone | warned |
| U2 | track-ups.com | parcel-scam style | warned | ups after a parcel word | warned |
| U3 | pick-ups-redelivery.com | parcel-scam style | warned | "pick-ups", but "redelivery" | warned |
| U4 | track-pick-ups.com | parcel-scam style | warned | "pick-ups", but "track" | nothing (wrong) |
| U5 | parcel-pick-ups.com | parcel-scam style | warned | "pick-ups", but "parcel" | nothing (wrong) |
| U6 | ups-parcel-help.com | parcel-scam style | warned | ups first | warned |
| U7 | upsdelivery-help.com | parcel-scam style | warned | ups joined to "delivery" | nothing (wrong) |
| U8 | pickups-redelivery.com | parcel-scam style | warned | "pickups" read as pick ups; "redelivery" | nothing (wrong) |
| U9 | ups-track.info | parcel-scam style | warned | ups first | warned |
| U10 | ups.com-track.top | parcel-scam style (the brand before another domain) | warned | "ups" is a whole name; not ups.com | warned |
| U11 | ups.parcel-fee.example | parcel-scam style | warned | "ups" is a whole name | warned |
| U12 | pop-ups-ups.example | hostile (an English word, then the brand) | warned | the second ups follows "ups", not an English root | warned |
| U13 | set-ups-redelivery.example | parcel-scam style | warned | "redelivery" | warned |
| U14 | www.check-ups-parcel.example | parcel-scam style | warned | "parcel" | warned |
| U15 | upstracking-center.com | parcel-scam style | warned | ups joined to "tracking" | nothing (wrong) |
| U16 | upsparcel-redeliver.com | parcel-scam style | warned | ups joined to "parcel" | nothing (wrong) |
| U17 | up5-redelivery.top | hostile (5 for s) | warned | up5 reads as ups | warned |
| U18 | pick-ups-parce1.top | hostile (1 for l) | warned | parce1 reads as parcel | warned |
| U19 | ups-tracking.help-center.top | parcel-scam style | warned | ups first | warned |
| U20 | myups-redelivery.com | parcel-scam style | warned | "ups" joined at the end, "redelivery" in the same name | nothing (wrong) |
| U21 | upsmychoice-update.com | parcel-scam style (UPS My Choice) | warned | "ups" joined to "mychoice" | nothing (wrong) |
| U22 | upsglobal-track.com | parcel-scam style | warned | "ups" joined at the start, "track" in the same name | nothing (wrong) |
| U23 | upsexpress-help.com | parcel-scam style | warned | "ups" joined to "express" | nothing (wrong) |
| U24 | upsdeliveries.com | parcel-scam style | warned | "ups" joined to "deliveries" | nothing (wrong) |
| U25 | ups24.top | parcel-scam style | warned | "ups" with a number | nothing (wrong) |
| U26 | pushups-tracking.com | unsure, decided toward warning (a fitness tracker or a parcel scam: the words cannot tell) | warned | "tracking" in the same name as "pushups"; a wrong warning costs a tap on No | warned |
| U27 | express-ups.com | parcel-scam style | warned | "ups" stands alone after a word that is not an English "-ups" root | warned |
| U28 | pop-ups-express-delivery.com | parcel-scam style | warned | "delivery" is a parcel word, so "pop-ups" is not excused | warned |
| U29 | ups-express-help.com | parcel-scam style | warned | "ups" first | warned |
| O1 | pop-ups-blocker.com | ordinary (a browser add-on) | nothing | "pop-ups", nothing about parcels | warned (wrong) |
| O2 | check-ups-clinic.com | ordinary (a doctor's text) | nothing | "check-ups", nothing about parcels | warned (wrong) |
| O3 | sign-ups-form.com | ordinary | nothing | "sign-ups" | warned (wrong) |
| O4 | setups.com | ordinary | nothing | "setups" read as set ups | nothing |
| O5 | startups.com | ordinary | nothing | "startups" read as start ups | nothing |
| O6 | groups.google.com | ordinary (Google's own site) | nothing | "groups" is not split (gro is not a root); google.com is Google's | nothing |
| O7 | cups.com | ordinary | nothing | "cups" is not split | nothing |
| O8 | pickups-trucks.com | ordinary (a truck dealer) | nothing | "pickups", and trucks are not parcels | nothing |
| O9 | www.pop-ups.com | ordinary | nothing | "pop-ups" | nothing |
| O10 | pick-ups.example | ordinary | nothing | "pick-ups" alone | nothing |
| O11 | sign-ups.example | ordinary | nothing | "sign-ups" | nothing |
| O12 | check-ups.example | ordinary | nothing | "check-ups" | nothing |
| O13 | upstairs-furniture.com | ordinary | nothing | "upstairs" is one word | nothing |
| O14 | upset-stomach-help.com | ordinary | nothing | "upset" is one word | nothing |
| O15 | backups-online.com | ordinary | nothing | "backups" read as back ups | nothing |
| O16 | follow-ups-reminder.com | ordinary (a doctor's reminder) | nothing | "follow-ups" | warned (wrong) |
| O17 | www.ups.com/track | UPS's own site | nothing | ups.com | nothing |
| O18 | wwwapps.ups.com | UPS's own site | nothing | a subdomain of ups.com | nothing |
| O19 | pop-ups.check-ups.example | ordinary | nothing | two English "-ups" words | nothing |
| O20 | ups-and-downs-therapy.com | ordinary (a counsellor) | nothing | "ups-and-downs" | warned (wrong) |
| O21 | touch-ups-paint.com | ordinary (a decorator) | nothing | "touch-ups" is an English "-ups" word | warned (wrong) |
| O22 | track.check-ups-clinic.com | ordinary (a clinic's tracking subdomain) | nothing | "track" is in another name between dots (Kevin, Oct 14) | warned (wrong) |
| O23 | ups-scs.com | UPS's own (supply chain) | nothing | owned by UPS (Kevin, Oct 14) | warned (wrong) |
| O24 | www.theupsstore.com | UPS's own (The UPS Store) | nothing | owned by UPS (Kevin, Oct 14) | nothing |
| O25 | upstate-shipping.com | ordinary (a shipping firm upstate) | nothing | "upstate" is English, though "shipping" is a parcel word | nothing |
| O26 | groups-tracking.com | ordinary | nothing | "groups" is English | nothing |
| O27 | cups-delivery.com | ordinary (a coffee shop) | nothing | "cups" is English | nothing |
| O28 | lineups.com | ordinary (sports) | nothing | "lineups", nothing about parcels | nothing |
| O29 | mash-ups.com | ordinary (music) | nothing | "mash-ups" is an English "-ups" word | warned (wrong) |
| O30 | pop-ups-express.com | ordinary (a pop-up shop) | nothing | "express" alone is not a parcel word (findings #138) | warned (wrong) |
| O31 | sign-ups-express.example | ordinary | nothing | as O30 | warned (wrong) |
| O32 | pickups-express.com | ordinary (a truck dealer) | nothing | as O30; "pickups" alone is English | warned (wrong) |

Unsure, and decided toward warning (a wrong warning costs a tap on "No"; a missed parcel scam can cost a card number):
"pick-ups-delivery" (a takeaway) and "backups-shipping" would be warned, since both carry a parcel word.
Decided toward quiet (findings #138): "express" alone is in many ordinary names (express shops, lanes, pharmacies), so a parcel
scam named "pop-ups-express" or "pickups-express" is missed; "express" still counts when joined to "ups" (U23), and "ups" alone
next to it is still warned (U27, U29). Not a row: no such names
were found.

## PS: popular sites are never look-alikes (findings #155 part 3)

The signed feed carries the 10,000 most-linked sites of the Majestic Million (CC BY 3.0). A site whose host, or a parent
domain of it, is on that list is never called a look-alike; only the part in front of it is still read for brand words and
alphabets, while whose site it is comes from the whole host (findings #159). The scam list still wins. The build leaves off the list
any name the look-alike rule flags, unless it is a brand's own reviewed site (findings #157), and builds without the list when the
Majestic file is missing or broken (findings #158).

Decided for PS10 (findings #157): a reviewed list rather than leaving out every flagged name. Leaving out every flagged name would
make the list do nothing: a popular name the rule does not flag needs no list, and the front-part reading already finds what it
would. share.google needs nothing from the list: a brand's own top-level domain is the brand's (ST20). The Oct 18 count over the top
10,000: 188 names flagged as look-alikes (18 more are shorteners, which the look-alike rule ignores), every one a brand's own: 179
google.* sites, maps.google.at, amazon-adsystem.com, media-amazon.com, m.media-amazon.com, images-amazon.com,
ssl-images-amazon.com, amazon.cn, amazon.jobs, apple.com.cn.

Decided for PS7 (findings #158): the popular list is used when at least nine in ten of the top 10,000 rows are good, distinct host
names; fewer, or no Domain column, leaves it out for that day. A short list only means fewer quiet sites, never a wrong warning.

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| PS1 | "A link my daughter sent, share.google" (the field report) | Nothing | Nothing | Its own subdomains (www.share.google, m.media-amazon.com) too | Majestic Million, Oct 18 (share.google ranks 162) | LinkDetectorPopularSitesTest#aGoogleShareLinkIsNotALookalikeWhenTheSiteIsPopular; LinkDetectorPopularSitesTest#anAmazonImageLinkIsNotALookalikeWhenTheSiteIsPopular; LinkDetectorPopularSitesTest#aSubdomainOfAPopularSiteIsNotALookalike | none | popular-site-not-a-lookalike |
| PS2 | Must never: "PayPal" in front of a popular host (paypal-login.sites.google.com) | The look-alike warning, as today | The note, as today | Mixed alphabets in front of it (Cyrillic "раypal") | Invented names on real popular hosts | LinkDetectorPopularSitesTest#aBrandNameInFrontOfAPopularSiteIsStillALookalike; LinkDetectorPopularSitesTest#mixedAlphabetsInFrontOfAPopularSiteAreStillALookalike | none | popular-site-front-part-still-read |
| PS3 | Must never: a popular site that is on the scam list | The scam warning | The note |  | Invented | LinkDetectorPopularSitesTest#aPopularSiteOnTheScamListIsStillAScamLink | none | link-uses-feed |
| PS4 | Hostile: a popular name with a look-alike letter, or inside another address (media-amaz0n.com, media-amazon.com.refund-help.example) | The look-alike warning | The note |  | Invented | LinkDetectorPopularSitesTest#aLookAlikeLetterVersionOfAPopularSiteIsStillALookalike; LinkDetectorPopularSitesTest#aPopularSiteNameInsideAnotherAddressIsStillALookalike | none | popular-site-not-a-lookalike |
| PS5 | Old feed (built before the list) or no feed yet (offline since install) | Exactly as today | Exactly as today | A site that drops out of the top 10,000 goes back to today's rule | Invented | LinkDetectorPopularSitesTest#anOldFeedWithoutThePopularListBehavesAsToday; LinkDetectorPopularSitesTest#noFeedAtAllBehavesAsToday | none | popular-site-not-a-lookalike |
| PS6 | Phone on the released 0.3.10 gets a feed with the list | The new feed is used as before; the list is never read as scam sites | Nothing new | The list rides in the host hashes under its own prefix, no new section | Real excerpts (Majestic, Phishing.Database, Echap) | PopularSitesFeedTest#theReleasedDecoderReadsAFeedWithThePopularList; PopularSitesFeedTest#aPopularSiteIsNeverAScamMatch | none | popular-entries-kept-apart |
| PS7 | The daily build gets a missing, short, changed or junk Majestic file | The scam lists still update; the feed comes without the popular list, so phones use today's rule (PS5) for a day | Nothing | Download failed, layout changed, fewer than 9,000 good host names in the top 10,000 rows, rows that are not host names; the job log says why in a warning (findings #158). Empty scam lists still refuse the whole build, so the last good feed stays | Real Majestic excerpt, cut | PopularSitesFeedTest#aBrokenPopularListIsLeftOutAndTheFeedStillBuilds; PopularSitesFeedTest#aRowThatIsNotAHostNameIsNotCounted; PopularSitesFeedTest#scamEntriesLeaveThePopularSitesOut | none | popular-list-skipped-when-short; build-counts-scam-entries-only |
| PS8 | Only the top 10,000 go in |  |  |  | Real Majestic excerpt | PopularSitesFeedTest#theTopMostLinkedSitesGoInAndNoMore; PopularSitesFeedTest#theRealSitesOfTheFieldReportAreOnTheList | none | popular-list-full-or-refused |
| PS9 | "A link to Apple's own icloud.apple.com" when apple.com is on the popular list | Nothing (as before the list) | Nothing | appleid.apple.com, maps.google.com under a popular google.com | Invented paths on real hosts | LinkDetectorPopularSitesTest#aBrandsOwnNameUnderItsPopularSiteIsNotALookalike | none | popular-site-owner-is-the-real-host |
| PS10 | Must never: a look-alike climbs into the top 10,000 (facebook-mypage.ru is at 70,427 on Oct 18) | The look-alike warning, as today: the build leaves it off the popular list | The note, as today | Any name the brand or mixed-alphabet rule flags, unless it is on the reviewed list of brands' own sites (179 Google country sites, google.dev/.org/.cat, maps.google.at, 7 Amazon sites, apple.com.cn: all 188 hits in the Oct 18 top 10,000); the job log names each one left out (findings #157) | Real Majestic excerpt plus one invented look-alike row | PopularSitesFeedTest#aLookalikeInTheTopListIsLeftOutAndNamed; PopularSitesFeedTest#theBrandsOwnReviewedSitesStayOnTheList | none | popular-lookalikes-left-out; popular-reviewed-brand-sites-kept |
