package com.aegida.core.net

/**
 * The only way Aegida talks to the network (NetworkSingleOwnerTest).
 *
 * A request names an [Endpoint], never a free-form URL, so a URL taken from a message or a
 * notification has no way in. Adding a host means adding an [Endpoint] here, which is also the list
 * the Play data-safety form is written from.
 */
interface HttpClient {
    suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String> = emptyMap()): HttpResult

    /** Cancels something this app published (an ntfy message still waiting to be delivered). */
    suspend fun delete(endpoint: Endpoint): HttpResult

    /** Downloads at most [maxBytes]; a longer answer is [FailureReason.TooBig], never read to the end. */
    suspend fun get(endpoint: Endpoint, maxBytes: Int): BytesResult
}

sealed interface HttpResult {
    data class Ok(val code: Int, val body: String) : HttpResult
    data class Failed(val code: Int?, val reason: FailureReason) : HttpResult
}

sealed interface BytesResult {
    class Ok(val bytes: ByteArray) : BytesResult
    data class Failed(val code: Int?, val reason: FailureReason) : BytesResult
}

enum class FailureReason { Offline, Timeout, HttpError, TooBig, Unknown }
