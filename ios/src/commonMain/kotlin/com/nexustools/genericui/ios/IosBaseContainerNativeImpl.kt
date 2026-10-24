package com.nexustools.genericui.ios

import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

/**
 * Base container [AbstractContainerNativeImpl] for the `:ios` UIKit backend.
 */
open class IosBaseContainerNativeImpl(
    uiViewClassName: String = "UIStackView",
    componentClassPrefix: String = "container"
) : IosBaseNativeImpl(uiViewClassName, componentClassPrefix), AbstractContainerNativeImpl<IosNativeView> {

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val childView = childImpl.nativeWidget as? IosNativeView ?: return
        nativeWidget.insertSubview(childView, index)
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        val childView = childImpl.nativeWidget as? IosNativeView ?: return
        if (childView.superview === nativeWidget) {
            childView.removeFromSuperview()
        }
    }

    override fun clearChildrenNative() {
        nativeWidget.clearSubviews()
    }

    override fun enableHardwareLayerCache(enabled: Boolean) {
        nativeWidget.metadata["CALayer.shouldRasterize"] = enabled.toString()
    }
}
