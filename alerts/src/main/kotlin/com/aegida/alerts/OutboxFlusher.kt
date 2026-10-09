package com.aegida.alerts

import com.aegida.core.alert.HistoryEntry
import com.aegida.core.alert.GuardianSettings
import com.aegida.core.alert.HistoryLog
import com.aegida.core.alert.Outbox
import com.aegida.core.alert.Outcome
import com.aegida.core.alert.QueuedMessage
import com.aegida.core.net.NtfyTopic
import com.aegida.core.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

/**
 * Tries the messages guardians have not received yet. Called after every check, so a phone that comes back
 * online delivers what it was holding. It sends nothing while guardian alerts are off, drops messages queued for a
 * guardian who has since been replaced, backs off (15 minutes, doubling, at most 6 hours per message), stops at the
 * first failure when nothing has got through (offline means they will all fail), and gives up on a message after a
 * week (History says so).
 */
class OutboxFlusher @Inject constructor(
    private val outbox: Outbox,
    private val ntfy: NtfySender,
    private val history: HistoryLog,
    private val guardians: GuardianSettings,
    private val clock: Clock,
) {
    /** How many messages were delivered. */
    suspend fun flush(): Int {
        val now = clock.now()
        val current = guardians.current()
        val topic = current.firstTopic
        // Alerts off or nobody paired: send nothing, and keep what is held (it goes if she turns alerts back on).
        if (!current.alertsOn || topic == null) return 0
        var delivered = 0
        val markers = mutableListOf<QueuedMessage>()
        val backedOff = mutableListOf<QueuedMessage>()
        // Oldest first, always. Once one message gets through the network is evidently up, so the rest go
        // without waiting out their own back-off; if nothing has gone yet the back-off still applies.
        for (message in outbox.all().sortedBy { it.queuedAt }) {
            // Queued for a guardian she has since replaced: not re-targeted, and the old guardian is not told.
            if (message.topic != topic.value) { outbox.remove(message.id); continue }
            if (message.attempts == GAVE_UP) {
                // The marker for a message given up on. It keeps "can't reach your guardian" open until a
                // later message gets through, and goes after a month so it cannot stay forever.
                if (elapsedAtLeast(message.queuedAt, now, MARKER_KEPT)) outbox.remove(message.id) else markers += message
                continue
            }
            if (elapsedAtLeast(message.queuedAt, now, EXPIRES)) { giveUp(message, now); continue }
            if (delivered == 0 && !elapsedAtLeast(message.lastTriedAt, now, wait(message))) { backedOff += message; continue }
            if (!trySend(message, now)) { if (delivered == 0) break else continue } // offline: they would all fail
            delivered++
        }
        // Something got through, so the network is up: the older ones that were still waiting go now, oldest first.
        if (delivered > 0) for (message in backedOff) if (trySend(message, now)) delivered++
        if (delivered > 0) markers.forEach { outbox.remove(it.id) }
        return delivered
    }

    private suspend fun trySend(message: QueuedMessage, now: Instant): Boolean {
        val sent = ntfy.send(NtfyTopic(message.topic), GuardianMessage(message.title, message.body, message.urgent))
        if (sent == SendResult.Delivered) {
            outbox.remove(message.id)
            history.add(HistoryEntry(now, message.problem, Outcome.GuardianTold))
            return true
        }
        outbox.failedAgain(message.id, now)
        return false
    }

    /**
     * A week undelivered. History says so, and the row stays as a marker (attempts = [GAVE_UP], never sent again) so
     * the oldest-undelivered time that DeliveryDetector reads stays old and "can't reach your guardian" is not
     * resolved (and so not written as "Fixed.") just because the outbox went empty.
     */
    private suspend fun giveUp(message: QueuedMessage, now: Instant) {
        history.add(HistoryEntry(now, message.problem, Outcome.GuardianNeverReached))
        outbox.remove(message.id)
        outbox.add(message.copy(id = 0, attempts = GAVE_UP))
    }

    /** A time in the future (the clock moved back) counts as long enough, so nothing waits forever. */
    private fun elapsedAtLeast(since: Instant, now: Instant, span: Duration): Boolean {
        val elapsed = Duration.between(since, now)
        return elapsed.isNegative || elapsed >= span
    }

    private fun wait(message: QueuedMessage): Duration {
        val steps = (message.attempts - 1).coerceIn(0, 5)
        return minOf(FIRST_WAIT.multipliedBy(1L shl steps), LONGEST_WAIT)
    }

    companion object {
        val FIRST_WAIT: Duration = Duration.ofMinutes(15)
        val LONGEST_WAIT: Duration = Duration.ofHours(6)
        val EXPIRES: Duration = Duration.ofDays(7)
        val MARKER_KEPT: Duration = Duration.ofDays(30)
        const val GAVE_UP = -1
    }
}
