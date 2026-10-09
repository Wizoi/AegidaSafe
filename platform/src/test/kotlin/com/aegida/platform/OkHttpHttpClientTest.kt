package com.aegida.platform

import com.aegida.core.net.BytesResult
import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import com.aegida.platform.net.OkHttpHttpClient
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class OkHttpHttpClientTest {
    private val server = MockWebServer().apply { start() }
    private val endpoint = Endpoint.Ntfy(NtfyTopic("aegida_" + "q1W2e3R4".repeat(4)))
    private val client = OkHttpHttpClient { server.url("/" + it.path).toString() }

    @After fun stop() = server.shutdown()

    @Test fun postsBodyAndHeaders() = runTest {
        server.enqueue(MockResponse().setBody("""{"id":"1"}"""))
        val result = client.post(endpoint, "hello", mapOf("Title" to "Mom's phone"))
        assertEquals(HttpResult.Ok(200, """{"id":"1"}"""), result)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("hello", request.body.readUtf8())
        assertEquals("Mom's phone", request.getHeader("Title"))
    }

    @Test fun aServerErrorIsAFailureNotAnException() = runTest {
        server.enqueue(MockResponse().setResponseCode(429))
        assertEquals(HttpResult.Failed(429, FailureReason.HttpError), client.post(endpoint, "x"))
    }

    @Test fun aRedirectIsNotFollowed() = runTest {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://example.com/"))
        assertEquals(HttpResult.Failed(302, FailureReason.HttpError), client.post(endpoint, "x"))
        assertEquals(1, server.requestCount)
    }

    @Test fun getReturnsTheBytesAndTheFeedUrlIsTheFixedOne() = runTest {
        val payload = ByteArray(1000) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(okio.Buffer().write(payload)))
        val result = client.get(Endpoint.Feed.LIST, maxBytes = 1000) as BytesResult.Ok // exactly the limit is allowed
        assertEquals(payload.toList(), result.bytes.toList())
        assertEquals("GET", server.takeRequest().method)
        assertEquals("https://wizoi.github.io/Aegida-Feed/feed.bin", Endpoint.Feed.LIST.url)
        assertEquals("https://wizoi.github.io/Aegida-Feed/feed.sig", Endpoint.Feed.SIGNATURE.url)
    }

    @Test fun getRefusesAnythingLongerThanTheLimitWithoutReadingItAll() = runTest {
        server.enqueue(MockResponse().setBody(okio.Buffer().write(ByteArray(1001))))
        assertEquals(BytesResult.Failed(200, FailureReason.TooBig), client.get(Endpoint.Feed.LIST, maxBytes = 1000))
        server.enqueue(MockResponse().setChunkedBody(okio.Buffer().write(ByteArray(5000)), 100)) // no Content-Length
        assertEquals(BytesResult.Failed(200, FailureReason.TooBig), client.get(Endpoint.Feed.LIST, maxBytes = 1000))
    }

    @Test fun getReportsNotFoundAndNeverFollowsARedirect() = runTest {
        server.enqueue(MockResponse().setResponseCode(404))
        assertEquals(BytesResult.Failed(404, FailureReason.HttpError), client.get(Endpoint.Feed.SIGNATURE, 100))
        server.enqueue(MockResponse().setResponseCode(301).setHeader("Location", "https://example.com/feed.bin"))
        assertEquals(BytesResult.Failed(301, FailureReason.HttpError), client.get(Endpoint.Feed.LIST, 100))
        assertEquals(2, server.requestCount)
    }

    @Test fun aHeaderTheTransportCannotCarryIsAFailedSendNotACrash() = runTest {
        // OkHttp throws IllegalArgumentException for a non-ASCII or newline header value (findings #48).
        assertEquals(HttpResult.Failed(null, FailureReason.Unknown), client.post(endpoint, "x", mapOf("Title" to "Jos\u00e9")))
        assertEquals(HttpResult.Failed(null, FailureReason.Unknown), client.post(endpoint, "x", mapOf("Title" to "a\nb")))
        assertEquals(0, server.requestCount)
    }
}
