package com.aegida.core.model

import com.aegida.core.text.Problem
import java.time.Instant

enum class DetectorId { Link, PopupSpam, Dns, Health, Drift, NewApp, RemoteControl, Stalkerware }

/** Mapped once to channel, banner behaviour and guardian message. */
enum class Severity { Info, NeedsTap, Urgent }

/** What a check concluded. There is deliberately no "safe". */
enum class Verdict { NoKnownProblems, Warning, Unknown }

enum class FindingState { Open, Acknowledged, Resolved, Escalated }

/** A repeated check updates the finding with the same key instead of making a new one. */
data class FindingKey(val detectorId: DetectorId, val problem: Problem, val subject: String)

/** Keys detectors may put in [Finding.evidence]; the app acts on these without deciding anything. */
object Evidence {
    const val NOTIFICATION_KEY = "notificationKey"
    const val PACKAGE = "package"
    const val CHANNEL_ID = "channelId"
    const val SITE = "site"
    const val HOST = "host"
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
}
