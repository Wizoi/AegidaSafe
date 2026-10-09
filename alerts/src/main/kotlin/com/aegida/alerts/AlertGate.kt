package com.aegida.alerts

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One lock for everything that reads or changes alert state: a check (snapshot, findings, banners, guardian
 * messages, the outbox), the owner's taps on a finding, the guardian alerts switch and a change of guardian.
 * Without it a burst of notifications, the daily job and a tap interleave: duplicate guardian messages, a
 * finding she just marked seen written back as open (findings #49). It is not reentrant: only the public entry
 * points take it, and they never call each other.
 */
@Singleton
class AlertGate @Inject constructor() {
    private val mutex = Mutex()
    suspend fun <T> run(block: suspend () -> T): T = mutex.withLock { block() }
}
