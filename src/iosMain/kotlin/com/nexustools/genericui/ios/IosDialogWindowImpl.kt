@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIModalPresentationFullScreen
import platform.UIKit.UIModalPresentationOverCurrentContext
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UIModalPresentationFormSheet
import platform.UIKit.UISheetPresentationControllerDetent
import platform.UIKit.UIView
import platform.UIKit.UIViewController
import platform.UIKit.modalInPresentation
import platform.UIKit.presentationController
import platform.UIKit.sheetPresentationController
import platform.UIKit.systemBackgroundColor

/**
 * Native UIKit modal `UIViewController` sheet/dialog implementation of [DialogWindowNativeImpl].
 */
class IosDialogWindowImpl : IosBaseContainerNativeImpl(
    nativeWidget = run {
        val root = UikitFlexContainerView(CGRectMake(0.0, 0.0, 360.0, 440.0)).apply {
            flexDirection = FlexDirection.COLUMN
            backgroundColor = UIColor.systemBackgroundColor
        }
        val vc = UikitViewController(customRootView = root)
        UikitViewHandle(root, vc)
    },
    componentClassPrefix = "dialog"
), DialogWindowNativeImpl<UikitViewHandle> {

    val viewController: UIViewController get() = nativeWidget.uiViewController!!
    private var dialogTitle: String = ""
    private var presentationStyleValue: DialogPresentationStyle = DialogPresentationStyle.WINDOW_MODAL
    private var isModalValue: Boolean = true
    private var isFullscreenValue: Boolean = false
    private var dismissDelegate: UikitPopoverDelegate? = null
    private var onClosedListener: (() -> Unit)? = null

    override fun setTitle(title: String) {
        dialogTitle = title
        viewController.title = title
    }

    override fun getTitle(): String = dialogTitle

    override fun setPresentationStyle(style: DialogPresentationStyle) {
        presentationStyleValue = style
        applyPresentationConfiguration()
    }

    override fun getPresentationStyle(): DialogPresentationStyle = presentationStyleValue

    override fun setModal(modal: Boolean) {
        isModalValue = modal
        viewController.modalInPresentation = modal
    }

    override fun setFullscreen(fullscreen: Boolean) {
        isFullscreenValue = fullscreen
        applyPresentationConfiguration()
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        uiView.setFrame(CGRectMake(x.toDouble(), y.toDouble(), width.toDouble(), height.toDouble()))
        viewController.setPreferredContentSize(CGSizeMake(width.toDouble(), height.toDouble()))
    }

    override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {
        applyPresentationConfiguration()
        val del = UikitPopoverDelegate(forcePopoverOnCompact = false) {
            onClosedListener?.invoke()
        }
        dismissDelegate = del
        viewController.presentationController?.delegate = del

        val ownerVc = (ownerWindow?.nativeWidget as? UikitViewHandle)?.uiViewController
        if (ownerVc != null && ownerVc.view.window != null) {
            ownerVc.presentViewController(viewController, animated = true, completion = null)
        } else {
            UikitEnvironment.presentIfWindowAttached(viewController, animated = true)
        }
    }

    override fun closeDialog() {
        if (viewController.presentingViewController != null) {
            viewController.dismissViewControllerAnimated(true) {
                onClosedListener?.invoke()
            }
        } else {
            onClosedListener?.invoke()
        }
    }

    override fun setOnDialogClosed(listener: (() -> Unit)?) {
        onClosedListener = listener
    }

    private fun applyPresentationConfiguration() {
        viewController.modalInPresentation = isModalValue
        if (isFullscreenValue) {
            viewController.modalPresentationStyle = UIModalPresentationFullScreen
            return
        }
        viewController.modalPresentationStyle = when (presentationStyleValue) {
            DialogPresentationStyle.SHEET_PULL_DOWN,
            DialogPresentationStyle.BOTTOM_SHEET -> UIModalPresentationPageSheet
            DialogPresentationStyle.APPLICATION_MODAL -> UIModalPresentationFormSheet
            DialogPresentationStyle.MODELESS -> UIModalPresentationOverCurrentContext
            DialogPresentationStyle.WINDOW_MODAL -> UIModalPresentationFormSheet
        }
        val sheet = viewController.sheetPresentationController
        if (sheet != null) {
            sheet.detents = when (presentationStyleValue) {
                DialogPresentationStyle.BOTTOM_SHEET -> listOf(
                    UISheetPresentationControllerDetent.mediumDetent(),
                    UISheetPresentationControllerDetent.largeDetent()
                )
                else -> listOf(
                    UISheetPresentationControllerDetent.largeDetent()
                )
            }
            sheet.prefersGrabberVisible = presentationStyleValue == DialogPresentationStyle.BOTTOM_SHEET ||
                presentationStyleValue == DialogPresentationStyle.SHEET_PULL_DOWN
        }
    }
}
