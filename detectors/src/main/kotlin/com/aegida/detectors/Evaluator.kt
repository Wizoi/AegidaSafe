package com.aegida.detectors

import com.aegida.core.model.Finding
import com.aegida.core.model.Snapshot
import com.aegida.core.time.Clock
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * Every trigger (worker, listener, package broadcast, app start) calls [run]; nothing else asks the
 * detectors. Runs are serialised, and running twice on the same snapshot gives the same findings.
 */
class Evaluator @Inject constructor(
    private val detectors: Set<@JvmSuppressWildcards Detector>,
    private val clock: Clock,
) {
    private val mutex = Mutex()

    suspend fun run(snapshot: Snapshot): List<Finding> = mutex.withLock {
        val input = Inputs(snapshot, clock.now())
        detectors.sortedBy { it.id }.flatMap { it.evaluate(input) }.distinctBy { it.key }
    }
}
