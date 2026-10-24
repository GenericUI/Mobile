package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl

/**
 * Android `androidx.core.widget.NestedScrollView` implementation of [ScrollViewNativeImpl].
 */
class AndroidScrollViewImpl : AndroidBaseContainerNativeImpl("androidx.core.widget.NestedScrollView", "overflow-auto"), ScrollViewNativeImpl<AndroidViewHandle> {
    override fun scrollTo(x: Int, y: Int) {
        nativeWidget.scrollX = x
        nativeWidget.scrollY = y
    }

    override fun scrollToBottom() {
        nativeWidget.scrollY = nativeWidget.bounds.height
    }
}
