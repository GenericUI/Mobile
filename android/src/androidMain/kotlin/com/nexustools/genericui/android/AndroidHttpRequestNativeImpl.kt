package com.nexustools.genericui.android

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpRequestNativeImpl
import com.nexustools.genericui.net.http.HttpMethod

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpRequestNativeImpl]
 * backed by `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` request output stream.
 */
class AndroidHttpRequestNativeImpl(
    url: String,
    method: HttpMethod = HttpMethod.GET,
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray? = null,
    timeoutMs: Long = 30_000L,
    followRedirects: Boolean = true,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : DefaultHttpRequestNativeImpl(
    url = url,
    method = method,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    timeoutMs = timeoutMs,
    followRedirects = followRedirects,
    byteOrder = byteOrder
) {
    val nativeApi: String = "java_net_HttpURLConnection_OutputStream"
}
