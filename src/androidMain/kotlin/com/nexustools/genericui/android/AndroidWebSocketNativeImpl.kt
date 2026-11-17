package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.jvm.JvmRfc6455WebSocketNativeImpl

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl]
 * backed by [JvmRfc6455WebSocketNativeImpl] executing RFC 6455 upgrades and frames over
 * `Socket` / `SSLSocket` on `Dispatchers.IO` (using `kotlin.io.encoding.Base64` compatible with `minSdk = 24`).
 */
class AndroidWebSocketNativeImpl : JvmRfc6455WebSocketNativeImpl(
    isOnMainThread = {
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
)
