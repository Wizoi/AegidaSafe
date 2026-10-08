package com.aegida.core.alert

import com.aegida.core.text.Problem
import java.time.Instant

/**
 * A guardian message that could not be sent yet (phone offline, ntfy down). It stays here, and is tried
 * again on later checks, until it is delivered or too old to matter.
 */
data class QueuedMessage(
    val id: Long = 0,
    /** The guardian's topic when it was queued; a guardian changed since still gets this one. */
    val topic: String,
    val title: String,
    val body: String,
    val urgent: Boolean,
    val problem: Problem,
    val queuedAt: Instant,
    /** Sends that failed so far, counting the first. */
    val attempts: Int,
    val lastTriedAt: Instant,
)

/** Stored by :store (the only SQL). */
interface Outbox {
    suspend fun add(message: QueuedMessage)
    /** Oldest first. */
    suspend fun all(): List<QueuedMessage>
    suspend fun remove(id: Long)
    suspend fun failedAgain(id: Long, at: Instant)
}
