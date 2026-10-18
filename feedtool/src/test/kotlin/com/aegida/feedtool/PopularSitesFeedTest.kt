package com.aegida.feedtool

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedFormatException
import com.aegida.core.feed.FeedSource
import com.aegida.core.feed.ThreatFeed
import com.aegida.core.url.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.IOException
import java.time.Instant

/**
 * The popular-site list in the feed (findings #155 part 3), built from the first 300 rows of the real Majestic Million of Oct 18
 * (src/test/resources/feeds/majestic-million.csv) and the real scam-list excerpts.
 */
class PopularSitesFeedTest {
    private fun fixture(name: String) = javaClass.getResource("/feeds/$name")!!.readText()
    private val majestic = fixture("majestic-million.csv")
    private val ranked = majestic.lines().drop(1).filter { it.isNotBlank() }.map { it.split(',')[2] }

    private fun build(popularTop: Int = 100, popular: String? = majestic) = FeedBuilder.build(
        Instant.now(), fixture("phishing-domains.txt"), fixture("phishing-links.txt"),
        extras = Extras(popular = popular?.let { FeedBuilder.popularDomains(it, popularTop) }),
    )

    private fun ThreatFeed.popular(domain: String) = popularSite(Links.parse("https://$domain/")!!)

    @Test fun theTopMostLinkedSitesGoInAndNoMore() {
        val (feed, report) = build()
        assertEquals(100, report.popularEntries)
        assertEquals(ranked[99], feed.popular(ranked[99]))
        assertNull(feed.popular(ranked[100]))
        val source = feed.sources.single { it.name == "Majestic Million" }
        assertEquals(100, source.entries)
        assertTrue(source.licence.startsWith("CC BY 3.0, Majestic-12 Ltd"))
    }

    @Test fun theRealSitesOfTheFieldReportAreOnTheList() {
        val (feed, _) = build(popularTop = 300)
        assertEquals("share.google", feed.popular("share.google"))
        assertEquals("sites.google.com", feed.popular("paypal-login.sites.google.com"))
    }

    @Test fun aShortOrChangedPopularListFailsTheBuild() { // the previous feed stays live
        assertThrows(IllegalArgumentException::class.java) { build(popularTop = 1_000) }
        assertThrows(IllegalArgumentException::class.java) { build(popular = majestic.replaceFirst(",Domain,", ",Site,")) }
        assertThrows(IllegalArgumentException::class.java) { build(popular = "") }
    }

    @Test fun aPopularSiteIsNeverAScamMatch() {
        val (feed, _) = build()
        ranked.take(100).filter { it !in FeedBuilder.SHARED_HOSTS }.forEach { assertNull(it, feed.match(Links.parse("https://$it/")!!)) }
    }

    @Test fun scamEntriesLeaveThePopularSitesOut() { // a feed whose scam lists came down empty is refused by the build
        val (_, without) = build(popular = null)
        val (_, with) = build()
        assertEquals(without.scamEntries, with.scamEntries)
        assertEquals(without.hostEntries + 100, with.hostEntries)
        val (_, onlyPopular) = FeedBuilder.build(Instant.now(), "", "", extras = Extras(popular = FeedBuilder.popularDomains(majestic, 100)))
        assertTrue(onlyPopular.scamEntries < BuildReport.MIN_SCAM_ENTRIES)
    }

    /** Hostile input: a label shaped like a web address, or a comma-free junk line, never becomes a popular entry. */
    @Test fun aRowThatIsNotAHostNameIsNotCounted() {
        val header = majestic.lineSequence().first()
        val junk = "$header\n1,1,share.google,google\n2,2,not a host!,com\n"
        assertThrows(IllegalArgumentException::class.java) { FeedBuilder.popularDomains(junk, top = 2) }
        assertEquals(listOf("share.google"), FeedBuilder.popularDomains(junk, top = 1))
    }

    /** Phones on the released 0.3.10 must keep taking new feeds: its decoder, copied below, reads a feed with the list. */
    @Test fun theReleasedDecoderReadsAFeedWithThePopularList() {
        val (feed, _) = FeedBuilder.build(
            Instant.now(), fixture("phishing-domains.txt"), fixture("phishing-links.txt"), fixture("urlhaus.txt"),
            Extras(EchapParser.parse(fixture("echap-ioc.yaml")), FeedBuilder.popularDomains(majestic, 300)),
        )
        val old = Released0310.decode(FeedCodec.encode(feed))
        assertEquals(feed.hostCount, old.hostCount)
        assertEquals(feed.urlCount, old.urlCount)
        assertEquals(feed.packageCount, old.packageCount)
        assertEquals(feed.sources.map(FeedSource::name), old.sources.map(FeedSource::name))
        val scam = fixture("phishing-domains.txt").lines().first { it.isNotBlank() && !it.startsWith("#") && it.trim() !in FeedBuilder.SHARED_HOSTS }
        assertNotNull(old.match(Links.parse("http://$scam/")!!))
        assertNull(old.match(Links.parse("https://share.google/aBcD123")!!)) // the list never reads as a scam on old phones
    }

    /** FeedCodec.decode exactly as released in 0.3.10 (commit 627f0c6); it refuses other versions and trailing bytes. */
    private object Released0310 {
        private const val MAGIC = 0x41454746
        private const val VERSION = 2
        private const val MAX_ENTRIES = 5_000_000
        private const val MAX_SOURCES = 16

        fun decode(bytes: ByteArray): ThreatFeed = try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC) throw FeedFormatException("not an Aegida feed")
                val version = input.readUnsignedByte()
                if (version != 1 && version != VERSION) throw FeedFormatException("unsupported feed version $version")
                val generatedAt = Instant.ofEpochSecond(input.readLong())
                val sources = List(input.readInt().checked(MAX_SOURCES)) { FeedSource(input.readUTF(), input.readUTF(), input.readInt()) }
                val hosts = LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() }
                val urls = LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() }
                val packages = if (version >= 2) LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() } else LongArray(0)
                val certificates = if (version >= 2) LongArray(input.readInt().checked(MAX_ENTRIES)) { input.readLong() } else LongArray(0)
                if (input.read() != -1) throw FeedFormatException("trailing bytes")
                ThreatFeed(generatedAt, sources, hosts, urls, packages, certificates)
            }
        } catch (ignored: IOException) {
            throw FeedFormatException("truncated feed")
        }

        private fun Int.checked(max: Int): Int = if (this in 0..max) this else throw FeedFormatException("bad count $this")
    }
}
