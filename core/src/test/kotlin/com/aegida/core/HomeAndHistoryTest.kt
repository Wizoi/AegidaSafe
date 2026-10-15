package com.aegida.core

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryRows
import com.aegida.core.alert.Outcome
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.HomeState
import com.aegida.core.model.SafetyPicture
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.util.Random
import org.junit.Assert.assertFalse

class HomeStateTest {
    private val now = Instant.now()
    private val checked = CheckRun(Trigger.Daily, now.minus(Duration.ofHours(3)), now.minus(Duration.ofHours(3)))

    private fun finding(problem: Problem, subject: String = "x", detector: DetectorId = DetectorId.Dns, ageMinutes: Long = 5) =
        Finding(FindingKey(detector, problem, subject), emptyMap(), now.minus(Duration.ofMinutes(ageMinutes)), now)

    @Test fun aCheckedPhoneWithNothingOpenHasNoKnownProblems() {
        assertEquals(HomeState.NoKnownProblems(checked.startedAt), HomeState.of(emptyList(), checked))
    }

    @Test fun anythingThatNeedsATapComesFirstEvenIfTheChecksAreOld() {
        val dns = finding(Problem.WebsiteFilterOff)
        val state = HomeState.of(listOf(dns, finding(Problem.ListsOld, detector = DetectorId.Health)), checked)
        assertEquals(HomeState.NeedsTaps(listOf(dns)), state)
    }

    @Test fun fixMyPhoneWalksThroughTheOldestFirstAndInfoItemsAreNotOnTheList() {
        val older = finding(Problem.ListenerOff, detector = DetectorId.Health, ageMinutes = 60)
        val newer = finding(Problem.WebsiteFilterOff, ageMinutes = 5)
        val info = finding(Problem.ListsOld, detector = DetectorId.Health)
        val state = HomeState.of(listOf(newer, info, older), checked) as HomeState.NeedsTaps
        assertEquals(listOf(older, newer), state.items)
    }

    @Test fun neverCheckedOrOldListsOrAMissedCheckIsCouldntCheckNotAllGood() { // the design's failure table
        assertEquals(HomeState.CouldntCheck(null), HomeState.of(emptyList(), null))
        assertEquals(HomeState.CouldntCheck(checked.startedAt), HomeState.of(listOf(finding(Problem.ListsOld, detector = DetectorId.Health)), checked))
        assertEquals(HomeState.CouldntCheck(checked.startedAt), HomeState.of(listOf(finding(Problem.MissedCheck, detector = DetectorId.Health)), checked))
    }
}

class HistoryRowsTest {
    private val now = Instant.now()

    @Test fun oneEventIsOneLineWithWhatWasDone() {
        val rows = HistoryRows.of(
            listOf(
                HistoryEntry(now, Problem.ScamLink, Outcome.GuardianTold),
                HistoryEntry(now, Problem.ScamLink, Outcome.OwnerWarned),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals("A message had a scam link", rows[0].title)
        assertEquals("You were warned. Your guardian was told.", rows[0].detail)
    }

    @Test fun oneReminderAboutTwoAppsIsOneLinePerApp() {
        val rows = HistoryRows.of(
            listOf(
                HistoryEntry(now, Problem.NewAppOutsidePlay, Outcome.GuardianReminded, "The app is called: “Fun Game”."),
                HistoryEntry(now, Problem.NewAppOutsidePlay, Outcome.GuardianReminded, "The app is called: “Photo Fixer”."),
            ),
        )
        assertEquals(listOf("The app is called: “Fun Game”.", "The app is called: “Photo Fixer”."), rows.map { it.facts }.sortedBy { it })
    }

    @Test fun newestFirstAndTwoEventsAtDifferentTimesStaySeparate() {
        val earlier = now.minus(Duration.ofHours(2))
        val rows = HistoryRows.of(
            listOf(
                HistoryEntry(earlier, Problem.WebsiteFilterOff, Outcome.OwnerWarned),
                HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.Fixed),
            ),
        )
        assertEquals(listOf(now, earlier), rows.map { it.at })
    }

    @Test fun greenWhenFixedOrAegidaDidItForHerAmberWhenSheStillHasToAct() {
        val fixed = HistoryRows.of(listOf(HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.Fixed))).single()
        val hidden = HistoryRows.of(listOf(HistoryEntry(now, Problem.SiteNotifications, Outcome.OwnerWarned))).single()
        val open = HistoryRows.of(listOf(HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.OwnerWarned))).single()
        assertTrue(fixed.handled)
        assertTrue(hidden.handled)
        assertEquals(false, open.handled)
    }

    @Test fun aGuardianWeCouldNotReachIsSaidPlainly() {
        val row = HistoryRows.of(listOf(HistoryEntry(now, Problem.ScamLink, Outcome.GuardianNotReached))).single()
        assertEquals("Aegida could not reach your guardian.", row.detail)
    }

    @Test fun everyProblemHasAHistoryLineWithNoInstructionInIt() {
        Problem.entries.forEach { assertTrue("${it.name}: ${it.whatHappened}", it.whatHappened.isNotBlank() && !it.whatHappened.contains("Tap")) }
    }
}

