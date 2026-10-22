package com.aegida.alerts

import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryDetail
import com.aegida.core.alert.Outcome
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import com.aegida.core.net.NtfyTopic
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.core.text.Texts
import com.aegida.detectors.Inputs
import com.aegida.detectors.PopupSpamDetector
import com.aegida.testkit.FakeBaselines
import com.aegida.testkit.FakeOutbox
import com.aegida.testkit.aChromePopup
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Her answer to "Was that you?" about a website (scenarios WP3, WP4, WP17 A13, A14), moved out of FindingActionsTest for size. */
class WebsiteAnswersTest {
    private val now = Instant.now()
    private val findings = Findings()
    private val owner = Owner()
    private val history = History()
    private val http = Http()
    private val topic = NtfyTopic.generate("Ruth", java.util.Random(3))
    private val outbox = FakeOutbox()
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, owner, history, GuardianLine(guardians, NtfySender(http), outbox)) { now }
    private val baselines = FakeBaselines()
    private val phone = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val actions = SeenMarker(findings, history, owner).let { seen ->
        val allowing = AppAllowing(findings, history, pipeline, seen) { now }
        FindingActions(seen, allowing, ChangeAcceptor(findings, baselines, { phone }, pipeline, seen) { now }, AlertGate()) { now }
    }

    private val newSite = "http://localhost:8765"

    /** What the real detector makes of a calm notification from a site she never had: the site and what it said (findings #117). */
    private suspend fun aNewWebsite(): Finding {
        baselines.value = Baseline.of(phone)
        val popup = aChromePopup(now, site = "$newSite/", title = "Today's recipes", text = "Lentil soup in 20 minutes")
        val f = PopupSpamDetector().evaluate(Inputs(phone, now, Setup.NotDone, null, popup, null, baseline = baselines.value)).single()
        assertEquals(Problem.NewWebsiteNotifications, f.key.problem)
        assertEquals("Today's recipes", f.evidence[Evidence.MESSAGE_TITLE]) // the words seen takes out
        pipeline.handle(listOf(f), setOf(DetectorId.PopupSpam))
        return f
    }

    @Test fun aNewWebsiteIsToldToTheGuardianAsAQuestionAndHidden() = runTest { // Kevin, Oct 9
        val f = aNewWebsite()
        val told = "A website (lo*****) that was not there before started sending notifications on Ruth's phone. Aegida asked Ruth: “Was that you?”"
        assertEquals(listOf(told), http.bodies)
        assertEquals(listOf(f.key), owner.shown)
    }

    @Test fun aNewWebsiteSheSaysYesToTheGuardianHearsSheConfirmedIt() = runTest { // findings #117, GN17
        val f = aNewWebsite()
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(f.key))
        assertEquals(setOf(newSite), baselines.value!!.websites)
        assertEquals(FindingState.Acknowledged, findings.get(f.key)!!.state)
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
        // The all-clear's History line is made from the finding as the guardian was told about it, so it says what the site said, as the
        // alarm's line does; seen has taken those words out of the stored finding (findings #117, #166).
        val told = history.entries.filter { it.outcome == Outcome.GuardianTold }.map { HistoryDetail.display(it.detail) }
        assertEquals(2, told.size)
        assertTrue(told[0]!!.contains("Today's recipes"))
        assertEquals(told[0], told[1])
    }

    /** What the real detector makes of a fake virus warning from a site she has not allowed: a fake warning, on record. */
    private suspend fun aFakeWarningFrom(site: String): Finding {
        val popup = aChromePopup(now, site = "$site/", n = 2, title = "Your phone is infected", text = "Tap to clean")
        val f = PopupSpamDetector().evaluate(Inputs(phone, now, Setup.NotDone, null, popup, null, baseline = baselines.value)).single()
        assertEquals(Problem.SiteNotifications, f.key.problem)
        pipeline.handle(listOf(f), setOf(DetectorId.PopupSpam))
        return f
    }

    /** Scenario WP17 A13 (findings #149): a site that sent a fake warning is never allowed by a Yes; the question closes. */
    @Test fun aYesForAWebsiteThatSentFakeWarningsBeforeKeepsItHidden() = runTest {
        val question = aNewWebsite()
        val fake = aFakeWarningFrom(newSite)
        assertTrue(actions.acknowledge(fake.key)) // seen: still on record
        assertTrue(actions.acceptChange(question.key))
        assertEquals(emptySet<String>(), baselines.value!!.websites)
        assertEquals(FindingState.Acknowledged, findings.get(question.key)!!.state)
        // Its next fake warning is still called fake, not "a website you allowed".
        val again = PopupSpamDetector().evaluate(
            Inputs(phone, now, Setup.NotDone, null, aChromePopup(now, site = "$newSite/", n = 3), null, baseline = baselines.value),
        ).single()
        assertEquals(Problem.SiteNotifications, again.key.problem)
    }

    /** Scenario WP17 A13: she is told plainly; the guardian is not told she confirmed it, since the site was not allowed. */
    @Test fun aYesForAWebsiteThatSentFakeWarningsIsToldHerAndNotTheGuardian() = runTest {
        aFakeWarningFrom(newSite) // still open; the question comes after it
        val question = aNewWebsite()
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(question.key))
        assertEquals(emptyList<String>(), http.bodies)
        assertTrue(actions.keptHidden(question.key))
        assertEquals("This website sent fake warnings before, so Aegida keeps hiding it.", Texts.Home.SITE_KEPT_HIDDEN)
    }

    /**
     * Scenario WP17 A16 (findings #150), must never: before setup is done a calm notice from a site is hidden as SiteNotifications (there is
     * no list to ask against), but it had no alarming words, so it is not a fake on record: after setup her Yes allows the site.
     */
    @Test fun aCalmNoticeDuringSetupDoesNotStopHerAllowingTheSiteLater() = runTest {
        val calm = aChromePopup(now, site = "$newSite/", n = 1, title = "New recipe posted", text = "Lentil soup in 20 minutes")
        val duringSetup = PopupSpamDetector().evaluate(Inputs(phone, now, Setup.NotDone, null, calm, null, baseline = null)).single()
        assertEquals(Problem.SiteNotifications, duringSetup.key.problem)
        pipeline.handle(listOf(duringSetup), setOf(DetectorId.PopupSpam))
        assertTrue(actions.acknowledge(duringSetup.key)) // seen: still stored
        val question = aNewWebsite()
        http.posts.clear(); http.bodies.clear()
        assertFalse(actions.keptHidden(question.key))
        assertTrue(actions.acceptChange(question.key))
        assertEquals(setOf(newSite), baselines.value!!.websites)
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }

    /**
     * Scenario WP17 A17 (findings #150): a fake warning stored before the alarming mark existed is not counted as a fake on record (decided:
     * fail open, as for any site without a fake on record), so her Yes allows the site.
     */
    @Test fun aFakeWarningStoredBeforeTheMarkDoesNotStopHerYes() = runTest {
        val question = aNewWebsite()
        val popup = aChromePopup(now, site = "$newSite/", n = 2, title = "Your phone is infected", text = "Tap to clean")
        val fake = PopupSpamDetector().evaluate(Inputs(phone, now, Setup.NotDone, null, popup, null, baseline = baselines.value)).single()
        assertEquals(Evidence.ALARMING_YES, fake.evidence[Evidence.ALARMING])
        pipeline.handle(listOf(fake.copy(evidence = fake.evidence - Evidence.ALARMING)), setOf(DetectorId.PopupSpam)) // as stored before #150
        http.posts.clear(); http.bodies.clear()
        assertFalse(actions.keptHidden(question.key))
        assertTrue(actions.acceptChange(question.key))
        assertEquals(setOf(newSite), baselines.value!!.websites)
    }

    /**
     * Findings #152: a question raised before #149 (the detector then kept no text of the notice, so it and its History line have only
     * the title) and answered Yes after the update still tells the guardian she confirmed it: the detail is made from the stored finding
     * both times, so the two match.
     */
    @Test fun aWebsiteQuestionFromBeforeTheUpdateAnsweredYesAfterItStillTellsTheGuardian() = runTest {
        val fresh = aNewWebsite()
        findings.rows.clear(); history.entries.clear(); http.posts.clear(); http.bodies.clear()
        val older = fresh.copy(evidence = fresh.evidence - Evidence.MESSAGE_TEXT) // as the detector before #149 made it
        pipeline.handle(listOf(older), setOf(DetectorId.PopupSpam))
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(older.key))
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }

    /** Scenario WP17 A14, must never: a fake from another site does not stop her allowing this one. */
    @Test fun aYesForAWebsiteIsNotRefusedBecauseAnotherSiteSentFakes() = runTest {
        val question = aNewWebsite()
        aFakeWarningFrom("https://other.example")
        http.posts.clear(); http.bodies.clear()
        assertTrue(actions.acceptChange(question.key))
        assertEquals(setOf(newSite), baselines.value!!.websites)
        assertFalse(actions.keptHidden(question.key))
        assertEquals(listOf("Ruth confirmed the change Aegida asked about. Nothing more to do."), http.bodies)
    }
}
