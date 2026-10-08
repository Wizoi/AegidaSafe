package com.aegida.core.feed

import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.store.FeedStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class FeedUpdaterTest {
    private val now = Instant.now().truncatedTo(ChronoUnit.SECONDS)
    private val keys: KeyPair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
    private val sources = listOf(FeedSource("test", "test", 1))

    private fun feedOf(size: Int, at: Instant) =
        ThreatFeed.of(at, sources, List(size) { "h$it.example" }, emptyList())

    private fun sign(bytes: ByteArray, key: KeyPair = keys) =
        Signature.getInstance(FeedVerifier.ALGORITHM).run { initSign(key.private); update(bytes); sign() }

    /** Serves what it is given and records what was asked for. */
    private class FakeHttp(var sig: BytesResult, var list: BytesResult) : HttpClient {
        val asked = mutableListOf<Endpoint>()
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult = error("not used")
        override suspend fun delete(endpoint: Endpoint): HttpResult = error("not used")
        override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult {
            asked += endpoint
            return if (endpoint === Endpoint.Feed.SIGNATURE) sig else list
        }
    }

    private class MemoryFeedStore(var feed: ThreatFeed? = null) : FeedStore {
        var savedBytes: ByteArray? = null
        override suspend fun current() = feed
        override suspend fun save(verifiedFeed: ByteArray) { savedBytes = verifiedFeed; feed = FeedCodec.decode(verifiedFeed) }
    }

    private fun updater(http: FakeHttp, store: MemoryFeedStore) =
        FeedUpdater(http, FeedVerifier(keys.public.encoded), store) { now }

    private fun published(feed: ThreatFeed, key: KeyPair = keys): FakeHttp {
        val bytes = FeedCodec.encode(feed)
        return FakeHttp(BytesResult.Ok(sign(bytes, key)), BytesResult.Ok(bytes))
    }

    @Test fun aGoodNewerFeedReplacesTheStoredOne() = runTest {
        val store = MemoryFeedStore(feedOf(2_000, now.minus(Duration.ofDays(1))))
        val result = updater(published(feedOf(2_100, now.minus(Duration.ofHours(1)))), store).update()
        assertEquals(FeedUpdate.Updated(now.minus(Duration.ofHours(1))), result)
        assertEquals(2_100, store.feed!!.hostCount)
    }

    @Test fun theFirstEverDownloadIsAccepted() = runTest {
        val store = MemoryFeedStore(null)
        assertEquals(FeedUpdate.Updated(now), updater(published(feedOf(1_500, now)), store).update())
    }

    @Test fun aFeedSignedWithAnotherKeyIsRejectedAndNothingIsStored() = runTest {
        val other = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val store = MemoryFeedStore(feedOf(2_000, now.minus(Duration.ofDays(1))))
        val before = store.feed
        val result = updater(published(feedOf(2_100, now), key = other), store).update()
        assertEquals(FeedUpdate.Rejected(FeedRejection.BadSignature), result)
        assertNull(store.savedBytes)
        assertEquals(before, store.feed)
    }

    @Test fun aSignedFeedThatIsTooSmallOrShrankTooMuchKeepsTheLastGoodOne() = runTest {
        val current = feedOf(4_000, now.minus(Duration.ofDays(1)))
        listOf(10 to FeedRejection.TooSmall, 1_900 to FeedRejection.ShrankTooMuch).forEach { (size, why) ->
            val store = MemoryFeedStore(current)
            assertEquals(FeedUpdate.Rejected(why), updater(published(feedOf(size, now)), store).update())
            assertNull(store.savedBytes)
        }
    }

    @Test fun signedGarbageIsMalformedNotACrash() = runTest {
        val garbage = ByteArray(5_000) { it.toByte() }
        val http = FakeHttp(BytesResult.Ok(sign(garbage)), BytesResult.Ok(garbage))
        assertEquals(FeedUpdate.Rejected(FeedRejection.Malformed), updater(http, MemoryFeedStore()).update())
    }

    @Test fun theSameFeedAgainIsUnchangedNotAnError() = runTest {
        val feed = feedOf(2_000, now.minus(Duration.ofHours(5)))
        val store = MemoryFeedStore(feed)
        assertEquals(FeedUpdate.Unchanged, updater(published(feed), store).update())
        assertNull(store.savedBytes)
    }

    @Test fun anOlderFeedThanTheStoredOneIsRejected() = runTest {
        val store = MemoryFeedStore(feedOf(2_000, now.minus(Duration.ofHours(1))))
        assertEquals(FeedUpdate.Rejected(FeedRejection.OlderThanCurrent), updater(published(feedOf(2_000, now.minus(Duration.ofDays(2)))), store).update())
    }

    @Test fun offlineOrAMissingFileIsNoConnectionAndTheFeedBytesAreNotAskedForWithoutASignature() = runTest {
        val http = FakeHttp(BytesResult.Failed(null, FailureReason.Offline), BytesResult.Ok(ByteArray(0)))
        assertEquals(FeedUpdate.NoConnection(FailureReason.Offline), updater(http, MemoryFeedStore()).update())
        assertEquals(listOf<Endpoint>(Endpoint.Feed.SIGNATURE), http.asked)
        val notFound = FakeHttp(BytesResult.Ok(ByteArray(70)), BytesResult.Failed(404, FailureReason.HttpError))
        assertEquals(FeedUpdate.NoConnection(FailureReason.HttpError), updater(notFound, MemoryFeedStore()).update())
    }
}