/** A later line closes an earlier one (findings #129, Kevin, Oct 14): rows HC1-HC13 of docs/scenarios/phone-changes.md. */
class HistoryClosingTest {
    private val now = Instant.now()
    private val dayOne = now.minus(Duration.ofDays(3))
    private val dayTwo = now.minus(Duration.ofDays(2))
    private val dayThree = now.minus(Duration.ofDays(1))

    /** The detail as the pipeline writes it for a new app, from the finding's evidence. */
    private fun app(label: String) = com.aegida.core.alert.HistoryDetail.of(Problem.NewAppOutsidePlay, mapOf(com.aegida.core.model.Evidence.APP_LABEL to label))
    private fun asked(at: Instant, detail: String?, problem: Problem = Problem.NewAppOutsidePlay) =
        listOf(HistoryEntry(at, problem, Outcome.OwnerWarned, detail), HistoryEntry(at, problem, Outcome.GuardianTold, detail))
    private fun rowAt(rows: List<com.aegida.core.alert.HistoryRow>, at: Instant) = rows.single { it.at == at }

    @Test fun herQuestionTurnsGreenWhenSheRemovedTheApp() { // HC1
        val rows = HistoryRows.of(asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Fun Game")))
        assertTrue(rowAt(rows, dayOne).handled)
    }

    @Test fun herQuestionTurnsGreenWhenAegidaLearnedTheAppIsFine() { // HC2
        val rows = HistoryRows.of(asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.NoLongerAConcern, app("Fun Game")))
        assertTrue(rowAt(rows, dayOne).handled)
    }

