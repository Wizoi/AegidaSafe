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
        Guardians(
            alertsOn = true, ownerName = "Mom", firstTopic = oldTopic,
            firstGuardianName = "Kevin", firstGuardianEmail = "k@example.com", firstGuardianPhone = "555",
        ),
    )
    private val sender = NtfySender(http)
    private val switch = DeadMansSwitch(guardians, sender) { java.time.Instant.EPOCH }
    private val changedAt = java.time.Instant.now()
    private val change = GuardianChange(guardians, GuardianAlerts(guardians, sender, switch, AlertGate()), switch, AlertGate()) { changedAt }

    @Test fun theNewGuardianGetsTheirOwnPrivateTopicAndTheOldGuardiansWaitingMessageIsCancelled() = runTest {
        val result = change.change("Ann", "ann@example.com", "777")
        val saved = guardians.value
        assertEquals("Ann", saved.firstGuardianName)
        assertEquals("ann@example.com", saved.firstGuardianEmail)
        assertEquals("777", saved.firstGuardianPhone)
        assertEquals("the repeat back-off must know when the new guardian was set (findings #75)", changedAt, saved.pairedAt)
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
            http.posts.map { it.first.url }.filterNot { it.contains(oldTopic.value) },
        )
        // the only thing the old guardian's topic gets is the one "the owner picked a different guardian" message
        assertEquals(1, http.posts.count { it.first.url.contains(oldTopic.value) })
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

    @Test fun theOldGuardianIsToldOnceThatTheyAreNoLongerHerGuardianBeforeTheirLinkStopsWorking() { // findings #56, Kevin chose this on Oct 8
        kotlinx.coroutines.test.runTest {
            val result = change.change("Ann", "ann@example.com", "")
            val toOld = http.posts.filter { it.first.url == "https://ntfy.sh/${oldTopic.value}" }
            assertEquals(1, toOld.size)
            val told = "Mom picked a different guardian in Aegida. You will not get messages about Mom's phone from now on. " +
                "If you did not expect this, please call Mom."
            assertEquals(told, toOld.single().second)
            assertEquals(SendResult.Delivered, result.oldGuardianTold)
            // told first, then the waiting "hasn't checked in" message is cancelled
            assertEquals(0, http.deleted.indexOfFirst { true })
        }
    }

    @Test fun nobodyIsToldWhenAlertsAreOffOrThereWasNoGuardian() {
        kotlinx.coroutines.test.runTest {
            guardians.value = guardians.value.copy(alertsOn = false)
            assertEquals(null, change.change("Ann", "a@example.com", "").oldGuardianTold)
            guardians.value = guardians.value.copy(alertsOn = true, firstGuardianName = "")
            http.posts.clear()
            assertEquals(null, change.change("Bo", "b@example.com", "").oldGuardianTold)
            assertTrue(http.posts.none { it.first.url.contains(oldTopic.value) })
        }
    }

    @Test fun aChangeStillHappensWhenTheOldGuardianCannotBeReached() {
        kotlinx.coroutines.test.runTest {
            http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
            val result = change.change("Ann", "ann@example.com", "")
            assertTrue(result.oldGuardianTold is SendResult.NotDelivered)
            assertEquals("Ann", guardians.value.firstGuardianName)
            assertNotEquals(oldTopic, guardians.value.firstTopic)
        }
    }

    @Test fun changingTheGuardianWhileAlertsAreOffSendsNothingAndKeepsTheCancelledFlag() = runTest { // finding #98
        guardians.value = guardians.value.copy(alertsOn = false, heartbeatCancelled = true, welcomePendingFor = "old")
        http.deleted.clear(); http.posts.clear()
        change.change("Ann", "ann@example.org", "")
        assertEquals("nothing goes to ntfy while alerts stay off", emptyList<Any>(), http.deleted + http.posts)
        assertEquals(true, guardians.value.heartbeatCancelled)
        assertEquals(null, guardians.value.welcomePendingFor)
    }

    @Test fun changingTheGuardianWhileAlertsAreOffAfterAFailedCancelSendsOneCancelToTheOldTopic() = runTest { // finding #99
        val old = guardians.value.firstTopic!!
        guardians.value = guardians.value.copy(alertsOn = false, heartbeatCancelled = false)
        http.deleted.clear(); http.posts.clear()
        change.change("Ann", "ann@example.org", "")
        assertEquals(listOf("https://ntfy.sh/${old.value}/heartbeat"), http.deleted.map { it.url })
        assertEquals(emptyList<Any>(), http.posts)
    }
}
