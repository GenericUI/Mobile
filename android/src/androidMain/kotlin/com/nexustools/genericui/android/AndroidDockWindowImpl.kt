package com.nexustools.genericui.android

import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Android `BottomSheetDialogFragment` / floating `PopupWindow` implementation of [DockWindowNativeImpl].
 */
class AndroidDockWindowImpl : AndroidBaseContainerNativeImpl("android.widget.FrameLayout", "dock-window"), DockWindowNativeImpl<AndroidViewHandle> {
    override val isToolModalWindow: Boolean = true
    override val hasTaskbarEntry: Boolean = false
    override val isMinimizable: Boolean = false
    override val isAlwaysOnTopOfOwner: Boolean = true

    var ownerWindow: WindowNativeImpl<*>? = null
        private set

    private var closeListener: (() -> Unit)? = null

    init {
        setAttribute("tool-modal", "true")
        setAttribute("show-in-taskbar", "false")
        setAttribute("minimizable", "false")
        setAttribute("always-on-top-of-owner", "true")
    }

    override fun setTitle(title: String) {
        nativeWidget.text = title
    }

    override fun setWindowBounds(bounds: Rect) {
        nativeWidget.bounds = bounds
    }

    override fun showDockWindow() {
        setVisible(true)
        bringToFront()
    }

    override fun hideDockWindow() {
        setVisible(false)
    }

    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {
        closeListener = listener
    }

    override fun attachToOwnerWindow(ownerWindow: WindowNativeImpl<*>?) {
        this.ownerWindow = ownerWindow
        setAttribute("ownerWindow", ownerWindow?.toString() ?: "")
        bringToFront()
    }

    override fun bringToFront() {
        setAttribute("broughtToFront", "true")
    }
}
