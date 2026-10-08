package com.aegida.feedtool

import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.url.Links
import java.time.Instant

/** What went into a feed and what was left out, printed by the build so a drop is never silent. */
data class BuildReport(
    val hostEntries: Int,
    val urlEntries: Int,
    val droppedSharedHosts: List<String>,
    val unparseable: Int,
)

object FeedBuilder {
    /**
     * Hosts many unrelated people publish on. A domain-list entry equal to one of these would flag every
     * page on it (findings #21: Phishing.Database lists sites.google.com, bit.ly and telegra.ph), so such
     * entries are dropped. Individual bad pages on them still come in as URL entries.
     */
    val SHARED_HOSTS = setOf(
        "sites.google.com", "docs.google.com", "drive.google.com", "forms.gle", "google.com",
        "bit.ly", "t.co", "tinyurl.com", "linktr.ee", "telegra.ph",
        "github.io", "github.com", "raw.githubusercontent.com", "gitlab.com", "pages.dev", "workers.dev",
        "web.app", "firebaseapp.com", "vercel.app", "netlify.app", "webflow.io", "glitch.me", "r2.dev",
        "000webhostapp.com", "weebly.com", "weeblysite.com", "wixsite.com", "blogspot.com", "square.site",
        "godaddysites.com", "myshopify.com", "duckdns.org", "ngrok.io", "ngrok-free.app", "ipfs.io",
        "amazonaws.com", "s3.amazonaws.com", "cloudfront.net", "azurewebsites.net", "windows.net",
        "sharepoint.com", "dropbox.com", "discord.com", "cdn.discordapp.com", "img1.wsimg.com",
    )

    private fun lines(text: String) = text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }

    fun build(
        generatedAt: Instant,
        phishingDomains: String,
        phishingLinks: String,
        urlhaus: String,
    ): Pair<ThreatFeed, BuildReport> {
        var unparseable = 0
        val dropped = sortedSetOf<String>()
        val hosts = HashSet<String>()
        var domainCount = 0
        lines(phishingDomains).forEach { line ->
            val host = Links.normalizeHost(line)
            when {
                host == null -> unparseable++
                host in SHARED_HOSTS -> dropped += host
                else -> { hosts += host; domainCount++ }
            }
        }

        val urls = HashSet<String>()
        fun addUrls(text: String): Int {
            var count = 0
            lines(text).forEach { line ->
                val link = if (line.startsWith("http://", true) || line.startsWith("https://", true)) Links.parse(line) else null
                if (link == null) unparseable++ else { urls += link.urlKey; count++ }
            }
            return count
        }
        val linkCount = addUrls(phishingLinks)
        val urlhausCount = addUrls(urlhaus)

        val sources = listOf(
            FeedSource("Phishing.Database domains", LICENCE_TO_VERIFY, domainCount),
            FeedSource("Phishing.Database links", LICENCE_TO_VERIFY, linkCount),
            FeedSource("URLhaus", LICENCE_TO_VERIFY, urlhausCount),
        )
        val feed = ThreatFeed.of(generatedAt, sources, hosts, urls)
        return feed to BuildReport(feed.hostCount, feed.urlCount, dropped.toList(), unparseable)
    }

    /** The brief lists both data licences under "To verify"; fill these in when checked. */
    const val LICENCE_TO_VERIFY = "licence to verify"
}
