@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.ios.GenericUiRootViewController
import com.nexustools.genericui.ios.IosContextMenuImpl
import com.nexustools.genericui.ios.UikitControlActionTarget
import com.nexustools.genericui.ios.UikitPointerInteractionDelegate
import com.nexustools.genericui.style.css.CssCursorValue
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CValue
import kotlinx.cinterop.alloc
import kotlinx.cinterop.nativeHeap
import kotlinx.cinterop.ptr
import platform.CoreGraphics.CGRect
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UIPointerInteraction
import platform.UIKit.UIPopoverArrowDirectionAny
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.addInteraction
import platform.UIKit.interactions
import platform.UIKit.popoverPresentationController
import platform.objc.OBJC_ASSOCIATION_RETAIN_NONATOMIC
import platform.objc.objc_setAssociatedObject

/**
 * Installs iPadOS pointer, hover, right-click/trackpad secondary context menu, popover
 * anchoring, and hardware keyboard shortcut integrations onto UIKit views and controllers.
 */
object IpadosPointerAndKeyboardInstaller {

    private val HOVER_TARGET_KEY = nativeHeap.alloc<ByteVar>().ptr
    private val POINTER_DELEGATE_KEY = nativeHeap.alloc<ByteVar>().ptr

    fun install(rootController: GenericUiRootViewController) {
        attachHoverRecognizer(rootController.hostView)
    }

    private fun attachHoverRecognizer(view: UIView) {
        val target = UikitControlActionTarget(view)
        objc_setAssociatedObject(view, HOVER_TARGET_KEY, target, OBJC_ASSOCIATION_RETAIN_NONATOMIC)
        val hover = UIHoverGestureRecognizer(
            target = target,
            action = UikitControlActionTarget.SEL_HOVER
        )
        view.addGestureRecognizer(hover)
    }

    fun installCursorInteraction(view: UIView, cursor: CssCursorValue? = null): UIPointerInteraction? {
        val delegate = UikitPointerInteractionDelegate(view, cursor)
        val interaction = UIPointerInteraction(delegate = delegate).takeIf { (it as Any?) != null }
        if (interaction != null) {
            objc_setAssociatedObject(view, POINTER_DELEGATE_KEY, delegate, OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            view.addInteraction(interaction)
            if (interaction.view == view || view.interactions.isNotEmpty()) {
                return interaction
            }
        }
        attachHoverRecognizer(view)
        return null
    }

    fun installContextMenu(view: UIView, contextMenu: IosContextMenuImpl) {
        contextMenu.attachToView(view)
    }

    fun registerShortcut(
        controller: GenericUiRootViewController,
        shortcut: KeyShortcut,
        title: String,
        action: () -> Unit
    ) {
        controller.registerKeyShortcut(shortcut, title, action)
    }

    fun anchorPopover(
        popoverController: UIViewController,
        sourceView: UIView,
        sourceRect: CValue<CGRect>? = null
    ) {
        popoverController.popoverPresentationController?.let { pop ->
            pop.sourceView = sourceView
            pop.sourceRect = sourceRect ?: sourceView.bounds
            pop.permittedArrowDirections = UIPopoverArrowDirectionAny
        }
    }
}
