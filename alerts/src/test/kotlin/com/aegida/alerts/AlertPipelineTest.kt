package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.MomNotifier
import com.aegida.core.alert.Outcome
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Problem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class AlertPipelineTest {
    private val now = Instant.now()
    private val topic = NtfyTopic("aegida_" + "p0O9i8U7".repeat(4))

    private val findings = Findings()
    private val mom = Mom()
    private val history = History()
    private val http = Http()
    private val outbox = FakeOutbox()
    private val settings = FakeGuardians(Guardians(alertsOn = true, momName = "Ruth", firstTopic = topic))
    private val pipeline = AlertPipeline(findings, mom, history, settings, NtfySender(http), outbox) { now }

    private fun finding(problem: Problem, subject: String, detector: DetectorId) =
        Finding(FindingKey(detector, problem, subject), emptyMap(), now.minus(Duration.ofSeconds(1)), now)

    private val scamLink = finding(Problem.ScamLink, "evil.example", DetectorId.Link)
    private val dnsOff = finding(Problem.WebsiteFilterOff, "security.cloudflare-dns.com", DetectorId.Dns)

    @Test fun aNewFindingWarnsMomAndTellsTheFirstGuardianInHerName() = runTest {
        val result = pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(PipelineResult(alerted = 1, resolved = 0, guardiansNotReached = 0), result)
        assertEquals(listOf(scamLink.key), mom.shown)
        assertEquals(listOf("A message with a scam link arrived on Ruth's phone. Aegida warned her."), http.bodies)
        assertEquals(listOf(Outcome.MomWarned, Outcome.GuardianTold), history.entries.map { it.outcome })
    }

    @Test fun theGuardianMessageNeverContainsTheLinkOrEvidence() = runTest {
        pipeline.handle(listOf(scamLink.copy(evidence = mapOf("host" to "evil.example"))), setOf(DetectorId.Link))
        assertTrue(http.bodies.none { "evil.example" in it || "http" in it })
    }

    @Test fun aRepeatDoesNotAlertAgain() = runTest {
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        val result = pipeline.handle(listOf(scamLink.copy(lastSeen = now.plusSeconds(60))), setOf(DetectorId.Link))
        assertEquals(0, result.alerted)
        assertEquals(1, mom.shown.size)
        assertEquals(1, http.bodies.size)
        assertEquals(now.plusSeconds(60), findings.rows[scamLink.key]!!.lastSeen)
    }

    @Test fun guardiansHearNothingWhenMomHasNotTurnedAlertsOn() = runTest {
        settings.value = settings.value.copy(alertsOn = false)
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(listOf(scamLink.key), mom.shown)
    }

    @Test fun anUnreachedGuardianIsRecordedNotDropped() = runTest {
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        val result = pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(1, result.guardiansNotReached)
        assertEquals(listOf(Outcome.MomWarned, Outcome.GuardianNotReached), history.entries.map { it.outcome })
    }

    @Test fun aSettingThatIsPutRightIsResolvedAndItsBannerCleared() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        val result = pipeline.handle(emptyList(), setOf(DetectorId.Dns))
        assertEquals(1, result.resolved)
        assertEquals(FindingState.Resolved, findings.rows[dnsOff.key]!!.state)
        assertEquals(listOf(dnsOff.key), mom.cleared)
        assertEquals(Outcome.Fixed, history.entries.last().outcome)
    }

    @Test fun eventFindingsAreNotResolvedJustBecauseNoMessageArrived() = runTest {
        pipeline.handle(listOf(scamLink), setOf(DetectorId.Link))
        assertEquals(0, pipeline.handle(emptyList(), setOf(DetectorId.Link, DetectorId.Dns)).resolved)
    }

    @Test fun aSettingNotCheckedThisRunIsNotResolved() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        assertEquals(0, pipeline.handle(emptyList(), setOf(DetectorId.Link)).resolved)
    }

    @Test fun infoFindingsAreStoredButDoNotBannerOrTellGuardians() = runTest {
        val listsOld = finding(Problem.ListsOld, "aegida", DetectorId.Health)
        pipeline.handle(listOf(listsOld), setOf(DetectorId.Health))
        assertEquals(emptyList<FindingKey>(), mom.shown)
        assertEquals(emptyList<String>(), http.bodies)
        assertEquals(FindingState.Open, findings.rows[listsOld.key]!!.state)
    }

    @Test fun aResolvedProblemThatComesBackAlertsAgain() = runTest {
        pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns))
        pipeline.handle(emptyList(), setOf(DetectorId.Dns))
        assertEquals(1, pipeline.handle(listOf(dnsOff), setOf(DetectorId.Dns)).alerted)
        assertEquals(2, mom.shown.size)
    }
}
