@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dock.DockMobileDrawerPolicy
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

/**
 * Native UIKit dock workspace arranging peripheral [DockAreaNativeImpl] zones around the
 * central document view while honoring [DockMobileDrawerPolicy] for compact screen widths.
 */
class IosDockWorkspaceImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 390.0, 720.0)).apply {
            flexDirection = FlexDirection.COLUMN
        }
    ),
    componentClassPrefix = "dock-workspace"
), DockWorkspaceNativeImpl<UikitViewHandle> {

    private var centralDocImpl: NativeImpl<*>? = null
    private val dockAreas = LinkedHashMap<DockPosition, DockAreaNativeImpl<*>>()
    private var drawerPolicy: DockMobileDrawerPolicy = DockMobileDrawerPolicy.AUTO

    override fun setCentralDocumentNative(centralImpl: NativeImpl<*>?) {
        (centralDocImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        centralDocImpl = centralImpl
        (centralImpl?.nativeWidget as? UikitViewHandle)?.uiView?.let { docView ->
            uiView.addSubview(docView)
        }
        layoutDockWorkspace()
    }

    override fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>) {
        dockAreas[position] = areaImpl
        (areaImpl.nativeWidget as? UikitViewHandle)?.uiView?.let { areaView ->
            uiView.addSubview(areaView)
        }
        layoutDockWorkspace()
    }

    override fun setWindowModeNative(modeName: String) {
        drawerPolicy = runCatching { DockMobileDrawerPolicy.valueOf(modeName) }
            .getOrDefault(DockMobileDrawerPolicy.AUTO)
        layoutDockWorkspace()
    }

    private fun layoutDockWorkspace() {
        val totalW = uiView.bounds.useContents { size.width }.takeIf { it > 0.0 } ?: 390.0
        val totalH = uiView.bounds.useContents { size.height }.takeIf { it > 0.0 } ?: 720.0
        val useDrawer = when (drawerPolicy) {
            DockMobileDrawerPolicy.ALWAYS_ENABLED -> true
            DockMobileDrawerPolicy.DISABLED -> false
            DockMobileDrawerPolicy.AUTO -> totalW < 600.0
        }

        var leftX = 0.0
        var rightW = 0.0
        var topY = 0.0
        var bottomH = 0.0

        val topArea = dockAreas[DockPosition.TOP]?.let { if (!it.isCollapsed() && !useDrawer) it else null }
        val bottomArea = dockAreas[DockPosition.BOTTOM]?.let { if (!it.isCollapsed() && !useDrawer) it else null }
        val leftArea = dockAreas[DockPosition.LEFT]?.let { if (!it.isCollapsed() && !useDrawer) it else null }
        val rightArea = dockAreas[DockPosition.RIGHT]?.let { if (!it.isCollapsed() && !useDrawer) it else null }

        if (topArea != null) {
            topY = 140.0
            (topArea.nativeWidget as? UikitViewHandle)?.uiView?.setFrame(CGRectMake(0.0, 0.0, totalW, topY))
        }
        if (bottomArea != null) {
            bottomH = 160.0
            (bottomArea.nativeWidget as? UikitViewHandle)?.uiView?.setFrame(
                CGRectMake(0.0, (totalH - bottomH).coerceAtLeast(topY), totalW, bottomH)
            )
        }
        val midH = (totalH - topY - bottomH).coerceAtLeast(0.0)
        if (leftArea != null) {
            leftX = 240.0.coerceAtMost(totalW * 0.35)
            (leftArea.nativeWidget as? UikitViewHandle)?.uiView?.setFrame(CGRectMake(0.0, topY, leftX, midH))
        }
        if (rightArea != null) {
            rightW = 240.0.coerceAtMost(totalW * 0.35)
            (rightArea.nativeWidget as? UikitViewHandle)?.uiView?.setFrame(
                CGRectMake((totalW - rightW).coerceAtLeast(leftX), topY, rightW, midH)
            )
        }

        if (useDrawer) {
            for ((pos, area) in dockAreas) {
                val areaView = (area.nativeWidget as? UikitViewHandle)?.uiView ?: continue
                if (area.isCollapsed()) {
                    areaView.hidden = true
                } else {
                    areaView.hidden = false
                    val drawerW = (totalW * 0.75).coerceAtLeast(240.0)
                    val x = if (pos == DockPosition.RIGHT) totalW - drawerW else 0.0
                    areaView.setFrame(CGRectMake(x, 0.0, drawerW, totalH))
                    uiView.bringSubviewToFront(areaView)
                }
            }
        }

        val centerW = (totalW - leftX - rightW).coerceAtLeast(0.0)
        (centralDocImpl?.nativeWidget as? UikitViewHandle)?.uiView?.setFrame(CGRectMake(leftX, topY, centerW, midH))
    }
}
