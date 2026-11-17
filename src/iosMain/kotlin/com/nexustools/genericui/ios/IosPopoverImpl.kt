@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.ui.Placement
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPopover
import platform.UIKit.UIPopoverArrowDirectionAny
import platform.UIKit.UIPopoverArrowDirectionDown
import platform.UIKit.UIPopoverArrowDirectionLeft
import platform.UIKit.UIPopoverArrowDirectionRight
import platform.UIKit.UIPopoverArrowDirectionUp
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UIKit.secondarySystemBackgroundColor

/**
 * Native UIKit `UIPopoverPresentationController`-backed popover implementation of [PopoverNativeImpl].
 */
class IosPopoverImpl : IosBaseContainerNativeImpl(
    nativeWidget = run {
        val root = UikitFlexContainerView(CGRectMake(0.0, 0.0, 260.0, 180.0)).apply {
            flexDirection = FlexDirection.COLUMN
            backgroundColor = UIColor.secondarySystemBackgroundColor
        }
        val vc = UikitViewController(customRootView = root).apply {
            modalPresentationStyle = UIModalPresentationPopover
            setPreferredContentSize(CGSizeMake(260.0, 180.0))
        }
        UikitViewHandle(root, vc)
    },
    componentClassPrefix = "popover"
), PopoverNativeImpl<UikitViewHandle> {

    val viewController: UIViewController get() = nativeWidget.uiViewController!!
    private var titleText: String? = null
    private var placementValue: Placement = Placement.BOTTOM
    private val popoverDelegate = UikitPopoverDelegate(forcePopoverOnCompact = true)

    override fun setTitle(title: String?) {
        titleText = title
        viewController.title = title
    }

    override fun setPlacement(placement: Placement) {
        placementValue = placement
    }

    override fun showAnchored(anchor: NativeImpl<*>?, x: Int, y: Int) {
        viewController.modalPresentationStyle = UIModalPresentationPopover
        val pop = viewController.popoverPresentationController
        if (pop != null) {
            pop.delegate = popoverDelegate
            val anchorView = (anchor?.nativeWidget as? UikitViewHandle)?.uiView
                ?: UikitEnvironment.topMostViewController()?.view
            if (anchorView != null) {
                pop.sourceView = anchorView
                pop.sourceRect = if (x > 0 || y > 0) {
                    CGRectMake(x.toDouble(), y.toDouble(), 1.0, 1.0)
                } else {
                    anchorView.bounds
                }
            }
            pop.permittedArrowDirections = when (placementValue) {
                Placement.TOP, Placement.TOP_START, Placement.TOP_END -> UIPopoverArrowDirectionDown
                Placement.BOTTOM, Placement.BOTTOM_START, Placement.BOTTOM_END -> UIPopoverArrowDirectionUp
                Placement.START -> UIPopoverArrowDirectionRight
                Placement.END -> UIPopoverArrowDirectionLeft
            }
        }
        UikitEnvironment.presentIfWindowAttached(viewController, animated = true)
    }

    override fun hide() {
        if (viewController.presentingViewController != null) {
            viewController.dismissViewControllerAnimated(true, completion = null)
        }
    }
}
