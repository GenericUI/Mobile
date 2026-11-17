package com.nexustools.genericui.android

import android.os.Looper
import android.util.Log
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.jvm.JvmHttpConnectionNativeImpl
import com.nexustools.genericui.jvm.JvmHttpRequestNativeImpl
import com.nexustools.genericui.jvm.JvmHttpResponseNativeImpl
import com.nexustools.genericui.jvm.JvmHttpUrlConnectionClient
import com.nexustools.genericui.net.http.HttpMethod
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl]
 * backed by [JvmHttpUrlConnectionClient] (`java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection`
 * on `Dispatchers.IO`).
 */
class AndroidHttpClientNativeImpl(
    defaultHandler: (suspend (HttpRequest) -> HttpResponse)? = null
) : JvmHttpUrlConnectionClient(
    defaultHandler = defaultHandler,
    isOnMainThread = {
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    },
    onRequestError = { request, err ->
        val reason = "${err::class.simpleName ?: "NetworkError"}: ${err.message ?: "Connection failed"}"
        runCatching {
            Log.w("GenericUIHttp", "HTTP ${request.method} ${request.url} failed: $reason")
        }
    },
    returnZeroStatusOnFailure = {
        runCatching { Looper.getMainLooper() != null }.getOrDefault(false)
    }
) {
    override val nativeApi: String = "java_net_HttpURLConnection"

    override fun createRequest(
        url: String,
        method: HttpMethod,
        headers: Map<String, String>,
        initialBodyBytes: ByteArray?,
        timeoutMs: Long,
        followRedirects: Boolean,
        byteOrder: ByteOrder
    ): JvmHttpRequestNativeImpl = AndroidHttpRequestNativeImpl(
        url = url,
        method = method,
        headers = headers,
        initialBodyBytes = initialBodyBytes,
        timeoutMs = timeoutMs,
        followRedirects = followRedirects,
        byteOrder = byteOrder
    )

    override fun createResponse(
        statusCode: Int,
        statusText: String,
        headers: Map<String, String>,
        initialBodyBytes: ByteArray,
        url: String,
        byteOrder: ByteOrder
    ): JvmHttpResponseNativeImpl = AndroidHttpResponseNativeImpl(
        statusCode = statusCode,
        statusText = statusText,
        headers = headers,
        initialBodyBytes = initialBodyBytes,
        url = url,
        byteOrder = byteOrder
    )

    override fun createConnection(
        request: HttpRequest,
        response: HttpResponse
    ): JvmHttpConnectionNativeImpl = AndroidHttpConnectionNativeImpl(request, response)

    override fun wrapResponse(raw: HttpResponse, request: HttpRequest): JvmHttpResponseNativeImpl {
        if (raw is AndroidHttpResponseNativeImpl) return raw
        return createResponse(
            statusCode = raw.statusCode,
            statusText = raw.statusText,
            headers = raw.headers,
            initialBodyBytes = raw.bodyBytes,
            url = raw.url.ifEmpty { request.url },
            byteOrder = raw.byteOrder
        )
    }

    override suspend fun execute(request: HttpRequest): AndroidHttpResponseNativeImpl =
        super.execute(request) as AndroidHttpResponseNativeImpl

    override suspend fun openConnection(request: HttpRequest): AndroidHttpConnectionNativeImpl =
        super.openConnection(request) as AndroidHttpConnectionNativeImpl
}
