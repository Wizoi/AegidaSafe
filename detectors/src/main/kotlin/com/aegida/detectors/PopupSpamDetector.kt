package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.WebsiteSites
import com.aegida.core.text.Problem
import javax.inject.Inject

/**
 * Owns "is a website sending notifications the owner did not already have?". Chrome posts each site on its own
 * channel "web:<origin>;<n>" (findings #18).
 *  - A site she already had when setup ended (the baseline's websites) is left alone, unless it starts using scare wording:
 *    then it is called fake and hidden ([Problem.SiteNotifications]).
 *  - A site that is not in that list and talks calmly is hidden and she is asked "Was that you?" ([Problem.NewWebsiteNotifications],
 *    Kevin, Oct 9); yes adds it to the list. Scare wording on a new site is still called fake, since "was that you?" would offer to
 *    allow a fake virus warning.
 *  - Before the list is recorded (setup unfinished, or a baseline from before this existed) every site is treated as fake, as before.
 * The app hides the notification and opens that site's switch for the owner (decisions.md).
 */
class PopupSpamDetector @Inject constructor() : Detector {
    override val id = DetectorId.PopupSpam

    override fun evaluate(input: Inputs): List<Finding> {
        val event = input.event ?: return emptyList()
        val channel = event.channelId ?: return emptyList()
        if (!channel.startsWith(SITE_CHANNEL_PREFIX)) return emptyList()
        val site = WebsiteSites.siteOf(channel) ?: return emptyList()
        val scary = SCARE_WORDS.containsMatchIn(event.title + " " + event.text)
        val recorded = input.baseline?.websites
        val problem = when {
            recorded == null -> if (site in input.setup.allowedSites && !scary) return emptyList() else Problem.SiteNotifications
            scary -> Problem.SiteNotifications
            site in recorded -> return emptyList()
            else -> Problem.NewWebsiteNotifications
        }
        return listOf(
            Finding(
                FindingKey(id, problem, site),
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
