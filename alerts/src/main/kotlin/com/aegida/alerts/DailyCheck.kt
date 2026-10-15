package com.aegida.alerts

import com.aegida.core.alert.FindingStore
import com.aegida.core.alert.HistoryLog
import com.aegida.core.feed.FeedUpdate
import com.aegida.core.feed.FeedUpdater
import com.aegida.core.health.CheckLog
import com.aegida.core.health.ListenerBinding
import com.aegida.core.health.ListenerTimes
import com.aegida.core.health.Trigger
import com.aegida.core.notice.NoticeLog
import com.aegida.core.notice.NoticeRules
import com.aegida.core.store.FeedStore
import com.aegida.core.model.Setup
import com.aegida.core.store.SetupStore
import com.aegida.detectors.HealthDetector
import com.aegida.core.time.Clock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/** [feed] is null when the download was skipped (mobile data, lists still fresh). */
data class DailyResult(
    val feed: FeedUpdate?,
    val check: CheckOutcome,
    val historyPruned: Int,
    val heartbeat: SendResult?,
    val checkRunsPruned: Int = 0,
    val findingsPruned: Int = 0,
)

/**
 * The daily job. The check always runs, even offline: a failed or missing feed update only means the lists
 * get older, which HealthDetector reports after 3 days. History and resolved findings older than 90 days are dropped, and so are
 * check-log rows older than 30 days (the newest daily one is always kept). The 9 MB feed is downloaded only when
 * the caller says the network is not metered, or when the stored lists are older than HealthDetector's limit. When the
 * notification listener is not bound and access is on, Android is asked to bind it again (finding #134): the app can repair itself.
 * After setup, the check then waits until it is bound or Android has had time to bind it (findings #136, #142).
 */
class DailyCheck @Inject constructor(
    private val feed: DailyFeed,
    private val runner: CheckRunner,
    private val pruning: DailyPruning,
    private val heartbeat: DailyHeartbeat,
    private val checkLog: CheckLog,
    private val listener: ListenerRepair = ListenerRepair.None,
) {
    suspend fun run(downloadFeed: Boolean = true): DailyResult {
        val askedAt = listener.askIfUnbound()
        val feedUpdate = feed.update(downloadFeed)
        if (askedAt != null) listener.waitForTheBind(askedAt)
        val check = runner.run(Trigger.Daily)
        val pruned = pruning.prune()
        return DailyResult(feedUpdate, check, pruned.history, heartbeat.keepInStep(), pruned.checkRuns, pruned.findings)
    }

    /**
     * The first full check, right after install, so Home does not say "hasn't checked yet" until tomorrow's job.
     * Null when a daily check has already run (nothing is downloaded then).
     */
    suspend fun runIfNeverRun(): DailyResult? = if (checkLog.latest(Trigger.Daily) == null) run() else null
}

/** The daily job's part in keeping the notification listener bound (findings #134, #136, #142). */
class ListenerRepair @Inject constructor(private val binding: ListenerBinding, private val setup: SetupStore, private val clock: Clock) {
    /**
     * Asks Android to bind the listener again when it is not bound now and access is on; the time it asked, or null if it did not.
     * With access off Android cannot bind it, and the check says so (ListenerOff).
     */
    fun askIfUnbound(): Instant? {
        if (binding.boundNow || !binding.accessOn) return null
        binding.requestRebind()
        return clock.now()
    }

    /**
     * After asking at [askedAt], waits until Android has bound the listener, at most [ListenerTimes.NEW_PROCESS_GRACE] from asking.
     * A check in a new process's first minutes cannot raise "can't see your notices" (finding #136), so without this a phone where
     * only the job starts Aegida would never show it; in a process that has run for long, checking before the bind it asked for
     * would show the step for a moment (finding #142). Before setup is done nothing about the listener is raised, so it does not
     * wait. The time is read once: the wait is counted in its own steps, so a clock changed meanwhile cannot make it endless.
     */
    suspend fun waitForTheBind(askedAt: Instant) {
        if (!setup.setup().done) return
        // At most the grace, also when the clock was set back since asking (finding #146 a): WorkManager stops a job after 10 minutes.
        var left = minOf(Duration.between(clock.now(), askedAt.plus(ListenerTimes.NEW_PROCESS_GRACE)).toMillis(), ListenerTimes.NEW_PROCESS_GRACE.toMillis())
        while (!binding.boundNow && left > 0) {
            val step = minOf(left, BIND_POLL_MS)
            delay(step)
            left -= step
        }
    }

    companion object {
        /** How often the wait looks whether Android has bound the listener. */
        const val BIND_POLL_MS = 1_000L

        /** The listener is always bound, so it never asks or waits; for code and tests that do not look at the listener. */
        val None = ListenerRepair(
            ListenerBinding.None,
            object : SetupStore {
                override suspend fun setup() = Setup.NotDone
                override suspend fun save(setup: Setup) = Unit
            },
        ) { Instant.EPOCH }
    }
}

/** The daily feed download: skipped (null) on mobile data while the stored lists are still fresh. */
class DailyFeed @Inject constructor(
    private val feedUpdater: FeedUpdater,
    private val feedStore: FeedStore,
    private val clock: Clock,
) {
    suspend fun update(downloadFeed: Boolean): FeedUpdate? = if (downloadFeed || listsTooOld()) feedUpdater.update() else null

    private suspend fun listsTooOld(): Boolean {
        val stored = feedStore.current() ?: return true
        return Duration.between(stored.generatedAt, clock.now()) > HealthDetector.LISTS_OLD
    }
}

/** How many rows each daily clean-up dropped. */
data class Pruned(val history: Int, val findings: Int, val checkRuns: Int)

/** The daily clean-up of everything that is kept only for a while. */
class DailyPruning @Inject constructor(
    private val history: HistoryLog,
    private val findings: FindingStore,
    private val checkLog: CheckLog,
    private val notices: NoticeLog = NoticeLog.None,
    private val clock: Clock,
) {
    suspend fun prune(): Pruned {
        val now = clock.now()
        val pruned = history.prune(now.minus(HISTORY_KEPT))
        val findingsPruned = findings.prune(now.minus(HISTORY_KEPT), now.plus(NoticeRules.CLOCK_SKEW))
        val runsPruned = checkLog.prune(now.minus(CHECK_LOG_KEPT))
        notices.prune(now.minus(NoticeRules.WEEK), now.plus(NoticeRules.CLOCK_SKEW)) // older than a week, or from a clock that was ahead (findings #92, #93)
        return Pruned(pruned, findingsPruned, runsPruned)
    }

    companion object {
        val HISTORY_KEPT: Duration = Duration.ofDays(90)
        val CHECK_LOG_KEPT: Duration = Duration.ofDays(30)
    }
}

/**
 * Checked in. Alerts on: push the "hasn't checked in" message two days further out. Alerts off: cancel it, retried daily
 * until it works once, then nothing while alerts stay off (findings #95). A welcome that failed is retried here too.
 */
class DailyHeartbeat @Inject constructor(
    private val switch: DeadMansSwitch,
    private val guardianAlerts: GuardianAlerts,
    private val gate: AlertGate,
) {
    suspend fun keepInStep(): SendResult? = gate.run {
        guardianAlerts.retryWelcome()
        switch.keepInStep()
    }
}
