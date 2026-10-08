package com.aegida.alerts

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outbox
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.net.NtfyTopic
import com.aegida.core.time.Clock
import java.time.Duration
import javax.inject.Inject

/**
 * Tries the messages guardians have not received yet. Called after every check, so a phone that comes back
 * online delivers what it was holding. It backs off (15 minutes, doubling, at most 6 hours per message),
 * stops at the first failure (offline means they will all fail), and drops a message after a week.
 */
class OutboxFlusher @Inject constructor(
    private val outbox: Outbox,
    private val ntfy: NtfySender,
    private val history: HistoryLog,
    private val clock: Clock,
) {
    /** How many messages were delivered. */
    suspend fun flush(): Int {
        val now = clock.now()
        var delivered = 0
        for (message in outbox.all()) {
            if (Duration.between(message.queuedAt, now) > EXPIRES) { outbox.remove(message.id); continue }
            if (Duration.between(message.lastTriedAt, now) < wait(message)) continue
            val sent = ntfy.send(NtfyTopic(message.topic), GuardianMessage(message.title, message.body, message.urgent))
            if (sent == SendResult.Delivered) {
                outbox.remove(message.id)
                history.add(HistoryEntry(now, message.problem, Outcome.GuardianTold))
                delivered++
            } else {
                outbox.failedAgain(message.id, now)
                break
            }
        }
        return delivered
    }

    private fun wait(message: QueuedMessage): Duration {
        val steps = (message.attempts - 1).coerceIn(0, 5)
        return minOf(FIRST_WAIT.multipliedBy(1L shl steps), LONGEST_WAIT)
    }

    companion object {
        val FIRST_WAIT: Duration = Duration.ofMinutes(15)
        val LONGEST_WAIT: Duration = Duration.ofHours(6)
        val EXPIRES: Duration = Duration.ofDays(7)
    }
}
