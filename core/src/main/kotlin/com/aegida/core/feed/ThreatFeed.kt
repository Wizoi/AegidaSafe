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
) {
    internal val hosts: LongArray = hostHashes.sortedDistinct()
    internal val urls: LongArray = urlHashes.sortedDistinct()

    val hostCount: Int get() = hosts.size
    val urlCount: Int get() = urls.size

    fun match(link: Link): FeedMatch? {
        if (urls.binarySearch(FeedHash.of(link.urlKey)) >= 0) return FeedMatch.Url
        val host = link.hostCandidates.firstOrNull { hosts.binarySearch(FeedHash.of(it)) >= 0 }
        return host?.let { FeedMatch.Host(it) }
    }

    companion object {
        fun of(generatedAt: Instant, sources: List<FeedSource>, hostKeys: Collection<String>, urlKeys: Collection<String>) =
            ThreatFeed(
                generatedAt,
                sources,
                hostKeys.map(FeedHash::of).toLongArray(),
                urlKeys.map(FeedHash::of).toLongArray(),
            )

        private fun LongArray.sortedDistinct(): LongArray = distinct().toLongArray().also { it.sort() }
    }
}

sealed interface FeedMatch {
    data class Host(val listedHost: String) : FeedMatch
    data object Url : FeedMatch
}

/** Where entries came from, shown in About with the licence. */
data class FeedSource(val name: String, val licence: String, val entries: Int)

object FeedHash {
    fun of(key: String): Long {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        return (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (digest[i].toLong() and 0xFF) }
    }
}
