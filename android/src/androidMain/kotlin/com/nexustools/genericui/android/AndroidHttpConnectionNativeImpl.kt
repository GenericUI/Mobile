package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.net.DefaultHttpConnectionNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.HttpConnectionNativeImpl]
 * representing a duplex HTTP exchange over `java.net.HttpURLConnection`.
 */
class AndroidHttpConnectionNativeImpl(
    request: HttpRequest,
    response: HttpResponse
) : DefaultHttpConnectionNativeImpl(request, response) {
    val nativeApi: String = "java_net_HttpURLConnection"
}
