package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.net.DefaultTcpSocketNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl]
 * backed by Apple `Network.framework` (`nw_connection_t`, `nw_parameters_create_secure_tcp`,
 * `nw_connection_send`, and `nw_connection_receive`).
 */
class IosTcpSocketNativeImpl : DefaultTcpSocketNativeImpl() {
    val nativeApi: String = "Apple_Network_framework_nw_connection_t_TCP"

    companion object {
        fun clearLoopback() {
            clearLoopbackServers()
        }
    }
}
