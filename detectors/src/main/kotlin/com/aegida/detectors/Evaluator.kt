package com.aegida.detectors

import com.aegida.core.health.CheckLog
import com.aegida.core.health.CheckRun
import com.aegida.core.health.Trigger
import com.aegida.core.model.Finding
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.Snapshot
import com.aegida.core.store.FeedStore
import com.aegida.core.store.SetupStore
import com.aegida.core.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Every trigger (worker, listener, package broadcast, app start) calls [run]; nothing else asks the
 * detectors. Runs are serialised, and running twice on the same inputs gives the same findings. Each run
 * is recorded so HealthDetector can tell when the daily check stopped happening.
 */
class Evaluator @Inject constructor(
    private val detectors: Set<@JvmSuppressWildcards Detector>,
    private val clock: Clock,
    private val setupStore: SetupStore,
    private val feedStore: FeedStore,
    private val checkLog: CheckLog,
) {
    private val mutex = Mutex()

    suspend fun run(trigger: Trigger, snapshot: Snapshot, event: NotificationEvent? = null): List<Finding> = mutex.withLock {
        val started = clock.now()
        val input = Inputs(snapshot, started, setupStore.setup(), feedStore.current(), event, checkLog.latest(Trigger.Daily))
        val findings = detectors.sortedBy { it.id }.flatMap { it.evaluate(input) }.distinctBy { it.key }
        checkLog.record(CheckRun(trigger, started, clock.now()))
        findings
    }
}
