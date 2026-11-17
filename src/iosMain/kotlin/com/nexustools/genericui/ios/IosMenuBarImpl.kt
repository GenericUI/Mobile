@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIMenu
import platform.UIKit.UIView

/**
 * Native UIKit [MenuBarNativeImpl] aggregating top-level [IosMenuImpl]s into UIKit `UIMenu`s.
 *
 * Subclassed by [com.nexustools.genericui.ipados.IpadosMenuBarImpl] for `UIMainMenuSystem` /
 * `UIMenuSystem.mainSystem` desktop-style menu bar integration on iPadOS.
 */
open class IosMenuBarImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))),
    componentClassPrefix = "menubar"
), MenuBarNativeImpl<UikitViewHandle> {

    fun topLevelMenus(): List<IosMenuImpl> =
        childImpls.filterIsInstance<IosMenuImpl>()

    fun buildRootUiMenu(title: String = "Menu"): UIMenu {
        val children = topLevelMenus().mapIndexed { idx, menu ->
            menu.buildUiMenu("menubar.$idx.${menu.getLabel()}")
        }
        return UIMenu.menuWithTitle(title = title, children = children)
    }
}
