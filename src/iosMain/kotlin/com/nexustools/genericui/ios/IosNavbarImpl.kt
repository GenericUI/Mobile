@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIImageView
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationItem
import platform.UIKit.UIView

/**
 * Native UIKit `UINavigationBar` + `UINavigationItem` implementation of [NavbarNativeImpl].
 */
class IosNavbarImpl(
    val navigationBar: UINavigationBar = UINavigationBar(frame = CGRectMake(0.0, 0.0, 390.0, 44.0))
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(navigationBar),
    componentClassPrefix = "navbar"
), NavbarNativeImpl<UikitViewHandle> {

    val navigationItem: UINavigationItem = UINavigationItem(title = "")
    private var brandTitle: String = ""
    private var brandIcon: String? = null
    private var showBrandFlag: Boolean? = null

    init {
        navigationBar.setItems(listOf(navigationItem), animated = false)
    }

    override fun contentContainerView(): UIView = navigationBar

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        syncRightBarItems()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        syncRightBarItems()
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        syncRightBarItems()
    }

    override fun setBrandTitle(title: String) {
        brandTitle = title
        refreshBrandDisplay()
    }

    override fun getBrandTitle(): String = brandTitle

    override fun setBrandIcon(icon: String?) {
        brandIcon = icon
        refreshBrandDisplay()
    }

    override fun getBrandIcon(): String? = brandIcon

    override fun setShowBrand(show: Boolean?) {
        showBrandFlag = show
        refreshBrandDisplay()
    }

    override fun getShowBrand(): Boolean? = showBrandFlag

    override fun isBrandVisible(): Boolean = showBrandFlag ?: true

    private fun refreshBrandDisplay() {
        val visible = isBrandVisible()
        navigationItem.title = if (visible) brandTitle else null
        val icon = if (visible) UikitGeometryAndColorUtil.systemIconImage(brandIcon) else null
        navigationItem.leftBarButtonItem = if (icon != null) {
            UIBarButtonItem(customView = UIImageView(image = icon))
        } else {
            null
        }
    }

    private fun syncRightBarItems() {
        val barItems = childImpls.mapNotNull { impl ->
            val v = (impl.nativeWidget as? UikitViewHandle)?.uiView ?: return@mapNotNull null
            UIBarButtonItem(customView = v)
        }
        navigationItem.rightBarButtonItems = barItems
    }
}
