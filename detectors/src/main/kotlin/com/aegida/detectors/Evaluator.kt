package com.aegida.detectors

import com.aegida.core.alert.Outbox
import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.Snapshot
import com.aegida.core.notice.AllowList
import com.aegida.core.notice.NoticeLog
import com.aegida.core.notice.NoticeRow
import com.aegida.core.notice.NoticeRules
import com.aegida.core.text.Problem
import com.aegida.core.store.BaselineStore
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import com.aegida.core.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import javax.inject.Inject

/**
 * Every trigger (worker, listener, package broadcast, app start) calls [run]; nothing else asks the
 * detectors. Runs are serialised, and running twice on the same inputs gives the same findings. Each run
 * is recorded so HealthDetector can tell when the daily check stopped happening.
 */
class Evaluator @Inject constructor(
    private val detectors: Set<@JvmSuppressWildcards Detector>,
    private val clock: Clock,
    private val memory: EvaluatorMemory,
    private val notices: NoticeLog = NoticeLog.None,
    private val allowList: AllowList = AllowList.None,
) {
    private val mutex = Mutex()

    /** Every run evaluates every detector, so these are the detectors a run has spoken for. */
    val detectorIds: Set<DetectorId> = detectors.map { it.id }.toSet()

    suspend fun run(trigger: Trigger, snapshot: Snapshot, event: NotificationEvent? = null): List<Finding> = mutex.withLock {
        val started = clock.now()
        val counted = event != null && NoticeRules.counted(event, snapshot.apps, setOfNotNull(snapshot.smsApp, snapshot.dialerApp, snapshot.browser))
        // A notice that counts is written down before the detectors look (finding #92); only counts and signals, never its words.
        val recent = if (event != null && counted) {
            val category = snapshot.apps.firstOrNull { it.packageName == event.packageName }?.category?.takeIf { it >= 0 }
            notices.record(event.packageName, event.key, event.whenMillis, started, NoticeRules.signals(event, category))
            notices.since(event.packageName, started.minus(NoticeRules.WEEK))
        } else {
            emptyList()
        }
        val allowedApps = allowList.all().filter { it.problem == Problem.NoisyApp }.map { it.subject }.toSet()
        val input = memory.inputs(snapshot, started, event, recent, allowedApps).copy(bootedAt = clock.bootedAt())
        val findings = detectors.sortedBy { it.id }.flatMap { it.evaluate(input) }.distinctBy { it.key }
        memory.record(CheckRun(trigger, started, clock.now()))
        findings
    }
}

/**
 * What the phone remembers between runs (setup, lists, the check log, the outbox, the baseline), read into one [Inputs] and the run
 * written back. A check that runs while the listener is bound records that it saw it bound (finding #134).
 */
class EvaluatorMemory @Inject constructor(
    private val setupStore: SetupStore,
    private val feedStore: FeedStore,
    private val checkLog: CheckLog,
    private val outbox: Outbox,
    private val baselines: BaselineStore,
    private val listener: ListenerTimesStore = ListenerTimesStore.None,
) {
    suspend fun inputs(snapshot: Snapshot, started: Instant, event: NotificationEvent?, recent: List<NoticeRow>, allowedApps: Set<String>): Inputs {
        if (snapshot.listenerBound) listener.seenBound(started)
        return Inputs(
            snapshot, started, setupStore.setup(), feedStore.current(), event, checkLog.latest(Trigger.Daily), outbox.all().minOfOrNull { it.queuedAt },
            baselines.baseline(), recent, allowedApps, listener.times(),
        )
    }

    suspend fun record(run: CheckRun) = checkLog.record(run)
}
