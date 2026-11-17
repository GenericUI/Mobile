package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * Android [SystemTrayNativeImpl] reporting `isSupported = false` and failing fast with
 * [UnsupportedOperationException] on system tray mutation calls.
 */
class AndroidSystemTrayImpl : SystemTrayNativeImpl {
    override val isSupported: Boolean = false

    override fun setIcon(iconPathOrUrl: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    override fun setTooltip(tooltip: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    override fun setBadgeText(badge: String?) {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    override fun setOnTrayIconClicked(listener: (() -> Unit)?) {}

    fun show() {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    fun hide() {
        throw UnsupportedOperationException("SystemTray is not supported on Android")
    }

    override fun dispose() {}
}
