package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl

/**
 * iOS implementation of [SystemTrayNativeImpl] (unsupported on iOS, updates home-screen icon badge).
 */
class IosSystemTrayImpl : SystemTrayNativeImpl {
    override val isSupported: Boolean = false
    override fun setIcon(iconPathOrUrl: String?) {}
    override fun setTooltip(tooltip: String?) {}
    override fun setBadgeText(badge: String?) {}
    override fun setContextMenu(menuImpl: ContextMenuNativeImpl<*>?) {}
    override fun setOnTrayIconClicked(listener: (() -> Unit)?) {}
    override fun dispose() {}
}
