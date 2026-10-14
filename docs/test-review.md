# Test review by domain area (Oct 11, 2026)

By an Opus 5.5 high agent at 1c43aa8 (73 test files, about 640 tests; ScreensTest runs four times for themes and font sizes).
Kevin: "Tests should be designed to cover the problem domain, not just validate the code change." See CLAUDE.md "Scenarios before code".

## Areas and what they cover

| Area | Main test classes | Covered, in Mom's terms |
|---|---|---|
| Scam links in messages | LinksTest, LinkSignsTest, DetectorsTest, AdviceLineTest, CheckRunnerTest; emulator_live | listed link, Cyrillic look-alike, "paypa1", link hidden far down, shorteners left alone, app named, link never shown as text |
| Website notifications | PopupSpamDetectorTest, WebsiteBaselineTest, CheckOutcomeTest, HostMaskTest; emulator_websites | fake virus pop-up hidden, new site asked once, Yes remembers, snoozed not deleted, masked for the guardian |
| Apps sending ads as notices | NoticeRulesTest, NoisyAppDetectorTest, EvaluatorTest; emulator_noisy_app | chats/calls/system never counted, bank/news/pharmacy score 0, 3 in an hour flags, allow list, Warn me again |
| Phone changes | DriftDetectorTest, AppDetectorsTest, StalkerwareDetectorTest, AndroidOemSourceTest | default apps changed, sideloaded app, remote-control and spying apps, Auto Blocker step only where present |
| Aegida's own health | DetectorsTest (health), DeliveryDetectorTest, DailyCheckTest | access revoked, missed daily check, old lists, offline, asleep for months |
| Guardian notes | AlertPipelineTest, AlertBackoffTest, AlertReminderTest, OutboxFlusherTest, GuardianAlertsTest, GuardianChangeTest, GuardianTextTest | in her name, back-off, urgent never held, queued offline, guardian change, alerts off, killed mid-send |
| History and Home | HomeAndHistoryTest, HistoryDetailTest, ScreensTest | never "all good" when unchecked, oldest first, handled colours, midnight in her time zone |
| Setup, settings, invitation | ScreensTest, SetupStepTest, InviteAndFixTest | every step at 200% font, no mail app, invitation text |
| Storage and migrations | RoomStoresTest, HistoryMigrationTest | migrations 1-2 and 2-3, pruning, hashed keys |
| Scam-list feed | ThreatFeedTest, FeedUpdaterTest, FeedBuilderTest | wrong key, list shrank, downgrade, real feed files |
| Public pages and policy | PublicPagesTest, PlayListingTest, ManifestPolicyTest | pages quote the app's own words, never "safe", banned permissions |
| Architecture guards | GuardTests, GuardSelfTest, mutants.json | one owner per concern, layering, copy rules |

## Gaps, highest risk first (finding #97)

1. **Samsung shapes never tested.** An app from the Galaxy Store, or a Samsung preload that is not a system app, may read as "a new app
   from outside the Play Store" (installer is not `com.android.vending`) and the guardian is told. No Samsung Messages, Google Messages or
   Samsung Internet notification is in any test; website channels are known only in Chrome's format. Needs real Samsung dumps.
2. **First run end to end.** Nothing drives setup through to "the guardian received the first message"; the topic's look-alike letters
   are untested.
3. **Android's own behaviour is not modelled.** No fake for snooze returning, the listener reconnecting after a reboot or update, or
   notices that arrived while Aegida was not listening (never checked).
4. **What goes over the network vs the privacy page.** No test lists every request with alerts off and on; guardian notes are checked
   against hostile evidence for one kind only.
5. **False look-alikes on real brand links** (from reading, not run): `apple.co`, `paypal.me`, `amazon.de`, `google.co.uk`; "pop-ups"
   splitting to "ups"; an all-Cyrillic "apple" missed.
6. **A news site she said Yes to** that posts "Retailer hacked" would be hidden as a fake warning, and the guardian told.
7. **Upgrade with real data:** no install over an older version on the emulator, no 1-to-3 migration from a real device's database.
8. **Listener on but dead:** if the system unbinds it while the daily job runs, link checks stop and Home still says no known problems.

## Tests that check the code's shape, not behaviour

`OffMainThreadTest` (counts call sites), `PictureBannerTest` (source text has no `runBlocking`), `ReviewFixesTest` website quiet week
(asserts a constant), `PublicPagesTest` counting tags. Invented fixtures where real data matters: `NoticeRulesTest` bank/news notices,
`Fixtures.message()`, Robolectric's package manager instead of a real `pm list packages -i` dump, `.example` domains only for look-alikes,
an `Http` fake that never returns ntfy's 429 or models its 12-hour cache, and fixed dates in a few tests.

## Proposed organisation

- A `:core` test-fixtures set with one fake per port, builders in Mom's terms (`aScamText()`, `aChromePopup()`, `aGalaxyStoreApp()`),
  a `FakeNotificationShade` that models snooze returns and listener reconnects, and real device dumps (emulator, Pixel, Samsung) as resources.
- Contract tests run against each fake and its Room store.
- One scenario file per area where it can run whole (in `:alerts`): scam texts, website pop-ups, noisy apps, phone changes, guardian
  notes, health, first run.
- Split ScreensTest by screen; name tests after the situation in plain words.
- A scenario table per area in `docs/scenarios/<area>.md`; each row names its test, emulator step and mutation check; a guard fails if a row
  names a test that does not exist.

Template: ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants (repeat, undo, failure, hostile) | Data source | Test | Emulator | Mutant.

## Phase 2 (Oct 14)

- **Shape tests replaced.** `PictureBannerTest.theBannerSourceDoesNotUseRunBlocking` (read the source text) is deleted. The rule it
  protected (posting a banner may happen on the main thread, so it must not block on the database) is already tested as behaviour by
  `aPickMadeBeforeTheFirstLoadFinishesIsNotOverwrittenByTheOldStoredOne`: the store is held shut, and `bitmap()`/`set()` must still return
  (a blocking call would hang the test). `bitmapNeverReadsTheStore` covers the other half. `PublicPagesTest`: the `>= 6 <details>` and
  `>= 2 <figure>` counts are dropped; what they stood for is checked per item (every `<figure>` has a caption and a labelled `<svg>`),
  and the Simple view's questions are still checked by their wording. Nothing else changed in what the page tests assert.
- **Builders in plain terms.** `core/src/testFixtures/.../testkit/Builders.kt`: `aScamText()`, `aChromePopup()`, `aPlayApp()`,
  `aGalaxyStoreApp()`, `anAppOpenedFromAFile()`, `aSystemApp()`, `anApp()`, and `GALAXY_STORE`. Shapes: Chrome's `p#<site>#<n>` key and
  `web:<site>;<id>` channel (emulator), Google Messages' key and `msg` category, and the Galaxy Store as installer and initiating package
  from the Samsung dumps in `build/samsung-rtl`. Used by `AppDetectorsTest` (its made-up `org.maker.store` is now the real Galaxy Store),
  `Fixtures.chromeSiteNotification`, `CheckRunnerTest`, `HealthScenariosTest`, `NetworkVsPrivacyPageTest` (five local copies removed).
- **Time and size.** Slowest class is 12 s (`HistoryScreenTest`), largest test file 326 lines: no split needed.
