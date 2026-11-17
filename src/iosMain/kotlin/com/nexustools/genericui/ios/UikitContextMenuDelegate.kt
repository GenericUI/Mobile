@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGPoint
import platform.UIKit.UIContextMenuConfiguration
import platform.UIKit.UIContextMenuInteraction
import platform.UIKit.UIContextMenuInteractionDelegateProtocol
import platform.UIKit.UIMenu
import platform.darwin.NSObject

/**
 * `UIContextMenuInteractionDelegate` returning native `UIContextMenuConfiguration` powered
 * by an active `UIMenu` constructed from [IosContextMenuImpl] / [IosMenuImpl].
 */
class UikitContextMenuDelegate(
    private val menuProvider: () -> UIMenu?
) : NSObject(), UIContextMenuInteractionDelegateProtocol {

    override fun contextMenuInteraction(
        interaction: UIContextMenuInteraction,
        configurationForMenuAtLocation: CValue<CGPoint>
    ): UIContextMenuConfiguration? {
        val menu = menuProvider() ?: return null
        return UIContextMenuConfiguration.configurationWithIdentifier(
            identifier = null,
            previewProvider = null,
            actionProvider = { _ -> menu }
        )
    }
}
