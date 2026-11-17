package com.nexustools.genericui.android

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.drawerlayout.widget.DrawerLayout
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl

/**
 * Android adaptive [DrawerLayout] / split-pane desktop-or-drawer workspace implementation of
 * [DockWorkspaceNativeImpl], hosting a central document canvas surrounded by `TOP`, `BOTTOM`,
 * `LEFT`, and `RIGHT` [DockAreaNativeImpl] zones (rendered inline in wide/desktop mode or as
 * sliding [DrawerLayout] drawers in compact mobile mode).
 */
class AndroidDockWorkspaceImpl : AndroidBaseContainerNativeImpl("androidx.drawerlayout.widget.DrawerLayout", "dock-workspace"), DockWorkspaceNativeImpl<AndroidViewHandle> {
    private var centralImpl: NativeImpl<*>? = null
    private val dockAreas = linkedMapOf<DockPosition, DockAreaNativeImpl<*>>()
    private var windowModeName: String = "MOBILE_DRAWERS"

    private var drawerLayoutView: DrawerLayout? = null
    private var topAreaSlot: FrameLayout? = null
    private var bottomAreaSlot: FrameLayout? = null
    private var inlineLeftSlot: FrameLayout? = null
    private var inlineRightSlot: FrameLayout? = null
    private var centralSlot: FrameLayout? = null
    private var leftDrawerSlot: FrameLayout? = null
    private var rightDrawerSlot: FrameLayout? = null

