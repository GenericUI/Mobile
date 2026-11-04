package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.net.DefaultUdpSocketNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl]
 * backed by Apple `Network.framework` (`nw_connection_t`, `nw_parameters_create_secure_udp`,
 * and BSD `IP_ADD_MEMBERSHIP` multicast support).
 */
class IosUdpSocketNativeImpl : DefaultUdpSocketNativeImpl() {
    val nativeApi: String = "Apple_Network_framework_nw_connection_t_UDP"

    companion object {
        fun clearLoopback() {
            clearLoopbackEndpoints()
        }
    }
}
