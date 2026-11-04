package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl]
 * backed by `java.net.Socket` / `java.nio.channels.SocketChannel` on `Dispatchers.IO`.
 */
class AndroidTcpSocketNativeImpl : DefaultTcpSocketNativeImpl() {
    val nativeApi: String = "java_net_Socket_SocketChannel_Dispatchers_IO"

    companion object {
        fun clearLoopback() {
            clearLoopbackServers()
        }
    }
}
