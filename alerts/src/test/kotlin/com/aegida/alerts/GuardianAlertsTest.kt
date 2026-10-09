package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.time.Clock
import kotlinx.coroutines.test.TestScope
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianAlertsTest {
    private val topic = NtfyTopic("aegida_" + "x9Y8z7W6".repeat(4))
    private val http = Http()
    private val guardians = FakeGuardians(Guardians(alertsOn = false, ownerName = "Mom", firstTopic = topic, firstGuardianName = "Kevin", firstGuardianEmail = "k@example.com"))
    private val start = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val alerts = GuardianAlerts(guardians, NtfySender(http), DeadMansSwitch(guardians, NtfySender(http)) { start }, AlertGate())

    /** Wraps [Http] and notes the virtual time of every request, so a test can see how far apart ntfy was asked. */
    private class Timed(private val inner: Http, private val millis: () -> Long) : HttpClient {
        val times = mutableListOf<Pair<String, Long>>()
        override suspend fun get(endpoint: Endpoint, maxBytes: Int) = inner.get(endpoint, maxBytes)
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
            times += "post" to millis(); return inner.post(endpoint, body, headers)
        }
        override suspend fun delete(endpoint: Endpoint): HttpResult {
            times += "delete" to millis(); return inner.delete(endpoint)
        }
    }

    private fun TestScope.paced(): Pair<GuardianAlerts, Timed> {
        val timed = Timed(http) { testScheduler.currentTime }
        val clock = Clock { start.plusMillis(testScheduler.currentTime) }
        val sender = NtfySender(timed)
        val switch = DeadMansSwitch(guardians, sender, clock)
        return GuardianAlerts(guardians, sender, switch, AlertGate()) to timed
    }

    @Test fun turningOnAndOffInUnderASecondWaitsSoNtfyCancelsTheHeartbeat() = runTest {
        val (paced, timed) = paced()
        paced.setAlertsOn(true)
        paced.setAlertsOn(false)
        val arm = timed.times.last { it.first == "post" }.second
        val cancel = timed.times.single { it.first == "delete" }.second
        assertTrue("cancel came ${cancel - arm} ms after the arm", cancel - arm >= 2_000)
    }

    @Test fun turningOffAndBackOnInUnderASecondWaitsBeforeTheNewHeartbeat() = runTest {
        val (paced, timed) = paced()
        guardians.value = guardians.value.copy(alertsOn = true)
        paced.setAlertsOn(false)
        paced.setAlertsOn(true)
        val cancel = timed.times.single { it.first == "delete" }.second
        val arm = timed.times.last { it.first == "post" }.second
        assertTrue("arm came ${arm - cancel} ms after the cancel", arm - cancel >= 2_000)
    }

    @Test fun aCancelLongAfterTheArmDoesNotWait() = runTest {
        val (paced, timed) = paced()
        paced.setAlertsOn(true)
        testScheduler.advanceTimeBy(60_000)
        val before = testScheduler.currentTime
        paced.setAlertsOn(false)
        assertEquals(before, timed.times.single { it.first == "delete" }.second)
    }

    @Test fun aWelcomeThatFailedIsRetriedOnceAndThenForgotten() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        alerts.setAlertsOn(true)
        http.result = HttpResult.Ok(200, "{}")
        http.posts.clear()
        assertEquals(SendResult.Delivered, alerts.retryWelcome())
        assertEquals(1, http.posts.size)
        assertEquals(null, alerts.retryWelcome()) // delivered: nothing waits
    }

    @Test fun turningOnSavesTellsTheGuardianOnceAndArmsTheHeartbeat() = runTest {
        alerts.setAlertsOn(true)
        assertTrue(guardians.value.alertsOn)
        assertEquals(2, http.posts.size)
        assertEquals("https://ntfy.sh/${topic.value}", http.posts[0].first.url)
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", http.posts[1].first.url)
        alerts.setAlertsOn(true) // already on: nothing more is sent
        assertEquals(2, http.posts.size)
    }

    @Test fun turningOffCancelsTheWaitingMessageAndSendsNothingNew() = runTest {
        alerts.setAlertsOn(true)
        http.posts.clear()
        alerts.setAlertsOn(false)
        assertFalse(guardians.value.alertsOn)
        assertEquals(0, http.posts.size)
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", http.deleted.single().url)
    }

    @Test fun withNoGuardianPairedNothingIsSent() = runTest {
        guardians.value = guardians.value.copy(firstTopic = null)
        alerts.setAlertsOn(true)
        assertTrue(guardians.value.alertsOn)
        assertEquals(0, http.posts.size)
    }

    @Test fun armingDirectlyWithAlertsOffSendsNothing() = runTest {
        val switch = DeadMansSwitch(guardians, NtfySender(http)) { java.time.Instant.now() }
        assertEquals(null, switch.arm())
        assertEquals(0, http.posts.size)
    }
}
