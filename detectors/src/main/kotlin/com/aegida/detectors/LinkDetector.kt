package com.aegida.detectors

import com.aegida.core.model.Aegida
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.model.NotificationEvent
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.text.Problem
import com.aegida.core.url.Link
import com.aegida.core.url.LinkSign
import com.aegida.core.url.LinkSigns
import com.aegida.core.url.LinkText
import com.aegida.core.url.Links
import com.aegida.core.url.TopLevelDomains
import javax.inject.Inject

/**
 * Owns "is a link in a notification known-bad or a look-alike?". Reads the link text only; never fetches
 * it. A link with no sign and no feed entry makes no finding: that is "no known problems found", not
 * "safe". A shortener alone makes no finding either, since where it goes cannot be known.
 */
class LinkDetector @Inject constructor() : Detector {
    override val id = DetectorId.Link

    override fun evaluate(input: Inputs): List<Finding> {
        val event = input.event
        return if (event == null || isNotForLinks(event)) emptyList() else findingsIn(event, input)
    }

    /** Aegida's own notices, and a website's (PopupSpamDetector owns those), are not checked for links. */
    private fun isNotForLinks(event: NotificationEvent): Boolean =
        event.packageName == Aegida.PACKAGE || event.channelId?.startsWith(PopupSpamDetector.SITE_CHANNEL_PREFIX) == true

    private fun findingsIn(event: NotificationEvent, input: Inputs): List<Finding> {
        // The links in the text as written are checked as always. Links that only appear once odd dots, invisible characters or a
        // broken fragment are dealt with (findings #104) count only with a real ending or a scheme, so Japanese full stops and
        // middle dots between ordinary words never make a "link" (findings #105).
        val plain = Links.extractParts(event.title, event.text)
        val prepared = listOf(event.title, event.text).flatMap { Links.findThorough(LinkText.prepare(it)) }
            .filter { it.raw.contains("://") || TopLevelDomains.isDomain(it.host.substringAfterLast('.')) }
        val links = (plain + prepared).distinctBy { it.host to it.path }
        return links.mapNotNull { link ->
            val problem = when {
                input.feed?.match(link) != null -> Problem.ScamLink
                // She said this very site is fine (findings #155): no look-alike warning. The scam list above still warns.
                link.host in input.allowedSites -> return@mapNotNull null
                looksAlike(link, input.feed) -> Problem.LookalikeLink
                else -> return@mapNotNull null
            }
            Finding(
                FindingKey(id, problem, link.host),
                buildMap {
                    put(Evidence.NOTIFICATION_KEY, event.key)
                    put(Evidence.PACKAGE, event.packageName)
                    put(Evidence.HOST, link.host)
                    // What it was, for the owner's advice page: who, what it said (cut short) and when. Kept until she has seen the warning.
                    put(Evidence.MESSAGE_TITLE, cap(event.title))
                    put(Evidence.MESSAGE_TEXT, cap(event.text))
                    put(Evidence.MESSAGE_AT, event.postedAt.toEpochMilli().toString())
                    // The owner is told which app it came from (Kevin, Oct 10); the phone's own app list names it.
                    input.snapshot.apps.firstOrNull { it.packageName == event.packageName }?.label?.takeIf { it.isNotBlank() }
                        ?.let { put(Evidence.APP_LABEL, it) }
                },
                input.now,
                input.now,
            )
        }
    }

    /**
     * A site on the popular list is never called a look-alike (findings #155): share.google, media-amazon.com. Only the part in
     * front of the popular site is still read, so paypal-login.sites.google.com is warned about: that part is not the site's. Whose
     * site it is comes from the whole host, so icloud.apple.com stays Apple's (findings #159).
     */
    private fun looksAlike(link: Link, feed: ThreatFeed?): Boolean {
        val popular = feed?.popularSite(link)
        if (popular == link.host) return false
        val signs = LinkSigns.of(link, popular)
        return LinkSign.BrandLookalike in signs || LinkSign.MixedScript in signs
    }

    private fun cap(raw: String): String =
        if (raw.codePointCount(0, raw.length) <= KEPT_CHARS) raw else raw.substring(0, raw.offsetByCodePoints(0, KEPT_CHARS))

    private companion object {
        const val KEPT_CHARS = 200
    }
}
