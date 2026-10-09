package com.aegida.core.feed

import com.aegida.core.url.Links
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.random.Random

class ThreatFeedTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val sources = listOf(FeedSource("Phishing.Database", "to verify", 2))
    private val feed = ThreatFeed.of(
        now.minus(Duration.ofHours(2)),
        sources,
        hostKeys = listOf("evil.example", "xn--pypal-4ve.com"),
        urlKeys = listOf(Links.parse("https://raw.githubusercontent.com/bad/repo/main/payload.sh")!!.urlKey),
    )

    private fun match(url: String) = feed.match(Links.parse(url)!!)

    @Test fun hostEntriesMatchTheHostAndEverySubdomain() {
        assertEquals(FeedMatch.Host("evil.example"), match("http://evil.example/"))
        assertEquals(FeedMatch.Host("evil.example"), match("https://login.www.evil.example/a?b=c"))
        assertEquals(FeedMatch.Host("xn--pypal-4ve.com"), match("https://pаypal.com/")) // Cyrillic а
    }

    @Test fun urlEntriesMatchOnlyThatPathNeverTheWholeSharedHost() {
        assertEquals(FeedMatch.Url, match("https://RAW.githubusercontent.com/bad/repo/main/payload.sh?token=1"))
        assertNull(match("https://raw.githubusercontent.com/good/repo/main/readme.md"))
    }

    @Test fun unlistedLinksDoNotMatch() {
        assertNull(match("https://example.org/"))
        assertNull(match("https://notevil.example/")) // a different name, not a subdomain
    }

    @Test fun addingAnEntryNeverRemovesAMatch() { // monotonic (design section 7)
        val random = Random(8)
        val base = List(200) { "h${random.nextInt(1000)}.example" }
        val probes = List(300) { Links.parse("http://x.h${random.nextInt(1000)}.example/")!! }
        val small = ThreatFeed.of(now, sources, base, emptyList())
        val bigger = ThreatFeed.of(now, sources, base + List(50) { "h${random.nextInt(1000)}.example" }, emptyList())
        probes.forEach { if (small.match(it) != null) assertNotNull(bigger.match(it)) }
    }

    @Test fun encodeDecodeRoundTrips() {
        val decoded = FeedCodec.decode(FeedCodec.encode(feed))
        assertEquals(feed.generatedAt, decoded.generatedAt)
        assertEquals(sources, decoded.sources)
        assertArrayEquals(feed.hosts, decoded.hosts)
        assertArrayEquals(feed.urls, decoded.urls)
    }

    @Test fun malformedBytesAreRejectedNotCrashed() {
        val good = FeedCodec.encode(feed)
        assertThrows(FeedFormatException::class.java) { FeedCodec.decode(good.copyOf(good.size - 3)) }
        assertThrows(FeedFormatException::class.java) { FeedCodec.decode(good + byteArrayOf(0)) }
        assertThrows(FeedFormatException::class.java) { FeedCodec.decode("hello".toByteArray()) }
    }

    @Test fun signatureMustMatchTheBytes() {
        val keys = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val bytes = FeedCodec.encode(feed)
        val sig = Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(keys.private); update(bytes); sign() }
        val verifier = FeedVerifier(keys.public.encoded)
        assertTrue(verifier.verify(bytes, sig))
        assertFalse(verifier.verify(bytes.copyOf().also { it[10] = (it[10] + 1).toByte() }, sig))
        assertFalse(verifier.verify(bytes, byteArrayOf(1, 2, 3)))
    }

    @Test fun sanityKeepsTheLastGoodFeedForSuspiciousDownloads() {
        fun sized(n: Int, at: Instant) = ThreatFeed.of(at, sources, List(n) { "h$it.example" }, emptyList())
        val current = sized(4_000, now.minus(Duration.ofDays(1)))
        assertNull(FeedSanity.check(sized(4_100, now), current, now))
        assertNull(FeedSanity.check(sized(1_500, now), null, now))
        assertEquals(FeedRejection.TooSmall, FeedSanity.check(sized(10, now), current, now))
        assertEquals(FeedRejection.FromTheFuture, FeedSanity.check(sized(4_100, now.plus(Duration.ofDays(1))), current, now))
        assertEquals(FeedRejection.OlderThanCurrent, FeedSanity.check(sized(4_100, now.minus(Duration.ofDays(2))), current, now))
        assertEquals(FeedRejection.ShrankTooMuch, FeedSanity.check(sized(1_900, now), current, now))
    }

    private fun withApps(packages: Int, certificates: Int, at: Instant) = ThreatFeed(
        at, sources, LongArray(2_000) { it.toLong() }, LongArray(0),
        LongArray(packages) { it.toLong() * 7 + 1 }, LongArray(certificates) { it.toLong() * 7 + 2 },
    )

    @Test fun sanityRejectsAnAbsurdNumberOfAppEntries() {
        assertNull(FeedSanity.check(withApps(100_000, 100_000, now), null, now))
        assertEquals(FeedRejection.TooManyEntries, FeedSanity.check(withApps(100_001, 10, now), null, now))
        assertEquals(FeedRejection.TooManyEntries, FeedSanity.check(withApps(10, 100_001, now), null, now))
    }

    @Test fun sanityRejectsAppListsThatShrankByMoreThanHalf() {
        val current = withApps(200, 150, now.minus(Duration.ofDays(1)))
        assertNull(FeedSanity.check(withApps(100, 75, now), current, now))
        assertEquals(FeedRejection.ShrankTooMuch, FeedSanity.check(withApps(99, 150, now), current, now))
        assertEquals(FeedRejection.ShrankTooMuch, FeedSanity.check(withApps(200, 74, now), current, now))
        assertEquals(FeedRejection.ShrankTooMuch, FeedSanity.check(withApps(0, 0, now), current, now))
    }

    @Test fun smallAppListsMayShrinkFreely() {
        val current = withApps(99, 99, now.minus(Duration.ofDays(1)))
        assertNull(FeedSanity.check(withApps(0, 0, now), current, now))
    }

    @Test fun duplicateHashesCollapseToOneAndTheDecodedFeedMatchesTheOriginal() {
        val dup = ThreatFeed(
            now, sources,
            hostHashes = longArrayOf(5, 3, 5, 9, 3), urlHashes = longArrayOf(-1, -1, 4),
            packageHashes = longArrayOf(8, 8, 8), certificateHashes = longArrayOf(),
        )
        assertEquals(listOf(3L, 5L, 9L), dup.hosts.toList())
        assertEquals(listOf(-1L, 4L), dup.urls.toList())
        assertEquals(listOf(8L), dup.packages.toList())
        val decoded = FeedCodec.decode(FeedCodec.encode(dup))
        assertArrayEquals(dup.hosts, decoded.hosts)
        assertArrayEquals(dup.urls, decoded.urls)
        assertArrayEquals(dup.packages, decoded.packages)
        assertEquals(dup.generatedAt, decoded.generatedAt)
        // Same answers as the original for real keys, too.
        val decodedFeed = FeedCodec.decode(FeedCodec.encode(feed))
        assertNotNull(decodedFeed.match(Links.parse("https://www.evil.example/x")!!))
        assertNull(decodedFeed.match(Links.parse("https://fine.example/x")!!))
    }
}
