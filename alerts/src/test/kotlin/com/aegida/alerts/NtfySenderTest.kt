package com.aegida.alerts

import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NtfySenderTest {
    private val topic = NtfyTopic("aegida_" + "x9Y8z7W6".repeat(4))
    private val message = GuardianMessage("Mom's phone", "AnyDesk was installed", urgent = true)

    private class RecordingHttp(private val result: HttpResult) : HttpClient {
        val sent = mutableListOf<Triple<Endpoint, String, Map<String, String>>>()
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
            sent += Triple(endpoint, body, headers)
            return result
        }
    }

    @Test fun postsToTheTopicWithTitleAndUrgentPriority() = runTest {
        val http = RecordingHttp(HttpResult.Ok(200, "{}"))
        assertEquals(SendResult.Delivered, NtfySender(http).send(topic, message))
        val (endpoint, body, headers) = http.sent.single()
        assertEquals("https://ntfy.sh/${topic.value}", endpoint.url)
        assertEquals("AnyDesk was installed", body)
        assertEquals(mapOf("Title" to "Mom's phone", "Priority" to "urgent"), headers)
    }

    @Test fun aFailedPostIsReportedNotDropped() = runTest {
        val failure = HttpResult.Failed(null, FailureReason.Offline)
        val result = NtfySender(RecordingHttp(failure)).send(topic, message)
        assertTrue(result is SendResult.NotDelivered && result.failure == failure)
    }
}
