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

    /** Finding #92: each counted notice is written down, and the third ad-like one in a day flags the app. */
    @Test fun threeAdLikeNoticesInADayFlagTheApp() = runTest {
        val notices = FakeNoticeLog()
        val e = Evaluator(setOf(NoisyAppDetector()), { now }, FakeSetupStore(), FakeFeedStore(Fixtures.feed()), log, outbox, FakeBaselines(), notices)
        fun ad(n: Int) = Fixtures.message("File converter is ready! Convert", title = "Image to PDF").copy(key = "0|com.android.shell|$n|null|1", importance = 4, whenMillis = n.toLong())
        assertEquals(emptyList<Problem>(), e.run(Trigger.NotificationPosted, snapshot(), ad(1)).map { it.key.problem })
        assertEquals(emptyList<Problem>(), e.run(Trigger.NotificationPosted, snapshot(), ad(2)).map { it.key.problem })
        assertEquals(listOf(Problem.NoisyApp), e.run(Trigger.NotificationPosted, snapshot(), ad(3)).map { it.key.problem })
        assertEquals(3, notices.rows.size)
    }

    @Test fun theSameNoticeComingBackFromASnoozeIsNotCountedAgain() = runTest { // finding #93
        val notices = FakeNoticeLog()
        val e = Evaluator(setOf(NoisyAppDetector()), { now }, FakeSetupStore(), FakeFeedStore(Fixtures.feed()), log, outbox, FakeBaselines(), notices)
        val ad = Fixtures.message("File converter is ready! Convert").copy(key = "0|x|1|null|1", importance = 4, whenMillis = 42)
        repeat(3) { e.run(Trigger.NotificationPosted, snapshot(), ad) }
        assertEquals(1, notices.rows.size)
    }

    @Test fun aNoticeThatIsNotCountedIsNotWrittenDown() = runTest {
        val notices = FakeNoticeLog()
        val e = Evaluator(setOf(NoisyAppDetector()), { now }, FakeSetupStore(), FakeFeedStore(Fixtures.feed()), log, outbox, FakeBaselines(), notices)
        e.run(Trigger.NotificationPosted, snapshot(), Fixtures.message("hi", title = "Mom").copy(category = "msg"))
        e.run(Trigger.Daily, snapshot())
        assertEquals(emptyList<Any>(), notices.rows)
    }

    @Test fun anAppTheOwnerSaidIsFineIsNotFlaggedEvenAfterManyNotices() = runTest {
        val allow = FakeAllowList().apply { items += com.aegida.core.notice.AllowedItem(Problem.NoisyApp, "com.android.shell", "Image to PDF", now) }
        val e = Evaluator(setOf(NoisyAppDetector()), { now }, FakeSetupStore(), FakeFeedStore(Fixtures.feed()), log, outbox, FakeBaselines(), FakeNoticeLog(), allow)
        repeat(5) { n ->
            val ad = Fixtures.message("File converter is ready! Convert").copy(key = "0|x|$n|null|1", importance = 4, whenMillis = n.toLong())
            assertEquals(emptyList<Problem>(), e.run(Trigger.NotificationPosted, snapshot(), ad).map { it.key.problem })
        }
    }

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
