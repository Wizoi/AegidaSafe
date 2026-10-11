package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.notice.NoticeRules
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * Owns "is an app flooding the owner with ads as notifications?" (finding #92). It reads the counted notices the Evaluator kept
 * for the app that just posted and asks [NoticeRules]. Ads drawn inside another app are out of reach, and nothing here hides or
 * blocks anything: the owner is told which app, and decides.
 */
class NoisyAppDetector @Inject constructor() : Detector {
    override val id = DetectorId.NoisyApp

    override fun evaluate(input: Inputs): List<Finding> {
        val event = input.event ?: return emptyList()
        val apps = input.snapshot.apps
        val roles = setOfNotNull(input.snapshot.smsApp, input.snapshot.dialerApp, input.snapshot.browser)
        if (!NoticeRules.counted(event, apps, roles)) return emptyList()
        if (event.packageName in input.allowedApps) return emptyList()
        if (!NoticeRules.flagged(input.notices, input.now)) return emptyList()
        val label = apps.firstOrNull { it.packageName == event.packageName }?.label?.takeIf { it.isNotBlank() }
        val evidence = buildMap {
            put(Evidence.PACKAGE, event.packageName)
            label?.let { put(Evidence.APP_LABEL, it) }
            put(Evidence.NOTICES, input.notices.count { !it.at.isBefore(input.now.minus(NoticeRules.WEEK)) }.toString())
            // One example for the warning page; cleared when the owner has seen it.
            put(Evidence.MESSAGE_TITLE, event.title.take(EXAMPLE_CHARS))
            put(Evidence.MESSAGE_AT, event.postedAt.toEpochMilli().toString())
        }
        return listOf(Finding(FindingKey(id, Problem.NoisyApp, event.packageName), evidence, input.now, input.now))
    }

    private companion object {
        const val EXAMPLE_CHARS = 200
    }
}
