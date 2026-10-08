package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * Owns "is a website sending notifications the owner did not already have?". Chrome posts each site on its own
 * channel "web:<origin>;<n>" (findings #18). A site from setup's baseline is left alone unless it starts
 * using scare wording. The app hides the notification and opens that site's switch for the owner (decisions.md).
 */
class PopupSpamDetector @Inject constructor() : Detector {
    override val id = DetectorId.PopupSpam

    override fun evaluate(input: Inputs): List<Finding> {
        val event = input.event ?: return emptyList()
        val channel = event.channelId ?: return emptyList()
        if (!channel.startsWith(SITE_CHANNEL_PREFIX)) return emptyList()
        val site = channel.removePrefix(SITE_CHANNEL_PREFIX).substringBefore(';')
        val scary = SCARE_WORDS.containsMatchIn(event.title + " " + event.text)
        if (site in input.setup.allowedSites && !scary) return emptyList()
        return listOf(
            Finding(
                FindingKey(id, Problem.SiteNotifications, site),
                mapOf(
                    Evidence.NOTIFICATION_KEY to event.key,
                    Evidence.PACKAGE to event.packageName,
                    Evidence.CHANNEL_ID to channel,
                    Evidence.SITE to site,
                ),
                input.now,
                input.now,
            ),
        )
    }

    companion object {
        const val SITE_CHANNEL_PREFIX = "web:"

        private val SCARE_WORDS = Regex(
            """(?i)\b(virus(es)?|infected|malware|hacked|trojan|spyware)\b|battery (is )?damaged|security (alert|warning)|clean (your|now|it)|your (phone|device) (is|has been)""",
        )
    }
}
