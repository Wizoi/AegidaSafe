package com.aegida.core.model

import java.time.Instant

enum class DetectorId { Link, PopupSpam, Dns, Health, Drift, NewApp, RemoteControl, Stalkerware }

/** Mapped once to channel, banner behaviour and guardian message. */
enum class Severity { Info, NeedsTap, Urgent }

/** What a check concluded. There is deliberately no "safe". */
enum class Verdict { NoKnownProblems, Warning, Unknown }

enum class FindingState { Open, Acknowledged, Resolved, Escalated }

/** A repeated check updates the finding with the same key instead of making a new one. */
data class FindingKey(val detectorId: DetectorId, val kind: String, val subject: String)

/** Only detectors create these (design section 5). */
data class Finding(
    val key: FindingKey,
    val severity: Severity,
    val evidence: String,
    val firstSeen: Instant,
    val lastSeen: Instant,
    val state: FindingState = FindingState.Open,
)
