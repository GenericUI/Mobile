package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Android `androidx.appcompat.app.AppCompatDialogFragment` / `MaterialAlertDialogBuilder` implementation of [DialogWindowNativeImpl].
 */
class AndroidDialogWindowImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.app.AppCompatDialog", "modal"), DialogWindowNativeImpl<AndroidViewHandle> {
    private var titleText: String = ""
    private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.WINDOW_MODAL
    private var modalFlag: Boolean = true
    private var fullscreenFlag: Boolean = false
    private var onClosedListener: (() -> Unit)? = null

    init {
        nativeWidget.visible = false
        nativeWidget.attributes["antialiasing"] = "true"
        nativeWidget.attributes["layoutWidth"] = "WRAP_CONTENT"
        nativeWidget.attributes["layoutHeight"] = "WRAP_CONTENT"
    }

    override fun setPreferredSize(size: Size?) {
        super<AndroidBaseContainerNativeImpl>.setPreferredSize(size)
        if (size == null) {
            nativeWidget.attributes["layoutWidth"] = "WRAP_CONTENT"
            nativeWidget.attributes["layoutHeight"] = "WRAP_CONTENT"
        } else {
            nativeWidget.attributes["layoutWidth"] = "${size.width}px"
            nativeWidget.attributes["layoutHeight"] = "${size.height}px"
        }
    }

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        val view = childImpl.nativeWidget as? AndroidViewHandle ?: return
        if (view.classes.contains("modal-footer") || childImpl.hasClass("modal-footer")) {
            view.attributes["dialogSlot"] = "footer"
            nativeWidget.attributes["hasFooterButtonBar"] = "true"
        } else {
            view.attributes["dialogSlot"] = "body"
        }
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        val hasFooter = nativeWidget.children.any {
            it.attributes["dialogSlot"] == "footer" || it.classes.contains("modal-footer")
        }
        if (!hasFooter) {
            nativeWidget.attributes.remove("hasFooterButtonBar")
        }
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        nativeWidget.attributes.remove("hasFooterButtonBar")
    }

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
    }

    override fun getTitle(): String = titleText

    override fun setPresentationStyle(style: DialogPresentationStyle) {
        presentationStyle = style
        nativeWidget.attributes["presentationStyle"] = style.name.lowercase()
    }

    override fun getPresentationStyle(): DialogPresentationStyle = presentationStyle

    override fun setModal(modal: Boolean) {
        modalFlag = modal
    }

    override fun setFullscreen(fullscreen: Boolean) {
        fullscreenFlag = fullscreen
        nativeWidget.attributes["fullscreen"] = fullscreen.toString()
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
