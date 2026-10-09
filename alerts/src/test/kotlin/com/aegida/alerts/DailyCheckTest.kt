package com.aegida.alerts

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.Outcome
import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.FeedUpdate
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.feed.FeedVerifier
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.net.HttpResult
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.FindingState
import com.aegida.core.model.PrivateDns
import com.aegida.core.model.Snapshot
import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.NtfyTopic
import com.aegida.core.alert.Guardians
import com.aegida.core.oem.Reading
import com.aegida.core.text.Problem
import com.aegida.detectors.DnsDetector
import com.aegida.detectors.Evaluator
import com.aegida.detectors.HealthDetector
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
    private val outbox = FakeOutbox()
    private val topic = NtfyTopic("aegida_" + "x9Y8z7W6".repeat(4))
    private val guardians = FakeGuardians(Guardians(alertsOn = true, ownerName = "Mom", firstTopic = topic, firstGuardianName = "Kevin", firstGuardianEmail = "k@example.com", firstGuardianPhone = ""))
    private val owner = Owner()

    private val evaluator = Evaluator(setOf(DnsDetector(), HealthDetector()), { now }, FakeSetupStore(), feedStore, checkLog, outbox, FakeBaselines())
    private val pipeline = AlertPipeline(findings, owner, history, guardians, NtfySender(http), outbox) { now }
    private val snapshot = Snapshot(now, PrivateDns.Automatic, "com.android.chrome", true, true, Reading.NotPresent, Reading.NotPresent)
    private val sender = NtfySender(http)
    private val switch = DeadMansSwitch(guardians, sender) { now }
    private val guardianAlerts = GuardianAlerts(guardians, sender, switch, AlertGate())
    private val daily = DailyCheck(
        FeedUpdater(http, FeedVerifier(keys.public.encoded), feedStore) { now },
        CheckRunner({ snapshot }, evaluator, pipeline, BaselineKeeper(FakeSetupStore(), FakeBaselines()), OutboxFlusher(outbox, NtfySender(http), history, guardians, { now }), AlertGate()),
        history,
        findings,
        checkLog,
        feedStore,
        switch,
        guardianAlerts,
        AlertGate(),
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

    @Test fun everyDailyCheckPushesTheNotCheckedInMessageTwoDaysOutAndAlertsOffSendsNothing() = runTest {
        daily.run()
        val (endpoint, _, headers) = http.posts.single()
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", endpoint.url)
        assertEquals("2880m", headers["X-Delay"])
        guardians.value = guardians.value.copy(alertsOn = false)
        http.posts.clear()
        daily.run()
        assertEquals(0, http.posts.size)
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", http.deleted.single().url) // and the waiting message is cancelled again
    }

    @Test fun aCancelThatFailedOfflineIsSentAgainByTheNextDailyCheckUntilItWorks() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        assertEquals(SendResult.NotDelivered(HttpResult.Failed(null, FailureReason.Offline)), daily.run().heartbeat)
        http.result = HttpResult.Ok(200, "{}")
        assertEquals(SendResult.Delivered, daily.run().heartbeat)
        assertEquals(2, http.deleted.size)
        assertEquals(0, http.posts.size)
    }

    @Test fun aWelcomeThatFailedOfflineIsSentByTheNextDailyCheckOnceAndNotAgain() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        guardianAlerts.setAlertsOn(true) // welcome and heartbeat both fail
        http.result = HttpResult.Ok(200, "{}")
        http.posts.clear()
        daily.run()
        assertEquals(listOf("https://ntfy.sh/${topic.value}", "https://ntfy.sh/${topic.value}/heartbeat"), http.posts.map { it.first.url })
        http.posts.clear()
        daily.run()
        assertEquals(listOf("https://ntfy.sh/${topic.value}/heartbeat"), http.posts.map { it.first.url })
    }

    @Test fun aPendingWelcomeIsNotSentToAGuardianWhoWasChangedOrWhenAlertsAreOff() = runTest {
        guardians.value = guardians.value.copy(alertsOn = false)
        http.result = HttpResult.Failed(null, FailureReason.Offline)
        guardianAlerts.setAlertsOn(true)
        http.result = HttpResult.Ok(200, "{}")
        guardians.value = guardians.value.copy(firstTopic = NtfyTopic("aegida_" + "q1w2e3r4".repeat(4)))
        http.posts.clear()
        daily.run()
        assertEquals(listOf("heartbeat"), http.posts.map { it.first.url.substringAfterLast('/') })
    }

    @Test fun onMobileDataFreshListsAreNotDownloadedButTheCheckAndHeartbeatStillRun() = runTest {
        publish(now.minus(Duration.ofHours(5)))
        daily.run() // Wi-Fi day: lists stored
        var downloads = 0
        val serve = http.feedResult
        http.feedResult = { downloads++; serve(it) }
        http.posts.clear()
        checkLog.runs.clear()
        val result = daily.run(downloadFeed = false)
        assertEquals(null, result.feed)
        assertEquals(0, downloads)
        assertEquals(listOf(Trigger.Daily), checkLog.runs.map { it.trigger })
        assertEquals("heartbeat", http.posts.single().first.url.substringAfterLast('/'))
    }

    @Test fun onMobileDataListsOlderThanThreeDaysAreDownloadedAnyway() = runTest {
        publish(now.minus(Duration.ofDays(4)))
        daily.run()
        publish(now.minus(Duration.ofHours(1)))
        assertEquals(FeedUpdate.Updated(now.minus(Duration.ofHours(1))), daily.run(downloadFeed = false).feed)
    }

    @Test fun onMobileDataWithNoListsAtAllTheyAreDownloaded() = runTest {
        publish(now.minus(Duration.ofHours(1)))
        assertEquals(FeedUpdate.Updated(now.minus(Duration.ofHours(1))), daily.run(downloadFeed = false).feed)
    }

    @Test fun resolvedFindingsOlderThanNinetyDaysAreDroppedWithHistory() = runTest { // findings #81 (c)
        fun resolved(subject: String, daysAgo: Long) = Finding(
            FindingKey(DetectorId.PopupSpam, Problem.SiteNotifications, subject), emptyMap(),
            now.minus(Duration.ofDays(daysAgo + 1)), now.minus(Duration.ofDays(daysAgo)), FindingState.Resolved,
        )
        findings.put(resolved("http://old.example", 91))
        findings.put(resolved("http://recent.example", 89))
        val result = daily.run()
        assertEquals(1, result.findingsPruned)
        val subjects = findings.rows.keys.map { it.subject }
        assertTrue("http://recent.example" in subjects)
        assertTrue("http://old.example" !in subjects)
    }

    @Test fun checkRunsOlderThanThirtyDaysAreDroppedAndTheNewestDailyOneIsKept() = runTest {
        checkLog.record(CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofDays(31)), now.minus(Duration.ofDays(31))))
        checkLog.record(CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofDays(29)), now.minus(Duration.ofDays(29))))
        assertEquals(1, daily.run().checkRunsPruned)
        assertEquals(listOf(Trigger.NotificationPosted, Trigger.Daily), checkLog.runs.map { it.trigger })
    }

    @Test fun aPhoneThatWasQuietForMonthsKeepsItsLastDailyRunSoTheMissedCheckStillShows() = runTest {
        val old = CheckRun(Trigger.Daily, now.minus(Duration.ofDays(60)), now.minus(Duration.ofDays(60)))
        checkLog.record(old)
        assertEquals(0, checkLog.prune(now.minus(Duration.ofDays(30))))
        assertEquals(old, checkLog.latest(Trigger.Daily))
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
        history.add(HistoryEntry(now.minus(Duration.ofDays(91)), Problem.ScamLink, Outcome.OwnerWarned))
        history.add(HistoryEntry(now.minus(Duration.ofDays(89)), Problem.ScamLink, Outcome.OwnerWarned))
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
