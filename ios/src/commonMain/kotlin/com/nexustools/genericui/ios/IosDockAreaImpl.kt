package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl

/**
 * iOS implementation of [DockAreaNativeImpl].
 */
class IosDockAreaImpl : IosBaseContainerNativeImpl("UIView", "dock-area"), DockAreaNativeImpl<IosNativeView> {
    private var position: DockPosition = DockPosition.RIGHT
    private var collapsed: Boolean = false

    override fun setPosition(position: DockPosition) { this.position = position }
    override fun setCollapsed(collapsed: Boolean) { this.collapsed = collapsed }
    override fun isCollapsed(): Boolean = collapsed
}
