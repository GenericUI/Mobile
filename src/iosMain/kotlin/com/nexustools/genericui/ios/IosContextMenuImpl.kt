@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAlertAction
import platform.UIKit.UIAlertActionStyleCancel
import platform.UIKit.UIAlertActionStyleDefault
import platform.UIKit.UIAlertController
import platform.UIKit.UIAlertControllerStyleActionSheet
import platform.UIKit.UIContextMenuInteraction
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElement
import platform.UIKit.UIView
import platform.UIKit.addInteraction
import platform.UIKit.popoverPresentationController
import platform.UIKit.removeInteraction

/**
 * Native UIKit `UIContextMenuInteraction` + `UIMenu` implementation of [ContextMenuNativeImpl],
 * also attaching `UIAlertControllerStyleActionSheet` fallback presentation for explicit programmatic [showAt].
 */
class IosContextMenuImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))),
    componentClassPrefix = "context-menu"
), ContextMenuNativeImpl<UikitViewHandle> {

    private var activeSheet: UIAlertController? = null
    private val contextDelegate = UikitContextMenuDelegate { buildContextUiMenu() }
    private var attachedInteraction: UIContextMenuInteraction? = null
    private var attachedAnchorView: UIView? = null

    fun attachToView(targetView: UIView) {
        attachedInteraction?.let { attachedAnchorView?.removeInteraction(it) }
        val interaction = UIContextMenuInteraction(delegate = contextDelegate)
        targetView.addInteraction(interaction)
        attachedInteraction = interaction
        attachedAnchorView = targetView
    }

    fun buildContextUiMenu(): UIMenu {
        val elements = mutableListOf<UIMenuElement>()
        for ((idx, child) in childImpls.withIndex()) {
            when (child) {
                is IosMenuItemImpl -> elements += child.toUiMenuElement("context.$idx")
                is IosMenuImpl -> elements += child.buildUiMenu("context.sub.$idx")
            }
        }
        return UIMenu.menuWithTitle(title = "", children = elements)
    }

    override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {
        val anchorView = (anchor?.nativeWidget as? UikitViewHandle)?.uiView
        if (anchorView != null) {
            attachToView(anchorView)
        }
        val presenter = UikitEnvironment.topMostViewController() ?: return
        val sheet = UIAlertController.alertControllerWithTitle(
            title = null,
            message = null,
            preferredStyle = UIAlertControllerStyleActionSheet
        )
        for (child in childImpls) {
            if (child is IosMenuItemImpl) {
                sheet.addAction(
                    UIAlertAction.actionWithTitle(
                        title = child.getLabel().ifEmpty { "Action" },
                        style = UIAlertActionStyleDefault
                    ) { _ ->
                        activeSheet = null
                        child.triggerClick()
                    }
                )
            }
        }
        sheet.addAction(
            UIAlertAction.actionWithTitle("Cancel", style = UIAlertActionStyleCancel) { _ ->
                activeSheet = null
            }
        )
        sheet.popoverPresentationController?.let { pop ->
            val src = anchorView ?: presenter.view
            pop.sourceView = src
            pop.sourceRect = CGRectMake(x.toDouble(), y.toDouble(), 1.0, 1.0)
        }
        activeSheet = sheet
        UikitEnvironment.presentIfWindowAttached(sheet, animated = true)
    }

    override fun hide() {
        val current = activeSheet ?: return
        activeSheet = null
        if (current.presentingViewController != null) {
            current.dismissViewControllerAnimated(true, completion = null)
        }
    }
}
