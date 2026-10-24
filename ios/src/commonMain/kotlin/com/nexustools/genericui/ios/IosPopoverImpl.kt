package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl

/**
 * iOS `UIPopoverPresentationController` implementation of [PopoverNativeImpl].
 */
class IosPopoverImpl : IosBaseContainerNativeImpl("UIPopoverPresentationController", "popover"), PopoverNativeImpl<IosNativeView> {
    private var placement: Placement = Placement.BOTTOM

    override fun setTitle(title: String?) {
        nativeWidget.text = title ?: ""
    }

    override fun setPlacement(placement: Placement) {
        this.placement = placement
    }

    override fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int) {
        setVisible(true)
    }

    override fun hide() {
        setVisible(false)
    }
}
