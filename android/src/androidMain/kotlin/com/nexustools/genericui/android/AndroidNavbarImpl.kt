package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * Android `com.google.android.material.appbar.MaterialToolbar` implementation of [NavbarNativeImpl].
 * Synchronizes directly with [GenericUiFragmentActivity]'s top toolbar when bound.
 */
class AndroidNavbarImpl : AndroidBaseContainerNativeImpl("com.google.android.material.appbar.MaterialToolbar", "navbar"), NavbarNativeImpl<AndroidViewHandle> {
    private val childItems = mutableListOf<NativeImpl<*>>()

    internal var onNavbarChanged: (() -> Unit)? = null

    val items: List<NativeImpl<*>>
        get() = childItems

    init {
        nativeWidget.attributes["titlePlacement"] = "left"
        nativeWidget.attributes["brandPlacement"] = "left"
        setFlexDirection(FlexDirection.ROW)
        setAlignItems(AlignItems.CENTER)
        explicitGapDp = 8
        syncAndroidView()
    }

    override fun setBrandTitle(title: String) {
        nativeWidget.text = title
        nativeWidget.attributes["brandTitle"] = title
        nativeWidget.attributes["titlePlacement"] = "left"
        nativeWidget.attributes["brandPlacement"] = "left"
        onNavbarChanged?.invoke()
    }

    override fun getBrandTitle(): String = nativeWidget.text

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        childItems.remove(childImpl)
        if (index < 0 || index >= childItems.size) {
            childItems.add(childImpl)
        } else {
            childItems.add(index, childImpl)
        }
        onNavbarChanged?.invoke()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childItems.remove(childImpl)
        onNavbarChanged?.invoke()
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        childItems.clear()
        onNavbarChanged?.invoke()
    }
}
