package com.aegida.alerts

import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import java.time.Duration
import java.util.Base64
import javax.inject.Inject

/** Space to tilde: what an HTTP header can carry as it is. */
private val PRINTABLE_ASCII = 0x20..0x7e

private const val HTTP_NOT_FOUND = 404

/**
 * The only code that sends to ntfy (NtfySingleOwnerTest). Called only by the alert classes in this module,
 * which build the message from a Problem or a fixed guardian text: coarse, in the owner's name, no URLs and no
 * message text.
 */
class NtfySender @Inject constructor(private val http: HttpClient) {

    suspend fun send(topic: NtfyTopic, message: GuardianMessage): SendResult {
        val headers = buildMap {
            put("Title", headerSafe(message.title))
            if (message.urgent) put("Priority", "urgent")
            message.delay?.let { put("X-Delay", "${it.toMinutes()}m") }
        }
        return when (val result = http.post(Endpoint.Ntfy(topic, message.sequenceId), message.body, headers)) {
            is HttpResult.Ok -> SendResult.Delivered
            is HttpResult.Failed -> SendResult.NotDelivered(result)
        }
    }

    /**
     * HTTP headers carry plain ASCII only, and OkHttp throws on anything else, so a name like "José" would stop every
     * guardian message (findings #48). Anything outside printable ASCII is sent as an RFC 2047 encoded word, which
     * ntfy decodes (checked live, findings #59). A newline is outside printable ASCII too, so it is encoded and cannot end the header.
     */
    private fun headerSafe(text: String): String {
        val clean = text.trim()
        if (clean.all { it.code in PRINTABLE_ASCII }) return clean
        return "=?UTF-8?B?" + Base64.getEncoder().encodeToString(clean.toByteArray(Charsets.UTF_8)) + "?="
    }

    /** Cancels a scheduled message by its sequence id. A message that is not there is already cancelled. */
    suspend fun cancel(topic: NtfyTopic, sequenceId: String): SendResult =
        when (val result = http.delete(Endpoint.Ntfy(topic, sequenceId))) {
            is HttpResult.Ok -> SendResult.Delivered
            is HttpResult.Failed -> if (result.code == HTTP_NOT_FOUND) SendResult.Delivered else SendResult.NotDelivered(result)
        }
}

/**
 * [delay] holds the message back that long (ntfy allows 10 seconds to 3 days). Sending again with the same
 * [sequenceId] replaces a message still waiting, which is how a "hasn't checked in" message is pushed back
 * every time the phone checks in.
 */
data class GuardianMessage(
    val title: String,
    val body: String,
    val urgent: Boolean,
    val delay: Duration? = null,
    val sequenceId: String? = null,
)

/** A send always reports what happened; a failed send is retried and shown in History, and one given up after a week says so (GuardianNeverReached). */
sealed interface SendResult {
    data object Delivered : SendResult
    data class NotDelivered(val failure: HttpResult.Failed) : SendResult
}
