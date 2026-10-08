package com.aegida.alerts

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.Outcome
import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.FeedUpdate
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.feed.FeedVerifier
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.Trigger
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.Evaluator
import com.aegida.detectors.HealthDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class DailyCheckTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

    private val feedStore = FakeFeedStore(null)
    private val checkLog = FakeCheckLog()
    private val findings = Findings()
    private val history = History()
    private val http = Http()
    private val mom = Mom()

    private val evaluator = Evaluator(setOf(DnsDetector(), HealthDetector()), { now }, FakeSetupStore(), feedStore, checkLog)
    private val pipeline = AlertPipeline(findings, mom, history, FakeGuardians(), NtfySender(http)) { now }
    private val snapshot = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val daily = DailyCheck(
        FeedUpdater(http, FeedVerifier(keys.public.encoded), feedStore) { now },
        CheckRunner({ snapshot }, evaluator, pipeline),
        history,
        checkLog,
    ) { now }

    private fun publish(generatedAt: Instant) {
        val feed = ThreatFeed.of(generatedAt, listOf(FeedSource("test", "test", 1)), List(2_000) { "h$it.example" }, emptyList())
        val bytes = FeedCodec.encode(feed)
        val sig = Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(keys.private); update(bytes); sign() }
        http.feedResult = { if (it === Endpoint.Feed.SIGNATURE) BytesResult.Ok(sig) else BytesResult.Ok(bytes) }
    }

    @Test fun offlineTheCheckStillRunsAndTheListsAreReportedOldNotAllGood() = runTest {
        val result = daily.run()
        assertEquals(FeedUpdate.NoConnection(FailureReason.Offline), result.feed)
        assertEquals(listOf(Trigger.Daily), checkLog.runs.map { it.trigger })
        assertEquals(listOf(Problem.ListsOld), result.check.findings.map { it.key.problem }) // no feed yet: lists are not current
    }

    @Test fun aPublishedFeedIsStoredAndThenTheListsAreNoLongerOld() = runTest {
        publish(now.minus(Duration.ofHours(2)))
        val result = daily.run()
        assertEquals(FeedUpdate.Updated(now.minus(Duration.ofHours(2))), result.feed)
        assertEquals(emptyList<Problem>(), result.check.findings.map { it.key.problem })
    }

    @Test fun anOldFeedFromADeadPublisherIsNoticedAfterThreeDays() = runTest {
        publish(now.minus(Duration.ofDays(4)))
        val result = daily.run()
        assertEquals(listOf(Problem.ListsOld), result.check.findings.map { it.key.problem })
    }

    @Test fun historyOlderThanNinetyDaysIsDroppedAndRecentKept() = runTest {
        history.add(HistoryEntry(now.minus(Duration.ofDays(91)), Problem.ScamLink, Outcome.MomWarned))
        history.add(HistoryEntry(now.minus(Duration.ofDays(89)), Problem.ScamLink, Outcome.MomWarned))
        assertEquals(1, daily.run().historyPruned)
        assertEquals(listOf(now.minus(Duration.ofDays(89))), history.entries.map { it.at })
    }

    @Test fun theVeryFirstRunDownloadsTheFeedAndChecksButANormalDayDoesNotDoItTwice() = runTest {
        publish(now.minus(Duration.ofHours(1)))
        var downloads = 0
        val serve = http.feedResult
        http.feedResult = { downloads++; serve(it) }

        val first = daily.runIfNeverRun()
        assertEquals(FeedUpdate.Updated(now.minus(Duration.ofHours(1))), first!!.feed)
        assertEquals(listOf(Trigger.Daily), checkLog.runs.map { it.trigger })
        val downloadsAfterFirst = downloads

        assertEquals(null, daily.runIfNeverRun()) // a Daily check exists now
        assertEquals(downloadsAfterFirst, downloads)
        assertEquals(1, checkLog.runs.size)
    }

    @Test fun aNotificationCheckAloneDoesNotCountAsTheFirstDailyCheck() = runTest {
        checkLog.record(com.aegida.core.health.CheckRun(Trigger.NotificationPosted, now, now))
        publish(now)
        assertEquals(FeedUpdate.Updated(now), daily.runIfNeverRun()!!.feed)
    }
}
