package com.nexustools.genericui.android

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpResponseNativeImpl

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpResponseNativeImpl]
 * backed by `java.net.HttpURLConnection` / `javax.net.ssl.HttpsURLConnection` response input stream.
 */
class AndroidHttpResponseNativeImpl(
    statusCode: Int,
    statusText: String = "",
    headers: Map<String, String> = emptyMap(),
    initialBodyBytes: ByteArray = ByteArray(0),
    url: String = "",
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
) : DefaultHttpResponseNativeImpl(
    statusCode = statusCode,
    statusText = statusText,
    headers = headers,
    initialBodyBytes = initialBodyBytes,
    url = url,
    byteOrder = byteOrder
) {
    val nativeApi: String = "java_net_HttpURLConnection_InputStream"
}
