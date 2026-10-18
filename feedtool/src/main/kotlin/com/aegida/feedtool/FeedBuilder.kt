package com.aegida.feedtool

import com.aegida.core.feed.AppKeys
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.url.LinkSign
import com.aegida.core.url.LinkSigns
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
    /** Popular domains the look-alike rule flags that are not on [FeedBuilder.REVIEWED_BRAND_SITES] (findings #157). */
    val popularLookalikesLeftOut: List<String> = emptyList(),
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
    val popular: PopularSites? = null,
)

/** The most-linked sites that go in, in rank order, and the look-alikes left out of them (findings #155, #157). */
data class PopularSites(val domains: List<String>, val lookalikesLeftOut: List<String> = emptyList())

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
            popular?.let { FeedSource("Majestic Million", MAJESTIC_LICENCE, it.domains.size) },
        )
        val feed = ThreatFeed.of(
            generatedAt, sources, hosts + popular?.domains.orEmpty().map(ThreatFeed::popularKey), urls,
            apps = AppKeys(packageNames = echap?.packages.orEmpty(), certificateSha1s = echap?.certificates.orEmpty()),
        )
        val report = BuildReport(
            feed.hostCount, feed.urlCount, dropped.toList(), unparseable, feed.packageCount, feed.certificateCount,
            popular?.domains.orEmpty().size, popular?.lookalikesLeftOut.orEmpty(),
        )
        return feed to report
    }

    /**
     * The first [top] rows of the Majestic Million, in its rank order, as the phone writes host names, without the look-alikes
     * ([lookalike]). Refuses a file whose layout changed or whose top rows hold fewer than nine in ten good, distinct host names, so a
     * broken download never publishes a short or wrong list; a few bad or repeated rows are left out (findings #158).
     */
    fun popularDomains(csv: String, top: Int = POPULAR_TOP): PopularSites {
        val rows = csv.lineSequence().filter { it.isNotBlank() }.toList()
        val column = rows.firstOrNull()?.split(',')?.indexOf("Domain") ?: -1
        require(column >= 0) { "the Majestic Million has no Domain column" }
        val domains = rows.drop(1).take(top).mapNotNull { row -> row.split(',').getOrNull(column)?.let(Links::normalizeHost) }.distinct()
        val needed = top - top / POPULAR_BAD_ROWS_ONE_IN
        require(domains.size >= needed) { "the Majestic Million gave ${domains.size} good domains in its top $top rows, not $needed" }
        val (left, kept) = domains.partition(::lookalike)
        return PopularSites(kept, left)
    }

    /**
     * The popular list, or nothing when the Majestic file is missing or unusable: then [warn] says why and the feed goes out without
     * it, so phones use the look-alike rule as before (PS5). A Majestic problem never stops the scam lists (findings #158).
     */
    fun popularOrNothing(csv: String?, top: Int = POPULAR_TOP, warn: (String) -> Unit): PopularSites? = when (csv) {
        null -> null.also { warn("the Majestic Million did not download") }
        else -> try {
            popularDomains(csv, top)
        } catch (e: IllegalArgumentException) {
            null.also { warn(e.message ?: "the Majestic Million could not be read") }
        }
    }

    /**
     * A popular domain the brand or mixed-alphabet rule calls a look-alike, unless it is a brand's own reviewed site: such a name is
     * left off the list, so a look-alike that climbs into the top list is still warned about (findings #157).
     */
    private fun lookalike(domain: String): Boolean {
        val signs = Links.parse("https://$domain/")?.let(LinkSigns::of).orEmpty()
        return domain !in REVIEWED_BRAND_SITES && (LinkSign.BrandLookalike in signs || LinkSign.MixedScript in signs)
    }

    /**
     * The brands' own sites the look-alike rule flags, reviewed one by one against the Majestic Million of Oct 18 (findings #157): all
     * 188 hits in its top 10,000 (179 Google country and other google.* sites, maps.google.at, 7 Amazon sites, apple.com.cn). Any
     * other hit is left out and named in the build log, to review and add here only if it is the brand's own. Leaving out every hit
     * instead would empty the list of its point: a popular site the rule does not flag needs no list.
     */
    val REVIEWED_BRAND_SITES: Set<String> = GOOGLE_ENDINGS.split(' ').map { "google.$it" }.toSet() + setOf(
        "maps.google.at", "amazon.cn", "amazon.jobs", "amazon-adsystem.com", "media-amazon.com", "m.media-amazon.com",
        "images-amazon.com", "ssl-images-amazon.com", "apple.com.cn",
    )

    /**
     * How many of the most-linked sites go in (findings #155, Oct 18 count over the whole Majestic Million): all 203 sites in the top
     * 10,000 that the brand rule calls look-alikes are the brands' own (Google's country and .google sites, media-amazon.com,
     * cloud.microsoft). Of those 203, the names under a brand's own ending (.google, .microsoft) and the shorteners need no help, which
     * leaves the 188 in [REVIEWED_BRAND_SITES] (findings #157, #161). The first that is not comes at 15,574 (whats-on-netflix.com), and facebook-mypage.ru at 70,427.
     */
    const val POPULAR_TOP = 10_000

    /** At most one top row in this many may be bad or repeated for the list to be used: 9,000 of 10,000 must be good (findings #158). */
    const val POPULAR_BAD_ROWS_ONE_IN = 10

    /** The endings of Google's own google.* sites in the Majestic top 10,000 of Oct 18, for [REVIEWED_BRAND_SITES]. */
    private const val GOOGLE_ENDINGS =
        "ac ad ae al am as at az ba be bf bg bi bj bs bt by cat cd cf cg ch ci cl cm cn co.ao co.bw co.ck co.cr co.id " +
        "co.il co.ke co.kr co.ls co.ma co.mz co.nz co.th co.tz co.ug co.uz co.ve co.vi co.za co.zm co.zw com.af com.ag " +
        "com.ai com.ar com.bd com.bh com.bn com.bo com.bz com.co com.cu com.cy com.do com.ec com.eg com.et com.fj " +
        "com.gh com.gi com.gt com.hk com.jm com.kh com.kw com.lb com.ly com.mt com.my com.na com.nf com.ng com.ni " +
        "com.np com.om com.pa com.pe com.pg com.ph com.pk com.pr com.py com.qa com.sa com.sb com.sg com.sl com.sv " +
        "com.tj com.tr com.tw com.ua com.uy com.vc com.vn cv cz dev dj dk dm dz ee fi fm ge gg gl gm gp gr gy hn hr ht " +
        "hu ie im iq is je jo kg ki kz la li lk lt lu lv md me mg mk ml mn ms mu mv mw no nr nu org pl pn ps pt ro rs " +
        "ru rw sc se sh si sk sm sn so st td tg tk tl tm tn to tt vg vu ws"

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
