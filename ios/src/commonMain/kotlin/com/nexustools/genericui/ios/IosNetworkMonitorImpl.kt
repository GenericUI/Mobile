package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl

/**
 * iOS `NWPathMonitor` implementation of [NetworkMonitorNativeImpl].
 */
class IosNetworkMonitorImpl : NetworkMonitorNativeImpl {
    private var onlineState: Boolean = true
    private var listener: ((Boolean) -> Unit)? = null

    override fun isOnline(): Boolean = onlineState
    override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) { this.listener = listener }
    override fun dispose() { listener = null }
}
