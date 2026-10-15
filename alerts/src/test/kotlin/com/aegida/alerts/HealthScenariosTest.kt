package com.aegida.alerts

import com.aegida.testkit.aScamText
import com.aegida.testkit.aChromePopup
import com.aegida.core.health.Trigger
import com.aegida.core.model.FindingState
import com.aegida.core.model.HomeState
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * docs/scenarios/health.md: what Android itself does to the listener (snooze returning, unbinding, binding again) and what
 * Home says then. The listener here does what AegidaListenerService does (that class needs Hilt, so it is not run on the
 * JVM): binding and unbinding go to ListenerLink, a posted notice runs a check and hides or snoozes what the check names.
 * "Unbinding" here is the service being destroyed (onDestroy); nothing relies on onListenerDisconnected (finding #134).
 */
class HealthScenariosTest : HealthScenarioFixture() {
    @Test fun aNewWebsitesNoticeThatComesBackFromItsSnoozeIsPutAsideAgainWithoutTellingAnyoneTwice() = runTest {
        shade.connect(listener)
        shade.post(newSite())
        assertTrue("asked about, so put aside", shade.isSnoozed(newSite().key))
        val toldAfterFirst = http.posts.size
        advance(Duration.ofMinutes(61))
        assertEquals("the phone brought it back and Aegida saw it again", 2, shade.delivered.count { it.key == newSite().key })
        assertTrue("she has not answered yet, so it is put aside again", shade.isSnoozed(newSite().key))
        assertEquals("one question for Mom", 1, owner.shown.count { it.problem == Problem.NewWebsiteNotifications })
        assertEquals("nothing more to the guardian", toldAfterFirst, http.posts.size)
    }

    @Test fun afterAPhoneRestartOrAppUpdateTheListenerBindsAgainAndAegidaChecksThePhone() = runTest {
        shade.connect(listener)
        shade.disconnect()
        val before = checkLog.runs.size
        advance(Duration.ofMinutes(5))
        shade.connect(listener)
        assertEquals(before + 1, checkLog.runs.size)
        assertEquals(Trigger.AppStart, checkLog.latest()!!.trigger)
        shade.post(scamText.copy(postedAt = now))
        assertEquals("notices are heard again once it is bound", listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    // H3 (finding #111): what came while Aegida was not listening is checked once, late, when it is bound again.

    @Test fun aScamTextThatArrivedWhileAegidaWasNotListeningIsCheckedOnceWhenItIsBoundAgain() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(10))
        shade.connect(listener)
        assertEquals("the usual warning, late", listOf(Problem.ScamLink), owner.shown.map { it.problem })
        assertEquals("the guardian gets the usual note", 1, http.posts.size)
        assertTrue("the text is left on her screen, as for any message", shade.isShown(scamText.key))
        assertTrue(home() is HomeState.NeedsTaps)
    }

    @Test fun aFullShadeHasOnlyItsNewestTwentyNoticesCheckedWhenAegidaIsBoundAgain() = runTest {
        shade.connect(listener)
        shade.disconnect()
        // The five oldest are scam texts; the newest twenty are not. Only the newest twenty are checked.
        repeat(25) { n ->
            advance(Duration.ofSeconds(30))
            val words = if (n < 5) scamText.text else "See you at lunch"
            shade.post(aScamText(now, text = words, id = 100 + n))
        }
        shade.connect(listener)
        assertEquals(ListenerLink.MAX_CHECKED, latePosts())
        assertEquals("the older ones are left unchecked", emptyList<Any>(), owner.shown)
    }

    @Test fun aScamTextSheAlreadyDealtWithIsNotWarnedAboutAgainWhenAegidaIsBoundAgain() = runTest {
        shade.connect(listener)
        shade.post(scamText.copy(postedAt = now))
        findings.rows.replaceAll { _, f -> f.copy(state = FindingState.Acknowledged) }
        advance(Duration.ofMinutes(5))
        shade.disconnect()
        advance(Duration.ofMinutes(5))
        shade.connect(listener)
        assertEquals("one warning, not a second", 1, owner.shown.size)
        assertEquals("one note to the guardian", 1, http.posts.size)
    }

    @Test fun aegidasOwnNoticeIsNotCheckedWhenAegidaIsBoundAgain() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(key = "0|$OWN_PACKAGE|1|null|10200", packageName = OWN_PACKAGE, postedAt = now))
        shade.connect(listener)
        assertEquals(0, latePosts())
        assertEquals(emptyList<Any>(), owner.shown)
    }

    @Test fun aNewWebsitesNoticeThatCameWhileAegidaWasNotListeningIsPutAsideWhenItIsBoundAgain() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(aChromePopup(now, site = "https://shop.example/", title = "Spring sale", text = "Our new collection is here"))
        shade.connect(listener)
        assertTrue("asked about late, so put aside like a new one", shade.isSnoozed(newSite().key))
        assertEquals(listOf(Problem.NewWebsiteNotifications), owner.shown.map { it.problem })
    }

    @Test fun whenThePhoneBindsAegidaSeveralTimesInAMinuteEachMissedNoticeIsCheckedOnce() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofSeconds(5))
        shade.post(scamText.copy(postedAt = now))
        repeat(3) {
            advance(Duration.ofSeconds(10))
            shade.connect(listener)
            advance(Duration.ofSeconds(5))
            shade.disconnect()
        }
        shade.connect(listener)
        assertEquals("checked once", 1, latePosts())
        assertEquals(1, owner.shown.size)
        assertEquals(1, http.posts.size)
    }

    @Test fun whenAegidaWasStoppedWithoutBeingToldTheNoticesThatCameSinceItWasLastSeenListeningAreCheckedOnce() = runTest {
        shade.connect(listener)
        shade.post(aScamText(now, text = "See you at lunch", id = 7))
        advance(Duration.ofMinutes(5))
        kill()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        shade.connect(listener)
        assertEquals("only the one that came after the last notice it heard", 1, latePosts() - 1)
        assertEquals(listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    @Test fun androidUnbindingAegidaWithoutCallingOnListenerDisconnectedStillGetsTheLateCheck() = runTest {
        shade.connect(listener)
        shade.lose() // the emulator never called onListenerDisconnected (finding #134); onDestroy clears the flag
        binding.boundNow = false
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(10))
        shade.connect(listener)
        assertEquals("the usual warning, late", listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    @Test fun onAFreshInstallNothingInTheShadeIsCheckedLate() = runTest {
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        shade.connect(listener)
        assertEquals("never seen listening, so it cannot tell what she has seen", 0, latePosts())
        assertEquals(emptyList<Any>(), owner.shown)
    }

    // H4 (finding #112): notification access is on, but the phone has stopped letting Aegida listen.

    @Test fun whenAccessIsOnButAegidaHasNotBeenListeningForOverAnHourHomeSaysItCannotSeeHerNotices() = runTest {
        lostForOverAnHour()
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
        assertTrue("never shown as fine", home() is HomeState.NeedsTaps)
        assertEquals(listOf(Problem.ListenerNotConnected), owner.shown.map { it.problem })
        assertEquals("the guardian is not told in the first day", 0, http.posts.size)
    }

    @Test fun whenAegidaHasNotBeenListeningForMoreThanADayTheGuardianIsToldOnce() = runTest {
        lostForOverAnHour()
        advance(Duration.ofHours(23))
        runner.run(Trigger.Daily)
        assertEquals("not yet a day since it was raised", 0, http.posts.size)
        advance(Duration.ofHours(2))
        runner.run(Trigger.Daily)
        advance(Duration.ofHours(1))
        runner.run(Trigger.AppStart)
        assertEquals(1, http.posts.size)
        assertTrue(http.posts.single().second.contains(Texts.Guardian.STILL_OPEN.format("Ruth").substringBefore("It has been")))
    }

    @Test fun theProblemIsClearedAsSoonAsAegidaIsListeningAgain() = runTest {
        lostForOverAnHour()
        shade.connect(listener)
        assertEquals(emptyList<Any>(), findings.open())
        assertEquals(listOf(Problem.ListenerNotConnected), owner.cleared.map { it.problem })
        assertTrue(home() is HomeState.NoKnownProblems)
    }

    @Test fun aegidaNotListeningIsNotRaisedBeforeSetupEnds() = runTest {
        setupStore.value = setupStore.value.copy(stepsDone = 2)
        lostForOverAnHour()
        assertFalse(findings.open().any { it.key.problem == Problem.ListenerNotConnected })
    }

    @Test fun aegidaNotListeningIsNotRaisedInTheFirstHour() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(59))
        runner.run(Trigger.Daily)
        assertEquals(emptyList<Any>(), findings.open())
    }

    @Test fun afterARestartAegidaNotListeningIsRaisedOnlyAnHourAfterThePhoneStarted() = runTest {
        shade.connect(listener)
        advance(Duration.ofHours(8))
        shade.restart()
        binding.boundNow = false
        bootedAt = now
        advance(Duration.ofMinutes(59))
        runner.run(Trigger.Daily)
        assertEquals("not in the first hour after the restart", emptyList<Any>(), findings.open())
        advance(Duration.ofMinutes(2))
        runner.run(Trigger.Daily)
        assertEquals("never bound since the restart", listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
    }

    @Test fun whileNotificationAccessIsOffOnlyTheStepToTurnItBackOnIsShown() = runTest {
        shade.connect(listener)
        shade.disconnect()
        listenerEnabled = false
        advance(Duration.ofHours(2))
        runner.run(Trigger.Daily)
        assertEquals(listOf(Problem.ListenerOff), findings.open().map { it.key.problem })
    }

    @Test fun afterAnUpdateFromAVersionThatDidNotRecordItAegidaNotListeningCountsFromTheUpdate() = runTest {
        updatedAt = now
        advance(Duration.ofMinutes(59))
        runner.run(Trigger.Daily)
        assertEquals("never seen listening: counted from the update, not from the restart two days ago", emptyList<Any>(), findings.open())
        advance(Duration.ofMinutes(2))
        runner.run(Trigger.Daily)
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
    }

    @Test fun androidUnbindingAegidaWithoutCallingOnListenerDisconnectedIsNoticedAfterAnHour() = runTest {
        shade.connect(listener)
        shade.lose()
        binding.boundNow = false
        advance(Duration.ofMinutes(61))
        runner.run(Trigger.Daily)
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
    }



    @Test fun aegidaNotListeningIsNotRaisedWhenTheClockWasSetBack() = runTest {
        shade.connect(listener)
        shade.disconnect()
        now = now.minus(Duration.ofHours(3))
        runner.run(Trigger.Daily)
        assertEquals(emptyList<Any>(), findings.open())
    }

    @Test fun whenNotificationAccessIsTurnedOffHomeAsksHerToTurnItBackOn() = runTest {
        listenerEnabled = false
        runner.run(Trigger.Daily)
        assertEquals(listOf(Problem.ListenerOff), findings.open().map { it.key.problem })
        assertTrue(home() is HomeState.NeedsTaps)
    }
}
