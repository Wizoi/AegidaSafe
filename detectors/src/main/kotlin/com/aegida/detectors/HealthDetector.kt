package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import java.time.Duration
import javax.inject.Inject

/**
 * Owns "can Aegida still watch?". Telling guardians "Aegida hasn't checked in" when the phone goes quiet
 * cannot come from the phone itself (findings #23); this covers what the phone can see when it runs.
 */
class HealthDetector @Inject constructor() : Detector {
    override val id = DetectorId.Health

    override fun evaluate(input: Inputs): List<Finding> = buildList {
        fun add(problem: Problem) = add(Finding(FindingKey(id, problem, "aegida"), emptyMap(), input.now, input.now))
        // Not before setup is finished: she has not been asked yet, or is half way through the step that asks. A skipped
        // step is raised when setup ends, and her guardian is told then (findings #65).
        if (input.setup.done) {
            if (!input.snapshot.listenerEnabled) add(Problem.ListenerOff)
            // Access is on but the phone stopped letting Aegida listen (findings #112, #134); while access is off, ListenerOff says it.
            else if (input.listener.lost(input.snapshot.listenerBound, input.now, input.bootedAt, input.snapshot.aegidaUpdatedAt)) {
                add(Problem.ListenerNotConnected)
            }
            if (!input.snapshot.canPostNotifications) add(Problem.NotificationsBlocked)
        }
        val lastDaily = input.lastDailyCheck
        if (lastDaily != null && Duration.between(lastDaily.startedAt, input.now) > MISSED_DAILY) add(Problem.MissedCheck)
        val feed = input.feed
        if (feed == null || Duration.between(feed.generatedAt, input.now) > LISTS_OLD) add(Problem.ListsOld)
    }

    companion object {
        val MISSED_DAILY: Duration = Duration.ofHours(26)
        val LISTS_OLD: Duration = Duration.ofDays(3)
    }
}
