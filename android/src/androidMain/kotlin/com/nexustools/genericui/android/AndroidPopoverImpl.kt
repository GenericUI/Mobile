package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * Android `android.widget.PopupWindow` implementation of [PopoverNativeImpl].
 */
class AndroidPopoverImpl : AndroidBaseContainerNativeImpl("android.widget.PopupWindow", "popover"), PopoverNativeImpl<AndroidViewHandle> {
    private var placement: Placement = Placement.BOTTOM

    override fun setTitle(title: String?) {
        nativeWidget.text = title ?: ""
    }

    override fun setPlacement(placement: Placement) {
        this.placement = placement
        nativeWidget.metadata["placement"] = placement.name.lowercase()
    }

    override fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int) {
        nativeWidget.bounds = nativeWidget.bounds.copy(x = x, y = y)
        setVisible(true)
    }

    override fun hide() {
        setVisible(false)
    }
}
