package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * iOS / iPadOS [SystemTrayNativeImpl] reporting [isSupported] = `false` and throwing
 * [UnsupportedOperationException] on every tray mutator per platform capability rules.
 */
class IosSystemTrayImpl : SystemTrayNativeImpl {
    override val isSupported: Boolean = false
    override var isDisposed: Boolean = false

    override fun setIcon(iconPathOrUrl: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on iOS/iPadOS")
    }

    override fun setTooltip(tooltip: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on iOS/iPadOS")
    }

    override fun setBadgeText(badge: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on iOS/iPadOS")
    }

    override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) {
        throw UnsupportedOperationException("SystemTray is not supported on iOS/iPadOS")
    }

    override fun setOnTrayIconClicked(listener: (() -> Unit)?) {
        throw UnsupportedOperationException("SystemTray is not supported on iOS/iPadOS")
    }

    override fun dispose() {
        isDisposed = true
    }
}
