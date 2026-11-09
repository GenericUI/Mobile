package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
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

    protected val childNativeImpls = mutableListOf<NativeImpl<*>>()

    override fun createDefaultAndroidView(context: Context): View? =
        AndroidFlexContainerView(context)

    override fun ensureAndroidView(context: Context?): View? {
        val view = super.ensureAndroidView(context)
        val vg = (nativeWidget.childHostViewGroup ?: (view as? ViewGroup))
        if (vg != null && context != null) {
            runCatching {
                for (childImpl in childNativeImpls.toList()) {
                    val childView = (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
                    val childHandle = childImpl.nativeWidget as? AndroidViewHandle
                    if (childView != null && childHandle != null && childView.parent !== vg) {
                        (childView.parent as? ViewGroup)?.removeView(childView)
                        val lp = if (vg is AndroidFlexContainerView) {
                            AndroidFlexContainerView.buildLayoutParamsFor(childHandle)
                        } else {
                            ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        vg.addView(childView, lp)
                    }
                }
            }
        }
        syncAndroidView()
        return view
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val flexView = (nativeWidget.childHostViewGroup ?: nativeWidget.androidView) as? AndroidFlexContainerView
            if (flexView != null) {
                flexView.flexDirection = nativeWidget.flexDirection
                flexView.flexWrap = nativeWidget.flexWrap
                flexView.alignItems = nativeWidget.alignItems
                flexView.justifyContent = nativeWidget.justifyContent
                flexView.gapPx = nativeWidget.gapPx
            }
        }
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        childNativeImpls.remove(childImpl)
        if (index < 0 || index >= childNativeImpls.size) {
            childNativeImpls.add(childImpl)
        } else {
            childNativeImpls.add(index, childImpl)
        }
        val ctx = AndroidViewHandle.activeContext
        if (ctx != null) {
            ensureAndroidView(ctx)
            (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(ctx)
        }
        nativeWidget.addChild(childHandle, index)
        syncAndroidView()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        childNativeImpls.remove(childImpl)
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        nativeWidget.removeChild(childHandle)
        syncAndroidView()
    }

    override fun clearChildrenNative() {
        childNativeImpls.clear()
        nativeWidget.clearChildren()
        syncAndroidView()
    }

    override fun enableHardwareLayerCache(enabled: Boolean) {
        runCatching {
            nativeWidget.androidView?.setLayerType(
                if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
                null
            )
        }
    }
}
