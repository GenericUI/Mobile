package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * Android Foreground Service Notification implementation of [SystemTrayNativeImpl].
 */
class AndroidSystemTrayImpl : SystemTrayNativeImpl {
    override val isSupported: Boolean = false

    override fun setIcon(iconPathOrUrl: String?) {}
    override fun setTooltip(tooltip: String?) {}
    override fun setBadgeText(badge: String?) {}
    override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) {}
    override fun setOnTrayIconClicked(listener: (() -> Unit)?) {}
    override fun dispose() {}
}
