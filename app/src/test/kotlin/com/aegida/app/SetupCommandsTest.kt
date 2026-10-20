package com.aegida.app

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.testkit.FakeSetupStore
import io.mockk.coEvery
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
    private val commands = SetupCommands(FakeSetupStore(), settings, mockk(relaxed = true), mockk(relaxed = true), mockk(relaxed = true))

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
