package com.nexustools.genericui.ios

import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl

/**
 * iOS implementation of [DockWindowNativeImpl] rendered as a floating sheet/popover utility panel.
 */
class IosDockWindowImpl : IosBaseContainerNativeImpl("UIView", "dock-window"), DockWindowNativeImpl<IosNativeView> {
    override fun setTitle(title: String) { nativeWidget.text = title }
    override fun setWindowBounds(bounds: Rect) { nativeWidget.frame = bounds }
    override fun showDockWindow() { setVisible(true) }
    override fun hideDockWindow() { setVisible(false) }
    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {}
}
