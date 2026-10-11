package com.aegida.platform.notify

import android.app.Notification
import android.os.Bundle
import com.aegida.core.model.NotificationEvent
import java.time.Instant

/**
 * Turns a notification another app posted into the plain [NotificationEvent] the detectors read. Takes
 * the pieces of a StatusBarNotification rather than the class itself, so it runs on a real Notification
 * in tests. Text stays on the phone; only the title and visible text are read, never actions or intents.
 */
object NotificationMapper {

    fun toEvent(key: String, packageName: String, notification: Notification, postedAtMillis: Long, facts: RankingFacts? = null): NotificationEvent {
        val extras = notification.extras
        return NotificationEvent(
            key = key,
            packageName = packageName,
            channelId = notification.channelId,
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty(),
            text = textOf(notification),
            postedAt = Instant.ofEpochMilli(postedAtMillis),
            category = notification.category,
            channelName = facts?.channelName,
            channelDescription = facts?.channelDescription,
            importance = facts?.importance ?: 0,
            ongoing = notification.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_FOREGROUND_SERVICE) != 0,
            conversation = facts?.conversation ?: false,
            sentimentNegative = facts?.sentimentNegative ?: false,
            groupSummary = notification.flags and Notification.FLAG_GROUP_SUMMARY != 0,
            whenMillis = notification.`when`,
        )
    }

    /** Body, long body, inbox lines and chat messages, once each, in that order. */
    private fun textOf(notification: Notification): String {
        val extras = notification.extras
        val parts = mutableListOf<String>()
        extras.getCharSequence(Notification.EXTRA_TEXT)?.let { parts += it.toString() }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { parts += it.toString() }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts += it.toString() }
        // Read each message bundle directly: the framework helper for this needs API 30, minSdk is 29 (findings #30).
        extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.forEach { message ->
            (message as? Bundle)?.getCharSequence(MESSAGE_TEXT_KEY)?.let { parts += it.toString() }
        }
        return parts.filter { it.isNotBlank() }.distinct().joinToString("\n")
    }

    /** The key MessagingStyle.Message writes its text under (stable since API 24). */
    private const val MESSAGE_TEXT_KEY = "text"
}

/** What the phone's ranking knows about a posted notice (read by the listener from `Ranking`): its channel's name, how it is shown, and the owner's habits with it. */
data class RankingFacts(
    val channelName: String?,
    val channelDescription: String?,
    val importance: Int,
    val conversation: Boolean,
    val sentimentNegative: Boolean,
)
