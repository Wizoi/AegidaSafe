package com.aegida.feedtool

import com.aegida.core.feed.AppKeys
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
    val packageEntries: Int = 0,
    val certificateEntries: Int = 0,
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
        /** Null leaves URLhaus out: its terms do not clearly allow republishing (findings #27). */
        urlhaus: String? = null,
        /** Null leaves the spying-app lists out. */
        echap: EchapIndicators? = null,
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
        val urlhausCount = urlhaus?.let(::addUrls)

        val sources = listOfNotNull(
            FeedSource("Phishing.Database domains", PHISHING_DATABASE_LICENCE, domainCount),
            FeedSource("Phishing.Database links", PHISHING_DATABASE_LICENCE, linkCount),
            urlhausCount?.let { FeedSource("URLhaus", LICENCE_TO_VERIFY, it) },
            echap?.let { FeedSource("Echap stalkerware indicators", ECHAP_LICENCE, it.packages.size + it.certificates.size) },
        )
        val feed = ThreatFeed.of(
            generatedAt, sources, hosts, urls,
            apps = AppKeys(packageNames = echap?.packages.orEmpty(), certificateSha1s = echap?.certificates.orEmpty()),
        )
        return feed to BuildReport(feed.hostCount, feed.urlCount, dropped.toList(), unparseable, feed.packageCount, feed.certificateCount)
    }

    /** Read from the repository's LICENSE on Oct 8 (findings #27); the notice must travel with the feed. */
    const val PHISHING_DATABASE_LICENCE = "MIT, Mitchell Krog, Nissar Chababy and Phishing.Database contributors"

    /** The repository README states CC BY (Oct 8); credit and a note that the feed holds hashes of the indicators must travel with it. */
    const val ECHAP_LICENCE = "CC BY 4.0, Echap (Stalkerware Indicators of Compromise, " +
        "https://github.com/AssoEchap/stalkerware-indicators), changed: stored as hashes"

    /** abuse.ch publishes no licence for URLhaus data (findings #27). */
    const val LICENCE_TO_VERIFY = "licence to verify"
}
