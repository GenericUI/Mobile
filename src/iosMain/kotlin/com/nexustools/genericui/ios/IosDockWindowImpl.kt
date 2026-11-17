@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIViewController
import platform.UIKit.presentationController
import platform.UIKit.systemBackgroundColor

/**
 * Native UIKit modal sheet dock host implementing [DockWindowNativeImpl].
 */
class IosDockWindowImpl : IosBaseContainerNativeImpl(
    nativeWidget = run {
        val view = UikitFlexContainerView(CGRectMake(0.0, 0.0, 320.0, 400.0)).apply {
            flexDirection = FlexDirection.COLUMN
            backgroundColor = UIColor.systemBackgroundColor
        }
        val vc = UikitViewController(customRootView = view).apply {
            modalPresentationStyle = UIModalPresentationPageSheet
        }
        UikitViewHandle(view, vc)
    },
    componentClassPrefix = "dock-window"
), DockWindowNativeImpl<UikitViewHandle> {

    val viewController: UIViewController get() = nativeWidget.uiViewController!!
    private var ownerWindow: WindowNativeImpl<*>? = null
    private var closeListener: (() -> Unit)? = null
    private val popoverDelegate = UikitPopoverDelegate(forcePopoverOnCompact = false) {
        closeListener?.invoke()
    }

    override val isToolModalWindow: Boolean = false
    override val hasTaskbarEntry: Boolean = false
    override val isMinimizable: Boolean = false
    override val isAlwaysOnTopOfOwner: Boolean = true

    override fun setTitle(title: String) {
        viewController.title = title
    }

    override fun setWindowBounds(bounds: Rect) {
        uiView.setFrame(
            CGRectMake(
                bounds.x.toDouble(),
                bounds.y.toDouble(),
                bounds.width.toDouble(),
                bounds.height.toDouble()
            )
        )
        viewController.setPreferredContentSize(CGSizeMake(bounds.width.toDouble(), bounds.height.toDouble()))
    }

    override fun showDockWindow() {
        viewController.presentationController?.delegate = popoverDelegate
        val ownerVc = (ownerWindow?.nativeWidget as? UikitViewHandle)?.uiViewController
        if (ownerVc != null && ownerVc.view.window != null) {
            ownerVc.presentViewController(viewController, animated = true, completion = null)
        } else {
            UikitEnvironment.presentIfWindowAttached(viewController, animated = true)
        }
    }

    override fun hideDockWindow() {
        if (viewController.presentingViewController != null) {
            viewController.dismissViewControllerAnimated(true) {
                closeListener?.invoke()
            }
        }
    }

    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {
        closeListener = listener
    }

    override fun attachToOwnerWindow(ownerWindow: WindowNativeImpl<*>?) {
        this.ownerWindow = ownerWindow
    }

    override fun bringToFront() {
        if (viewController.presentingViewController == null) {
            showDockWindow()
        }
    }
}
