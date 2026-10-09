package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.net.NtfyTopic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardianChangeTest {
    private val oldTopic = NtfyTopic("aegida_" + "x9Y8z7W6".repeat(4))
    private val http = Http()
    private val guardians = FakeGuardians(
        Guardians(alertsOn = true, ownerName = "Mom", firstTopic = oldTopic, firstGuardianName = "Kevin", firstGuardianEmail = "k@example.com", firstGuardianPhone = "555"),
    )
    private val sender = NtfySender(http)
    private val switch = DeadMansSwitch(guardians, sender) { java.time.Instant.EPOCH }
    private val change = GuardianChange(guardians, GuardianAlerts(guardians, sender, switch, AlertGate()), switch, AlertGate())

    @Test fun theNewGuardianGetsTheirOwnPrivateTopicAndTheOldGuardiansWaitingMessageIsCancelled() = runTest {
        val result = change.change("Ann", "ann@example.com", "777")
        val saved = guardians.value
        assertEquals("Ann", saved.firstGuardianName)
        assertEquals("ann@example.com", saved.firstGuardianEmail)
        assertEquals("777", saved.firstGuardianPhone)
        assertNotNull(saved.firstTopic)
        assertNotEquals(oldTopic, saved.firstTopic)
        assertEquals("Kevin", result.previousName)
        assertEquals("https://ntfy.sh/${oldTopic.value}/heartbeat", http.deleted.single().url)
    }

    @Test fun withAlertsOnTheNewGuardianIsToldAndTheHeartbeatMovesToThem() = runTest {
        change.change("Ann", "ann@example.com", "")
        val newTopic = guardians.value.firstTopic!!
        assertEquals(
            listOf("https://ntfy.sh/${newTopic.value}", "https://ntfy.sh/${newTopic.value}/heartbeat"),
            http.posts.map { it.first.url },
        )
        assertTrue(http.posts.none { it.first.url.contains(oldTopic.value) })
    }

    @Test fun withAlertsOffNothingIsSentAndNothingIsArmed() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        change.change("Ann", "ann@example.com", "")
        assertEquals(0, http.posts.size)
    }

    @Test fun ownerNameAndAlertsSwitchAreKept() = runTest {
        change.change("Ann", "ann@example.com", "")
        assertEquals("Mom", guardians.value.ownerName)
        assertTrue(guardians.value.alertsOn)
    }
}
