package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * iOS `UIViewController` modal / `UISheetPresentationController` implementation of [DialogWindowNativeImpl].
 */
class IosDialogWindowImpl : IosBaseContainerNativeImpl("UIViewController", "modal"), DialogWindowNativeImpl<IosNativeView> {
    private var titleText: String = ""
    private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.WINDOW_MODAL
    private var onClosedListener: (() -> Unit)? = null

    init {
        nativeWidget.metadata["antialiasing"] = "true"
        nativeWidget.metadata["layoutWidth"] = "fit-content"
        nativeWidget.metadata["layoutHeight"] = "fit-content"
    }

    override fun setPreferredSize(size: Size?) {
        super<IosBaseContainerNativeImpl>.setPreferredSize(size)
        if (size == null) {
            nativeWidget.metadata["layoutWidth"] = "fit-content"
            nativeWidget.metadata["layoutHeight"] = "fit-content"
        } else {
            nativeWidget.metadata["layoutWidth"] = "${size.width}px"
            nativeWidget.metadata["layoutHeight"] = "${size.height}px"
        }
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        val view = childImpl.nativeWidget as? IosNativeView ?: return
        val userClasses = view.metadata["userClasses"]
            ?.split(Regex("\\s+"))
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        if (childImpl.hasClass("modal-footer") || userClasses.contains("modal-footer")) {
            view.metadata["dialogSlot"] = "footer"
            nativeWidget.metadata["hasFooterButtonBar"] = "true"
        } else {
            view.metadata["dialogSlot"] = "body"
        }
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        val hasFooter = nativeWidget.subviews.any { it.metadata["dialogSlot"] == "footer" }
        if (!hasFooter) {
            nativeWidget.metadata.remove("hasFooterButtonBar")
        }
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        nativeWidget.metadata.remove("hasFooterButtonBar")
    }

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
