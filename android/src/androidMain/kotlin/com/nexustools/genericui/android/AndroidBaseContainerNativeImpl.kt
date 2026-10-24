package com.nexustools.genericui.android

import android.view.View
import com.nexustools.genericui.core.AbstractContainerNativeImpl
import com.nexustools.genericui.core.NativeImpl

/**
 * Base container [AbstractContainerNativeImpl] for the `:android` backend.
 */
open class AndroidBaseContainerNativeImpl(
    viewClassName: String = "android.widget.LinearLayout",
    componentClassPrefix: String = "container",
    initialView: View? = null
) : AndroidBaseNativeImpl(viewClassName, componentClassPrefix, initialView), AbstractContainerNativeImpl<AndroidViewHandle> {

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        nativeWidget.addChild(childHandle, index)
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        nativeWidget.removeChild(childHandle)
    }

    override fun clearChildrenNative() {
        nativeWidget.clearChildren()
    }

    override fun enableHardwareLayerCache(enabled: Boolean) {
        nativeWidget.androidView?.setLayerType(
            if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
            null
        )
    }
}
