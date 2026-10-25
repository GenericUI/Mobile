package com.nexustools.genericui.ios

import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * iOS implementation of [DockWindowNativeImpl] rendered as a floating sheet/popover utility panel.
 */
class IosDockWindowImpl : IosBaseContainerNativeImpl("UIView", "dock-window"), DockWindowNativeImpl<IosNativeView> {
    override val isToolModalWindow: Boolean = true
    override val hasTaskbarEntry: Boolean = false
    override val isMinimizable: Boolean = false
    override val isAlwaysOnTopOfOwner: Boolean = true

    var ownerWindow: WindowNativeImpl<*>? = null
        private set

    init {
        setAttribute("tool-modal", "true")
        setAttribute("show-in-taskbar", "false")
        setAttribute("minimizable", "false")
        setAttribute("always-on-top-of-owner", "true")
    }

    override fun setTitle(title: String) { nativeWidget.text = title }
    override fun setWindowBounds(bounds: Rect) { nativeWidget.frame = bounds }
    override fun showDockWindow() {
        setVisible(true)
        bringToFront()
    }
    override fun hideDockWindow() { setVisible(false) }
    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {}

    override fun attachToOwnerWindow(ownerWindow: WindowNativeImpl<*>?) {
        this.ownerWindow = ownerWindow
        setAttribute("ownerWindow", ownerWindow?.toString() ?: "")
        bringToFront()
    }

    override fun bringToFront() {
        setAttribute("broughtToFront", "true")
    }
}
