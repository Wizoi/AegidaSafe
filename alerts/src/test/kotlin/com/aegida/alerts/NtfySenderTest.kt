package com.aegida.alerts

import com.aegida.core.net.BytesResult
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
        override suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult = error("not used")
        override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
            sent += Triple(endpoint, body, headers)
            return result
        }
        val deleted = mutableListOf<Endpoint>()
        override suspend fun delete(endpoint: Endpoint): HttpResult {
            deleted += endpoint
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

    @Test fun aDelayedMessageCarriesTheDelayAndReplacesBySequenceId() = runTest {
        val http = RecordingHttp(HttpResult.Ok(200, "{}"))
        val waiting = GuardianMessage("Mom", "quiet", urgent = false, delay = java.time.Duration.ofDays(2), sequenceId = "heartbeat")
        NtfySender(http).send(topic, waiting)
        val (endpoint, _, headers) = http.sent.single()
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", endpoint.url)
        assertEquals("2880m", headers["X-Delay"])
    }

    @Test fun cancelDeletesBySequenceIdAndAMissingMessageCountsAsCancelled() = runTest {
        val http = RecordingHttp(HttpResult.Ok(200, "{}"))
        assertEquals(SendResult.Delivered, NtfySender(http).cancel(topic, "heartbeat"))
        assertEquals("https://ntfy.sh/${topic.value}/heartbeat", http.deleted.single().url)
        val gone = RecordingHttp(HttpResult.Failed(404, FailureReason.HttpError))
        assertEquals(SendResult.Delivered, NtfySender(gone).cancel(topic, "heartbeat"))
        val down = RecordingHttp(HttpResult.Failed(null, FailureReason.Offline))
        assertTrue(NtfySender(down).cancel(topic, "heartbeat") is SendResult.NotDelivered)
    }

    @Test fun aFailedPostIsReportedNotDropped() = runTest {
        val failure = HttpResult.Failed(null, FailureReason.Offline)
        val result = NtfySender(RecordingHttp(failure)).send(topic, message)
        assertTrue(result is SendResult.NotDelivered && result.failure == failure)
    }

    /** What ntfy does with an RFC 2047 encoded-word title (checked live on ntfy.sh, findings #59). */
    private fun decodeTitle(header: String): String {
        val match = Regex("^=\\?UTF-8\\?B\\?([A-Za-z0-9+/=]+)\\?=$").matchEntire(header)
        return if (match == null) header else String(java.util.Base64.getDecoder().decode(match.groupValues[1]), Charsets.UTF_8)
    }

    @Test fun anOwnerNameWithAccentsOrSymbolsGoesOutAsAnAsciiOnlyHeaderThatDecodesBack() = runTest {
        listOf("José", "Zoë", "Mom \u2764", "Åsa Öberg").forEach { name ->
            val http = RecordingHttp(HttpResult.Ok(200, "{}"))
            NtfySender(http).send(topic, GuardianMessage(name, "$name's phone", urgent = false))
            val title = http.sent.single().third.getValue("Title")
            assertTrue("header must be plain ASCII: $title", title.all { it.code in 0x20..0x7e })
            assertEquals(name, decodeTitle(title))
            assertEquals("$name's phone", http.sent.single().second) // the body is UTF-8 as it is
        }
    }

    @Test fun aNewlineInTheNameCannotBreakTheHeaderOrAddOne() = runTest {
        val http = RecordingHttp(HttpResult.Ok(200, "{}"))
        NtfySender(http).send(topic, GuardianMessage("Ruth\r\nX-Delay: 1m", "body", urgent = false))
        val title = http.sent.single().third.getValue("Title")
        assertTrue(title.none { it == '\r' || it == '\n' })
        assertEquals(setOf("Title"), http.sent.single().third.keys)
    }

    @Test fun anOrdinaryNameIsSentAsItIs() = runTest {
        val http = RecordingHttp(HttpResult.Ok(200, "{}"))
        NtfySender(http).send(topic, GuardianMessage("Ruth", "body", urgent = false))
        assertEquals("Ruth", http.sent.single().third.getValue("Title"))
    }
}
