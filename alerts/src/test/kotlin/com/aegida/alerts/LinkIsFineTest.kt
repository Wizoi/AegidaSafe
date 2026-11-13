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

/** "This link is fine" on a look-alike link warning: scenario rows ST28-ST39 (docs/scenarios/scam-texts.md, findings #155). */
class LinkIsFineTest {
    private val now = Instant.now()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = NtfyTopic.generate("Ruth", java.util.Random(3))))
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

    private val lookalike = link(Problem.LookalikeLink, "paypal-help.com")

    @Test fun sayingALookalikeLinkIsFinePutsTheSiteOnTheListAndTellsTheGuardianOnlyAMaskedAddress() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        http.posts.clear(); http.bodies.clear()

        assertTrue(actions.allow(lookalike.key))

        assertEquals(listOf(AllowedItem(Problem.LookalikeLink, "paypal-help.com", "paypal-help.com", now)), allowList.items)
        assertEquals(FindingState.Acknowledged, findings.get(lookalike.key)!!.state)
        val told = "Ruth told Aegida that a link to a website (pa*****.com) is fine, " +
            "so Aegida will stop warning that it pretends to be a company. If that surprises you, it may be a good time to ask Ruth."
        assertEquals(listOf(told), http.bodies)
        val line = history.entries.single { it.problem == Problem.SiteAllowed && it.outcome == Outcome.Acknowledged }
        assertEquals("The link went to paypal-help.com.", line.detail)
        assertFalse("a second tap does nothing more", actions.allow(lookalike.key))
        assertEquals(1, http.bodies.size)
    }

    @Test fun sayingALinkIsFineClearsTheSenderAndWordsKeptForTheWarning() = runTest { // T1.4
        val withWords = lookalike.copy(evidence = lookalike.evidence + mapOf(
            Evidence.MESSAGE_TITLE to "PayPal", Evidence.MESSAGE_TEXT to "Verify now (link)", Evidence.MESSAGE_AT to "123",
        ))
        pipeline.handle(listOf(withWords), setOf(DetectorId.Link))
        assertTrue(actions.allow(withWords.key))
        assertEquals(mapOf(Evidence.HOST to "paypal-help.com", Evidence.APP_LABEL to "Messages"), findings.get(withWords.key)!!.evidence)
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
        // The second has the brand in plain letters and a Cyrillic "е" in "sеcure": still never (findings #156).
        listOf("pаypal.com", "paypal-sеcure.com").map { java.net.IDN.toASCII(it) }.forEach { host ->
            val homograph = link(Problem.LookalikeLink, host)
            pipeline.handle(listOf(homograph), setOf(DetectorId.Link))
            assertFalse(host, actions.allow(homograph.key))
            assertTrue(host, findings.get(homograph.key)!!.state.needsHer)
        }
        assertEquals(emptyList<Any>(), allowList.items)
    }

    /** ST37: all Cyrillic, so not mixed, yet it reads "apple"; the guardian would see only xn*****.com (findings #156). */
    @Test fun aNameAllInAnotherAlphabetCanNeverBeSaidToBeFine() = runTest {
        val apple = java.net.IDN.toASCII("аррӏе.com", java.net.IDN.ALLOW_UNASSIGNED)
        assertEquals("xn--80ak6aa92e.com", apple)
        // The second has the brand in plain letters in front of it.
        listOf(apple, "paypal.$apple").forEach { host ->
            val homograph = link(Problem.LookalikeLink, host)
            pipeline.handle(listOf(homograph), setOf(DetectorId.Link))
            assertFalse(host, actions.allow(homograph.key))
            assertTrue(host, findings.get(homograph.key)!!.state.needsHer)
        }
        assertEquals(emptyList<Any>(), allowList.items)
    }

    /** ST38: no ordinary site spells a brand with a number or "rn" for "m"; those are made to fool her (findings #156). */
    @Test fun aBrandSpelledWithSwappedLettersCanNeverBeSaidToBeFine() = runTest {
        // paypal-rnicrosoft.com: a plain brand next to a swapped one is still a swapped brand (findings #161).
        listOf("paypa1-help.com", "rnicrosoft-support.com", "paypal-rnicrosoft.com").forEach { host ->
            val swapped = link(Problem.LookalikeLink, host)
            pipeline.handle(listOf(swapped), setOf(DetectorId.Link))
            assertFalse(host, actions.allow(swapped.key))
            assertTrue(host, findings.get(swapped.key)!!.state.needsHer)
        }
        assertEquals(emptyList<Any>(), allowList.items)
    }

    @Test fun theLinkIsFineNoteIsQueuedWhenOfflineAsItsOwnKind() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        http.result = com.aegida.core.net.HttpResult.Failed(null, com.aegida.core.net.FailureReason.Offline)
        assertTrue(actions.allow(lookalike.key))
        assertEquals(listOf(Problem.SiteAllowed), outbox.all().filter { it.problem != Problem.LookalikeLink }.map { it.problem })
        assertEquals(listOf("paypal-help.com"), allowList.items.map { it.subject })
    }

    @Test fun withAlertsOffTheLinkIsFineStillWorksAndNothingIsSent() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        assertTrue(actions.allow(lookalike.key))
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(listOf("paypal-help.com"), allowList.items.map { it.subject })
    }

    @Test fun warnMeAgainForAWebsiteTakesItOffTheListAndTheNextLinkWarns() = runTest {
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        actions.allow(lookalike.key)
        actions.stopAllowing(Problem.LookalikeLink, "paypal-help.com")
        assertEquals(emptyList<Any>(), allowList.items)
        val shownBefore = owner.shown.size
        pipeline.handle(listOf(lookalike), setOf(DetectorId.Link))
        assertEquals(shownBefore + 1, owner.shown.size)
    }
}
