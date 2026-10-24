package com.nexustools.genericui.android

import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
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
        nativeWidget.visibility = false
    }

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
    }

    override fun getTitle(): String = titleText

    override fun setPresentationStyle(style: DialogPresentationStyle) {
        presentationStyle = style
        nativeWidget.metadata["presentationStyle"] = style.name.lowercase()
    }

    override fun getPresentationStyle(): DialogPresentationStyle = presentationStyle

    override fun setModal(modal: Boolean) {
        modalFlag = modal
    }

    override fun setFullscreen(fullscreen: Boolean) {
        fullscreenFlag = fullscreen
        nativeWidget.metadata["fullscreen"] = fullscreen.toString()
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
