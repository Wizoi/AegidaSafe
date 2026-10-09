package com.aegida.core.feed

import com.aegida.core.url.Link
import java.security.MessageDigest
import java.time.Instant

/**
 * The combined scam list the phone checks links against. Entries are 8-byte SHA-256 prefixes, so the
 * file holds no readable bad URLs and lookups are a binary search. Built by :feedtool from the real
 * feeds (findings #21, #22) with the same [Links] code the phone uses to read links.
 *
 * - Host entries (Phishing.Database domains) match a link's host or any parent domain.
 * - URL entries (Phishing.Database links, URLhaus) match only the exact host + path, because many sit
 *   on shared hosts such as raw.githubusercontent.com.
 */
class ThreatFeed(
    val generatedAt: Instant,
    val sources: List<FeedSource>,
    hostHashes: LongArray,
    urlHashes: LongArray,
    packageHashes: LongArray = LongArray(0),
    certificateHashes: LongArray = LongArray(0),
) {
    internal val hosts: LongArray = hostHashes.sortedDistinct()
    internal val urls: LongArray = urlHashes.sortedDistinct()

    internal val packages: LongArray = packageHashes.sortedDistinct()
    internal val certificates: LongArray = certificateHashes.sortedDistinct()

    val hostCount: Int get() = hosts.size
    val packageCount: Int get() = packages.size
    val certificateCount: Int get() = certificates.size
    val urlCount: Int get() = urls.size

    fun match(link: Link): FeedMatch? {
        if (urls.binarySearch(FeedHash.of(link.urlKey)) >= 0) return FeedMatch.Url
        val host = link.hostCandidates.firstOrNull { hosts.binarySearch(FeedHash.of(it)) >= 0 }
        return host?.let { FeedMatch.Host(it) }
    }

    /**
     * Is this installed app one of the known spying apps? By package name, or by the SHA-1 of a certificate it is
     * signed with (hex, any case), which still matches when the app is renamed. Never matches a website entry.
     */
    fun matchApp(packageName: String, certificateSha1s: Set<String>): AppMatch? {
        if (packages.binarySearch(FeedHash.of(PACKAGE_PREFIX + packageName.lowercase())) >= 0) return AppMatch.Package
        if (certificateSha1s.any { certificates.binarySearch(FeedHash.of(CERTIFICATE_PREFIX + it.lowercase())) >= 0 }) return AppMatch.Certificate
        return null
    }

    companion object {
        /** Keeps the three kinds of entry apart: a package name never equals a website's hash. */
        const val PACKAGE_PREFIX = "pkg:"
        const val CERTIFICATE_PREFIX = "cert:"

        fun of(
            generatedAt: Instant,
            sources: List<FeedSource>,
            hostKeys: Collection<String>,
            urlKeys: Collection<String>,
            packageNames: Collection<String> = emptyList(),
            certificateSha1s: Collection<String> = emptyList(),
        ) = ThreatFeed(
            generatedAt,
            sources,
            hostKeys.map(FeedHash::of).toLongArray(),
            urlKeys.map(FeedHash::of).toLongArray(),
            packageNames.map { FeedHash.of(PACKAGE_PREFIX + it.lowercase()) }.toLongArray(),
            certificateSha1s.map { FeedHash.of(CERTIFICATE_PREFIX + it.lowercase()) }.toLongArray(),
        )

        private fun LongArray.sortedDistinct(): LongArray = distinct().toLongArray().also { it.sort() }
    }
}

sealed interface FeedMatch {
    data class Host(val listedHost: String) : FeedMatch
    data object Url : FeedMatch
}

sealed interface AppMatch {
    data object Package : AppMatch
    data object Certificate : AppMatch
}

/** Where entries came from, shown in About with the licence. */
data class FeedSource(val name: String, val licence: String, val entries: Int)

object FeedHash {
    fun of(key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (digest[i].toLong() and 0xFF) }
    }
}
