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
        // While alerts are off and the cancel already went through, nothing is sent (the pages promise it, findings #98).
        val cancelled = if (before.alertsOn || !before.heartbeatCancelled) switch.disarm() else null
        settings.save(
            settings.current().copy( // read again: disarm may have saved
                firstTopic = NtfyTopic.generate(before.ownerName),
                firstGuardianName = name.trim(),
                firstGuardianEmail = email.trim(),
                firstGuardianPhone = phone.trim(),
                pairedAt = clock.now(),
                welcomePendingFor = null,
                heartbeatCancelled = !before.alertsOn, // the new topic has nothing waiting; startTelling arms it when alerts are on
            ),
        )
        if (before.alertsOn) alerts.startTelling()
        GuardianChanged(before.firstGuardianName, cancelled, told)
    }
}
