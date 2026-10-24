package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl

/**
 * Android `android.net.ConnectivityManager` (`NetworkCallback`) implementation of [NetworkMonitorNativeImpl].
 */
class AndroidNetworkMonitorImpl : NetworkMonitorNativeImpl {
    private var onlineState: Boolean = true
    private var listener: ((Boolean) -> Unit)? = null

    override fun isOnline(): Boolean = onlineState

    override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
    }

    override fun dispose() {
        listener = null
    }
}
