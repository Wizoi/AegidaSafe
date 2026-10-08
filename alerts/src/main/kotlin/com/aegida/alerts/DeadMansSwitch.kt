package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.text.Texts
import java.time.Duration
import javax.inject.Inject

/**
 * "Aegida hasn't checked in." A phone that is off or asleep cannot tell anyone, and there is no server, so
 * the phone leaves a message waiting at ntfy that is due in [QUIET_FOR]. Every successful daily check
 * pushes it back by sending again under the same sequence id; a phone that goes quiet lets it fire.
 */
class DeadMansSwitch @Inject constructor(
    private val guardians: GuardianSettings,
    private val ntfy: NtfySender,
) {
    /** Null when there is nobody to tell (alerts off or no guardian paired). */
    suspend fun arm(): SendResult? {
        val settings = guardians.current()
        val topic = settings.firstTopic ?: return null
        if (!settings.alertsOn) return null
        val owner = settings.ownerName.ifBlank { Texts.APP_NAME }
        return ntfy.send(
            topic,
            GuardianMessage(title = owner, body = Texts.Guardian.NOT_CHECKED_IN.format(owner), urgent = false, delay = QUIET_FOR, sequenceId = SEQUENCE_ID),
        )
    }

    /** Cancels the waiting message, for when the owner turns guardian alerts off. */
    suspend fun disarm(): SendResult? {
        val topic = guardians.current().firstTopic ?: return null
        return ntfy.cancel(topic, SEQUENCE_ID)
    }

    companion object {
        /** Two quiet days (the brief); ntfy holds a scheduled message for at most three. */
        val QUIET_FOR: Duration = Duration.ofDays(2)
        const val SEQUENCE_ID = "heartbeat"
    }
}
