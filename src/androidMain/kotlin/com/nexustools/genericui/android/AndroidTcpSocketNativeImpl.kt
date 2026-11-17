package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.jvm.JvmTcpSocketNativeImpl

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl]
 * backed by [JvmTcpSocketNativeImpl] (`java.net.Socket` / `javax.net.ssl.SSLSocket` +
 * `JvmPermissiveX509TrustManager` on `Dispatchers.IO`).
 */
class AndroidTcpSocketNativeImpl : JvmTcpSocketNativeImpl(
    isOnMainThread = {
        runCatching {
            val main = Looper.getMainLooper() ?: return@runCatching false
            Looper.myLooper() === main
        }.getOrDefault(false)
    }
) {
    override val nativeApi: String = "java_net_Socket_SocketChannel_Dispatchers_IO"
}
