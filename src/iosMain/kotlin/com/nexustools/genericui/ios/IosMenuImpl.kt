@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElement
import platform.UIKit.UIView

/**
 * Native UIKit `UIMenu` container wrapping child [IosMenuItemImpl] and nested [IosMenuImpl] items.
 */
class IosMenuImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))),
    componentClassPrefix = "menu"
), MenuNativeImpl<UikitViewHandle> {

    private var labelText: String = ""
    private var iconName: String? = null

    override fun setLabel(label: String) {
        labelText = label
    }

    override fun getLabel(): String = labelText

    override fun setIcon(icon: String?) {
        iconName = icon
    }

    override fun getIcon(): String? = iconName

    fun buildUiMenu(identifierPrefix: String = "menu"): UIMenu {
        val elements = mutableListOf<UIMenuElement>()
        for ((idx, child) in childImpls.withIndex()) {
            when (child) {
                is IosMenuItemImpl -> elements += child.toUiMenuElement("$identifierPrefix.$idx.${child.getLabel()}")
                is IosMenuImpl -> elements += child.buildUiMenu("$identifierPrefix.$idx")
            }
        }
        return UIMenu.menuWithTitle(
            title = labelText,
            image = UikitGeometryAndColorUtil.systemIconImage(iconName),
            identifier = identifierPrefix,
            options = 0uL,
            children = elements
        )
    }
}
