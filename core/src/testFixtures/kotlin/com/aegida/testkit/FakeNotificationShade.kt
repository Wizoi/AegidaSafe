package com.aegida.testkit

import com.aegida.core.model.NotificationEvent
import java.time.Duration
import java.time.Instant

/**
 * The phone's notification shade as one listener sees it, modelled on Android's documented behaviour
 * (NotificationListenerService): a notice posted while the listener is bound is delivered to it; one posted while it is
 * not bound stays in the shade but is never delivered; when the listener binds again only onListenerConnected is called,
 * and the notices already in the shade are not replayed (a listener has to ask for them with getActiveNotifications).
 * A snoozed notice leaves the shade and comes back, the same notice with the same key and post time, when its time is up.
 * Not yet compared with an emulator trace (docs/scenarios/health.md).
 */
class FakeNotificationShade(private var now: Instant) {
    interface Listener {
        suspend fun onConnected()
        suspend fun onPosted(event: NotificationEvent)

        /** onListenerDisconnected; not called when the process is killed or the phone is switched off. */
        suspend fun onDisconnected() {}
    }

    private val shown = linkedMapOf<String, NotificationEvent>()
    private val snoozed = linkedMapOf<String, Pair<NotificationEvent, Instant>>()
    private var listener: Listener? = null

    /** Everything delivered to a listener, in order, including notices that came back from a snooze. */
    val delivered = mutableListOf<NotificationEvent>()

    /** What getActiveNotifications would return: the notices in the shade now. */
    fun active(): List<NotificationEvent> = shown.values.toList()

    fun isShown(key: String) = key in shown

    fun isSnoozed(key: String) = key in snoozed

    /** The system binds the listener (setup granted access, the phone restarted, the app was updated). */
    suspend fun connect(l: Listener) {
        listener = l
        l.onConnected()
    }

    /** The system unbinds the listener (an update, low memory). Notices still arrive in the shade. */
    suspend fun disconnect() {
        val l = listener ?: return
        listener = null
        l.onDisconnected()
    }

    /** The process is killed, or the phone switched off: the listener is gone and is not told. */
    fun lose() { listener = null }

    /** The phone restarts: the listener is gone without being told, and the shade is cleared. */
    fun restart() { lose(); shown.clear(); snoozed.clear() }

    suspend fun post(event: NotificationEvent) {
        shown[event.key] = event
        listener?.let { delivered += event; it.onPosted(event) }
    }

    fun cancel(key: String) { shown.remove(key) }

    fun snooze(key: String, duration: Duration) {
        val event = shown.remove(key) ?: return
        snoozed[key] = event to now.plus(duration)
    }

    /** Moves the phone's clock on; snoozed notices whose time is up come back and are delivered again. */
    suspend fun advance(by: Duration) {
        now = now.plus(by)
        val due = snoozed.filterValues { (_, until) -> !until.isAfter(now) }
        for ((key, pair) in due) {
            snoozed.remove(key)
            post(pair.first)
        }
    }
}
