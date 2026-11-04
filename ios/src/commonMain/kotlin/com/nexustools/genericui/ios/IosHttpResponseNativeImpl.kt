package com.nexustools.genericui.ios

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.net.DefaultHttpResponseNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpResponseNativeImpl]
 * backed by Foundation `NSHTTPURLResponse` and `NSURLSessionDataDelegate` stream.
 */
class IosHttpResponseNativeImpl(
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
    val nativeApi: String = "Foundation_NSHTTPURLResponse_NSURLSessionDataTask"
}
