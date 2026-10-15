# Scenarios: scam texts

A text message or chat arrives with a link in it. Aegida reads the link on the phone, and never opens it.
Each row names the test that covers it. `GAP` means nothing covers it yet. `scripts/ScenarioTablesTest` (guards) fails the build
if a named test does not exist. Source of the gaps: [test-review.md](../test-review.md) and findings #97.

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| ST1 | "A text came with a link that is on the scam list" | A warning on Home naming the app; the text stays where it is | A note naming the app and a masked address (two letters, then stars) | Repeat the same day: owner warned, guardian not told again | Real feed file | DetectorsTest#aListedLinkIsAScamLink; CheckRunnerTest#aTextMessageWithAScamLinkIsWarnedAboutButLeftInPlace | emulator_live.py (scam link, back-off) | link-uses-feed; scam-text-not-hidden |
| ST2 | "The link looks like PayPal but a letter is Russian" | Same warning | Same note | Mixed alphabets; all-Cyrillic name is not mixed | Invented names | LinkSignsTest#mixedAlphabetsInOneNameAreALookalike; LinkSignsTest#anAllCyrillicNameIsNotMixed | emulator_live.py (look-alike message) | links-removed-by-the-link-finder |
| ST3 | "The link says paypa1 with a number one" | Warning | Note | Brand word on another domain | Invented names | LinkSignsTest#brandWordsOnOtherDomainsAreLookalikes; DetectorsTest#aLookalikeIsFlaggedEvenWhenNotListed | emulator_live.py (look-alike message) | scam-warning-names-the-app |
| ST4 | "A real bank link" | Nothing | Nothing | The brand's own sites; words that only contain a brand | Invented names | LinkSignsTest#theBrandsOwnSitesAreNot; LinkSignsTest#wordsThatMerelyContainABrandAreNot | none | GAP |
| ST5 | "The link is a shortener (bit.ly)" | Nothing: the real target cannot be read without opening it | Nothing | Shortener on a subdomain | Invented names | DetectorsTest#plainAndShortenedLinksMakeNoFinding; LinkSignsTest#shortenersAreSeenEvenOnSubdomains | emulator_live.py (shared links) | GAP |
| ST6 | "The link is hidden far down a long message" | Warning; the message is cut short and the link is never shown as text | Note | Very long message | Invented text | DetectorsTest#aVeryLongMessageIsCutShortBeforeItIsKept; AdviceLineTest#aLongMessageIsCutShortAndALinkNeverShowsAsText | none | link-scan-uncapped |
| ST7 | "Show me opens the page about the text" | App, time, who it is from, what it said, no link | n/a | Nothing known about the message | Invented text | AdviceLineTest#theAdvicePageSaysTheAppTheTimeWhoItIsFromAndWhatItSaysWithoutTheLink; AdviceLineTest#theAdvicePageStillWorksWhenNothingAboutTheMessageIsKnown | none | scam-address-never-shown-as-text |
| ST8 | "The app that sent it has a sneaky name" | The name is shown in quotes and cannot carry an instruction | A label that is an address reaches the guardian masked | App named like a web address | Invented labels | AdviceLineTest#anAppLabelCannotSmuggleAnInstruction; LinkTextScenariosTest#anAppNamedLikeAWebAddressReachesTheGuardianMasked | none | guardian-label-masks-addresses |
| ST9 | "A link written with odd characters" | Warning; address replaced by the word (link) | Masked address | Zero-width and ideographic dots; ending such as evil.Top | Invented text | LinkTextScenariosTest#aCharacterBrowsersIgnoreIsRemovedSoTheGuardianStillGetsAUsefulMask; LinkTextScenariosTest#onThePhoneARealDomainEndingIsALinkEvenAfterATitle | none | dot-look-alikes-are-dots; site-endings-are-always-links |
| ST10 | "After I looked, the words of the message are gone" | History keeps the app, not the sender and words | n/a | Seen finding pruned later | Room database | HistoryDetailTest#aScamMessageSaysTheAppTheSenderTheWordsWithoutTheLinkAndTheMaskedAddress | none | message-words-cleared-when-seen; seen-findings-pruned |
| ST11 | "A friend's message in a chat app" | Link in a chat message is seen | Note | Chat apps by name | Emulator | CheckRunnerTest#anOrdinaryNotificationChangesNothing | emulator_live.py (chat message) | chat-messages-read |
| ST12 | "Samsung Messages, Google Messages, Samsung Internet text" | Same as any text | Same | Real Samsung notification shapes | Samsung dump needed | GAP | GAP | GAP |
| ST13 | "A real brand link that looks odd (apple.co, paypal.me, amazon.de, google.co.uk)" | Nothing (no false warning) | Nothing | Brand short links (amzn.to, a.co, g.co, youtu.be) and country sites; was warned before findings #108 | The brands' own domains, by name | LinkSignsTest#aRealBrandShortOrCountryLinkIsNotALookalike | none | brand-own-short-and-country-sites |
| ST14 | "An all-Cyrillic look-alike of apple" | Warning | Note | аррӏе.com and аррӏе.рф (palochka for l); was missed before findings #108 | Invented names | LinkSignsTest#anAllCyrillicAppleIsALookalike | none | palochka-reads-as-l |
| ST15 | "A message that arrived while Aegida was not listening" | Checked once when Aegida is bound again (finding #111): the usual warning, late; at most the newest 20; never a second warning for one heard before | The usual note | Listener unbound by an update or low memory; killed or restarted without an unbind recorded: not checked (health H3f) | FakeNotificationShade (Android docs; no emulator trace yet) | HealthScenariosTest#aScamTextThatArrivedWhileAegidaWasNotListeningIsCheckedOnceWhenItIsBoundAgain; HealthScenariosTest#aScamTextSheAlreadyDealtWithIsNotWarnedAboutAgainWhenAegidaIsBoundAgain | GAP | late-check-only-after-the-unbind |
| ST16 | "A link starts with a real brand site but goes somewhere else (amazon.de-login..., paypal.me.verify-account...)" | Warning (must never pass as the brand) | Note | Brand word on an ending not listed (amazon.xyz, paypal.ru, apple.com.co) | Invented names | LinkSignsTest#aBrandWordOnAnUnlistedEndingOrBeforeAnotherDomainIsStillALookalike | none | GAP |
| ST17 | "The words pop-ups or sign-ups in a link or a sentence" | Nothing (not the parcel company) | Nothing | pop-ups.com, "blocked pop-ups.Tap", check-ups; was warned before findings #108 | Invented names | LinkSignsTest#theWordPopUpsIsNotTheParcelCompany | none | ups-in-an-english-word-is-not-the-brand |
| ST18 | "A parcel scam with ups next to a hyphen (ups-redelivery, track-ups)" | Warning | Note | ups after another ups (pop-ups-ups); an English "-ups" word with more after it (pick-ups-redelivery, set-ups-redelivery: the "-ups" word is excused only when nothing in the name is about parcels, ST19 and findings #127); pop-ups, sign-ups, check-ups, pick-ups alone stay quiet | Invented names | LinkSignsTest#upsBeforeOrAfterAHyphenIsStillTheParcelCompany; LinkSignsTest#aParcelScamWithPickUpsOrSetUpsBeforeMoreWordsStillImitatesUps | none | ups-parcel-words-keep-the-brand |
| ST19 | "A parcel scam name with ups inside it, or an ordinary name with ups in it" | Scam (U rows): a look-alike link warning (any brand sign alone is enough: LinkDetector turns BrandLookalike into Problem.LookalikeLink); ordinary (O rows): nothing | Scam: a note, as ST18; ordinary: nothing | the U and O rows below | Invented names in the shapes of reported parcel-scam domains | LinkSignsTest#aParcelScamNameWithUpsInItImitatesUps; LinkSignsTest#anOrdinaryNameWithUpsInItIsNotTheParcelCompany | none | ups-in-an-english-word-is-not-the-brand; ups-parcel-words-keep-the-brand; ups-joined-to-a-parcel-word; ups-and-downs-is-not-the-brand; ups-parcel-word-in-the-same-label; ups-joined-at-either-end; ups-with-a-number; ups-own-supply-chain-site; ups-english-words-starting-ups; ups-english-words-ending-ups; ups-touch-ups |

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
  shipment, courier, express, mychoice);
- in a name with a parcel word, "ups" joined to either end of a word is the brand too (myups-redelivery, upsglobal-track,
  pushups-tracking), unless the word is English: ending in "ups" (cups, groups, soups, ...) or starting with "ups" (upstate, upset,
  upstairs, upstream, upside, ...);
- "ups-and-downs" is never the brand.
UPS's own sites are never warned: ups.com, ups-scs.com and theupsstore.com and their subdomains (Kevin, Oct 14). "Today" is the
result before #127 for U1-U19 and O1-O19, and before #133 for the rows #133 added.

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

Unsure, and decided toward warning (a wrong warning costs a tap on "No"; a missed parcel scam can cost a card number):
"pick-ups-delivery" (a takeaway) and "backups-shipping" would be warned, since both carry a parcel word. Not a row: no such names
were found.
