package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.PhoneChange
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.GuardianPhoneWords
import com.aegida.core.text.Problem
import com.aegida.testkit.FakeOutbox
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Release B, T5 (names) and K7/K8b (the phone note). Row ids are from docs/tester-feedback-design.md and docs/scenarios/guardian-notes.md. */
class GuardianDetailsTest {
    private val now = Instant.now()
    private val topic = NtfyTopic.generate("Ruth", java.util.Random(3))
    private val http = Http()
    private val outbox = FakeOutbox()
    private val history = History()
    private val guardians = FakeGuardians(
        Guardians(
            alertsOn = true, ownerName = "Ruth", firstTopic = topic,
            firstGuardianName = "Sam", firstGuardianEmail = "sam@example.org", firstGuardianPhone = "555 0100",
        ),
    )
    private val sender = NtfySender(http)
    private val line = GuardianLine(guardians, sender, outbox)
    private val gate = AlertGate()
    private val pipeline = AlertPipeline(Findings(), Owner(), history, line) { now }
    private val details = GuardianDetails(guardians, pipeline, line, history, gate) { now }
    private val switch = DeadMansSwitch(guardians, sender) { now }
    private val alerts = GuardianAlerts(guardians, sender, switch, gate)

    private val noteWords = Regex("added a phone number|changed the phone number|took your phone number")
    private fun phoneNotes() = http.bodies.filter { noteWords.containsMatchIn(it) }

    @Test fun t5_1_aSpellingFixKeepsTheChannelAndLaterNotesUseTheNewName() = runTest {
        guardians.value = guardians.value.copy(ownerName = "Ruht")
        assertTrue(details.fixNames("Ruth", "Samuel"))
        assertEquals("Ruth", guardians.value.ownerName)
        assertEquals("Samuel", guardians.value.firstGuardianName)
        assertEquals(topic, guardians.value.firstTopic)
        assertEquals("sam@example.org", guardians.value.firstGuardianEmail)
        assertEquals("555 0100", guardians.value.firstGuardianPhone)
    }

    @Test fun t5_3_aSpellingFixSendsNothingAndCancelsNothing() = runTest {
        details.fixNames("Ruthie", "Samuel")
        assertEquals(emptyList<Any>(), http.posts + http.deleted)
        assertEquals(emptyList<Any>(), outbox.all())
        assertEquals(null, guardians.value.pairedAt)
    }

