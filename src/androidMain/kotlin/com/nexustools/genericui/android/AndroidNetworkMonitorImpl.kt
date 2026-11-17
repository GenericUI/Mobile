package com.nexustools.genericui.android

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import java.net.NetworkInterface

/**
 * Android `android.net.ConnectivityManager` (`NetworkCallback`) implementation of [NetworkMonitorNativeImpl].
 */
class AndroidNetworkMonitorImpl(
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : NetworkMonitorNativeImpl {

    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var callbackRegistered: Boolean = false
    private var listener: ((Boolean) -> Unit)? = null

    @Volatile
    private var onlineState: Boolean = true

    init {
        val activeCtx = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        connectivityManager = runCatching {
            activeCtx?.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        }.getOrNull()
        onlineState = evaluateOnlineState()
        registerCallbackIfPossible()
    }

    override fun isOnline(): Boolean {
        onlineState = evaluateOnlineState()
        return onlineState
    }

    override fun setOnConnectivityChanged(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
        if (listener != null && !callbackRegistered) {
            registerCallbackIfPossible()
        }
    }

    override fun dispose() {
        val cm = connectivityManager
        val cb = networkCallback
        if (cm != null && cb != null && callbackRegistered) {
            runCatching { cm.unregisterNetworkCallback(cb) }
        }
        callbackRegistered = false
        networkCallback = null
        connectivityManager = null
        listener = null
    }

    private fun registerCallbackIfPossible() {
        val cm = connectivityManager ?: return
        if (callbackRegistered) return
        val cb = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                refreshAndNotify()
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                refreshAndNotify()
            }

            override fun onLost(network: Network) {
                refreshAndNotify()
            }

            override fun onUnavailable() {
                refreshAndNotify()
            }
        }
        val registered = runCatching {
            cm.registerDefaultNetworkCallback(cb)
            true
        }.getOrDefault(false)
        if (registered) {
            networkCallback = cb
            callbackRegistered = true
        }
    }

    private fun refreshAndNotify() {
        val next = evaluateOnlineState()
        if (next != onlineState) {
            onlineState = next
            val currentListener = listener
            if (currentListener != null) {
                if (runCatching { android.os.Looper.getMainLooper() != null }.getOrDefault(false)) {
                    AndroidViewHandle.runOnMainThread {
                        currentListener(next)
                    }
                } else {
                    currentListener(next)
                }
            }
        }
    }

    private fun evaluateOnlineState(): Boolean {
        val cm = connectivityManager
        if (cm != null) {
            return runCatching {
                val activeNetwork = cm.activeNetwork ?: return@runCatching false
                val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
                caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }.getOrDefault(false)
        }
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return@runCatching true
            var hasAny = false
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                hasAny = true
                if (!iface.isLoopback && iface.isUp) {
                    return@runCatching true
                }
            }
            !hasAny
        }.getOrDefault(true)
    }
}
