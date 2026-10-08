package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.net.NtfyTopic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianAlertsTest {
    private val topic = NtfyTopic("aegida_" + "x9Y8z7W6".repeat(4))
    private val http = Http()
    private val guardians = FakeGuardians(Guardians(alertsOn = false, momName = "Mom", firstTopic = topic, firstGuardianName = "Kevin", firstGuardianEmail = "k@example.com"))
    private val alerts = GuardianAlerts(guardians, NtfySender(http), DeadMansSwitch(guardians, NtfySender(http)))

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
}
