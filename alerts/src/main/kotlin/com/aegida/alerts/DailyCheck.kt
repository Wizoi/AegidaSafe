package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryLog
import com.aegida.core.feed.FeedUpdate
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.health.CheckLog
import com.aegida.core.health.Trigger
import com.aegida.core.notice.NoticeLog
import com.aegida.core.notice.NoticeRules
import com.aegida.core.store.FeedStore
import com.aegida.detectors.HealthDetector
import com.aegida.core.time.Clock
import java.time.Duration
import javax.inject.Inject

/** [feed] is null when the download was skipped (mobile data, lists still fresh). */
data class DailyResult(val feed: FeedUpdate?, val check: CheckOutcome, val historyPruned: Int, val heartbeat: SendResult?, val checkRunsPruned: Int = 0, val findingsPruned: Int = 0)

/**
 * The daily job. The check always runs, even offline: a failed or missing feed update only means the lists
 * get older, which HealthDetector reports after 3 days. History and resolved findings older than 90 days are dropped, and so are
 * check-log rows older than 30 days (the newest daily one is always kept). The 9 MB feed is downloaded only when
 * the caller says the network is not metered, or when the stored lists are older than HealthDetector's limit.
 */
class DailyCheck @Inject constructor(
    private val feedUpdater: FeedUpdater,
    private val runner: CheckRunner,
    private val history: HistoryLog,
    private val findings: FindingStore,
    private val checkLog: CheckLog,
    private val feedStore: FeedStore,
    private val switch: DeadMansSwitch,
    private val guardianAlerts: GuardianAlerts,
    private val gate: AlertGate,
    private val notices: NoticeLog = NoticeLog.None,
    private val clock: Clock,
) {
    suspend fun run(downloadFeed: Boolean = true): DailyResult {
        val feed = if (downloadFeed || listsTooOld()) feedUpdater.update() else null
        val check = runner.run(Trigger.Daily)
        val now = clock.now()
        val pruned = history.prune(now.minus(HISTORY_KEPT))
        val findingsPruned = findings.prune(now.minus(HISTORY_KEPT))
        val runsPruned = checkLog.prune(now.minus(CHECK_LOG_KEPT))
        notices.prune(now.minus(NoticeRules.WEEK), now.plus(NoticeRules.CLOCK_SKEW)) // older than a week, or from a clock that was ahead (findings #92, #93)
        // Checked in. Alerts on: push the "hasn't checked in" message two days further out. Alerts off: cancel it again,
        // which retries a cancel that failed offline (always sent while off; one tiny request a day). A welcome that failed is retried here too.
        val heartbeat = gate.run {
            guardianAlerts.retryWelcome()
            switch.keepInStep()
        }
        return DailyResult(feed, check, pruned, heartbeat, runsPruned, findingsPruned)
    }

    private suspend fun listsTooOld(): Boolean {
        val stored = feedStore.current() ?: return true
        return Duration.between(stored.generatedAt, clock.now()) > HealthDetector.LISTS_OLD
    }

    /**
     * The first full check, right after install, so Home does not say "hasn't checked yet" until tomorrow's job.
     * Null when a daily check has already run (nothing is downloaded then).
     */
    suspend fun runIfNeverRun(): DailyResult? = if (checkLog.latest(Trigger.Daily) == null) run() else null

    companion object {
        val HISTORY_KEPT: Duration = Duration.ofDays(90)
        val CHECK_LOG_KEPT: Duration = Duration.ofDays(30)
    }
}
