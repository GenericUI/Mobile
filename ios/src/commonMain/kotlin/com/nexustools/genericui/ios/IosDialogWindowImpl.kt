package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * iOS `UIViewController` modal / `UISheetPresentationController` implementation of [DialogWindowNativeImpl].
 */
class IosDialogWindowImpl : IosBaseContainerNativeImpl("UIViewController", "modal"), DialogWindowNativeImpl<IosNativeView> {
    private var titleText: String = ""
    private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.WINDOW_MODAL
    private var onClosedListener: (() -> Unit)? = null

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
    }

    override fun getTitle(): String = titleText

    override fun setPresentationStyle(style: DialogPresentationStyle) {
        presentationStyle = style
    }

    override fun getPresentationStyle(): DialogPresentationStyle = presentationStyle

    override fun setModal(modal: Boolean) {
        nativeWidget.metadata["isModalInPresentation"] = modal.toString()
    }

    override fun setFullscreen(fullscreen: Boolean) {
        nativeWidget.metadata["modalPresentationStyle"] = if (fullscreen) "fullScreen" else "pageSheet"
    }

    override fun showDialog(ownerWindow: WindowNativeImpl<*>?) {
        setVisible(true)
    }

    override fun closeDialog() {
        setVisible(false)
        onClosedListener?.invoke()
    }

    override fun setOnDialogClosed(listener: (() -> Unit)?) {
        onClosedListener = listener
    }
}
