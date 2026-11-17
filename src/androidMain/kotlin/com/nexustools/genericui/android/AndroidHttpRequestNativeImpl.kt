package com.nexustools.genericui.android

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.jvm.JvmHttpRequestNativeImpl
import com.nexustools.genericui.net.http.HttpMethod

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.HttpRequestNativeImpl]
 * backed by [JvmHttpRequestNativeImpl] (`java.net.HttpURLConnection` output streaming).
 */
class AndroidHttpRequestNativeImpl(
    url: String,
    method: HttpMethod = HttpMethod.GET,
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray? = null,
    timeoutMs: Long = 30_000L,
    followRedirects: Boolean = true,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : JvmHttpRequestNativeImpl(
    url = url,
    method = method,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    timeoutMs = timeoutMs,
    followRedirects = followRedirects,
    byteOrder = byteOrder
) {
    override val nativeApi: String = "java_net_HttpURLConnection_OutputStream"
}
