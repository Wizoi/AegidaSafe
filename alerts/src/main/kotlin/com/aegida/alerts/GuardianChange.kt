package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.time.Clock
import com.aegida.core.net.NtfyTopic
import javax.inject.Inject

/** [previousName] is who the owner should also tell herself; [oldGuardianTold] is Aegida's own short message to them (null when none was due). */
data class GuardianChanged(val previousName: String, val cancelled: SendResult?, val oldGuardianTold: SendResult? = null)

/**
 * The one place the guardian is changed. The person holding the phone decides (Kevin's decision), so this does
 * not ask anyone. The guardian being replaced is sent one short message first (Kevin, Oct 8: a coached change must not
 * be silent). The new guardian gets a new private topic: the topic is the password, so the old guardian's link stops
 * working, and their waiting "hasn't checked in" message is cancelled so they are not alarmed later.
 */
class GuardianChange @Inject constructor(
    private val settings: GuardianSettings,
    private val alerts: GuardianAlerts,
    private val switch: DeadMansSwitch,
    private val gate: AlertGate,
    private val clock: Clock,
) {
    suspend fun change(name: String, email: String, phone: String): GuardianChanged = gate.run {
        val before = settings.current()
        val told = alerts.tellReplaced(before)
        val cancelled = switch.disarm()
        settings.save(
            before.copy(
                firstTopic = NtfyTopic.generate(),
                firstGuardianName = name.trim(),
                firstGuardianEmail = email.trim(),
                firstGuardianPhone = phone.trim(),
                pairedAt = clock.now(),
            ),
        )
        if (before.alertsOn) alerts.startTelling()
        GuardianChanged(before.firstGuardianName, cancelled, told)
    }
}
