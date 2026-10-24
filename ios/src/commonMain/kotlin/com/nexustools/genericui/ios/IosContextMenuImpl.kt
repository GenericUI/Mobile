package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

/**
 * iOS `UIContextMenuInteraction` implementation of [ContextMenuNativeImpl].
 */
class IosContextMenuImpl : IosBaseContainerNativeImpl("UIContextMenu", "dropdown-menu"), ContextMenuNativeImpl<IosNativeView> {
    override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {
        setVisible(true)
    }

    override fun hide() {
        setVisible(false)
    }
}
