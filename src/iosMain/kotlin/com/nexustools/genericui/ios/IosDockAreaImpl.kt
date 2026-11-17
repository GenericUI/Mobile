@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.secondarySystemBackgroundColor

/**
 * Native UIKit dock zone implementing [DockAreaNativeImpl], backing edge drawer collapse/slide-over
 * visibility for `DockMobileDrawerPolicy` and horizontal/vertical panel docking.
 */
class IosDockAreaImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 260.0, 260.0)).apply {
            flexDirection = FlexDirection.COLUMN
            backgroundColor = UIColor.secondarySystemBackgroundColor
        }
    ),
    componentClassPrefix = "dock-area"
), DockAreaNativeImpl<UikitViewHandle> {

    private var dockPosition: DockPosition = DockPosition.LEFT
    private var collapsedState: Boolean = false

    override fun setPosition(position: DockPosition) {
        dockPosition = position
        val flex = uiView as UikitFlexContainerView
        flex.flexDirection = when (position) {
            DockPosition.TOP, DockPosition.BOTTOM -> FlexDirection.ROW
            else -> FlexDirection.COLUMN
        }
        flex.setNeedsLayout()
    }

    fun getDockPosition(): DockPosition = dockPosition

    override fun setCollapsed(collapsed: Boolean) {
        collapsedState = collapsed
        uiView.hidden = collapsed
        uiView.superview?.setNeedsLayout()
    }

    override fun isCollapsed(): Boolean = collapsedState
}
