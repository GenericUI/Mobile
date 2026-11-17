package com.nexustools.genericui.android

import android.os.Looper
import com.nexustools.genericui.jvm.JvmUdpSocketNativeImpl

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl]
 * backed by [JvmUdpSocketNativeImpl] (`java.net.MulticastSocket` / `java.net.DatagramSocket`
 * on `Dispatchers.IO`).
 */
class AndroidUdpSocketNativeImpl : JvmUdpSocketNativeImpl(
    isOnMainThread = {
        runCatching {
            val main = Looper.getMainLooper() ?: return@runCatching false
            Looper.myLooper() === main
        }.getOrDefault(false)
    }
) {
    override val nativeApi: String = "java_net_DatagramSocket_MulticastSocket_Dispatchers_IO"
}
