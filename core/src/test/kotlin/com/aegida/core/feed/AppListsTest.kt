package com.aegida.core.feed

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.time.Instant
import java.time.temporal.ChronoUnit

/** The app side of the feed: package names and signing-certificate hashes of known spying apps. */
class AppListsTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val sources = listOf(FeedSource("Echap", "CC BY 4.0", 3))
    private val feed = ThreatFeed.of(
        now, sources, hostKeys = listOf("evil.example"), urlKeys = emptyList(),
        packageNames = listOf("com.spyzee", "COM.Fone"),
        certificateSha1s = listOf("31A6ECECD97CF39BC4126B8745CD94A7C30BF81C"),
    )

    @Test fun aListedPackageNameMatchesWhateverTheCase() {
        assertEquals(AppMatch.Package, feed.matchApp("com.spyzee", emptySet()))
        assertEquals(AppMatch.Package, feed.matchApp("com.fone", emptySet()))
        assertNull(feed.matchApp("com.android.chrome", emptySet()))
    }

    @Test fun aListedSigningCertificateMatchesEvenUnderANewPackageName() {
        assertEquals(AppMatch.Certificate, feed.matchApp("org.renamed.app", setOf("31a6ececd97cf39bc4126b8745cd94a7c30bf81c")))
        assertEquals(AppMatch.Certificate, feed.matchApp("org.renamed.app", setOf("0000000000000000000000000000000000000000", "31A6ECECD97CF39BC4126B8745CD94A7C30BF81C")))
        assertNull(feed.matchApp("org.renamed.app", setOf("0000000000000000000000000000000000000000")))
    }

    @Test fun aPackageNameIsNotMistakenForAWebsiteOrTheOtherWayRound() {
        assertNull(feed.matchApp("evil.example", emptySet()))
        val onlyHosts = ThreatFeed.of(now, sources, listOf("com.spyzee"), emptyList())
        assertNull(onlyHosts.matchApp("com.spyzee", emptySet()))
    }

    @Test fun theAppListsSurviveEncodingAndDecoding() {
        val decoded = FeedCodec.decode(FeedCodec.encode(feed))
        assertEquals(AppMatch.Package, decoded.matchApp("com.spyzee", emptySet()))
        assertEquals(AppMatch.Certificate, decoded.matchApp("x.y", setOf("31A6ECECD97CF39BC4126B8745CD94A7C30BF81C")))
        assertEquals(feed.packageCount, decoded.packageCount)
        assertEquals(feed.certificateCount, decoded.certificateCount)
    }

    @Test fun aVersionOneFeedStillDecodesWithNoAppLists() {
        val v1 = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(0x41454746); out.writeByte(1); out.writeLong(now.epochSecond)
                out.writeInt(0)
                out.writeInt(1); out.writeLong(FeedHash.of("evil.example"))
                out.writeInt(0)
            }
        }.toByteArray()
        val decoded = FeedCodec.decode(v1)
        assertEquals(1, decoded.hostCount)
        assertEquals(0, decoded.packageCount)
        assertNull(decoded.matchApp("com.spyzee", emptySet()))
        assertTrue(FeedCodec.encode(decoded).size > v1.size) // re-encoded as version 2
    }
}
