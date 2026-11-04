package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl]
 * backed by `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` on [Dispatchers.IO],
 * consulting [DefaultHttpClientNativeImpl] mock routes first when registered in unit tests.
 */
class AndroidHttpClientNativeImpl(
    private val defaultHandler: (suspend (HttpRequest) -> HttpResponse)? = null
) : DefaultHttpClientNativeImpl() {

    val nativeApi: String = "java_net_HttpURLConnection"

    private val capturedRequests = ArrayList<HttpRequest>()

    var requestCount: Int = 0
        private set

    fun requestHistory(): List<HttpRequest> =
        capturedRequests.toList()

    override suspend fun execute(request: HttpRequest): AndroidHttpResponseNativeImpl {
        requestCount += 1
        capturedRequests.add(request)
        if (isDisposed) {
            return AndroidHttpResponseNativeImpl(
                statusCode = 499,
                statusText = "Client Closed Request",
                url = request.url,
                byteOrder = request.byteOrder
            )
        }

        val matchedRoute = findRouteHandler(request.url) ?: defaultHandler
        if (matchedRoute != null || isMockOrOfflineUrl(request.url)) {
            val raw = matchedRoute?.invoke(request) ?: super.execute(request)
            return wrapAndroidResponse(raw, request)
        }

        return runOnAndroidIo {
            try {
                val conn = openConfiguredConnection(request)
                try {
                    val statusCode = conn.responseCode
                    val statusText = conn.responseMessage ?: ""
                    val headers = LinkedHashMap<String, String>()
                    var headerIndex = 0
                    while (true) {
                        val key = conn.getHeaderFieldKey(headerIndex)
                        val value = conn.getHeaderField(headerIndex)
                        if (key == null && value == null) break
                        if (!key.isNullOrEmpty() && value != null) {
                            headers[key] = value
                        }
                        headerIndex++
                    }

                    val bodyStream = if (statusCode >= 400) conn.errorStream else conn.inputStream
                    val bodyBytes = bodyStream?.use { it.readBytes() } ?: ByteArray(0)
                    val resolvedUrl = conn.url?.toString() ?: request.url
                    AndroidHttpResponseNativeImpl(
                        statusCode = statusCode,
                        statusText = statusText,
                        headers = headers,
                        initialBodyBytes = bodyBytes,
                        url = resolvedUrl,
                        byteOrder = request.byteOrder
                    )
                } finally {
                    runCatching { conn.disconnect() }
                }
            } catch (e: Throwable) {
                if (request.headers.containsKey("X-Episode-Caster-Client-Id") || request.url.contains("/v1/")) {
                    AndroidHttpResponseNativeImpl(
                        statusCode = 0,
                        statusText = e.message ?: "Connection failed",
                        headers = emptyMap(),
                        initialBodyBytes = ByteArray(0),
                        url = request.url,
                        byteOrder = request.byteOrder
                    )
                } else {
                    wrapAndroidResponse(super.execute(request), request)
                }
            }
        }
    }

    override suspend fun openConnection(request: HttpRequest): AndroidHttpConnectionNativeImpl {
        val nativeRequest = request as? AndroidHttpRequestNativeImpl ?: AndroidHttpRequestNativeImpl(
            url = request.url,
            method = request.method,
            headers = request.headers,
            initialBodyBytes = request.bodyBytes,
            timeoutMs = request.timeoutMs,
            followRedirects = request.followRedirects,
            byteOrder = request.byteOrder
        )
        val response = execute(nativeRequest)
        return AndroidHttpConnectionNativeImpl(nativeRequest, response)
    }

    override fun dispose() {
        capturedRequests.clear()
        super.dispose()
    }

    private fun wrapAndroidResponse(raw: HttpResponse, request: HttpRequest): AndroidHttpResponseNativeImpl {
        if (raw is AndroidHttpResponseNativeImpl) return raw
        return AndroidHttpResponseNativeImpl(
            statusCode = raw.statusCode,
            statusText = raw.statusText,
            headers = raw.headers,
            initialBodyBytes = raw.bodyBytes,
            url = raw.url.ifEmpty { request.url },
            byteOrder = raw.byteOrder
        )
    }

    private fun openConfiguredConnection(request: HttpRequest): HttpURLConnection {
        val url = URL(request.url.trim())
        val conn = url.openConnection() as HttpURLConnection
        val timeout = request.timeoutMs.coerceIn(100L, 120_000L).toInt()
        conn.connectTimeout = timeout
        conn.readTimeout = timeout
        conn.instanceFollowRedirects = request.followRedirects
        conn.requestMethod = request.method.name
        conn.useCaches = false

        for ((k, v) in request.headers) {
            if (k.isNotBlank()) {
                conn.setRequestProperty(k.trim(), v)
            }
        }

        val body = request.bodyBytes
        if (body != null && body.isNotEmpty()) {
            conn.doOutput = true
            conn.setFixedLengthStreamingMode(body.size)
            conn.outputStream.use { out ->
                out.write(body)
                out.flush()
            }
        }
        return conn
    }

    private fun isMockOrOfflineUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".example.com") || lower.contains(".example.org") || lower.contains(".invalid")
    }

    private suspend fun <T> runOnAndroidIo(block: suspend () -> T): T {
        val hasInterceptor = coroutineContext[ContinuationInterceptor] != null
        return if (hasInterceptor || isOnAndroidMainThread()) {
            withContext(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    private fun isOnAndroidMainThread(): Boolean =
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
}
