package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * iOS `UINavigationBar` implementation of [NavbarNativeImpl].
 */
class IosNavbarImpl : IosBaseContainerNativeImpl("UINavigationBar", "navbar"), NavbarNativeImpl<IosNativeView> {
    private var brandTitleText: String = ""
    private val childItems = mutableListOf<NativeImpl<*>>()

    val items: List<NativeImpl<*>>
        get() = childItems

    init {
        nativeWidget.metadata["titlePlacement"] = "left"
        nativeWidget.metadata["brandPlacement"] = "left"
    }

    override fun setBrandTitle(title: String) {
        brandTitleText = title
        nativeWidget.text = title
        nativeWidget.metadata["brandTitle"] = title
        nativeWidget.metadata["titlePlacement"] = "left"
        nativeWidget.metadata["brandPlacement"] = "left"
    }

    override fun getBrandTitle(): String = brandTitleText

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        childItems.remove(childImpl)
        if (index < 0 || index >= childItems.size) {
            childItems.add(childImpl)
        } else {
            childItems.add(index, childImpl)
        }
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childItems.remove(childImpl)
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        childItems.clear()
    }
}
