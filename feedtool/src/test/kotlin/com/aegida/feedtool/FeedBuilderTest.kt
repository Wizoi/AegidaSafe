package com.aegida.feedtool

import com.aegida.core.feed.FeedCodec
import com.aegida.core.feed.FeedMatch
import com.aegida.core.url.Links
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Runs on excerpts of the real feeds (src/test/resources/feeds, downloaded Oct 8). */
class FeedBuilderTest {
    private fun fixture(name: String) = javaClass.getResource("/feeds/$name")!!.readText()
    private fun dataLines(name: String) = fixture(name).lines().filter { it.isNotBlank() && !it.startsWith("#") }

    private val built = FeedBuilder.build(
        Instant.now(), fixture("phishing-domains.txt"), fixture("phishing-links.txt"), fixture("urlhaus.txt"),
    )
    private val feed = built.first
    private val report = built.second

    private fun match(url: String) = feed.match(Links.parse(url)!!)

    @Test fun sharedHostsInTheDomainListAreDroppedAndReported() { // findings #21
        assertEquals(listOf("bit.ly", "sites.google.com", "telegra.ph"), report.droppedSharedHosts)
        assertNull(match("https://sites.google.com/view/a-church-bake-sale"))
        assertNull(match("https://bit.ly/3xYz"))
    }

    @Test fun everyRealDomainLineMatchesItselfAndItsSubdomains() {
        val domains = dataLines("phishing-domains.txt").filter { Links.normalizeHost(it) !in FeedBuilder.SHARED_HOSTS }
        domains.forEach { domain ->
            assertNotNull("not matched: $domain", match("http://$domain/"))
            assertNotNull("subdomain not matched: $domain", match("http://login.$domain/x"))
        }
    }

    @Test fun everyRealHttpLinkLineMatchesAsAUrlEntry() {
        val links = (dataLines("phishing-links.txt") + dataLines("urlhaus.txt")).filter { it.startsWith("http", true) }
        assertTrue(links.size > 40)
        links.forEach { assertNotNull("not matched: $it", match(it)) }
    }

    @Test fun urlhausOnSharedHostsMatchesOnlyThatFile() { // findings #22
        val listed = dataLines("urlhaus.txt").first { "raw.githubusercontent.com" in it }
        assertEquals(FeedMatch.Url, match(listed))
        assertNull(match("https://raw.githubusercontent.com/someone/project/main/README.md"))
    }

    @Test fun nonHttpLinesAreCountedNotSilentlyLost() {
        val ftp = dataLines("phishing-links.txt").count { it.startsWith("ftp://") }
        assertTrue("fixture should contain the ftp lines seen in the real file", ftp >= 1)
        assertEquals(ftp, report.unparseable)
    }

    @Test fun urlhausIsLeftOutUnlessGivenAndPhishingDatabaseIsCreditedAsMit() { // findings #27
        val (without, _) = FeedBuilder.build(Instant.now(), fixture("phishing-domains.txt"), fixture("phishing-links.txt"))
        assertEquals(listOf("Phishing.Database domains", "Phishing.Database links"), without.sources.map { it.name })
        assertTrue(without.sources.all { it.licence.startsWith("MIT") })
        assertNull(without.match(Links.parse(dataLines("urlhaus.txt").first { "raw.githubusercontent.com" in it })!!))
        assertEquals(listOf("licence to verify"), feed.sources.filter { it.name == "URLhaus" }.map { it.licence })
    }

    @Test fun theBuiltFeedSurvivesEncoding() {
        val decoded = FeedCodec.decode(FeedCodec.encode(feed))
        assertEquals(feed.hostCount, decoded.hostCount)
        assertEquals(feed.urlCount, decoded.urlCount)
        assertEquals(3, decoded.sources.size) // this build passed all three fixtures
    }
}
