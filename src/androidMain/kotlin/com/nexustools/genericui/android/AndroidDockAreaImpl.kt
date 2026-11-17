package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl

/**
 * Android View container implementation of [DockAreaNativeImpl], hosting docked [AndroidDockGroupImpl]
 * stacks oriented horizontally (`TOP`/`BOTTOM`) or vertically (`LEFT`/`RIGHT`/`CENTER`/`FLOATING`).
 */
class AndroidDockAreaImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-area"), DockAreaNativeImpl<AndroidViewHandle> {
    var position: DockPosition = DockPosition.RIGHT
        private set
    private var collapsed: Boolean = false
    internal var onAreaStateChanged: (() -> Unit)? = null

    private var areaHostView: AndroidFlexContainerView? = null

    override fun createDefaultAndroidView(context: Context): View? {
        val host = AndroidFlexContainerView(context).apply {
            applyDockOrientation(this)
        }
        areaHostView = host
        nativeWidget.childHostViewGroup = host
        return host
    }

    private fun applyDockOrientation(container: AndroidFlexContainerView) {
        val isHorizontalBand = position == DockPosition.TOP || position == DockPosition.BOTTOM
        container.flexDirection = if (isHorizontalBand) FlexDirection.ROW else FlexDirection.COLUMN
        container.alignItems = AlignItems.STRETCH
        val isDark = AndroidViewHandle.isDarkTheme()
        container.setBackgroundColor(if (isDark) 0xFF181B22.toInt() else 0xFFF2F4F8.toInt())
    }

    override fun setPosition(position: DockPosition) {
        this.position = position
        nativeWidget.attributes["dockPosition"] = position.name.lowercase()
        syncAndroidView()
        onAreaStateChanged?.invoke()
    }

    override fun setCollapsed(collapsed: Boolean) {
        this.collapsed = collapsed
        nativeWidget.visible = !collapsed
        nativeWidget.attributes["collapsed"] = collapsed.toString()
        syncAndroidView()
        onAreaStateChanged?.invoke()
    }

    override fun isCollapsed(): Boolean = collapsed

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                areaHostView?.let { host ->
                    applyDockOrientation(host)
                    host.visibility = if (collapsed) View.GONE else View.VISIBLE
                }
            }
        }
    }
}
