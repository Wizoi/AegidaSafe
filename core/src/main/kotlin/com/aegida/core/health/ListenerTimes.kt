package com.aegida.core.health

import java.time.Duration
import java.time.Instant

/**
 * The last time a check saw Aegida's notification listener bound (findings #111, #112, #134). Null: never seen (a new install,
 * or an update from a version that did not record it). Android does not reliably call onListenerDisconnected (on the emulator it
 * never did, finding #134), so nothing here depends on an unbind being recorded: every check that runs while the listener is bound
 * moves this time on, and every notice the listener hears runs such a check.
 *
 * [checkedAtLastSeen]: the keys of late notices posted exactly at [lastSeenBound] whose check has finished (finding #146 c). Two notices
 * can share a post time (a group summary and its child); after a kill between them, the one not yet checked is checked at the next
 * bind and the other is not checked again.
 */
data class ListenerTimes(val lastSeenBound: Instant? = null, val checkedAtLastSeen: Set<String> = emptySet()) {
    /** A notice posted at [postedAt] with [key] has not been checked yet: posted after [lastSeenBound], or at it and not checked. */
    fun notCheckedYet(key: String, postedAt: Instant): Boolean {
        val since = lastSeenBound ?: return false
        return postedAt.isAfter(since) || (postedAt == since && key !in checkedAtLastSeen)
    }

    /**
     * Since when Aegida has not been hearing notices, given that it is not bound now: the latest of the last time it was seen
     * bound, the phone's start [bootedAt] and Aegida's install or update [updatedAt]. So the hour starts again after every restart
     * and every update. Null when none of them is known. A time ahead of [now] (clock set back) gives a negative age, so
     * [lost] never counts it.
     */
    private fun notHearingSince(bootedAt: Instant?, updatedAt: Instant?): Instant? =
        listOfNotNull(lastSeenBound, bootedAt, updatedAt).maxOrNull()

    /** Not bound now ([boundNow] false) and not seen bound for longer than [LOST_AFTER]. */
    fun lost(boundNow: Boolean, now: Instant, bootedAt: Instant?, updatedAt: Instant?): Boolean =
        !boundNow && notHearingSince(bootedAt, updatedAt)?.let { Duration.between(it, now) > LOST_AFTER } ?: false

    companion object {
        /** The phone often unbinds and binds again within minutes (an update, low memory); only an hour without it is a problem. */
        val LOST_AFTER: Duration = Duration.ofHours(1)

        /**
         * Android binds the listener within seconds of starting the app process, so for this long after it started "not bound" may
         * only mean "not bound yet" (finding #136).
         */
        val NEW_PROCESS_GRACE: Duration = Duration.ofMinutes(2)

        /** This process started less than [NEW_PROCESS_GRACE] before [now]. False when it is not known when it started. */
        fun inNewProcessGrace(now: Instant, processStartedAt: Instant?): Boolean =
            processStartedAt != null && !processStartedAt.isAfter(now) && Duration.between(processStartedAt, now) < NEW_PROCESS_GRACE
    }
}

/** Where [ListenerTimes] are kept between runs. Implemented by :store (one named setting, no new table). */
interface ListenerTimesStore {
    suspend fun times(): ListenerTimes

    /**
     * A check asked for at [at] finished while the listener was bound, or a late check of a notice posted at [at] finished; [checked]:
     * the keys of the late notices posted at [at] checked so far (replaces what was kept, see [ListenerTimes.checkedAtLastSeen]).
     */
    suspend fun seenBound(at: Instant, checked: Set<String> = emptySet())

    /** Records nothing; for code and tests that do not look at the listener. */
    object None : ListenerTimesStore {
        override suspend fun times() = ListenerTimes()
        override suspend fun seenBound(at: Instant, checked: Set<String>) = Unit
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

    /** Notification access is on for Aegida's listener: Android can bind it (finding #142). */
    val accessOn: Boolean

    /** Asks Android to bind the listener again; does nothing while notification access is off. */
    fun requestRebind()

    /** Always bound and never asks; for code and tests that do not look at the listener. */
    object None : ListenerBinding {
        override val boundNow = true
        override val accessOn = true
        override fun markBound(bound: Boolean) = Unit
        override fun requestRebind() = Unit
    }
}
