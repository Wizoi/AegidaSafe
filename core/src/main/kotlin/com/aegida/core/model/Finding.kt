package com.aegida.core.model

import com.aegida.core.text.Problem
import java.time.Instant

enum class DetectorId { Link, PopupSpam, NoisyApp, Dns, Health, Delivery, Drift, NewApp, RemoteControl, Stalkerware }

/** Mapped once to channel, banner behaviour and guardian message. */
enum class Severity { Info, NeedsTap, Urgent }

enum class FindingState {
    Open, Acknowledged, Resolved,

    /** Still not dealt with after a day, so the guardian was reminded. Counts as open: it is still on Home. */
    Escalated,
    ;

    /** The owner has not dealt with it yet. */
    val needsHer: Boolean get() = this == Open || this == Escalated
}

/** A repeated check updates the finding with the same key instead of making a new one. */
data class FindingKey(val detectorId: DetectorId, val problem: Problem, val subject: String)

/** Keys detectors may put in [Finding.evidence]; the app acts on these without deciding anything. */
object Evidence {
    const val NOTIFICATION_KEY = "notificationKey"
    const val PACKAGE = "package"
    const val CHANNEL_ID = "channelId"
    const val SITE = "site"
    const val HOST = "host"
    const val APP_LABEL = "appLabel"

    /**
     * Facts an app cannot fake, shown next to its claimed name (#164): where it came from ([AppSource.evidence]) and when it was
     * first installed (epoch millis).
     */
    const val APP_SOURCE = "appSource"
    const val APP_ADDED = "appAdded"

    /** Who sent a scam message, what it said (both cut short) and when it came in (epoch millis); kept on the phone only until the owner has seen the warning. */
    const val MESSAGE_TITLE = "messageTitle"
    const val MESSAGE_TEXT = "messageText"
    const val MESSAGE_AT = "messageAt"
    const val NOTICES = "notices" // how many of an app's notices in the last week looked like ads

    /** [ALARMING_YES] when a website's notice passed the scare-word gate; absent for a calm one (findings #150). Kept after she has seen it. */
    const val ALARMING = "alarming"
    const val ALARMING_YES = "yes"
    val MESSAGE_KEYS = setOf(MESSAGE_TITLE, MESSAGE_TEXT, MESSAGE_AT)
}

/** Only detectors create these (design section 5). Evidence stays on the phone; guardians get Problem text only. */
data class Finding(
    val key: FindingKey,
    val evidence: Map<String, String>,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val state: FindingState = FindingState.Open,
) {
    val severity: Severity get() = key.problem.severity

    /** The same finding without the words of the message it was raised for (the owner has seen it). */
    fun withoutMessage(): Finding = copy(evidence = evidence - Evidence.MESSAGE_KEYS)
}
