package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.net.DefaultHttpConnectionNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpConnectionNativeImpl]
 * representing a duplex HTTP exchange over Foundation `NSURLSessionDataTask`.
 */
class IosHttpConnectionNativeImpl(
    request: HttpRequest,
    response: HttpResponse
) : DefaultHttpConnectionNativeImpl(request, response) {
    val nativeApi: String = "Foundation_NSURLSession_NSURLSessionDataTask"
}
