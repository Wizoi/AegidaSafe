package com.aegida.detectors

import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.NotificationEvent
import com.aegida.core.model.ScareWords
import com.aegida.core.model.WebsiteSites
import com.aegida.core.text.Problem
import java.time.Instant
import javax.inject.Inject

/**
 * Owns "is a website sending notifications the owner did not already have?". Chrome posts each site on its own
 * channel "web:<origin>;<n>" (findings #18).
 *  - A site she already had when setup ended or said Yes to (the baseline's websites) is left alone, unless its notice passes the
 *    scare-word gate ([ScareWords]): then it is hidden and she is told, but the guardian is not ([Problem.SiteScaryNews]). Whether it
 *    is real news or a fake is not guessed (Kevin, Oct 15; findings #148).
 *  - A site that is not in that list and talks calmly is hidden and she is asked "Was that you?" ([Problem.NewWebsiteNotifications],
 *    Kevin, Oct 9); yes adds it to the list. Scare wording on a new site is still called fake, since "was that you?" would offer to
 *    allow a fake virus warning.
 *  - Before the list is recorded (setup unfinished, or a baseline from before this existed) a site she allowed in setup is read as
 *    a known site and every other site is treated as fake, as before.
 * The app hides the notification and opens that site's switch for the owner (decisions.md).
 */
class PopupSpamDetector @Inject constructor() : Detector {
    override val id = DetectorId.PopupSpam

    override fun evaluate(input: Inputs): List<Finding> {
        val notice = siteNotice(input.event)
        val problem = notice?.let { problemFor(it, input) }
        return if (notice == null || problem == null) emptyList() else listOf(findingFor(notice, problem, input.now))
    }

    /** A notification from one website's channel, with the site it names. */
    private class SiteNotice(val event: NotificationEvent, val channel: String, val site: String)

    private fun siteNotice(event: NotificationEvent?): SiteNotice? {
        val channel = event?.channelId?.takeIf { it.startsWith(SITE_CHANNEL_PREFIX) }
        val site = channel?.let { WebsiteSites.siteOf(it) }
        return if (event != null && channel != null && site != null) SiteNotice(event, channel, site) else null
    }

    /** Null when there is nothing to say about this site. */
    private fun problemFor(notice: SiteNotice, input: Inputs): Problem? {
        val site = notice.site
        val anyScareWord = ScareWords.anyIn(notice.event.title, notice.event.text)
        val recorded = input.baseline?.websites
        val known = if (recorded == null) site in input.setup.allowedSites else site in recorded
        return when {
            known && anyScareWord -> Problem.SiteScaryNews
            known -> null
            recorded == null || anyScareWord -> Problem.SiteNotifications
            else -> Problem.NewWebsiteNotifications
        }
    }

    private fun findingFor(notice: SiteNotice, problem: Problem, now: Instant): Finding {
        val event = notice.event
        return Finding(
            FindingKey(id, problem, notice.site),
            mapOf(
                Evidence.NOTIFICATION_KEY to event.key,
                Evidence.PACKAGE to event.packageName,
                Evidence.CHANNEL_ID to notice.channel,
                Evidence.SITE to notice.site,
                // What the website said, for History (Kevin, Oct 10); kept only until the owner has seen the warning.
                Evidence.MESSAGE_TITLE to event.title.take(EXAMPLE_CHARS),
                Evidence.MESSAGE_AT to event.postedAt.toEpochMilli().toString(),
            ),
            now,
            now,
        )
    }

    companion object {
        const val SITE_CHANNEL_PREFIX = "web:"
        private const val EXAMPLE_CHARS = 200
    }
}
