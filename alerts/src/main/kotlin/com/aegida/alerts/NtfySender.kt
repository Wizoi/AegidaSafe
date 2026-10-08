package com.aegida.alerts

import com.aegida.core.net.Endpoint
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import com.aegida.core.net.NtfyTopic
import javax.inject.Inject

/**
 * The only code that sends to ntfy (NtfySingleOwnerTest). Called only by AlertPipeline, which builds
 * the message from a Finding: coarse, in Mom's name, no URLs and no message text.
 */
class NtfySender @Inject constructor(private val http: HttpClient) {

    suspend fun send(topic: NtfyTopic, message: GuardianMessage): SendResult {
        val headers = buildMap {
            put("Title", message.title)
            if (message.urgent) put("Priority", "urgent")
        }
        return when (val result = http.post(Endpoint.Ntfy(topic), message.body, headers)) {
            is HttpResult.Ok -> SendResult.Delivered
            is HttpResult.Failed -> SendResult.NotDelivered(result)
        }
    }
}

data class GuardianMessage(val title: String, val body: String, val urgent: Boolean)

/** A send always reports what happened; a failed send is retried and shown in History, never dropped. */
sealed interface SendResult {
    data object Delivered : SendResult
    data class NotDelivered(val failure: HttpResult.Failed) : SendResult
}
