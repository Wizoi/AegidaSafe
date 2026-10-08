package com.aegida.alerts

import com.aegida.core.alert.HistoryLog
import com.aegida.core.feed.FeedUpdate
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.health.Trigger
import com.aegida.core.time.Clock
import java.time.Duration
import javax.inject.Inject

data class DailyResult(val feed: FeedUpdate, val check: CheckOutcome, val historyPruned: Int)

/**
 * The daily job. The check always runs, even offline: a failed or missing feed update only means the lists
 * get older, which HealthDetector reports after 3 days. History older than 90 days is dropped.
 */
class DailyCheck @Inject constructor(
    private val feedUpdater: FeedUpdater,
    private val runner: CheckRunner,
    private val history: HistoryLog,
    private val clock: Clock,
) {
    suspend fun run(): DailyResult {
        val feed = feedUpdater.update()
        val check = runner.run(Trigger.Daily)
        val pruned = history.prune(clock.now().minus(HISTORY_KEPT))
        return DailyResult(feed, check, pruned)
    }

    companion object {
        val HISTORY_KEPT: Duration = Duration.ofDays(90)
    }
}
