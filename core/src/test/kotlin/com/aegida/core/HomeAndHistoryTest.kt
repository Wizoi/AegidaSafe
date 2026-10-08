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
                HistoryEntry(now, Problem.ScamLink, Outcome.MomWarned),
            ),
        )
        assertEquals(1, rows.size)
        assertEquals("A message had a scam link", rows[0].title)
        assertEquals("You were warned. Your guardian was told.", rows[0].detail)
    }

    @Test fun newestFirstAndTwoEventsAtDifferentTimesStaySeparate() {
        val earlier = now.minus(Duration.ofHours(2))
        val rows = HistoryRows.of(
            listOf(
                HistoryEntry(earlier, Problem.WebsiteFilterOff, Outcome.MomWarned),
                HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.Fixed),
            ),
        )
        assertEquals(listOf(now, earlier), rows.map { it.at })
    }

    @Test fun greenWhenFixedOrAegidaDidItForHerAmberWhenSheStillHasToAct() {
        val fixed = HistoryRows.of(listOf(HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.Fixed))).single()
        val hidden = HistoryRows.of(listOf(HistoryEntry(now, Problem.SiteNotifications, Outcome.MomWarned))).single()
        val open = HistoryRows.of(listOf(HistoryEntry(now, Problem.WebsiteFilterOff, Outcome.MomWarned))).single()
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
}
