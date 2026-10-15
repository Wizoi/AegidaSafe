package com.aegida.alerts

import com.aegida.core.health.ListenerBinding
import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.NotificationEvent
import com.aegida.core.time.Clock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Notices the listener is to remove or put aside after a check. */
data class ShadeActions(val hide: List<String> = emptyList(), val snooze: List<String> = emptyList())

/**
 * What the notification listener does when the system binds or unbinds it (findings #111, #112, #134). It tells [binding] whether it
 * is bound, so a check can tell Home when Aegida stopped hearing notices. On binding again it checks the phone, then each notice that
 * arrived while it was not listening, once, through the same path as a newly posted one: Android does not deliver those again.
 * Nothing here waits for onListenerDisconnected, which Android does not reliably call (finding #134).
 */
@Singleton // one per process, so its lock covers every bind (finding #135)
class ListenerLink @Inject constructor(
    private val checks: CheckRunner,
    private val times: ListenerTimesStore,
    private val binding: ListenerBinding,
    private val seen: SeenBound,
) {
    /**
     * Two binds at once (finding #135) would both read the same "last seen bound" and check each missed notice twice. One at a
     * time, the second reads the time the first one's checks moved on, so it checks nothing again.
     */
    private val oneAtATime = Mutex()

    /**
     * [inShade] is what the phone shows now (getActiveNotifications); Aegida's own notices ([ownPackage]) are skipped. Only
     * notices posted after the last time a check saw the listener bound are checked: every notice heard runs such a check, so
     * earlier ones were heard already and a warning she has dealt with is never raised twice. The time is read before the listener
     * is marked bound. When it was never seen bound (a new install, or an update from 0.3.9) nothing is checked, because it cannot
     * tell which notices she has already seen. At most [MAX_CHECKED], the newest; older ones are left unchecked.
     *
     * While these checks run, no check moves "last seen bound" past a notice not yet checked (finding #141): it moves to each late
     * notice's own time once its check has finished, and to the time of this bind only after the last one. A process killed half
     * way leaves it at the last notice finished, so the next bind checks the rest.
     */
    suspend fun connected(inShade: List<NotificationEvent>, ownPackage: String): ShadeActions = oneAtATime.withLock {
        val since = times.times().lastSeenBound
        binding.markBound(true)
        seen.duringLateChecks { checked ->
            checks.run(Trigger.AppStart)
            if (since == null) return@duringLateChecks ShadeActions()
            val missed = inShade.filter { it.packageName != ownPackage && it.postedAt.isAfter(since) }.sortedBy { it.postedAt }.takeLast(MAX_CHECKED)
            val hide = mutableListOf<String>()
            val snooze = mutableListOf<String>()
            for (event in missed) {
                val outcome = checks.run(Trigger.NotificationPosted, event)
                checked(event.postedAt)
                hide += outcome.notificationsToHide
                snooze += outcome.notificationsToSnooze
            }
            ShadeActions(hide, snooze)
        }
    }

    /** onListenerDisconnected or onDestroy: no longer bound. Android may never call either (finding #134); checks do not rely on it. */
    fun disconnected() = binding.markBound(false)

    companion object {
        /** A shade can hold dozens of notices; each check reads the phone, so only the newest this many are checked late. */
        const val MAX_CHECKED = 20
    }
}

/**
 * The one writer of "last seen bound" (findings #134, #141). A check writes the time it was asked for, and only when it has finished
 * with the listener bound: a check killed half way leaves the time where it was, so its notice is checked again at the next bind.
 * While a bind's late checks run, the checks that finish are only noted; the time is written when the late checks are done.
 */
@Singleton // one per process: the late checks of a bind and every other check share it
class SeenBound @Inject constructor(private val times: ListenerTimesStore, private val clock: Clock) {
    private val lock = Mutex()
    private var lateChecks = false
    private var heldLatest: Instant? = null

    /** When a check is asked for. */
    fun now(): Instant = clock.now()

    /** A check asked for at [askedAt] has finished; [bound]: it saw the listener bound. */
    suspend fun checkFinished(askedAt: Instant, bound: Boolean) {
        if (!bound) return
        lock.withLock {
            if (lateChecks) heldLatest = listOfNotNull(heldLatest, askedAt).max() else times.seenBound(askedAt)
        }
    }

    /**
     * Runs a bind's late checks; [block] calls its argument with each late notice's post time once that notice's check has finished.
     * If [block] does not finish (the process is killed, or a check fails), the time stays at the last notice finished.
     */
    suspend fun <T> duringLateChecks(block: suspend (checked: suspend (Instant) -> Unit) -> T): T {
        lock.withLock { lateChecks = true; heldLatest = null }
        var done = false
        try {
            return block { postedAt -> times.seenBound(postedAt) }.also { done = true }
        } finally {
            withContext(NonCancellable) {
                lock.withLock {
                    lateChecks = false
                    heldLatest?.takeIf { done }?.let { times.seenBound(it) }
                    heldLatest = null
                }
            }
        }
    }
}
