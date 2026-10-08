package com.aegida.platform

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
}
