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
}

sealed interface HttpResult {
    data class Ok(val code: Int, val body: String) : HttpResult
    data class Failed(val code: Int?, val reason: FailureReason) : HttpResult
}

enum class FailureReason { Offline, Timeout, HttpError, Unknown }
