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
    initialView: View? = null,
    initialTagName: String = ""
) : AndroidBaseNativeImpl(viewClassName, componentClassPrefix, initialView, initialTagName), AbstractContainerNativeImpl<AndroidViewHandle> {

    protected val childNativeImpls = mutableListOf<NativeImpl<*>>()

    protected fun snapshotChildNativeImpls(): List<NativeImpl<*>> =
        synchronized(childNativeImpls) { childNativeImpls.toList() }

    override fun createDefaultAndroidView(context: Context): View? =
        AndroidFlexContainerView(context)

    override fun ensureAndroidView(context: Context?): View? {
        val view = super.ensureAndroidView(context)
        if (context != null && !nativeWidget.disposed) {
            val snapshot = snapshotChildNativeImpls()
            for (childImpl in snapshot) {
                (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
            }
            AndroidViewHandle.runOnMainThread {
                runCatching {
                    if (nativeWidget.disposed) return@runCatching
                    val activeVg = (nativeWidget.childHostViewGroup ?: (nativeWidget.androidView as? ViewGroup))
                        ?: return@runCatching
                    val liveChildren = snapshotChildNativeImpls()
                    val liveSet = liveChildren.toHashSet()
                    for (snapChild in snapshot) {
                        if (snapChild !in liveSet || (snapChild.nativeWidget as? AndroidViewHandle)?.disposed == true) {
                            val staleView = (snapChild as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
                            if (staleView != null && staleView.parent === activeVg) {
                                activeVg.removeView(staleView)
                            }
                        }
                    }
                    for ((liveIndex, childImpl) in liveChildren.withIndex()) {
                        val childView = (childImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
                        val childHandle = childImpl.nativeWidget as? AndroidViewHandle
                        if (childView != null &&
                            childHandle != null &&
                            childImpl in liveChildren &&
                            childHandle.parentHandle === nativeWidget &&
                            !childHandle.disposed
                        ) {
                            val needsReattach = childView.parent === activeVg &&
                                activeVg.isAttachedToWindow &&
                                !childView.isAttachedToWindow
                            val lp = if (activeVg is AndroidFlexContainerView) {
                                AndroidFlexContainerView.buildLayoutParamsFor(childHandle)
                            } else {
                                ViewGroup.MarginLayoutParams(
                                    ViewGroup.LayoutParams.WRAP_CONTENT,
                                    ViewGroup.LayoutParams.WRAP_CONTENT
                                )
                            }
                            if (childView.parent !== activeVg || needsReattach) {
                                (childView.parent as? ViewGroup)?.removeView(childView)
                                val insertIdx = liveIndex.coerceIn(0, activeVg.childCount)
                                activeVg.addView(childView, insertIdx, lp)
                            } else {
                                childView.layoutParams = lp
                            }
                        }
                    }
                    activeVg.requestLayout()
                    activeVg.invalidate()
                }
            }
        }
        syncAndroidView()
        return view
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
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
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        if (nativeWidget.disposed || childHandle.disposed) return
        synchronized(childNativeImpls) {
            childNativeImpls.remove(childImpl)
            if (index < 0 || index >= childNativeImpls.size) {
                childNativeImpls.add(childImpl)
            } else {
                childNativeImpls.add(index, childImpl)
            }
        }
        val ctx = AndroidViewHandle.activeContext
        if (ctx != null) {
            ensureOwnAndroidView(ctx)
            (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(ctx)
        }
        nativeWidget.addChild(childHandle, index)
        syncAndroidView()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        synchronized(childNativeImpls) {
            childNativeImpls.remove(childImpl)
        }
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: return
        nativeWidget.removeChild(childHandle)
        syncAndroidView()
    }

    override fun clearChildrenNative() {
        synchronized(childNativeImpls) {
            childNativeImpls.clear()
        }
        nativeWidget.clearChildren()
        syncAndroidView()
    }

    override fun enableHardwareLayerCache(enabled: Boolean) {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                nativeWidget.androidView?.setLayerType(
                    if (enabled) View.LAYER_TYPE_HARDWARE else View.LAYER_TYPE_NONE,
                    null
                )
            }
        }
    }
}
