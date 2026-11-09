package com.nexustools.genericui.android

import android.view.ViewGroup
import android.widget.PopupWindow
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * Android `android.widget.PopupWindow` implementation of [PopoverNativeImpl].
 */
class AndroidPopoverImpl : AndroidBaseContainerNativeImpl("android.widget.PopupWindow", "popover"), PopoverNativeImpl<AndroidViewHandle> {
    private var placement: Placement = Placement.BOTTOM
    private var activePopupWindow: PopupWindow? = null

    override fun setTitle(title: String?) {
        nativeWidget.text = title ?: ""
    }

    override fun setPlacement(placement: Placement) {
        this.placement = placement
        nativeWidget.attributes["placement"] = placement.name.lowercase()
    }

    override fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int) {
        nativeWidget.bounds = nativeWidget.bounds.copy(x = x, y = y)
        setVisible(true)
        runCatching {
            val anchorView = (anchor?.nativeWidget as? AndroidViewHandle)?.androidView ?: return@runCatching
            val contentView = ensureAndroidView(anchorView.context) ?: return@runCatching
            (contentView.parent as? ViewGroup)?.removeView(contentView)
            val popup = PopupWindow(
                contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
            ).apply {
                isOutsideTouchable = true
                elevation = AndroidViewHandle.dpToPxFloat(8f)
            }
            activePopupWindow?.dismiss()
            activePopupWindow = popup
            popup.showAsDropDown(anchorView, AndroidViewHandle.dpToPx(x), AndroidViewHandle.dpToPx(y))
        }
    }

    override fun hide() {
        setVisible(false)
        runCatching {
            activePopupWindow?.dismiss()
            activePopupWindow = null
        }
    }
}
