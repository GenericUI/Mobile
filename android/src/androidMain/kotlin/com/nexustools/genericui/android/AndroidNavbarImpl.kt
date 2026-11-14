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
    private var brandTitleText: String = ""
    private var brandIconSpec: String? = null
    private var showBrandOverride: Boolean? = null

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

    private fun syncBrandAttributes() {
        val visible = isBrandVisible()
        val iconVal = brandIconSpec
        nativeWidget.text = if (visible) brandTitleText else ""
        nativeWidget.attributes["brandTitle"] = if (visible) brandTitleText else ""
        if (visible && !iconVal.isNullOrBlank()) {
            nativeWidget.attributes["brandIcon"] = iconVal
        } else {
            nativeWidget.attributes.remove("brandIcon")
        }
        nativeWidget.attributes["showBrand"] = visible.toString()
        nativeWidget.attributes["titlePlacement"] = "left"
        nativeWidget.attributes["brandPlacement"] = "left"
        onNavbarChanged?.invoke()
    }

    override fun setBrandTitle(title: String) {
        brandTitleText = title
        syncBrandAttributes()
    }

    override fun getBrandTitle(): String = brandTitleText

    override fun setBrandIcon(icon: String?) {
        brandIconSpec = icon
        syncBrandAttributes()
    }

    override fun getBrandIcon(): String? = brandIconSpec

    override fun setShowBrand(show: Boolean?) {
        showBrandOverride = show
        syncBrandAttributes()
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
