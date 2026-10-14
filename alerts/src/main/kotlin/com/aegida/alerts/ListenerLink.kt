package com.aegida.alerts

import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.NotificationEvent
import com.aegida.core.time.Clock
import javax.inject.Inject

/** Notices the listener is to remove or put aside after a check. */
data class ShadeActions(val hide: List<String> = emptyList(), val snooze: List<String> = emptyList())

/**
 * What the notification listener does when the system binds or unbinds it (findings #111, #112). It records both times, so the
 * health check can tell when Aegida stopped hearing notices. On binding again it checks the phone, then each notice that arrived
 * while it was not listening, once, through the same path as a newly posted one: Android does not deliver those again.
 */
class ListenerLink @Inject constructor(
    private val checks: CheckRunner,
    private val times: ListenerTimesStore,
    private val clock: Clock,
) {
    /**
     * [inShade] is what the phone shows now (getActiveNotifications); Aegida's own notices ([ownPackage]) are skipped. Only
     * notices posted after the last recorded unbind are checked: earlier ones were heard already, so a warning she has dealt
     * with is never raised twice. When no unbind was recorded (the process was killed, or the phone restarted without one)
     * nothing is checked, because it cannot tell which notices she has already seen. At most [MAX_CHECKED], the newest; older
     * ones are left unchecked.
     */
    suspend fun connected(inShade: List<NotificationEvent>, ownPackage: String): ShadeActions {
        val before = times.times()
        times.connected(clock.now())
        checks.run(Trigger.AppStart)
        val since = before.disconnectedAt?.takeIf { before.lastWasDisconnect } ?: return ShadeActions()
        val missed = inShade.filter { it.packageName != ownPackage && it.postedAt.isAfter(since) }.sortedBy { it.postedAt }.takeLast(MAX_CHECKED)
        val hide = mutableListOf<String>()
        val snooze = mutableListOf<String>()
        for (event in missed) {
            val outcome = checks.run(Trigger.NotificationPosted, event)
            hide += outcome.notificationsToHide
            snooze += outcome.notificationsToSnooze
        }
        return ShadeActions(hide, snooze)
    }

    suspend fun disconnected() = times.disconnected(clock.now())

    companion object {
        /** A shade can hold dozens of notices; each check reads the phone, so only the newest this many are checked late. */
        const val MAX_CHECKED = 20
    }
}
