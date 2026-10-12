package com.aegida.alerts

import com.aegida.core.alert.GuardianSettings
import com.aegida.core.text.Texts
import com.aegida.core.time.Clock
import kotlinx.coroutines.delay
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Aegida hasn't checked in." A phone that is off or asleep cannot tell anyone, and there is no server, so
 * the phone leaves a message waiting at ntfy that is due in [QUIET_FOR]. Every successful daily check
 * pushes it back by sending again under the same sequence id; a phone that goes quiet lets it fire.
 */
@Singleton
class DeadMansSwitch @Inject constructor(
    private val guardians: GuardianSettings,
    private val ntfy: NtfySender,
    private val clock: Clock,
) {
    /** When the last arm or disarm request went out. In memory: ntfy's same-second trouble only matters within seconds. */
    private var lastRequestAt: Instant? = null

    /**
     * ntfy does not replace or cancel a message by sequence id when the request comes in the same second as the
     * one before (findings #44). So an arm followed at once by a disarm (or the reverse) would leave the
     * wrong message waiting. Wait out [MIN_GAP] since the last request; callers hold the AlertGate, so nothing else interleaves.
     */
    private suspend fun spaceFromLastRequest() {
        val last = lastRequestAt ?: return
        val since = Duration.between(last, clock.now())
        val wait = if (since.isNegative) MIN_GAP else MIN_GAP.minus(since)
        if (!wait.isNegative && !wait.isZero) delay(wait.toMillis())
    }

    /**
     * Alerts on: push the message out again. Alerts off: cancel it until that works once, then send nothing while alerts stay off,
     * so the pages can truthfully say nothing is sent (findings #95). Null when no guardian is paired or nothing needs sending.
     */
    suspend fun keepInStep(): SendResult? {
        val current = guardians.current()
        if (current.alertsOn) return arm()
        if (current.heartbeatCancelled) return null
        return disarm()
    }

    /** Null when there is nobody to tell (alerts off or no guardian paired). */
    suspend fun arm(): SendResult? {
        val settings = guardians.current()
        val topic = settings.firstTopic ?: return null
        if (!settings.alertsOn) return null
        val owner = settings.ownerName.ifBlank { Texts.APP_NAME }
        spaceFromLastRequest()
        if (settings.heartbeatCancelled) guardians.save(settings.copy(heartbeatCancelled = false))
        return ntfy.send(
            topic,
            GuardianMessage(title = owner, body = Texts.Guardian.NOT_CHECKED_IN.format(owner), urgent = false, delay = QUIET_FOR, sequenceId = SEQUENCE_ID),
        ).also { lastRequestAt = clock.now() }
    }

    /** Cancels the waiting message, for when the owner turns guardian alerts off. */
    suspend fun disarm(): SendResult? {
        val topic = guardians.current().firstTopic ?: return null
        spaceFromLastRequest()
        return ntfy.cancel(topic, SEQUENCE_ID).also { result ->
            lastRequestAt = clock.now()
            if (result == SendResult.Delivered) guardians.current().let { guardians.save(it.copy(heartbeatCancelled = true)) }
        }
    }

    companion object {
        /** Two quiet days (the brief); ntfy holds a scheduled message for at most three. */
        val QUIET_FOR: Duration = Duration.ofDays(2)
        const val SEQUENCE_ID = "heartbeat"

        /** Two seconds, so the second request is never in the same second as the first. */
        val MIN_GAP: Duration = Duration.ofSeconds(2)
    }
}
