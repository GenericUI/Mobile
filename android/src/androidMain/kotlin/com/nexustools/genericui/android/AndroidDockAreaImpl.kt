package com.nexustools.genericui.android

import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl

/**
 * Android implementation of [DockAreaNativeImpl].
 */
class AndroidDockAreaImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-area"), DockAreaNativeImpl<AndroidViewHandle> {
    private var position: DockPosition = DockPosition.RIGHT
    private var collapsed: Boolean = false

    override fun setPosition(position: DockPosition) {
        this.position = position
        nativeWidget.metadata["dockPosition"] = position.name.lowercase()
    }

    override fun setCollapsed(collapsed: Boolean) {
        this.collapsed = collapsed
        nativeWidget.visibility = !collapsed
    }

    override fun isCollapsed(): Boolean = collapsed
}
