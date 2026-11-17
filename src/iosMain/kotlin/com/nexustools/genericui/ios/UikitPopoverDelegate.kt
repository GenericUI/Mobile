package com.nexustools.genericui.ios

import platform.UIKit.UIModalPresentationNone
import platform.UIKit.UIModalPresentationStyle
import platform.UIKit.UIPopoverPresentationControllerDelegateProtocol
import platform.UIKit.UIPresentationController
import platform.UIKit.UITraitCollection
import platform.darwin.NSObject

/**
 * `UIPopoverPresentationControllerDelegate` forcing true popover presentation even in compact
 * horizontal size classes on iPhone and forwarding dismissal callbacks.
 */
class UikitPopoverDelegate(
    private val forcePopoverOnCompact: Boolean = true,
    private val onDismissed: (() -> Unit)? = null
) : NSObject(), UIPopoverPresentationControllerDelegateProtocol {

    override fun adaptivePresentationStyleForPresentationController(controller: UIPresentationController): UIModalPresentationStyle =
        if (forcePopoverOnCompact) UIModalPresentationNone else controller.presentationStyle

    override fun adaptivePresentationStyleForPresentationController(
        controller: UIPresentationController,
        traitCollection: UITraitCollection
    ): UIModalPresentationStyle =
        if (forcePopoverOnCompact) UIModalPresentationNone else controller.presentationStyle

    override fun presentationControllerDidDismiss(presentationController: UIPresentationController) {
        onDismissed?.invoke()
    }
}
