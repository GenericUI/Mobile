package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android `androidx.appcompat.app.AlertDialog` implementation of [StandardDialogsNativeImpl].
 */
class AndroidStandardDialogsImpl : StandardDialogsNativeImpl {
    override fun showAlert(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        type: BootstrapType,
        okButtonText: String,
        onDismissed: () -> Unit
    ) {
        val activity = resolveActiveActivity()
        if (activity == null) {
            onDismissed()
            return
        }
        runCatching {
            var completed = false
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(okButtonText.ifBlank { "OK" }) { _, _ ->
                    if (!completed) {
                        completed = true
                        onDismissed()
                    }
                }
                .setOnCancelListener {
                    if (!completed) {
                        completed = true
                        onDismissed()
                    }
                }
                .show()
        }.onFailure {
            onDismissed()
        }
    }

    override fun showConfirm(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        confirmText: String,
        cancelText: String,
        type: BootstrapType,
        onResult: (Boolean) -> Unit
    ) {
        val activity = resolveActiveActivity()
        if (activity == null) {
            onResult(true)
            return
        }
        runCatching {
            var completed = false
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(confirmText.ifBlank { "OK" }) { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(true)
                    }
                }
                .setNegativeButton(cancelText.ifBlank { "Cancel" }) { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(false)
                    }
                }
                .setOnCancelListener {
                    if (!completed) {
                        completed = true
                        onResult(false)
                    }
                }
                .show()
        }.onFailure {
            onResult(true)
        }
    }

    override fun showPrompt(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        defaultValue: String,
        placeholder: String,
        confirmText: String,
        cancelText: String,
        onResult: (String?) -> Unit
    ) {
        val activity = resolveActiveActivity()
        if (activity == null) {
            onResult(defaultValue)
            return
        }
        runCatching {
            var completed = false
            val input = AppCompatEditText(activity).apply {
                setText(defaultValue)
                hint = placeholder
            }
            val container = FrameLayout(activity).apply {
                val padH = AndroidViewHandle.dpToPx(20)
                val padV = AndroidViewHandle.dpToPx(8)
                setPadding(padH, padV, padH, padV)
                addView(input)
            }
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setMessage(message.takeIf { it.isNotBlank() })
                .setView(container)
                .setPositiveButton(confirmText.ifBlank { "OK" }) { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(input.text?.toString().orEmpty())
                    }
                }
                .setNegativeButton(cancelText.ifBlank { "Cancel" }) { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(null)
                    }
                }
                .setOnCancelListener {
                    if (!completed) {
                        completed = true
                        onResult(null)
                    }
                }
                .show()
        }.onFailure {
            onResult(defaultValue)
        }
    }

    override fun showChoice(
        owner: WindowNativeImpl<*>?,
        title: String,
        message: String,
        options: List<String>,
        selectedIndex: Int,
        onResult: (Int?) -> Unit
    ) {
        val activity = resolveActiveActivity()
        if (activity == null) {
            onResult(selectedIndex.takeIf { it in options.indices })
            return
        }
        runCatching {
            var completed = false
            var currentChoice = selectedIndex.takeIf { it in options.indices } ?: 0
            AlertDialog.Builder(activity)
                .setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), currentChoice) { _, which ->
                    currentChoice = which
                }
                .setPositiveButton("OK") { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(currentChoice.takeIf { it in options.indices })
                    }
                }
                .setNegativeButton("Cancel") { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(null)
                    }
                }
                .setOnCancelListener {
                    if (!completed) {
                        completed = true
                        onResult(null)
                    }
                }
                .show()
        }.onFailure {
            onResult(selectedIndex.takeIf { it in options.indices })
        }
    }

    override fun showColorChooser(
        owner: WindowNativeImpl<*>?,
        title: String,
        initialColor: RgbaColor,
        onResult: (RgbaColor?) -> Unit
    ) {
        onResult(initialColor)
    }

    override fun dispose() {}

    private fun resolveActiveActivity(): Activity? {
        val host = AndroidFileBrowserDialogImpl.activeHostActivity
        if (host != null && !host.isFinishing && !host.isDestroyed) return host
        var ctx: Context? = AndroidViewHandle.activeContext
        while (ctx is ContextWrapper) {
            if (ctx is Activity) {
                return if (!ctx.isFinishing && !ctx.isDestroyed) ctx else null
            }
            ctx = ctx.baseContext
        }
        val act = ctx as? Activity
        return if (act != null && !act.isFinishing && !act.isDestroyed) act else null
    }
}
