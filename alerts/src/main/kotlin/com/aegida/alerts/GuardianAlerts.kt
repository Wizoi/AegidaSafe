package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.text.Texts
import javax.inject.Inject

/** What turning "tell my guardian about serious problems" on or off did. */
data class AlertsSwitched(val welcome: SendResult?, val heartbeat: SendResult?)

/**
 * The one place the alerts switch is changed. Turning it on tells the guardian once that Aegida is set up
 * (so they know the link works) and starts the "hasn't checked in" message; turning it off cancels that
 * message so a phone the owner chose not to report on never alarms anyone.
 */
class GuardianAlerts @Inject constructor(
    private val settings: GuardianSettings,
    private val ntfy: NtfySender,
    private val switch: DeadMansSwitch,
) {
    suspend fun setAlertsOn(on: Boolean): AlertsSwitched {
        val current = settings.current()
        if (current.alertsOn == on) return AlertsSwitched(null, null)
        settings.save(current.copy(alertsOn = on))
        if (!on) return AlertsSwitched(null, switch.disarm())
        return startTelling()
    }

    /** Tells the current guardian Aegida is set up and starts the "hasn't checked in" message. Used when alerts go on and when the guardian changes. */
    suspend fun startTelling(): AlertsSwitched {
        val current = settings.current()
        val owner = current.ownerName.ifBlank { Texts.APP_NAME }
        val welcome = current.firstTopic?.let {
            ntfy.send(it, GuardianMessage(title = owner, body = Texts.Guardian.SET_UP.format(owner), urgent = false))
        }
        return AlertsSwitched(welcome, switch.arm())
    }
}
