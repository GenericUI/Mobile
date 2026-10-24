package com.nexustools.genericui.android

import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl

/**
 * Android `BottomSheetDialogFragment` / floating `PopupWindow` implementation of [DockWindowNativeImpl].
 */
class AndroidDockWindowImpl : AndroidBaseContainerNativeImpl("android.widget.FrameLayout", "dock-window"), DockWindowNativeImpl<AndroidViewHandle> {
    private var closeListener: (() -> Unit)? = null

    override fun setTitle(title: String) {
        nativeWidget.text = title
    }

    override fun setWindowBounds(bounds: Rect) {
        nativeWidget.bounds = bounds
    }

    override fun showDockWindow() {
        setVisible(true)
    }

    override fun hideDockWindow() {
        setVisible(false)
    }

    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {
        closeListener = listener
    }
}
