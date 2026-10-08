package com.aegida.core.feed

import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.store.FeedStore
import com.aegida.core.time.Clock
import java.util.Base64

/** The public half of the key that signs the feed. The private half exists only as a GitHub secret. */
object FeedKey {
    /** Same value as feedtool/feed-public-key.txt; FeedKeyTest fails if the two differ. */
    const val PUBLIC_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEpwZ+KVHhHXLpFvqo8T1CNF+CHfyIYvyQiSNXDkYtLX+28SYLamXpjDPN723BY4/rrW7Emp79Xz/nfOfPHkqbFg=="

    fun verifier() = FeedVerifier(Base64.getDecoder().decode(PUBLIC_BASE64))
}

sealed interface FeedUpdate {
    data class Updated(val generatedAt: java.time.Instant) : FeedUpdate

    /** The published feed is the one already stored. */
    data object Unchanged : FeedUpdate
    data class NoConnection(val reason: FailureReason) : FeedUpdate

    /** Downloaded but not used; the last good feed stays (design section 6). */
    data class Rejected(val why: FeedRejection) : FeedUpdate
}

/**
 * Downloads the feed and its signature, and replaces the stored feed only if the signature matches the
 * built-in key, the file is well-formed, and it passes the same sanity rules the publisher applies.
 * The signature is fetched first (70 bytes) so a missing feed costs almost nothing.
 */
class FeedUpdater(
    private val http: HttpClient,
    private val verifier: FeedVerifier,
    private val store: FeedStore,
    private val clock: Clock,
) {
    suspend fun update(): FeedUpdate {
        val signature = when (val result = http.get(Endpoint.Feed.SIGNATURE, MAX_SIGNATURE_BYTES)) {
            is BytesResult.Ok -> result.bytes
            is BytesResult.Failed -> return FeedUpdate.NoConnection(result.reason)
        }
        val bytes = when (val result = http.get(Endpoint.Feed.LIST, MAX_FEED_BYTES)) {
            is BytesResult.Ok -> result.bytes
            is BytesResult.Failed -> return FeedUpdate.NoConnection(result.reason)
        }
        // Signature before parsing, so unsigned bytes never reach the decoder.
        if (!verifier.verify(bytes, signature)) return FeedUpdate.Rejected(FeedRejection.BadSignature)
        val candidate = try {
            FeedCodec.decode(bytes)
        } catch (e: FeedFormatException) {
            return FeedUpdate.Rejected(FeedRejection.Malformed)
        }
        val current = store.current()
        if (current != null && candidate.generatedAt == current.generatedAt) return FeedUpdate.Unchanged
        FeedSanity.check(candidate, current, clock.now())?.let { return FeedUpdate.Rejected(it) }
        store.save(bytes)
        return FeedUpdate.Updated(candidate.generatedAt)
    }

    companion object {
        const val MAX_SIGNATURE_BYTES = 256
        const val MAX_FEED_BYTES = 32 * 1024 * 1024
    }
}
