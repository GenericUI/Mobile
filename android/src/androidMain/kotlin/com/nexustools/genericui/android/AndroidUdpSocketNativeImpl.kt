package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl]
 * backed by `java.net.DatagramSocket` and `java.net.MulticastSocket` on `Dispatchers.IO`.
 */
class AndroidUdpSocketNativeImpl : DefaultUdpSocketNativeImpl() {
    val nativeApi: String = "java_net_DatagramSocket_MulticastSocket_Dispatchers_IO"

    companion object {
        fun clearLoopback() {
            clearLoopbackEndpoints()
        }
    }
}
