package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.Guardians
import com.aegida.core.net.NtfyTopic
import com.aegida.core.text.Texts
import javax.inject.Inject
import javax.inject.Singleton

/** What turning "tell my guardian about serious problems" on or off did. */
data class AlertsSwitched(val welcome: SendResult?, val heartbeat: SendResult?)

/**
 * The one place the alerts switch is changed. Turning it on tells the guardian once that Aegida is set up
 * (so they know the link works) and starts the "hasn't checked in" message; turning it off cancels that
 * message so a phone the owner chose not to report on never alarms anyone.
 */
@Singleton
class GuardianAlerts @Inject constructor(
    private val settings: GuardianSettings,
    private val ntfy: NtfySender,
    private val switch: DeadMansSwitch,
    private val gate: AlertGate,
) {
    /**
     * The guardian whose welcome did not go out (offline). Kept in memory and retried by the daily check while alerts
     * stay on and the guardian is unchanged. Lost if the process dies first; the messages that matter (findings) have the Outbox.
     */
    private var welcomePendingFor: NtfyTopic? = null

    suspend fun setAlertsOn(on: Boolean): AlertsSwitched = gate.run { switchAlerts(on) }

    private suspend fun switchAlerts(on: Boolean): AlertsSwitched {
        val current = settings.current()
        if (current.alertsOn == on) return AlertsSwitched(null, null)
        settings.save(current.copy(alertsOn = on))
        if (!on) {
            welcomePendingFor = null
            return AlertsSwitched(null, switch.disarm())
        }
        return startTelling()
    }

    /** Tells the current guardian Aegida is set up and starts the "hasn't checked in" message. Used when alerts go on and when the guardian changes; the caller holds the [AlertGate]. */
    suspend fun startTelling(): AlertsSwitched {
        val current = settings.current()
        val owner = current.ownerName.ifBlank { Texts.APP_NAME }
        welcomePendingFor = null
        val welcome = current.firstTopic?.let { sendWelcome(it, owner) }
        return AlertsSwitched(welcome, switch.arm())
    }

    /** The daily check's second try at a welcome that failed. Null when none is waiting. The caller holds the [AlertGate]. */
    suspend fun retryWelcome(): SendResult? {
        val pending = welcomePendingFor ?: return null
        val current = settings.current()
        if (!current.alertsOn || current.firstTopic != pending) { welcomePendingFor = null; return null }
        return sendWelcome(pending, current.ownerName.ifBlank { Texts.APP_NAME })
    }

    /**
     * One short message to the guardian she is replacing, sent before their link stops working: if a scammer coached
     * her into the change, the family hears about it (findings #56). Only when alerts are on and there was a guardian
     * to tell. A failure is not retried: the old topic is no longer the guardian's, so the outbox would drop it. The caller holds the [AlertGate].
     */
    suspend fun tellReplaced(previous: Guardians): SendResult? {
        val topic = previous.firstTopic?.takeIf { previous.alertsOn && previous.firstGuardianName.isNotBlank() } ?: return null
        val owner = previous.ownerName.ifBlank { Texts.APP_NAME }
        return ntfy.send(topic, GuardianMessage(title = owner, body = Texts.Guardian.CHANGED.format(owner), urgent = false))
    }

    private suspend fun sendWelcome(topic: NtfyTopic, owner: String): SendResult {
        val sent = ntfy.send(topic, GuardianMessage(title = owner, body = Texts.Guardian.SET_UP.format(owner), urgent = false))
        welcomePendingFor = if (sent == SendResult.Delivered) null else topic
        return sent
    }
}
