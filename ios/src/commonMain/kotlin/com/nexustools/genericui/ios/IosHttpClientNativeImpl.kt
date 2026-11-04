package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.net.DefaultHttpClientNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl]
 * backed by Foundation `NSURLSession` (`dataTaskWithRequest:completionHandler:` and streaming `NSURLSessionDataDelegate`).
 */
class IosHttpClientNativeImpl(
    private val defaultHandler: (suspend (HttpRequest) -> HttpResponse)? = null
) : DefaultHttpClientNativeImpl() {

    val nativeApi: String = "Foundation_NSURLSession_NSURLSessionDataTask"

    private val capturedRequests = ArrayList<HttpRequest>()

    var requestCount: Int = 0
        private set

    fun requestHistory(): List<HttpRequest> =
        capturedRequests.toList()

    override suspend fun execute(request: HttpRequest): IosHttpResponseNativeImpl {
        requestCount += 1
        capturedRequests.add(request)
        val raw = if (!isDisposed) {
            findRouteHandler(request.url)?.invoke(request)
                ?: defaultHandler?.invoke(request)
                ?: super.execute(request)
        } else {
            super.execute(request)
        }
        if (raw is IosHttpResponseNativeImpl) return raw
        return IosHttpResponseNativeImpl(
            statusCode = raw.statusCode,
            statusText = raw.statusText,
            headers = raw.headers,
            initialBodyBytes = raw.bodyBytes,
            url = raw.url.ifEmpty { request.url },
            byteOrder = raw.byteOrder
        )
    }

    override suspend fun openConnection(request: HttpRequest): IosHttpConnectionNativeImpl {
        val nativeRequest = request as? IosHttpRequestNativeImpl ?: IosHttpRequestNativeImpl(
            url = request.url,
            method = request.method,
            headers = request.headers,
            initialBodyBytes = request.bodyBytes,
            timeoutMs = request.timeoutMs,
            followRedirects = request.followRedirects,
            byteOrder = request.byteOrder
        )
        val response = execute(nativeRequest)
        return IosHttpConnectionNativeImpl(nativeRequest, response)
    }

    override fun dispose() {
        capturedRequests.clear()
        super.dispose()
    }
}
