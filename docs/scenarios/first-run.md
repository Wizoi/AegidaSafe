# Scenarios: first run

From the first time she opens Aegida to her guardian getting the first message. Each row names the test that covers it. `GAP` means
nothing covers it yet. `ScenarioTablesTest` (guards) fails the build if a named test or mutation check does not exist. Source of the
gap: [test-review.md](../test-review.md) gap 2, findings #97 and #110.

The whole-phone tests (`FirstRunScenariosTest`) run in `:alerts` with the shared fakes and do each step the way `MainViewModel` does
it; `MainViewModel` itself needs Hilt and Android and is not run on the JVM (its screens are covered by `SetupScreensTest`).

| ID | Situation (Mom's words) | Mom sees | Guardian gets | Variants | Data source | Test | Emulator | Mutant |
|---|---|---|---|---|---|---|---|---|
| FR1 | "I set Aegida up and said yes to telling Kevin" | Each step, then Home: No known problems found | First a message saying Aegida is set up on her phone; the "hasn't checked in" message waits | Lists downloaded at first launch; missing access is not raised before setup ends | Shared fakes; signed test feed | FirstRunScenariosTest#fromFirstLaunchToTheGuardianReceivingTheFirstMessageAndHomeSayingNoKnownProblems; DetectorsTest#lostPermissionsAreNotRaisedWhileSetupIsStillBeingDone | GAP | first-full-check-at-first-launch; first-message-when-alerts-go-on; health-not-before-setup-ends |
| FR2 | "Kevin's private name has letters that look alike" (hostile: I, l, 1, O, 0) | Nothing different | The first message, sent to the very name in the invitation, letter for letter | Today the name can hold look-alikes; the invitation says copy and paste, or tap the link; typing it on another device could go wrong (finding #110) | Topic made of look-alikes | FirstRunScenariosTest#aPrivateNameWithLookAlikeLettersReachesTheGuardianExactlyAsTheInvitationSpellsIt; InviteAndFixTest#theInviteIsAnEmailDraftToTheGuardianWithBothInstallLinksAndHerPrivateLink | GAP | GAP |
| FR3 | "I had no signal when I said yes" (failure) | Setup finishes as usual | The first message, sent by the next daily check, once | Not sent again after it worked; not sent to a guardian she changed since | Shared fakes, network offline | FirstRunScenariosTest#whenSheIsOfflineAtTheAlertsStepTheFirstMessageIsSentByTheNextDailyCheck; DailyCheckTest#aWelcomeThatFailedOfflineIsSentByTheNextDailyCheckOnceAndNotAgain; DailyCheckTest#aPendingWelcomeIsNotSentToAGuardianWhoWasChangedOrWhenAlertsAreOff | GAP | GAP |
| FR4 | "I said no to telling Kevin" (must never send) | Setup finishes; Home as usual | Nothing, ever | Daily checks after setup; no guardian paired | Shared fakes | FirstRunScenariosTest#whenSheSaysNoToTellingHerGuardianNothingIsEverSentToThem; GuardianAlertsTest#withNoGuardianPairedNothingIsSent | GAP | alerts-no-op-if-unchanged |
| FR5 | "I could not finish the About step" | The step waits for a name, a guardian and an email | Nothing | Bad email | Robolectric | SetupScreensTest#aboutStepNeedsAValidNameGuardianAndEmailBeforeContinuing | GAP | GAP |
| FR6 | "Kevin subscribed a day late" | n/a | Today nothing until a problem (ntfy keeps messages about 12 hours); "Send the invitation again" resends the first message | ntfy's cache and 429 are not modelled by the fake | ntfy docs | GuardianAlertsTest#resendingTheInvitationWhileAlertsAreOnSendsTheSetUpMessageAgain | GAP | welcome-resent-with-invite |
