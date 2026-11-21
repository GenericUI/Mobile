package com.nexustools.genericui.android

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Looper
import com.nexustools.genericui.jvm.JvmUdpSocketNativeImpl

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl]
 * backed by [JvmUdpSocketNativeImpl] (`java.net.MulticastSocket` / `java.net.DatagramSocket`
 * on `Dispatchers.IO`), managing a reference-counted [WifiManager.MulticastLock] while joined
 * to one or more multicast groups.
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

    private val multicastLockMutex = Any()
    private val activeMulticastGroups = linkedSetOf<String>()

    @Volatile
    private var wifiMulticastLock: WifiManager.MulticastLock? = null

    @Volatile
    private var lockAcquiredBySocket: Boolean = false

    internal fun isMulticastLockHeld(): Boolean = synchronized(multicastLockMutex) {
        lockAcquiredBySocket && runCatching { wifiMulticastLock?.isHeld == true }.getOrDefault(false)
    }

    private fun acquireWifiMulticastLockIfNeeded() {
        synchronized(multicastLockMutex) {
            if (lockAcquiredBySocket) return
            val appCtx = AndroidViewHandle.activeContext?.let { it.applicationContext ?: it } ?: return
            val wifiManager = runCatching {
                appCtx.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            }.getOrNull() ?: return
            val lock = wifiMulticastLock ?: runCatching {
                wifiManager.createMulticastLock(MULTICAST_LOCK_TAG).apply {
                    setReferenceCounted(true)
                }
            }.getOrNull()?.also { wifiMulticastLock = it } ?: return
            runCatching {
                lock.acquire()
                lockAcquiredBySocket = true
            }
        }
    }

    private fun releaseWifiMulticastLockIfIdle(clearReference: Boolean = false) {
        synchronized(multicastLockMutex) {
            val lock = wifiMulticastLock
            if (lock != null && lockAcquiredBySocket) {
                runCatching {
                    if (lock.isHeld) {
                        lock.release()
                    }
                }
            }
            lockAcquiredBySocket = false
            if (clearReference) {
                wifiMulticastLock = null
            }
        }
    }

    override suspend fun joinMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        val groupKey = multicastAddress.trim()
        if (!supportsMulticast() || isDisposed || groupKey.isEmpty()) return false
        synchronized(multicastLockMutex) {
            activeMulticastGroups.add(groupKey)
        }
        acquireWifiMulticastLockIfNeeded()
        val joined = super.joinMulticastGroup(multicastAddress, interfaceName)
        if (!joined) {
            val shouldRelease = synchronized(multicastLockMutex) {
                activeMulticastGroups.remove(groupKey)
                activeMulticastGroups.isEmpty()
            }
            if (shouldRelease) {
                releaseWifiMulticastLockIfIdle()
            }
        }
        return joined
    }

    override suspend fun leaveMulticastGroup(multicastAddress: String, interfaceName: String?): Boolean {
        val left = super.leaveMulticastGroup(multicastAddress, interfaceName)
        val groupKey = multicastAddress.trim()
        val shouldRelease = synchronized(multicastLockMutex) {
            activeMulticastGroups.remove(groupKey)
            activeMulticastGroups.isEmpty()
        }
        if (shouldRelease) {
            releaseWifiMulticastLockIfIdle()
        }
        return left
    }

    override fun dispose() {
        synchronized(multicastLockMutex) {
            activeMulticastGroups.clear()
        }
        releaseWifiMulticastLockIfIdle(clearReference = true)
        super.dispose()
    }

    companion object {
        private const val MULTICAST_LOCK_TAG = "genericui-udp-multicast"
    }
}
