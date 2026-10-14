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
    /**
     * What the History line for this note says it was about (the app, the site: [HistoryDetail]), made from the finding as it was told.
     * Written to History when the note is delivered later, so "the guardian heard about this very app" can be matched exactly
     * (findings #116). Null for notes queued before Room version 4 and for kinds with nothing more to say.
     */
    val detail: String? = null,
    /** A reminder covers every warning that was due; each of them is one more kind-and-detail pair here, so each gets its own History line on delivery (findings #131). */
    val alsoCovers: List<Covered> = emptyList(),
)

/** One more warning that a held message was about: its kind and its [QueuedMessage.detail]. */
data class Covered(val problem: Problem, val detail: String?)

/** Stored by :store (the only SQL). */
interface Outbox {
    suspend fun add(message: QueuedMessage)
    /** Oldest first. */
    suspend fun all(): List<QueuedMessage>
    suspend fun remove(id: Long)
    suspend fun failedAgain(id: Long, at: Instant)
}
