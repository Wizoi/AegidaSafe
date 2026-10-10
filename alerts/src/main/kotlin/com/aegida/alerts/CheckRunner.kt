package com.aegida.alerts

import com.aegida.core.health.Trigger
import com.aegida.core.model.Finding
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.Snapshotter
import com.aegida.core.text.Problem
import com.aegida.detectors.Evaluator
import javax.inject.Inject

/** What one check found and what the pipeline did about it. */
data class CheckOutcome(val findings: List<Finding>, val pipeline: PipelineResult) {
    /** Notifications to remove: those whose finding says so (Problem.hidesNotification) and names one, except a new website. */
    val notificationsToHide: List<String>
        get() = findings.filter { it.key.problem.hidesNotification && it.key.problem != Problem.NewWebsiteNotifications }.mapNotNull { it.evidence[com.aegida.core.model.Evidence.NOTIFICATION_KEY] }

    /**
     * A new website is asked about ("Was that you?"), so its notification is put aside for a while instead of deleted: the phone
     * brings it back, and the next check shows it if the owner said yes and puts it aside again if not (Kevin, Oct 10).
     */
    val notificationsToSnooze: List<String>
        get() = findings.filter { it.key.problem == Problem.NewWebsiteNotifications }.mapNotNull { it.evidence[com.aegida.core.model.Evidence.NOTIFICATION_KEY] }
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
    private val gate: AlertGate,
) {
    suspend fun run(trigger: Trigger, event: NotificationEvent? = null): CheckOutcome = gate.run {
        val snapshot = snapshotter.take()
        baseline.keep(snapshot)
        val findings = evaluator.run(trigger, snapshot, event)
        val result = pipeline.handle(findings, evaluator.detectorIds)
        outbox.flush()
        CheckOutcome(findings, result)
    }
}
