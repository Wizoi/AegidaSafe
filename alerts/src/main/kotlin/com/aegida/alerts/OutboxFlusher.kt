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
    /** What one pass over the outbox has seen so far. */
    private class Pass {
        var delivered = 0
        val markers = mutableListOf<QueuedMessage>()
        val backedOff = mutableListOf<QueuedMessage>()
    }

    /** How many messages were delivered. */
    suspend fun flush(): Int {
        val now = clock.now()
        val current = guardians.current()
        val topic = current.firstTopic
        // Alerts off or nobody paired: send nothing, and keep what is held (it goes if she turns alerts back on).
        if (!current.alertsOn || topic == null) return 0
        val pass = Pass()
        // Oldest first, always. Once one message gets through the network is evidently up, so the rest go
        // without waiting out their own back-off; if nothing has gone yet the back-off still applies.
        for (message in outbox.all().sortedBy { it.queuedAt }) {
            if (!visit(message, topic, now, pass)) break
        }
        // Something got through, so the network is up: the older ones that were still waiting go now, oldest first.
        if (pass.delivered > 0) for (message in pass.backedOff) if (trySend(message, now)) pass.delivered++
        if (pass.delivered > 0) pass.markers.forEach { outbox.remove(it.id) }
        return pass.delivered
    }

    /** Deals with one queued message. False means stop here: offline, and nothing has got through, so they would all fail. */
    private suspend fun visit(message: QueuedMessage, topic: NtfyTopic, now: Instant, pass: Pass): Boolean {
        when {
            // Queued for a guardian she has since replaced: not re-targeted, and the old guardian is not told.
            message.topic != topic.value -> outbox.remove(message.id)
            // The marker for a message given up on. It keeps "can't reach your guardian" open until a
            // later message gets through, and goes after a month so it cannot stay forever.
            message.attempts == GAVE_UP -> if (elapsedAtLeast(message.queuedAt, now, MARKER_KEPT)) outbox.remove(message.id) else pass.markers += message
            elapsedAtLeast(message.queuedAt, now, EXPIRES) -> giveUp(message, now)
            pass.delivered == 0 && !elapsedAtLeast(message.lastTriedAt, now, wait(message)) -> pass.backedOff += message
            trySend(message, now) -> pass.delivered++
            else -> return pass.delivered > 0
        }
        return true
    }

    private suspend fun trySend(message: QueuedMessage, now: Instant): Boolean {
        val sent = ntfy.send(NtfyTopic(message.topic), GuardianMessage(message.title, message.body, message.urgent))
        if (sent == SendResult.Delivered) {
            outbox.remove(message.id)
            // A reminder delivered later reads as reminded, exactly as one delivered at once does (findings #144).
            val outcome = if (message.reminder) Outcome.GuardianReminded else Outcome.GuardianTold
            history.add(HistoryEntry(now, message.problem, outcome, message.detail))
            message.alsoCovers.forEach { history.add(HistoryEntry(now, it.problem, outcome, it.detail)) }
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
        history.add(HistoryEntry(now, message.problem, Outcome.GuardianNeverReached, message.detail))
        message.alsoCovers.forEach { history.add(HistoryEntry(now, it.problem, Outcome.GuardianNeverReached, it.detail)) }
        outbox.remove(message.id)
        outbox.add(message.copy(id = 0, attempts = GAVE_UP))
    }

    /** A time in the future (the clock moved back) counts as long enough, so nothing waits forever. */
    private fun elapsedAtLeast(since: Instant, now: Instant, span: Duration): Boolean {
        val elapsed = Duration.between(since, now)
        return elapsed.isNegative || elapsed >= span
    }

    private fun wait(message: QueuedMessage): Duration {
        val steps = (message.attempts - 1).coerceIn(0, MAX_WAIT_DOUBLINGS)
        return minOf(FIRST_WAIT.multipliedBy(1L shl steps), LONGEST_WAIT)
    }

    companion object {
        val FIRST_WAIT: Duration = Duration.ofMinutes(15)
        val LONGEST_WAIT: Duration = Duration.ofHours(6)
        val EXPIRES: Duration = Duration.ofDays(7)
        val MARKER_KEPT: Duration = Duration.ofDays(30)
        const val GAVE_UP = -1

        /** The wait doubles at most this many times: 15 minutes up to 8 hours, which [LONGEST_WAIT] then caps. */
        private const val MAX_WAIT_DOUBLINGS = 5
    }
}
