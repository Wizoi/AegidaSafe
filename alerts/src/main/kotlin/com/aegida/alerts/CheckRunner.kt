package com.aegida.alerts

import com.aegida.core.health.ListenerTimesStore
import com.aegida.core.health.Trigger
import com.aegida.core.model.Finding
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.Snapshotter
import com.aegida.core.text.Problem
import com.aegida.detectors.Evaluator
import java.time.Instant
import javax.inject.Inject

/** What one check found and what the pipeline did about it. */
data class CheckOutcome(val findings: List<Finding>, val pipeline: PipelineResult) {
    /**
     * Notifications to remove: those whose finding says so (Problem.hidesNotification) and names one. A new website's notice is put
     * aside instead, unless she already said No (it is in its quiet week) or the question has waited a day (findings #94).
     */
    val notificationsToHide: List<String>
        get() = findings.filter { it.key.problem.hidesNotification && (it.key.problem != Problem.NewWebsiteNotifications || settled(it)) }.mapNotNull(::keyOf)

    /**
     * A new website is asked about ("Was that you?"), so its notification is put aside for a while instead of deleted: the phone
     * brings it back, and the next check shows it if the owner said yes (Kevin, Oct 10).
     */
    val notificationsToSnooze: List<String>
        get() = findings.filter { it.key.problem == Problem.NewWebsiteNotifications && !settled(it) }.mapNotNull(::keyOf)

    private fun settled(finding: Finding) = finding.key in pipeline.quieted || finding.key in pipeline.openLong

    private fun keyOf(finding: Finding) = finding.evidence[com.aegida.core.model.Evidence.NOTIFICATION_KEY]
}

/**
 * The one way a trigger turns into a check (design section 5): read the phone, evaluate, alert. The
 * notification listener, the daily job and app start all call [run]; none of them decide anything.
 */
class CheckRunner @Inject constructor(
    private val snapshotter: Snapshotter,
    private val evaluator: Evaluator,
    private val pipeline: AlertPipeline,
    private val baseline: BaselineKeeper,
    private val outbox: OutboxFlusher,
    private val gate: CheckGate,
) {
    /** [late]: [event] arrived while the listener was not bound and is checked at the next bind, perhaps again after a kill (finding #146 b). */
    suspend fun run(trigger: Trigger, event: NotificationEvent? = null, late: Boolean = false): CheckOutcome = gate.run { finished ->
        val snapshot = snapshotter.take()
        baseline.keep(snapshot)
        val findings = evaluator.run(trigger, snapshot, event)
        val result = pipeline.handle(
            findings, evaluator.detectorIds, phone = snapshot, lateNoticeAt = event?.postedAt?.takeIf { late }, allowedSites = baseline.allowedSites(),
        )
        outbox.flush()
        // Only now: a check killed before this point leaves "last seen bound" where it was, so its notice is checked again (finding #141).
        finished(snapshot.listenerBound)
        CheckOutcome(findings, result)
    }
}

/**
 * A check's turn at the one alert lock ([AlertGate], findings #49), and what it tells [SeenBound] when it has finished (finding #141):
 * the time it was asked for, taken before it waits for its turn.
 */
class CheckGate @Inject constructor(
    private val gate: AlertGate,
    private val seen: SeenBound = SeenBound(ListenerTimesStore.None) { Instant.EPOCH },
) {
    /** [block] calls its argument once it has finished, with whether the check saw the listener bound. */
    suspend fun <T> run(block: suspend (finished: suspend (bound: Boolean) -> Unit) -> T): T {
        val askedAt = seen.now()
        return gate.run { block { bound -> seen.checkFinished(askedAt, bound) } }
    }
}
