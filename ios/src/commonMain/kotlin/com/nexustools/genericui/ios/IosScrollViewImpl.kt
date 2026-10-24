package com.nexustools.genericui.ios

import com.nexustools.genericui.mobile.gesture.MobileFlingPhysics
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * iOS `UIScrollView` implementation of [ScrollViewNativeImpl] with [MobileFlingPhysics].
 */
class IosScrollViewImpl : IosBaseContainerNativeImpl("UIScrollView", "overflow-auto"), ScrollViewNativeImpl<IosNativeView> {
    val flingPhysics: MobileFlingPhysics = MobileFlingPhysics()

    override fun scrollTo(x: Int, y: Int) {
        nativeWidget.contentOffsetX = x.toFloat()
        nativeWidget.contentOffsetY = y.toFloat()
    }

    override fun scrollToBottom() {
        nativeWidget.contentOffsetY = nativeWidget.frame.height.toFloat()
    }
}
