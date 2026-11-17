@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.ios.UikitEnvironment
import platform.UIKit.UIMainMenuSystem
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuBuilderProtocol
import platform.UIKit.UIMenuEdit
import platform.UIKit.UIMenuFile
import platform.UIKit.UIMenuHelp
import platform.UIKit.UIMenuSystem
import platform.UIKit.UIMenuView
import platform.UIKit.UIMenuWindow

/**
 * Bridge connecting [IpadosMenuBarImpl] hierarchies onto iPadOS's native system menu bar
 * (`UIMenuSystem.mainSystem` / `UIMainMenuSystem.sharedSystem` + `buildMenuWithBuilder`),
 * translating top-level menus into genuine `UIMenu` entries while supporting live rebuilds.
 */
object IpadosMenuBarBridge {

    var activeMenuBar: IpadosMenuBarImpl? = null
        private set

    var lastBuiltMenus: List<UIMenu> = emptyList()
        private set

    fun registerAndRebuild(menuBar: IpadosMenuBarImpl?) {
        activeMenuBar = menuBar
        lastBuiltMenus = menuBar?.let { buildUiMenus(it) } ?: emptyList()
        if (UikitEnvironment.safeSharedApplication() != null) {
            runCatching {
                UIMenuSystem.mainSystem.setNeedsRebuild()
                UIMenuSystem.mainSystem.setNeedsRevalidate()
            }
        }
    }

    fun buildUiMenus(menuBar: IpadosMenuBarImpl): List<UIMenu> =
        menuBar.topLevelMenus().mapIndexed { idx, menu ->
            val cleanTitle = menu.getLabel().trim().lowercase()
            val mappedIdentifier = when (cleanTitle) {
                "file" -> UIMenuFile
                "edit" -> UIMenuEdit
                "view" -> UIMenuView
                "window" -> UIMenuWindow
                "help" -> UIMenuHelp
                else -> "com.nexustools.genericui.ipados.menu.$idx.$cleanTitle"
            } ?: "com.nexustools.genericui.ipados.menu.$idx.$cleanTitle"
            menu.buildUiMenu(mappedIdentifier)
        }

    fun populateMenuBuilder(builder: UIMenuBuilderProtocol) {
        val bar = activeMenuBar ?: return
        val menus = buildUiMenus(bar)
        lastBuiltMenus = menus
        if (builder.system != UIMenuSystem.mainSystem) return

        var anchorIdentifier = UIMenuFile
        for (uiMenu in menus) {
            val id = uiMenu.identifier
            when (id) {
                UIMenuFile, UIMenuEdit, UIMenuView, UIMenuWindow, UIMenuHelp -> {
                    builder.replaceChildrenOfMenuForIdentifier(id) { _ ->
                        uiMenu.children
                    }
                    anchorIdentifier = id
                }
                else -> {
                    builder.insertSiblingMenu(uiMenu, afterMenuForIdentifier = anchorIdentifier)
                    anchorIdentifier = id
                }
            }
        }
    }

    fun configureMainMenuSystemIfAvailable() {
        runCatching {
            val shared = UIMainMenuSystem.sharedSystem
            shared.setNeedsRebuild()
        }
    }
}
