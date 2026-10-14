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
 *  - A site she already had when setup ended or said Yes to (the baseline's websites) is left alone, unless it uses scare wording
 *    about her phone ("your phone is infected"): then it is called fake and hidden ([Problem.SiteNotifications]). A news story
 *    ("Retailer hacked") from such a site stays (findings #109).
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
        val words = event.title + " " + event.text
        val anyScareWord = com.aegida.core.model.ScareWords.REGEX.containsMatchIn(words)
        // A site she said Yes to or had at setup may report news ("Retailer hacked"); it is fake only when it talks about her phone.
        val scary = anyScareWord && ABOUT_HER_PHONE.containsMatchIn(words)
        val recorded = input.baseline?.websites
        val problem = when {
            recorded == null -> if (site in input.setup.allowedSites && !scary) return emptyList() else Problem.SiteNotifications
            site in recorded && scary -> Problem.SiteNotifications
            site in recorded -> return emptyList()
            anyScareWord -> Problem.SiteNotifications
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
                    // What the website said, for History (Kevin, Oct 10); kept only until the owner has seen the warning.
                    Evidence.MESSAGE_TITLE to event.title.take(200),
                    Evidence.MESSAGE_AT to event.postedAt.toEpochMilli().toString(),
                ),
                input.now,
                input.now,
            ),
        )
    }

    companion object {
        const val SITE_CHANNEL_PREFIX = "web:"

        /** Scare wording aimed at her own phone, not a news story about someone else (findings #109). */
        private val ABOUT_HER_PHONE = Regex(
            """(?i)\byour (phone|device|mobile|android|battery)\b|\bthis (phone|device)\b|battery (is )?damaged|clean (your|now|it)|""" +
                """\b(virus(es)?|malware|trojan|spyware) (detected|found on)\b|\byou('re| are| have been|'ve been) (infected|hacked)\b""",
        )
    }
}
