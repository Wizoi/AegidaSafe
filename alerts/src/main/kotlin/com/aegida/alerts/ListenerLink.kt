package com.aegida.alerts

import com.aegida.core.health.ListenerBinding
import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.NotificationEvent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * is marked bound, so a check that starts meanwhile does not move it on. When it was never seen bound (a new install, or an
     * update from 0.3.9) nothing is checked, because it cannot tell which notices she has already seen. At most [MAX_CHECKED],
     * the newest; older ones are left unchecked.
     */
    suspend fun connected(inShade: List<NotificationEvent>, ownPackage: String): ShadeActions = oneAtATime.withLock {
        val since = times.times().lastSeenBound
        binding.markBound(true)
        checks.run(Trigger.AppStart)
        if (since == null) return@withLock ShadeActions()
        val missed = inShade.filter { it.packageName != ownPackage && it.postedAt.isAfter(since) }.sortedBy { it.postedAt }.takeLast(MAX_CHECKED)
        val hide = mutableListOf<String>()
        val snooze = mutableListOf<String>()
        for (event in missed) {
            val outcome = checks.run(Trigger.NotificationPosted, event)
            hide += outcome.notificationsToHide
            snooze += outcome.notificationsToSnooze
        }
        ShadeActions(hide, snooze)
    }

    /** onListenerDisconnected or onDestroy: no longer bound. Android may never call either (finding #134); checks do not rely on it. */
    fun disconnected() = binding.markBound(false)

    companion object {
        /** A shade can hold dozens of notices; each check reads the phone, so only the newest this many are checked late. */
        const val MAX_CHECKED = 20
    }
}
