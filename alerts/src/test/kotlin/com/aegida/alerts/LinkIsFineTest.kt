package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.net.NtfyTopic
import com.aegida.core.notice.AllowedItem
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.testkit.FakeAllowList
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeOutbox
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/** "This link is fine" on a look-alike link warning: scenario rows ST28-ST34 (docs/scenarios/scam-texts.md, findings #155). */
class LinkIsFineTest {
    private val now = Instant.now()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = NtfyTopic.generate(java.util.Random(3))))
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val allowList = FakeAllowList()
    private val phone = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val actions: FindingActions = SeenMarker(findings, history, owner).let { seen ->
        val allowing = AppAllowing(findings, history, pipeline, seen, allowList) { now }
        FindingActions(seen, allowing, ChangeAcceptor(findings, FakeBaselines(), { phone }, pipeline, seen) { now }, AlertGate()) { now }
    }

    private fun link(problem: Problem, host: String) = Finding(
        FindingKey(DetectorId.Link, problem, host),
        mapOf(Evidence.HOST to host, Evidence.APP_LABEL to "Messages"),
        now.minus(Duration.ofMinutes(1)),
        now,
    )

    private val lookalike = link(Problem.LookalikeLink, "paypa1-help.com")

    @Test fun sayingALookalikeLinkIsFinePutsTheSiteOnTheListAndTellsTheGuardianOnlyAMaskedAddress() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        http.posts.clear(); http.bodies.clear()

        assertTrue(actions.allow(lookalike.key))

        assertEquals(listOf(AllowedItem(Problem.LookalikeLink, "paypa1-help.com", "paypa1-help.com", now)), allowList.items)
        assertEquals(FindingState.Acknowledged, findings.get(lookalike.key)!!.state)
        val told = "Ruth told Aegida that a link to a website (pa*****.com) is fine, " +
            "so Aegida will stop warning that it pretends to be a company. If that surprises you, it may be a good time to ask Ruth."
        assertEquals(listOf(told), http.bodies)
        val line = history.entries.single { it.problem == Problem.SiteAllowed && it.outcome == Outcome.Acknowledged }
        assertEquals("The link went to paypa1-help.com.", line.detail)
        assertFalse("a second tap does nothing more", actions.allow(lookalike.key))
        assertEquals(1, http.bodies.size)
    }

    @Test fun aLinkOnTheScamListCanNeverBeSaidToBeFine() = runTest {
        val scam = link(Problem.ScamLink, "evil.example")
        pipeline.handle(listOf(scam), setOf(DetectorId.Link))
        http.posts.clear(); http.bodies.clear()
        assertFalse(actions.allow(scam.key))
        assertEquals(emptyList<Any>(), allowList.items)
        assertEquals(emptyList<String>(), http.bodies)
        assertTrue(findings.get(scam.key)!!.state.needsHer)
    }

    /** ST34: a Latin name with a Cyrillic letter in it ("pаypal.com") is the one a scammer would coach her to allow. */
    @Test fun aNameThatMixesAlphabetsCanNeverBeSaidToBeFine() = runTest {
        val homograph = link(Problem.LookalikeLink, java.net.IDN.toASCII("pаypal.com"))
        pipeline.handle(listOf(homograph), setOf(DetectorId.Link))
        assertFalse(actions.allow(homograph.key))
        assertEquals(emptyList<Any>(), allowList.items)
        assertTrue(findings.get(homograph.key)!!.state.needsHer)
    }

    @Test fun theLinkIsFineNoteIsQueuedWhenOfflineAsItsOwnKind() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        assertTrue(actions.allow(lookalike.key))
        assertEquals(listOf(Problem.SiteAllowed), outbox.all().filter { it.problem != Problem.LookalikeLink }.map { it.problem })
        assertEquals(listOf("paypa1-help.com"), allowList.items.map { it.subject })
    }

    @Test fun withAlertsOffTheLinkIsFineStillWorksAndNothingIsSent() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        assertTrue(actions.allow(lookalike.key))
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(listOf("paypa1-help.com"), allowList.items.map { it.subject })
    }

    @Test fun warnMeAgainForAWebsiteTakesItOffTheListAndTheNextLinkWarns() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        actions.allow(lookalike.key)
        actions.stopAllowing(Problem.LookalikeLink, "paypa1-help.com")
        assertEquals(emptyList<Any>(), allowList.items)
        val shownBefore = owner.shown.size
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        assertEquals(shownBefore + 1, owner.shown.size)
    }
}
