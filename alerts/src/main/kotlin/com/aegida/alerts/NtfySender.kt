package com.aegida.alerts

import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import java.time.Duration
import javax.inject.Inject

/**
 * The only code that sends to ntfy (NtfySingleOwnerTest). Called only by the alert classes in this module,
 * which build the message from a Problem or a fixed guardian text: coarse, in the owner's name, no URLs and no
 * message text.
 */
class NtfySender @Inject constructor(private val http: HttpClient) {

    suspend fun send(topic: NtfyTopic, message: GuardianMessage): SendResult {
        val headers = buildMap {
            put("Title", message.title)
            if (message.urgent) put("Priority", "urgent")
            message.delay?.let { put("X-Delay", "${it.toMinutes()}m") }
        }
        return when (val result = http.post(Endpoint.Ntfy(topic, message.sequenceId), message.body, headers)) {
            is HttpResult.Ok -> SendResult.Delivered
            is HttpResult.Failed -> SendResult.NotDelivered(result)
        }
    }

    /** Cancels a scheduled message by its sequence id. A message that is not there is already cancelled. */
    suspend fun cancel(topic: NtfyTopic, sequenceId: String): SendResult =
        when (val result = http.delete(Endpoint.Ntfy(topic, sequenceId))) {
            is HttpResult.Ok -> SendResult.Delivered
            is HttpResult.Failed -> if (result.code == 404) SendResult.Delivered else SendResult.NotDelivered(result)
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

/** A send always reports what happened; a failed send is retried and shown in History, never dropped. */
sealed interface SendResult {
    data object Delivered : SendResult
    data class NotDelivered(val failure: HttpResult.Failed) : SendResult
}