    override fun createDefaultAndroidView(context: Context): View? {
        val drawerRoot = DrawerLayout(context).also { drawerLayoutView = it }

        val mainColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val topSlot = FrameLayout(context).apply { visibility = View.GONE }.also { topAreaSlot = it }
        val middleRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val inLeft = FrameLayout(context).apply { visibility = View.GONE }.also { inlineLeftSlot = it }
        val center = FrameLayout(context).also {
            centralSlot = it
            nativeWidget.childHostViewGroup = it
        }
        val inRight = FrameLayout(context).apply { visibility = View.GONE }.also { inlineRightSlot = it }
        val bottomSlot = FrameLayout(context).apply { visibility = View.GONE }.also { bottomAreaSlot = it }

        val sidePaneWidth = AndroidViewHandle.dpToPx(240)
        middleRow.addView(inLeft, LinearLayout.LayoutParams(sidePaneWidth, ViewGroup.LayoutParams.MATCH_PARENT))
        middleRow.addView(center, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
        middleRow.addView(inRight, LinearLayout.LayoutParams(sidePaneWidth, ViewGroup.LayoutParams.MATCH_PARENT))

        mainColumn.addView(topSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        mainColumn.addView(middleRow, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mainColumn.addView(bottomSlot, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        drawerRoot.addView(
            mainColumn,
            DrawerLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        val drawerW = AndroidViewHandle.dpToPx(280)
        val isDark = AndroidViewHandle.isDarkTheme()
        val drawerBg = if (isDark) 0xFF181B22.toInt() else 0xFFF7F8FB.toInt()

        val leftDrawer = FrameLayout(context).apply {
            setBackgroundColor(drawerBg)
            isClickable = true
        }.also { leftDrawerSlot = it }
        val rightDrawer = FrameLayout(context).apply {
            setBackgroundColor(drawerBg)
            isClickable = true
        }.also { rightDrawerSlot = it }

        drawerRoot.addView(
            leftDrawer,
            DrawerLayout.LayoutParams(drawerW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START)
        )
        drawerRoot.addView(
            rightDrawer,
            DrawerLayout.LayoutParams(drawerW, ViewGroup.LayoutParams.MATCH_PARENT, Gravity.END)
        )

        drawerRoot.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerOpened(drawerView: View) {
                if (drawerView === leftDrawerSlot) {
                    (dockAreas[DockPosition.LEFT] as? AndroidDockAreaImpl)?.nativeWidget?.attributes?.set("drawerOpen", "true")
                } else if (drawerView === rightDrawerSlot) {
                    (dockAreas[DockPosition.RIGHT] as? AndroidDockAreaImpl)?.nativeWidget?.attributes?.set("drawerOpen", "true")
                }
            }

            override fun onDrawerClosed(drawerView: View) {
                if (drawerView === leftDrawerSlot) {
                    (dockAreas[DockPosition.LEFT] as? AndroidDockAreaImpl)?.nativeWidget?.attributes?.set("drawerOpen", "false")
                } else if (drawerView === rightDrawerSlot) {
                    (dockAreas[DockPosition.RIGHT] as? AndroidDockAreaImpl)?.nativeWidget?.attributes?.set("drawerOpen", "false")
                }
            }
        })

        return drawerRoot
    }

    override fun setCentralDocumentNative(centralImpl: NativeImpl<*>?) {
        this.centralImpl?.let { removeChildNative(it) }
        this.centralImpl = centralImpl
        if (centralImpl != null) {
            addChildNative(centralImpl)
        }
        syncWorkspaceViews()
    }

    override fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>) {
        (dockAreas[position] as? AndroidDockAreaImpl)?.onAreaStateChanged = null
        dockAreas[position]?.let { removeChildNative(it) }
        dockAreas[position] = areaImpl
        if (areaImpl is AndroidDockAreaImpl) {
            areaImpl.onAreaStateChanged = { syncWorkspaceViews() }
        }
        addChildNative(areaImpl)
        syncWorkspaceViews()
    }

    override fun setWindowModeNative(modeName: String) {
        windowModeName = modeName
        nativeWidget.attributes["windowMode"] = modeName
        syncWorkspaceViews()
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        syncWorkspaceViews()
    }

    private fun useSlidingDrawers(): Boolean {
        val upper = windowModeName.uppercase()
        if (upper.contains("INLINE") || upper.contains("DESKTOP") || upper.contains("SPLIT")) {
            return false
        }
        val widthDp = nativeWidget.bounds.width
        return widthDp <= 0 || widthDp < 840
    }

    private fun mountSlotChild(slot: FrameLayout?, impl: NativeImpl<*>?, ctx: Context, visible: Boolean) {
        if (slot == null) return
        slot.removeAllViews()
        if (impl == null || !visible) {
            slot.visibility = View.GONE
            return
        }
        val childView = (impl as? AndroidBaseNativeImpl)?.ensureAndroidView(ctx)
        if (childView == null) {
            slot.visibility = View.GONE
            return
        }
        (childView.parent as? ViewGroup)?.removeView(childView)
        slot.addView(
            childView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        slot.visibility = View.VISIBLE
    }

    private fun syncWorkspaceViews() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val drawerRoot = drawerLayoutView ?: return@runCatching
                val ctx = drawerRoot.context ?: return@runCatching
                val compactDrawers = useSlidingDrawers()

                mountSlotChild(centralSlot, centralImpl, ctx, visible = true)

                val topArea = dockAreas[DockPosition.TOP]
                mountSlotChild(topAreaSlot, topArea, ctx, visible = topArea != null && !topArea.isCollapsed())

                val bottomArea = dockAreas[DockPosition.BOTTOM]
                mountSlotChild(bottomAreaSlot, bottomArea, ctx, visible = bottomArea != null && !bottomArea.isCollapsed())

                val leftArea = dockAreas[DockPosition.LEFT]
                val rightArea = dockAreas[DockPosition.RIGHT]

                if (compactDrawers) {
                    mountSlotChild(inlineLeftSlot, null, ctx, visible = false)
                    mountSlotChild(inlineRightSlot, null, ctx, visible = false)
                    mountSlotChild(leftDrawerSlot, leftArea, ctx, visible = leftArea != null)
                    mountSlotChild(rightDrawerSlot, rightArea, ctx, visible = rightArea != null)
                    leftDrawerSlot?.let { slot ->
                        if (leftArea == null || leftArea.isCollapsed()) {
                            drawerRoot.closeDrawer(slot)
                        }
                    }
                    rightDrawerSlot?.let { slot ->
                        if (rightArea == null || rightArea.isCollapsed()) {
                            drawerRoot.closeDrawer(slot)
                        }
                    }
                } else {
                    mountSlotChild(leftDrawerSlot, null, ctx, visible = false)
                    mountSlotChild(rightDrawerSlot, null, ctx, visible = false)
                    mountSlotChild(inlineLeftSlot, leftArea, ctx, visible = leftArea != null && !leftArea.isCollapsed())
                    mountSlotChild(inlineRightSlot, rightArea, ctx, visible = rightArea != null && !rightArea.isCollapsed())
                }
            }
        }
    }
}
