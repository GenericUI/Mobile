package com.nexustools.genericui.android

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.jvm.JvmHttpResponseNativeImpl

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.HttpResponseNativeImpl]
 * backed by [JvmHttpResponseNativeImpl] (`java.net.HttpURLConnection` input streaming).
 */
class AndroidHttpResponseNativeImpl(
    statusCode: Int,
    statusText: String = "",
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray = ByteArray(0),
    url: String = "",
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : JvmHttpResponseNativeImpl(
    statusCode = statusCode,
    statusText = statusText,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    url = url,
    byteOrder = byteOrder
) {
    override val nativeApi: String = "java_net_HttpURLConnection_InputStream"
}