    @Test fun sayingYesTurnsTheQuestionAndItsReminderGreen() { // HC3, HC10
        val rows = HistoryRows.of(
            asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.GuardianReminded, app("Fun Game")) +
                HistoryEntry(dayThree, Problem.NewAppOutsidePlay, Outcome.Acknowledged, app("Fun Game")),
        )
        assertTrue(rowAt(rows, dayOne).handled)
        assertTrue(rowAt(rows, dayTwo).handled)
    }

    @Test fun seeTheNewerWarningClosesTheQuestionButNotTheNewerWarning() { // HC4
        val rows = HistoryRows.of(
            asked(dayOne, app("Fun Game")) + asked(dayTwo, null, Problem.StalkerwareApp) +
                HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.SeeNewerWarning, app("Fun Game")),
        )
        assertTrue(rowAt(rows, dayOne).handled)
        val closing = rows.single { it.at == dayTwo && it.title == Problem.NewAppOutsidePlay.whatHappened }
        assertEquals("See the newer warning.", closing.detail)
        assertTrue(closing.handled)
        assertFalse(rows.single { it.title == Problem.StalkerwareApp.whatHappened }.handled)
    }

    @Test fun aQuestionAskedAgainAfterAnEarlierFixStaysAmber() { // HC5: an open question never turns green
        val rows = HistoryRows.of(
            asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Fun Game")) +
                asked(dayThree, app("Fun Game")),
        )
        assertTrue(rowAt(rows, dayOne).handled)
        assertFalse(rowAt(rows, dayThree).handled)
    }

    @Test fun aFixedLineForAnotherAppLeavesThisAppsRowAmber() { // HC6
        val rows = HistoryRows.of(asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Photo Fixer")))
        assertFalse(rowAt(rows, dayOne).handled)
    }

    @Test fun aFixedSpyingAppLineNeverTurnsAnotherSpyingAppRowGreen() { // HC7: the urgent kinds never name the app
        val rows = HistoryRows.of(asked(dayOne, null, Problem.StalkerwareApp) + asked(dayTwo, null, Problem.StalkerwareApp) +
            HistoryEntry(dayThree, Problem.StalkerwareApp, Outcome.Fixed))
        assertFalse(rowAt(rows, dayOne).handled)
        assertFalse(rowAt(rows, dayTwo).handled)
    }

    @Test fun websiteProtectionTurnedBackOnTurnsTheEarlierWarningGreen() { // HC8
        val rows = HistoryRows.of(asked(dayOne, null, Problem.WebsiteFilterOff) + HistoryEntry(dayTwo, Problem.WebsiteFilterOff, Outcome.Fixed))
        assertTrue(rowAt(rows, dayOne).handled)
    }

    @Test fun aLookAlikeAppNameDoesNotCloseTheRealAppsRow() { // HC9: Cyrillic "а" in the second name
        val rows = HistoryRows.of(asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Fun Gаme")))
        assertFalse(rowAt(rows, dayOne).handled)
    }

    @Test fun aClosingLineStampedBeforeTheQuestionLeavesItAmber() { // HC11: the clock was set back
        val rows = HistoryRows.of(asked(dayTwo, app("Fun Game")) + HistoryEntry(dayOne, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Fun Game")))
        assertFalse(rowAt(rows, dayTwo).handled)
    }

    @Test fun aClosingLineWhoseQuestionWasPrunedIsJustItsOwnGreenLine() { // HC12
        val rows = HistoryRows.of(listOf(HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed, app("Fun Game"))))
        assertTrue(rows.single().handled)
    }

    @Test fun aClosingLineFromBeforeTheUpdateWithNoAppNameClosesNoAppRow() { // HC13
        val rows = HistoryRows.of(asked(dayOne, app("Fun Game")) + HistoryEntry(dayTwo, Problem.NewAppOutsidePlay, Outcome.Fixed))
        assertFalse(rowAt(rows, dayOne).handled)
    }
}

class SafetyPictureAndTopicTest {
    @Test fun thereAreTenPicturesWithDistinctIdsAndLabels() {
        assertEquals(10, SafetyPicture.entries.size)
        assertEquals(10, SafetyPicture.entries.map { it.id }.toSet().size)
        assertEquals(10, SafetyPicture.entries.map { it.label }.toSet().size)
    }

    @Test fun anUnknownPictureIdIsNoPictureNotACrash() {
        assertEquals(SafetyPicture.Owl, SafetyPicture.fromId("Owl"))
        assertNull(SafetyPicture.fromId("Dragon"))
        assertNull(SafetyPicture.fromId(null))
    }

    @Test fun generatedTopicsAreLongUrlSafeAndDifferentEachTime() {
        val random = Random(42)
        val topics = List(50) { NtfyTopic.generate(random).value }
        assertEquals(50, topics.toSet().size)
        topics.forEach {
            assertEquals(47, it.length) // "aegida_" + 40
            assertTrue(it.all { c -> c.isLetterOrDigit() || c == '_' })
        }
        assertNotEquals(NtfyTopic.generate().value, NtfyTopic.generate().value) // the real SecureRandom
    }

    @Test fun anUnansweredWebsiteQuestionIsNotShownAsHandledAndALateAllowNoteIsNotShownAsNeedsYou() { // findings #94, #96
        val at = Instant.parse("2026-10-11T16:00:00Z")
        val asked = HistoryRows.of(listOf(HistoryEntry(at, Problem.NewWebsiteNotifications, Outcome.OwnerWarned))).single()
        assertFalse(asked.handled)
        val fake = HistoryRows.of(listOf(HistoryEntry(at, Problem.SiteNotifications, Outcome.OwnerWarned))).single()
        assertTrue("a fake warning that was hidden needs nothing", fake.handled)
        val lateNote = HistoryRows.of(listOf(HistoryEntry(at, Problem.AppAllowed, Outcome.GuardianTold))).single()
        assertTrue(lateNote.handled)
    }
}
