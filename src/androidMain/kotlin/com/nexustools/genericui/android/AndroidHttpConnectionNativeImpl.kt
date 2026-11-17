package com.nexustools.genericui.android

import com.nexustools.genericui.jvm.JvmHttpConnectionNativeImpl
import com.nexustools.genericui.net.http.HttpRequest
import com.nexustools.genericui.net.http.HttpResponse

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.HttpConnectionNativeImpl]
 * backed by [JvmHttpConnectionNativeImpl] (`java.net.HttpURLConnection`).
 */
class AndroidHttpConnectionNativeImpl(
    request: HttpRequest,
    response: HttpResponse
) : JvmHttpConnectionNativeImpl(
    request = request,
    response = response
) {
    override val nativeApi: String = "java_net_HttpURLConnection"
}
