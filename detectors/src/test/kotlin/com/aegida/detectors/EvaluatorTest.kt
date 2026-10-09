package com.aegida.detectors

import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.text.Problem
import com.aegida.detectors.Fixtures.chromeSiteNotification
import com.aegida.detectors.Fixtures.now
import com.aegida.detectors.Fixtures.snapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration

class EvaluatorTest {
    private val log = FakeCheckLog()
    private val outbox = FakeOutbox()
    private val all = setOf(LinkDetector(), PopupSpamDetector(), DnsDetector(), HealthDetector())
    private fun evaluator(detectors: Set<Detector> = all) =
        Evaluator(detectors, { now }, FakeSetupStore(), FakeFeedStore(Fixtures.feed()), log, outbox, FakeBaselines())

    @Test fun runningTwiceGivesTheSameFindings() = runTest {
        val e = evaluator()
        assertEquals(e.run(Trigger.NotificationPosted, snapshot(), chromeSiteNotification), e.run(Trigger.NotificationPosted, snapshot(), chromeSiteNotification))
    }

    @Test fun twoTriggersAtOnceBothGetTheFullResult() = runTest {
        val e = evaluator()
        val results = listOf(
            async { e.run(Trigger.NotificationPosted, snapshot(), chromeSiteNotification) },
            async { e.run(Trigger.NotificationPosted, snapshot(), chromeSiteNotification) },
        ).awaitAll()
        assertEquals(listOf(Problem.SiteNotifications), results[0].map { it.key.problem })
        assertEquals(results[0], results[1])
        assertEquals(2, log.runs.size)
    }

    @Test fun everyRunIsRecorded() = runTest {
        evaluator().run(Trigger.Daily, snapshot())
        assertEquals(listOf(CheckRun(Trigger.Daily, now, now)), log.runs)
    }

    @Test fun frequentNotificationRunsDoNotHideAMissedDailyCheck() = runTest {
        log.record(CheckRun(Trigger.Daily, now.minus(Duration.ofDays(2)), now.minus(Duration.ofDays(2))))
        log.record(CheckRun(Trigger.NotificationPosted, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5))))
        val problems = evaluator().run(Trigger.AppStart, snapshot()).map { it.key.problem }
        assertEquals(listOf(Problem.MissedCheck), problems)
    }
}
