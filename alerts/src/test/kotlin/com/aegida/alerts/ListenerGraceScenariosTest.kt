package com.aegida.alerts

import com.aegida.core.health.ListenerTimes
import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.HomeState
import com.aegida.core.text.Problem
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * docs/scenarios/health.md H4k-H4p (findings #135, #136): two binds at once, and checks in a process that has just started,
 * before Android has had time to bind the listener.
 */
class ListenerGraceScenariosTest : HealthScenarioFixture() {
    // H4p (finding #135): two binds at the same moment. A real store read suspends, so the second bind can start meanwhile.

    @Test fun twoBindsAtTheSameMomentCheckEachMissedNoticeOnce() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        val slowStore = object : ListenerTimesStore by listenerTimes {
            override suspend fun times() = listenerTimes.times().also { yield() }
        }
        val racing = ListenerLink(runner, slowStore, binding)
        val inShade = shade.active()
        List(2) { launch { racing.connected(inShade, OWN_PACKAGE) } }.joinAll()
        assertEquals("checked once", 1, latePosts())
        assertEquals(listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    // H4k-H4n (finding #136): a check in a new process before Android has had time to bind the listener.

    @Test fun aCheckInANewProcessBeforeAndroidBindsAegidaDoesNotShowTheStep() = runTest {
        shade.connect(listener)
        advance(Duration.ofHours(3)) // a quiet phone: nothing heard, so "last seen bound" is three hours old
        kill()
        processStartedAt = now
        advance(Duration.ofSeconds(2))
        runner.run(Trigger.AppStart)
        assertEquals("not in a process two seconds old", emptyList<Problem>(), findings.open().map { it.key.problem })
        assertEquals(emptyList<Any>(), owner.shown)
        advance(Duration.ofSeconds(3))
        shade.connect(listener)
        assertEquals(emptyList<Problem>(), findings.open().map { it.key.problem })
    }

    @Test fun aCheckAfterTheFirstTwoMinutesOfANewProcessStillShowsTheStep() = runTest {
        shade.connect(listener)
        advance(Duration.ofHours(3))
        kill()
        processStartedAt = now
        advance(ListenerTimes.NEW_PROCESS_GRACE.plusSeconds(1))
        runner.run(Trigger.AppStart)
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
    }

    @Test fun theStepStaysWhenANewProcessChecksBeforeAndroidCouldBindAegida() = runTest {
        lostForOverAnHour()
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
        kill()
        advance(Duration.ofHours(12))
        processStartedAt = now
        advance(Duration.ofSeconds(2))
        runner.run(Trigger.AppStart)
        assertEquals("kept, never shown as fine", listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
        assertTrue(home() is HomeState.NeedsTaps)
        assertEquals("not shown twice", 1, owner.shown.size)
    }

    @Test fun aDailyJobThatStartsTheProcessWaitsForTheListenerBeforeChecking() = runTest {
        waited = { testScheduler.currentTime }
        shade.connect(listener)
        advance(Duration.ofHours(3))
        kill()
        processStartedAt = now
        daily.run(downloadFeed = false)
        assertEquals("asked Android to bind it first", 1, binding.rebindRequests)
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
    }

    @Test fun aDailyJobThatStartsTheProcessDoesNotRaiseTheStepWhenAndroidBindsAegidaMeanwhile() = runTest {
        waited = { testScheduler.currentTime }
        shade.connect(listener)
        advance(Duration.ofHours(3))
        kill()
        processStartedAt = now
        launch { delay(3_000); shade.connect(listener) }
        daily.run(downloadFeed = false)
        assertEquals(emptyList<Problem>(), findings.open().map { it.key.problem })
    }
}
