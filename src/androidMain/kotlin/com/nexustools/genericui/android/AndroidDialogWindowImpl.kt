package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatTextView
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dialog.DialogPresentationStyle
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Android `androidx.appcompat.app.AppCompatDialog` implementation of [DialogWindowNativeImpl].
 */
class AndroidDialogWindowImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.app.AppCompatDialog", "modal"), DialogWindowNativeImpl<AndroidViewHandle> {
    private var titleText: String = ""
    private var presentationStyle: DialogPresentationStyle = DialogPresentationStyle.WINDOW_MODAL
    private var modalFlag: Boolean = true
    private var fullscreenFlag: Boolean = false
    private var onClosedListener: (() -> Unit)? = null

    private var activeDialog: AppCompatDialog? = null
    private var dialogTitleView: TextView? = null
    private var suppressDismissCallback: Boolean = false

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
        AndroidViewHandle.runOnMainThread {
            runCatching {
                dialogTitleView?.text = title
                activeDialog?.setTitle(title)
            }
        }
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
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val activity = resolveHostActivity() ?: return@runCatching
                if (activity.isFinishing || activity.isDestroyed) return@runCatching

                activeDialog?.let { existing ->
                    suppressDismissCallback = true
                    try {
                        if (existing.isShowing) existing.dismiss()
                    } finally {
                        suppressDismissCallback = false
                    }
                }

                val contentView = ensureAndroidView(activity) ?: return@runCatching
                (contentView.parent as? ViewGroup)?.removeView(contentView)

                val isDark = AndroidViewHandle.isDarkTheme()
                val surfaceBgArgb = if (isDark) 0xFF1E222A.toInt() else 0xFFFFFFFF.toInt()
                val borderArgb = if (isDark) 0xFF2E3440.toInt() else 0xFFE0E4EC.toInt()
                val titleArgb = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                val closeFgArgb = if (isDark) 0xFFA5B0C2.toInt() else 0xFF5A6270.toInt()

                val pad = AndroidViewHandle.dpToPx(16)
                val rootLayout = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(pad, pad, pad, pad)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = AndroidViewHandle.dpToPxFloat(12f)
                        setColor(surfaceBgArgb)
                        setStroke(AndroidViewHandle.dpToPx(1).coerceAtLeast(1), borderArgb)
                    }
                }

                val headerRow = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val bottomPad = AndroidViewHandle.dpToPx(10)
                    setPadding(0, 0, 0, bottomPad)
                }
                val titleTv = AppCompatTextView(activity).apply {
                    text = titleText
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    setTypeface(null, Typeface.BOLD)
                    setTextColor(titleArgb)
                }
                dialogTitleView = titleTv
                val closeBtn = AppCompatButton(activity).apply {
                    text = "\u2715"
                    isAllCaps = false
                    gravity = Gravity.CENTER
                    minWidth = 0
                    minimumWidth = 0
                    minHeight = 0
                    minimumHeight = 0
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                    setTextColor(closeFgArgb)
                    background = ColorDrawable(Color.TRANSPARENT)
                    val closePad = AndroidViewHandle.dpToPx(6)
                    setPadding(closePad, closePad, closePad, closePad)
                    setOnClickListener {
                        closeDialog()
                    }
                }
                headerRow.addView(
                    titleTv,
                    LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                )
                headerRow.addView(
                    closeBtn,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                rootLayout.addView(
                    headerRow,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                val scrollView = ScrollView(activity).apply {
                    isFillViewport = true
                }
                scrollView.addView(
                    contentView,
                    ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
                rootLayout.addView(
                    scrollView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        1f
                    )
                )

                val dialog = AppCompatDialog(activity)
                dialog.supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setContentView(rootLayout)
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)
                dialog.setOnDismissListener {
                    if (suppressDismissCallback) return@setOnDismissListener
                    activeDialog = null
                    dialogTitleView = null
                    AndroidViewHandle.dispatchOnLogicThread {
                        if (isVisible()) {
                            setVisible(false)
                            onClosedListener?.invoke()
                        }
                    }
                }
                activeDialog = dialog
                dialog.show()

                dialog.window?.let { win ->
                    win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    val dm = activity.resources.displayMetrics
                    val dialogWidth = if (fullscreenFlag) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        (dm.widthPixels * 0.92f).toInt()
                    }
                    val dialogHeight = if (fullscreenFlag) {
                        ViewGroup.LayoutParams.MATCH_PARENT
                    } else {
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    }
                    win.setLayout(dialogWidth, dialogHeight)
                    win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                }
            }
        }
    }

    override fun closeDialog() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val dialog = activeDialog
                activeDialog = null
                dialogTitleView = null
                if (dialog != null) {
                    suppressDismissCallback = true
                    try {
                        if (dialog.isShowing) {
                            dialog.dismiss()
                        }
                    } finally {
                        suppressDismissCallback = false
                    }
                }
            }
        }
        AndroidViewHandle.dispatchOnLogicThread {
            setVisible(false)
            onClosedListener?.invoke()
        }
    }

    override fun setOnDialogClosed(listener: (() -> Unit)?) {
        onClosedListener = listener
    }

    private fun resolveHostActivity(): Activity? {
        AndroidActivityRegistry.resolveActiveActivity()?.let { return it }
        AndroidFileBrowserDialogImpl.activeHostActivity?.let { return it }
        var ctx: Context? = AndroidViewHandle.activeContext
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }
}
