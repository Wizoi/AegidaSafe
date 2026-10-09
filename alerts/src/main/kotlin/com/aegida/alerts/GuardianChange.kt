package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.net.NtfyTopic
import javax.inject.Inject

/** [previousName] is who the owner should let know; Aegida sends them nothing. */
data class GuardianChanged(val previousName: String, val cancelled: SendResult?)

/**
 * The one place the guardian is changed. The person holding the phone decides (Kevin's decision), so this does
 * not ask anyone. The new guardian gets a new private topic: the topic is the password, so the old guardian's
 * link stops working, and their waiting "hasn't checked in" message is cancelled so they are not alarmed later.
 */
class GuardianChange @Inject constructor(
    private val settings: GuardianSettings,
    private val alerts: GuardianAlerts,
    private val switch: DeadMansSwitch,
    private val gate: AlertGate,
) {
    suspend fun change(name: String, email: String, phone: String): GuardianChanged = gate.run {
        val before = settings.current()
        val cancelled = switch.disarm()
        settings.save(
            before.copy(
                firstTopic = NtfyTopic.generate(),
                firstGuardianName = name.trim(),
                firstGuardianEmail = email.trim(),
                firstGuardianPhone = phone.trim(),
            ),
        )
        if (before.alertsOn) alerts.startTelling()
        GuardianChanged(before.firstGuardianName, cancelled)
    }
}
