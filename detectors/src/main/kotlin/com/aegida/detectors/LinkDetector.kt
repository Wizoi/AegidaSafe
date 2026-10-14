package com.aegida.detectors

import com.aegida.core.model.Aegida
import com.aegida.core.model.DetectorId
import com.aegida.core.model.Evidence
import com.aegida.core.model.Finding
import com.aegida.core.model.FindingKey
import com.aegida.core.text.Problem
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
        val event = input.event ?: return emptyList()
        if (event.packageName == Aegida.PACKAGE) return emptyList()
        if (event.channelId?.startsWith(PopupSpamDetector.SITE_CHANNEL_PREFIX) == true) return emptyList()
        // The links in the text as written are checked as always. Links that only appear once odd dots, invisible characters or a
        // broken fragment are dealt with (findings #104) count only with a real ending or a scheme, so Japanese full stops and
        // middle dots between ordinary words never make a "link" (findings #105).
        val plain = Links.extractParts(event.title, event.text)
        val prepared = listOf(event.title, event.text).flatMap { Links.findThorough(LinkText.prepare(it)) }
            .filter { it.raw.contains("://") || TopLevelDomains.isDomain(it.host.substringAfterLast('.')) }
        val links = (plain + prepared).distinctBy { it.host to it.path }
        return links.mapNotNull { link ->
            val signs = LinkSigns.of(link)
            val problem = when {
                input.feed?.match(link) != null -> Problem.ScamLink
                LinkSign.BrandLookalike in signs || LinkSign.MixedScript in signs -> Problem.LookalikeLink
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
                    input.snapshot.apps.firstOrNull { it.packageName == event.packageName }?.label?.takeIf { it.isNotBlank() }?.let { put(Evidence.APP_LABEL, it) }
                },
                input.now,
                input.now,
            )
        }
    }

    private fun cap(raw: String): String =
        if (raw.codePointCount(0, raw.length) <= KEPT_CHARS) raw else raw.substring(0, raw.offsetByCodePoints(0, KEPT_CHARS))

    private companion object {
        const val KEPT_CHARS = 200
    }
}
