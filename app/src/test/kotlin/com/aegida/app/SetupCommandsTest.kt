package com.aegida.app

import com.aegida.alerts.AlertGate
import com.aegida.alerts.GuardianAlerts
import com.aegida.alerts.GuardianChange
import com.aegida.alerts.GuardianChanged
import com.aegida.alerts.GuardianDetails
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.testkit.FakeSetupStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The channel her invitation carries (docs/scenarios/first-run.md FR8 and FR13, finding #162). */
class SetupCommandsTest {
    private var stored = Guardians.None
    private val settings = mockk<GuardianSettings>().also {
        coEvery { it.current() } coAnswers { stored }
        coEvery { it.save(any()) } coAnswers { stored = firstArg() }
    }
    private val gate = AlertGate()
    private val alerts = GuardianAlerts(settings, mockk(relaxed = true), mockk(relaxed = true), gate)
    private val change = mockk<GuardianChange>().also {
        coEvery { it.change(any(), any(), any()) } returns GuardianChanged("Kevin", null)
    }
    private val commands = SetupCommands(
        FakeSetupStore(), settings, alerts, change,
        GuardianDetails(settings, mockk(relaxed = true), mockk(relaxed = true), gate, alerts, mockk(relaxed = true)),
        mockk(relaxed = true),
    )

    @Test fun aNewEmailInSetupGoesThroughGuardianChangeAndASameEmailDoesNot() = runTest { // findings #187, T5.18
        commands.saveAbout("Ruth", "Kevin", "kevin@example.com", "")
        commands.prepareInvite()
        commands.saveAbout("Ruth", "Kevin", "KEVIN@example.com ", "")
        coVerify(exactly = 0) { change.change(any(), any(), any()) }
        commands.saveAbout("Ruth", "Ann", "ann@example.com", "555 0100")
        coVerify(exactly = 1) { change.change("Ann", "ann@example.com", "555 0100") }
        assertEquals("the old guardian is untouched until GuardianChange has run", "Kevin", stored.firstGuardianName)
    }

    @Test fun theInvitationsChannelStartsWithTheNameSheTypedInAbout() = runTest {
        commands.saveAbout(" José María ", "Kevin", "kevin@example.com", "")
        val topic = commands.prepareInvite().firstTopic!!.value
        assertTrue(topic, topic.matches(Regex("Jose-aegida-[A-Za-z0-9]{40}")))
        assertEquals("and it is the one saved for the alerts", topic, stored.firstTopic!!.value)
    }

    @Test fun sendingTheInvitationAgainKeepsTheSameChannel() = runTest {
        commands.saveAbout("Ruth", "Kevin", "kevin@example.com", "")
        val first = commands.prepareInvite().firstTopic
        assertEquals(first, commands.prepareInvite().firstTopic)
        assertEquals(first, stored.firstTopic)
    }

    @Test fun changingHerNameLaterKeepsTheChannelHerGuardianHas() = runTest {
        commands.saveAbout("Ruth", "Kevin", "kevin@example.com", "")
        val given = commands.prepareInvite().firstTopic
        commands.saveAbout("Ann", "Kevin", "kevin@example.com", "")
        assertEquals(given, commands.prepareInvite().firstTopic)
        assertTrue(given!!.value.startsWith("Ruth-aegida-"))
    }
}
