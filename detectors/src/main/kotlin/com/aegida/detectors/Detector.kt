package com.aegida.detectors

import com.aegida.core.feed.ThreatFeed
import com.aegida.core.health.CheckRun
import com.aegida.core.model.Baseline
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.Setup
import com.aegida.core.model.Snapshot
import java.time.Instant

/** Each detector owns exactly one question (design section 5). Pure and deterministic. */
interface Detector {
    val id: DetectorId
    fun evaluate(input: Inputs): List<Finding>
}

/** Everything a detector may look at. */
data class Inputs(
    val snapshot: Snapshot,
    val now: Instant,
    val setup: Setup,
    /** The last good feed, or null before the first download. */
    val feed: ThreatFeed?,
    /** The notification that triggered this run, if any. */
    val event: NotificationEvent?,
    /** The last daily check before this run, or null if there has never been one. */
    val lastDailyCheck: CheckRun?,
    /** When the oldest guardian message still waiting to be delivered was first held back, or null. */
    val oldestUndelivered: Instant? = null,
    /** What the phone looked like when setup finished (plus changes she accepted), or null before then. */
    val baseline: Baseline? = null,
    /** The counted notices of the app that posted [event], from the last week (finding #92). */
    val notices: List<com.aegida.core.notice.NoticeRow> = emptyList(),
    /** Apps the owner said are fine, so no "noisy app" warning for them. */
    val allowedApps: Set<String> = emptySet(),
)
