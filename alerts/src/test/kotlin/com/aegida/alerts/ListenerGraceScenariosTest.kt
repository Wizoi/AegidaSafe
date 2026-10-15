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
import com.aegida.testkit.aScamText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration

/**
 * docs/scenarios/health.md H3h-H3i and H4k-H4u (findings #135, #136, #141, #142): two binds at once, a process killed half way through
 * a check, checks in a process that has just started before Android has had time to bind the listener, and the daily job's wait.
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
        val racing = ListenerLink(runner, slowStore, binding, seen)
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

    // H3h-H3i (finding #141): the process is killed half way through a check.

    @Test fun killedDuringTheLateChecksTheRestAreCheckedAtTheNextBind() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        shade.post(aScamText(now, address = "https://evil.example/second", id = 8).copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        killInCheck = 3 // the bind's own check, the first late notice, then killed in the second
        assertTrue(runCatching { shade.connect(listener) }.exceptionOrNull() is Killed)
        assertEquals("the first was checked before the kill", 1, latePosts())
        kill()
        advance(Duration.ofMinutes(1))
        shade.connect(listener)
        assertEquals("the second is checked at the next bind, the first not again", 2, latePosts())
    }

    @Test fun killedDuringTheFirstLateCheckItIsCheckedAtTheNextBind() = runTest {
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(1))
        shade.post(scamText.copy(postedAt = now))
        advance(Duration.ofMinutes(1))
        killInCheck = 2 // the bind's own check finished; killed in the first late notice's
        assertTrue(runCatching { shade.connect(listener) }.exceptionOrNull() is Killed)
        kill()
        advance(Duration.ofMinutes(1))
        shade.connect(listener)
        assertEquals(1, latePosts())
        assertEquals(listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    @Test fun killedDuringOneNoticesCheckItIsCheckedAgainAtTheNextBind() = runTest {
        shade.connect(listener)
        advance(Duration.ofMinutes(1))
        killInCheck = 1
        assertTrue(runCatching { shade.post(scamText.copy(postedAt = now)) }.exceptionOrNull() is Killed)
        assertEquals(emptyList<Any>(), owner.shown)
        kill()
        advance(Duration.ofMinutes(1))
        shade.connect(listener)
        assertEquals(1, latePosts())
        assertEquals(listOf(Problem.ScamLink), owner.shown.map { it.problem })
    }

    // H4q-H4u (finding #142): when the daily job asks Android to bind the listener again, and when it waits.

    @Test fun withAccessOffTheDailyJobNeitherAsksNorWaits() = runTest {
        waited = { testScheduler.currentTime }
        shade.connect(listener)
        kill()
        listenerEnabled = false
        processStartedAt = now
        daily.run(downloadFeed = false)
        assertEquals("no wait", 0L, testScheduler.currentTime)
        assertEquals("not asked", 0, binding.rebindRequests)
        assertEquals(listOf(Problem.ListenerOff), findings.open().map { it.key.problem })
    }

    @Test fun theFirstLaunchBeforeSetupChecksAtOnce() = runTest {
        waited = { testScheduler.currentTime }
        setupStore.value = com.aegida.core.model.Setup.NotDone
        listenerEnabled = false
        processStartedAt = now
        daily.runIfNeverRun()
        assertEquals("no wait before access is given", 0L, testScheduler.currentTime)
        assertEquals(0, binding.rebindRequests)
        assertEquals(listOf(Trigger.Daily), checkLog.runs.map { it.trigger })
        listenerEnabled = true // the setup step gave access; Android has not bound it yet
        val before = testScheduler.currentTime
        daily.run(downloadFeed = false)
        assertTrue("no wait for the bind during setup", testScheduler.currentTime - before < ListenerTimes.NEW_PROCESS_GRACE.toMillis())
        assertEquals("asked to bind it", 1, binding.rebindRequests)
    }

    @Test fun aLongRunningProcessWaitsForTheRebindItAskedForInsteadOfFlashingTheStep() = runTest {
        waited = { testScheduler.currentTime }
        processStartedAt = now.minus(Duration.ofDays(1))
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(61))
        launch { delay(3_000); shade.connect(listener) }
        daily.run(downloadFeed = false)
        assertEquals(emptyList<Problem>(), findings.open().map { it.key.problem })
        assertEquals("never shown, not even for a moment", emptyList<Any>(), owner.shown)
        assertTrue("waited only until it was bound", testScheduler.currentTime < 10_000)
    }

    @Test fun aLongRunningProcessThatIsReallyLostShowsTheStepAfterTheWait() = runTest {
        waited = { testScheduler.currentTime }
        processStartedAt = now.minus(Duration.ofDays(1))
        shade.connect(listener)
        shade.disconnect()
        advance(Duration.ofMinutes(61))
        daily.run(downloadFeed = false)
        assertEquals(listOf(Problem.ListenerNotConnected), findings.open().map { it.key.problem })
        assertTrue("waited until two minutes after asking", testScheduler.currentTime >= ListenerTimes.NEW_PROCESS_GRACE.toMillis())
        assertTrue("and no longer", testScheduler.currentTime < ListenerTimes.NEW_PROCESS_GRACE.plusMinutes(1).toMillis())
    }

    @Test fun aDailyJobKilledWhileItWaitsRecordsNoCheck() = runTest {
        waited = { testScheduler.currentTime }
        shade.connect(listener)
        advance(Duration.ofHours(3))
        kill()
        processStartedAt = now
        val job = launch { daily.run(downloadFeed = false) }
        delay(30_000)
        job.cancel()
        assertEquals("no daily check recorded", emptyList<Trigger>(), checkLog.runs.map { it.trigger }.filter { it == Trigger.Daily })
        daily.run(downloadFeed = false)
        assertEquals("the next run asks again", 2, binding.rebindRequests)
    }
}