    @Test fun t5_8_aNameFixedWhileAlertsAreOffIsSavedAndNothingIsSent() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        details.fixNames("Ruthie", "Samuel")
        assertEquals("Samuel", guardians.value.firstGuardianName)
        assertEquals(emptyList<Any>(), http.posts + http.deleted)
    }

    @Test fun t5_9_savingWithNothingChangedWritesNothingNew() = runTest {
        val before = guardians.value
        assertTrue(details.fixNames("Ruth", "Sam"))
        assertEquals(before, guardians.value)
        assertEquals(emptyList<Any>(), history.entries)
    }

    @Test fun t5_10_aBlankNameIsNotSaved() = runTest {
        assertFalse(details.fixNames("Ruth", "   "))
        assertFalse(details.fixNames("", "Sam"))
        assertEquals("Sam", guardians.value.firstGuardianName)
        assertEquals("Ruth", guardians.value.ownerName)
    }

    @Test fun t5_12_aNoteQueuedBeforeTheFixIsDeliveredFromTheOutboxInTheOldSpelling() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        details.setPhone("555 0111")
        assertEquals(1, outbox.all().size)
        details.fixNames("Ruthie", "Sam")
        assertTrue(outbox.all().single().body.startsWith("Ruth "))
        assertEquals(topic.value, outbox.all().single().topic)
    }

    @Test fun t5_15_aNameShapedLikeAWebAddressIsMaskedInEveryNoteToTheGuardian() = runTest {
        details.fixNames("bank-help.com", "Sam")
        details.setPhone("555 0199")
        val note = phoneNotes().single()
        assertFalse(note, note.contains("bank-help.com"))
        assertTrue(note, note.contains("*"))
        alerts.resendWelcome()
        assertFalse(http.bodies.any { it.contains("bank-help.com") })
        assertEquals("the visible name is the owner's own", "bank-help.com", guardians.value.ownerName)
    }

    @Test fun k7_phoneAddedChangedAndRemovedEachSayWhatHappenedAndNeverTheNumber() = runTest {
        guardians.value = guardians.value.copy(firstGuardianPhone = "")
        details.setPhone("555 0123")
        assertEquals(
            "Ruth added a phone number for you in Aegida, so its Call button rings you. If you did not expect this, please call Ruth.",
            phoneNotes().last(),
        )
        details.setPhone("555 0456")
        assertEquals("Ruth changed the phone number Aegida has for you. If you did not expect this, please call Ruth.", phoneNotes().last())
        details.setPhone("")
        assertEquals("Ruth took your phone number out of Aegida. If you did not expect this, please call Ruth.", phoneNotes().last())
        assertTrue(http.bodies.none { it.contains("0123") || it.contains("0456") })
        assertTrue(http.posts.none { (_, _, headers) -> headers.values.any { it.contains("0123") || it.contains("0456") } })
        assertEquals(3, history.entries.count { it.problem == Problem.GuardianPhoneChanged && it.outcome == Outcome.Acknowledged })
    }

    @Test fun k7_1_offlineTheNoteIsQueuedAsItsOwnKindAndTheNumberIsSaved() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        details.setPhone("555 0111")
        assertEquals("555 0111", guardians.value.firstGuardianPhone)
        assertEquals(listOf(Problem.GuardianPhoneChanged), outbox.all().map { it.problem })
        assertTrue(history.entries.any { it.problem == Problem.GuardianPhoneChanged && it.outcome == Outcome.GuardianNotReached })
    }

    @Test fun k7_2_andK8b_aPhoneChangedWhileAlertsAreOffIsHeldAndSentWhenAlertsGoOn() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        details.setPhone("555 0111")
        assertEquals("555 0111", guardians.value.firstGuardianPhone)
        assertEquals(PhoneChange.Changed, guardians.value.phoneNotePending)
        assertEquals(emptyList<Any>(), http.posts)
        alerts.setAlertsOn(true)
        details.sendHeldPhoneNote()
        assertEquals(1, phoneNotes().size)
        assertNull(guardians.value.phoneNotePending)
        details.sendHeldPhoneNote()
        assertEquals("sent once", 1, phoneNotes().size)
    }

    @Test fun k8b_manyChangesWhileAlertsAreOffAreOneHeldNote() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        details.setPhone("555 0111")
        details.setPhone("555 0222")
        details.setPhone("555 0333")
        alerts.setAlertsOn(true)
        details.sendHeldPhoneNote()
        assertEquals(1, phoneNotes().size)
    }

    @Test fun k7_3_noChannelYetMeansNoNoteAndNothingHeld() = runTest {
        guardians.value = guardians.value.copy(firstTopic = null)
        details.setPhone("555 0111")
        assertEquals(emptyList<Any>(), http.posts)
        assertNull(guardians.value.phoneNotePending)
        assertEquals("555 0111", guardians.value.firstGuardianPhone)
    }

    @Test fun k7_4_aNoteStillWaitingInTheOutboxIsNotFollowedByAnother() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        details.setPhone("555 0111")
        details.setPhone("555 0222")
        details.setPhone("555 0333")
        assertEquals(1, outbox.all().size)
        assertEquals("555 0333", guardians.value.firstGuardianPhone)
    }

    @Test fun k7_5_changeThenUndoSendsBothNotes() = runTest {
        details.setPhone("555 0111")
        details.setPhone("555 0100")
        assertEquals(2, phoneNotes().size)
    }

    @Test fun k7_6_sameDigitsWithDifferentSpacesOrAnExtensionSendNothingButAreSaved() = runTest {
        details.setPhone("(555) 0100")
        details.setPhone("555-0100,123")
        details.setPhone("555 0100;9")
        assertEquals(emptyList<Any>(), http.posts)
        assertEquals("555 0100;9", guardians.value.firstGuardianPhone)
        assertEquals(emptyList<Any>(), history.entries)
    }

    @Test fun k7_8_theNoteGoesBeforeTheNumberIsSaved() = runTest {
        var savedWhenSent: String? = null
        http.onPost = { savedWhenSent = guardians.value.firstGuardianPhone }
        details.setPhone("555 0111")
        assertEquals("the old number is still stored while the note is sent", "555 0100", savedWhenSent)
        assertEquals("555 0111", guardians.value.firstGuardianPhone)
    }

    @Test fun k7_9_theNoteBuilderHasNoRoomForANumber() {
        for (change in PhoneChange.entries) {
            val note = GuardianPhoneWords.phoneNote(change, "Ruth")
            assertFalse(note, note.any { it.isDigit() })
        }
    }

    @Test fun k7_7_aPhoneSetInsideThePickADifferentGuardianFlowSendsNoPhoneNote() = runTest {
        val change = GuardianChange(guardians, alerts, switch, gate, details) { now }
        guardians.value = guardians.value.copy(alertsOn = false, phoneNotePending = PhoneChange.Changed)
        change.change("Ann", "ann@example.org", "555 0999")
        assertNull("a held note about the old guardian's number is dropped", guardians.value.phoneNotePending)
        assertEquals(emptyList<Any>(), phoneNotes())
    }

    @Test fun k9_theSameEmailKeepsTheChannelAndSendsNothingAboutAChangedGuardian() = runTest {
        val result = GuardianChange(guardians, alerts, switch, gate, details) { now }.change("Samuel", " SAM@example.org ", "555 0100")
        assertTrue(result.samePerson)
        assertEquals(topic, guardians.value.firstTopic)
        assertEquals("Samuel", guardians.value.firstGuardianName)
        assertEquals(emptyList<Any>(), http.posts + http.deleted)
    }

    @Test fun k9_aNewEmailMakesANewChannelAndCancelsTheOldOnesWaitingMessage() = runTest {
        val result = GuardianChange(guardians, alerts, switch, gate, details) { now }.change("Ann", "ann@example.org", "")
        assertFalse(result.samePerson)
        assertTrue(guardians.value.firstTopic != topic)
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", http.deleted.first().url)
    }

    @Test fun t5_18_setupBackWithTheSameEmailFixesNamesAndPhoneButNotTheChannel() = runTest {
        assertEquals(AboutSaved.Saved, details.saveAbout("Ruth", "Samuel", "sam@example.org", "555 0100"))
        assertEquals(topic, guardians.value.firstTopic)
        assertEquals("Samuel", guardians.value.firstGuardianName)
    }

    @Test fun t5_18_setupBackWithANewEmailAsksForANewGuardianAfterSavingTheNames() = runTest {
        assertEquals(AboutSaved.NewGuardian, details.saveAbout("Ruthie", "Ann", "ann@example.org", "555 0100"))
        assertEquals("Ruthie", guardians.value.ownerName)
        assertEquals("the email is not written here: GuardianChange owns it", "sam@example.org", guardians.value.firstGuardianEmail)
    }

    @Test fun firstRunWithNoChannelSavesEverythingAsTyped() = runTest {
        guardians.value = Guardians.None
        assertEquals(AboutSaved.Saved, details.saveAbout(" Ruth ", " Sam ", " sam@example.org ", " 555 0100 "))
        val typed = Guardians.None.copy(ownerName = "Ruth", firstGuardianName = "Sam", firstGuardianEmail = "sam@example.org", firstGuardianPhone = "555 0100")
        assertEquals(typed, guardians.value)
        assertEquals(emptyList<Any>(), http.posts)
    }
}
