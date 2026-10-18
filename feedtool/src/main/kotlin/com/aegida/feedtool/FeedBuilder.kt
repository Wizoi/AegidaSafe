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
    val popularEntries: Int = 0,
) {
    /**
     * Scam entries only. The popular sites are host entries too, so the phone's "too small" check alone would pass a feed whose
     * scam lists came down empty; the build refuses one under [MIN_SCAM_ENTRIES] (findings #155).
     */
    val scamEntries: Int get() = hostEntries - popularEntries + urlEntries

    companion object {
        /** The same floor the phone applies to the whole feed (FeedSanity). */
        const val MIN_SCAM_ENTRIES = 1_000
    }
}

/** The lists beyond the scam sites; each null leaves its list out. */
data class Extras(
    /** The spying-app lists. */
    val echap: EchapIndicators? = null,
    /** From [FeedBuilder.popularDomains] (findings #155). */
    val popular: List<String>? = null,
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
        extras: Extras = Extras(),
    ): Pair<ThreatFeed, BuildReport> {
        val (echap, popular) = extras
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
            popular?.let { FeedSource("Majestic Million", MAJESTIC_LICENCE, it.size) },
        )
        val feed = ThreatFeed.of(
            generatedAt, sources, hosts + popular.orEmpty().map(ThreatFeed::popularKey), urls,
            apps = AppKeys(packageNames = echap?.packages.orEmpty(), certificateSha1s = echap?.certificates.orEmpty()),
        )
        val report = BuildReport(
            feed.hostCount, feed.urlCount, dropped.toList(), unparseable, feed.packageCount, feed.certificateCount, popular.orEmpty().size,
        )
        return feed to report
    }

    /**
     * The first [top] domains of the Majestic Million, in its rank order, as the phone writes host names. Refuses a file whose
     * layout changed or that holds fewer rows, so a broken download fails the run instead of publishing a short list.
     */
    fun popularDomains(csv: String, top: Int = POPULAR_TOP): List<String> {
        val rows = csv.lineSequence().filter { it.isNotBlank() }.toList()
        val column = rows.firstOrNull()?.split(',')?.indexOf("Domain") ?: -1
        require(column >= 0) { "the Majestic Million has no Domain column" }
        val domains = rows.drop(1).take(top).mapNotNull { row -> row.split(',').getOrNull(column)?.let(Links::normalizeHost) }.distinct()
        require(domains.size == top) { "the Majestic Million gave ${domains.size} domains, not $top" }
        return domains
    }

    /**
     * How many of the most-linked sites go in (findings #155, Oct 18 count over the whole Majestic Million): all 203 sites in the top
     * 10,000 that the brand rule calls look-alikes are the brands' own (Google's country and .google sites, media-amazon.com,
     * cloud.microsoft); the first that is not comes at 15,574 (whats-on-netflix.com), and facebook-mypage.ru at 70,427.
     */
    const val POPULAR_TOP = 10_000

    /** Read from majestic.com/reports/majestic-million on Oct 18 (findings #155); the notice must travel with the feed. */
    const val MAJESTIC_LICENCE = "CC BY 3.0, Majestic-12 Ltd (The Majestic Million, https://majestic.com/reports/majestic-million), " +
        "changed: top 10,000 domains, stored as hashes"

    /** Read from the repository's LICENSE on Oct 8 (findings #27); the notice must travel with the feed. */
    const val PHISHING_DATABASE_LICENCE = "MIT, Mitchell Krog, Nissar Chababy and Phishing.Database contributors"

    /** The repository README states CC BY (Oct 8); credit and a note that the feed holds hashes of the indicators must travel with it. */
    const val ECHAP_LICENCE = "CC BY 4.0, Echap (Stalkerware Indicators of Compromise, " +
        "https://github.com/AssoEchap/stalkerware-indicators), changed: stored as hashes"

    /** abuse.ch publishes no licence for URLhaus data (findings #27). */
    const val LICENCE_TO_VERIFY = "licence to verify"
}
