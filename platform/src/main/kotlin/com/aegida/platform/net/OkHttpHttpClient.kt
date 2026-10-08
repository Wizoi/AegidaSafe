package com.aegida.platform.net

import com.aegida.core.net.Endpoint
import com.aegida.core.net.FailureReason
import com.aegida.core.net.HttpClient
import com.aegida.core.net.HttpResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** The only OkHttp in the app (NetworkSingleOwnerTest). [urlFor] is replaced only by tests. */
class OkHttpHttpClient internal constructor(
    private val urlFor: (Endpoint) -> String,
) : HttpClient {

    @Inject constructor() : this({ it.url })

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false)
        .build()

    override suspend fun post(endpoint: Endpoint, body: String, headers: Map<String, String>): HttpResult {
        check(endpoint.host in Endpoint.ALLOWED_HOSTS) { "host not allowed" }
        val request = Request.Builder()
            .url(urlFor(endpoint))
            .post(body.toRequestBody("text/plain; charset=utf-8".toMediaType()))
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) HttpResult.Ok(response.code, response.body?.string().orEmpty())
                    else HttpResult.Failed(response.code, FailureReason.HttpError)
                }
            } catch (e: UnknownHostException) {
                HttpResult.Failed(null, FailureReason.Offline)
            } catch (e: InterruptedIOException) {
                HttpResult.Failed(null, FailureReason.Timeout)
            } catch (e: IOException) {
                HttpResult.Failed(null, FailureReason.Unknown)
            }
        }
    }
}
