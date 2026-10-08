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

    /** Like Room REPLACE: one row per key. */
    private class Findings : FindingStore {
        val rows = linkedMapOf<FindingKey, Finding>()
        override suspend fun open() = rows.values.filter { it.state == FindingState.Open }
        override suspend fun get(key: FindingKey) = rows[key]
        override suspend fun put(finding: Finding) { rows[finding.key] = finding }
    }

    private class Mom : MomNotifier {
        val shown = mutableListOf<FindingKey>()
        val cleared = mutableListOf<FindingKey>()
        override fun show(finding: Finding) { shown += finding.key }
        override fun clear(key: FindingKey) { cleared += key }
    }

    private class History : HistoryLog {
        val entries = mutableListOf<HistoryEntry>()
        override suspend fun add(entry: HistoryEntry) { entries += entry }
        override suspend fun since(from: Instant) = entries.filter { !it.at.isBefore(from) }.sortedByDescending { it.at }
        override suspend fun prune(before: Instant): Int {
            val old = entries.filter { it.at.isBefore(before) }
            entries.removeAll(old)
            return old.size
        }
    }

    private class Http(var result: HttpResult = HttpResult.Ok(200, "{}")) : HttpClient {
        val bodies = mutableListOf<String>()
        override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult = error("not used")
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
            bodies += body
            return result
        }
    }

    private val findings = Findings()
    private val mom = Mom()
    private val history = History()
    private val http = Http()
    private var guardians = Guardians(alertsOn = true, momName = "Ruth", firstTopic = topic)
    private val settings = object : GuardianSettings {
        override suspend fun current() = guardians
        override suspend fun save(guardians: Guardians) { this@AlertPipelineTest.guardians = guardians }
    }
    private val pipeline = AlertPipeline(findings, mom, history, settings, NtfySender(http)) { now }

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
        guardians = guardians.copy(alertsOn = false)
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
