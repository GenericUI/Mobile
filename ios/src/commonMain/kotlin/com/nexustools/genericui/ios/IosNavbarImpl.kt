package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * iOS `UINavigationBar` implementation of [NavbarNativeImpl].
 */
class IosNavbarImpl : IosBaseContainerNativeImpl("UINavigationBar", "navbar"), NavbarNativeImpl<IosNativeView> {
    private var brandTitleText: String = ""
    private var brandIconSpec: String? = null
    private var showBrandOverride: Boolean? = null
    private val childItems = mutableListOf<NativeImpl<*>>()

    val items: List<NativeImpl<*>>
        get() = childItems

    init {
        nativeWidget.metadata["titlePlacement"] = "left"
        nativeWidget.metadata["brandPlacement"] = "left"
    }

    private fun syncBrandMetadata() {
        val visible = isBrandVisible()
        nativeWidget.text = if (visible) brandTitleText else ""
        nativeWidget.metadata["brandTitle"] = if (visible) brandTitleText else ""
        if (visible && !brandIconSpec.isNullOrBlank()) {
            nativeWidget.metadata["brandIcon"] = brandIconSpec!!
        } else {
            nativeWidget.metadata.remove("brandIcon")
        }
        nativeWidget.metadata["showBrand"] = visible.toString()
        nativeWidget.metadata["titlePlacement"] = "left"
        nativeWidget.metadata["brandPlacement"] = "left"
    }

    override fun setBrandTitle(title: String) {
        brandTitleText = title
        syncBrandMetadata()
    }

    override fun getBrandTitle(): String = brandTitleText

    override fun setBrandIcon(icon: String?) {
        brandIconSpec = icon
        syncBrandMetadata()
    }

    override fun getBrandIcon(): String? = brandIconSpec

    override fun setShowBrand(show: Boolean?) {
        showBrandOverride = show
        syncBrandMetadata()
    }

    override fun getShowBrand(): Boolean? = showBrandOverride

    override fun isBrandVisible(): Boolean = showBrandOverride ?: true

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
