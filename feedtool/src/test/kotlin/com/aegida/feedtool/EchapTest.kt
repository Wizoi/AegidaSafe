package com.aegida.feedtool

import com.aegida.core.feed.AppMatch
import com.aegida.core.feed.FeedCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Runs on Echap's real ioc.yaml (src/test/resources/feeds/echap-ioc.yaml, downloaded Oct 8, CC BY 4.0). */
class EchapTest {
    private val text = javaClass.getResource("/feeds/echap-ioc.yaml")!!.readText()
    private val echap = EchapParser.parse(text)

    @Test fun theRealListYieldsItsFamiliesPackagesAndCertificates() {
        assertEquals(132, echap.families)
        assertEquals(610, echap.packages.size)
        assertEquals(470, echap.certificates.size)
        assertTrue("com.thetruth" in echap.packages) // TheTruthSpy, the first entry
        assertTrue("31a6ececd97cf39bc4126b8745cd94a7c30bf81c" in echap.certificates)
    }

    @Test fun everyPackageAndCertificateInTheRealListMatchesInTheBuiltFeed() {
        val (feed, report) = FeedBuilder.build(Instant.now(), "evil.example\n", "https://evil.example/a\n", extras = Extras(echap = echap))
        echap.packages.forEach { assertEquals(it, AppMatch.Package, feed.matchApp(it, emptySet())) }
        echap.certificates.forEach { assertEquals(it, AppMatch.Certificate, feed.matchApp("org.renamed", setOf(it.uppercase()))) }
        assertNull(feed.matchApp("com.android.chrome", setOf("0000000000000000000000000000000000000000")))
        assertEquals(echap.packages.size, report.packageEntries)
        assertEquals(echap.certificates.size, report.certificateEntries)
    }

    @Test fun theBuiltFeedNamesEchapAndItsLicenceAndSurvivesEncoding() {
        val feed = FeedBuilder.build(Instant.now(), "evil.example\n", "https://evil.example/a\n", extras = Extras(echap = echap)).first
        val source = feed.sources.single { "Echap" in it.name }
        assertTrue("CC BY 4.0" in source.licence)
        assertEquals(echap.packages.size + echap.certificates.size, source.entries)
        assertNotNull(FeedCodec.decode(FeedCodec.encode(feed)).matchApp("com.thetruth", emptySet()))
    }

    @Test fun aFileThatYieldsAlmostNothingIsRefusedNotPublishedAsAnEmptyList() {
        assertThrows(IllegalArgumentException::class.java) { EchapParser.parse("- name: X\n  packages:\n  - com.a.b\n") }
        assertThrows(IllegalArgumentException::class.java) { EchapParser.parse("<html>Not found</html>") }
    }

    @Test fun withoutEchapTheFeedHasNoAppLists() {
        val feed = FeedBuilder.build(Instant.now(), "evil.example\n", "https://evil.example/a\n").first
        assertEquals(0, feed.packageCount)
        assertTrue(feed.sources.none { "Echap" in it.name })
    }
}
