package com.aegida.core.health

import java.time.Duration
import java.time.Instant

/**
 * The last time a check saw Aegida's notification listener bound (findings #111, #112, #134). Null: never seen (a new install,
 * or an update from a version that did not record it). Android does not reliably call onListenerDisconnected (on the emulator it
 * never did, finding #134), so nothing here depends on an unbind being recorded: every check that runs while the listener is bound
 * moves this time on, and every notice the listener hears runs such a check.
 */
data class ListenerTimes(val lastSeenBound: Instant? = null) {
    /**
     * Since when Aegida has not been hearing notices, given that it is not bound now: the latest of the last time it was seen
     * bound, the phone's start [bootedAt] and Aegida's install or update [updatedAt]. So the hour starts again after every restart
     * and every update. Null when none of them is known. A time ahead of [now] (clock set back) never counts.
     */
    fun notHearingSince(now: Instant, bootedAt: Instant?, updatedAt: Instant?): Instant? =
        listOfNotNull(lastSeenBound, bootedAt, updatedAt).maxOrNull()?.takeIf { !it.isAfter(now) }

    /** Not bound now ([boundNow] false) and not seen bound for longer than [LOST_AFTER]. */
    fun lost(boundNow: Boolean, now: Instant, bootedAt: Instant?, updatedAt: Instant?): Boolean =
        !boundNow && notHearingSince(now, bootedAt, updatedAt)?.let { Duration.between(it, now) > LOST_AFTER } ?: false

    companion object {
        /** The phone often unbinds and binds again within minutes (an update, low memory); only an hour without it is a problem. */
        val LOST_AFTER: Duration = Duration.ofHours(1)
    }
}

/** Where [ListenerTimes] are kept between runs. Implemented by :store (one named setting, no new table). */
interface ListenerTimesStore {
    suspend fun times(): ListenerTimes

    /** A check ran at [at] while the listener was bound. */
    suspend fun seenBound(at: Instant)

    /** Records nothing; for code and tests that do not look at the listener. */
    object None : ListenerTimesStore {
        override suspend fun times() = ListenerTimes()
        override suspend fun seenBound(at: Instant) = Unit
    }
}

/**
 * Whether the system has Aegida's notification listener bound in this process right now (finding #134). The listener runs in the
 * app process, so a check reads what the listener service last said. Implemented by :platform.
 */
interface ListenerBinding {
    /** The listener service said it was bound and has not said it was unbound or destroyed since. False in a new process. */
    val boundNow: Boolean

    /** Set by the listener service (bound in onListenerConnected; unbound in onListenerDisconnected and onDestroy). */
    fun markBound(bound: Boolean)

    /** Asks Android to bind the listener again; does nothing while notification access is off. */
    fun requestRebind()

    /** Always bound and never asks; for code and tests that do not look at the listener. */
    object None : ListenerBinding {
        override val boundNow = true
        override fun markBound(bound: Boolean) = Unit
        override fun requestRebind() = Unit
    }
}
