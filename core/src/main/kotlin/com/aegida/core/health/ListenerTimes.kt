package com.aegida.core.health

import java.time.Duration
import java.time.Instant

/**
 * When the system last bound and unbound Aegida's notification listener (findings #111, #112). Null: not recorded yet (a new
 * install, or an update from a version that did not record it).
 */
data class ListenerTimes(val connectedAt: Instant? = null, val disconnectedAt: Instant? = null) {
    /** The last thing recorded was the system unbinding it (at the same moment as a bind counts as after it). */
    val lastWasDisconnect: Boolean
        get() = disconnectedAt != null && (connectedAt == null || !disconnectedAt.isBefore(connectedAt))

    /**
     * Since when Aegida has not been hearing notices, or null if it is (or nothing was recorded). A phone restarted after the last
     * bind counts from the restart, so the hour starts again after every restart. A time ahead of [now] (clock set back) never counts.
     */
    fun notHearingSince(now: Instant, bootedAt: Instant?): Instant? {
        val since = when {
            lastWasDisconnect -> maxOf(disconnectedAt!!, bootedAt ?: disconnectedAt)
            connectedAt != null && bootedAt != null && connectedAt.isBefore(bootedAt) -> bootedAt
            else -> null
        }
        return since?.takeIf { !it.isAfter(now) }
    }

    /** Not hearing for longer than [LOST_AFTER]. */
    fun lost(now: Instant, bootedAt: Instant?): Boolean =
        notHearingSince(now, bootedAt)?.let { Duration.between(it, now) > LOST_AFTER } ?: false

    companion object {
        /** The phone often unbinds and binds again within minutes (an update, low memory); only an hour without it is a problem. */
        val LOST_AFTER: Duration = Duration.ofHours(1)
    }
}

/** Where [ListenerTimes] are kept between runs. Implemented by :store (two named settings, no new table). */
interface ListenerTimesStore {
    suspend fun times(): ListenerTimes
    suspend fun connected(at: Instant)
    suspend fun disconnected(at: Instant)

    /** Records nothing; for code and tests that do not look at the listener. */
    object None : ListenerTimesStore {
        override suspend fun times() = ListenerTimes()
        override suspend fun connected(at: Instant) = Unit
        override suspend fun disconnected(at: Instant) = Unit
    }
}
