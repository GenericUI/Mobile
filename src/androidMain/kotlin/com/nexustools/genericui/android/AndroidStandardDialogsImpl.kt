package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatEditText
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import kotlin.math.roundToInt

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
        val activity = resolveActiveActivity()
        if (activity == null) {
            onResult(initialColor)
            return
        }
        runCatching {
            var completed = false
            var curR = initialColor.r.coerceIn(0, 255)
            var curG = initialColor.g.coerceIn(0, 255)
            var curB = initialColor.b.coerceIn(0, 255)
            var curA = initialColor.a.coerceIn(0, 255)

            val pad = AndroidViewHandle.dpToPx(16)
            val root = LinearLayout(activity).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(pad, pad, pad, pad / 2)
            }

            val previewSwatch = View(activity).apply {
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    AndroidViewHandle.dpToPx(48)
                ).apply {
                    bottomMargin = AndroidViewHandle.dpToPx(8)
                }
            }
            val hexLabel = TextView(activity).apply {
                gravity = Gravity.CENTER
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setPadding(0, 0, 0, AndroidViewHandle.dpToPx(8))
            }

            fun updateSwatchPreview() {
                val argb = Color.argb(curA, curR, curG, curB)
                previewSwatch.background = GradientDrawable().apply {
                    setColor(argb)
                    cornerRadius = AndroidViewHandle.dpToPxFloat(8f)
                    setStroke(AndroidViewHandle.dpToPx(1), Color.GRAY)
                }
                hexLabel.text = String.format("#%02X%02X%02X%02X", curR, curG, curB, curA)
            }

            fun addChannelSlider(label: String, initialValue: Int, onUpdate: (Int) -> Unit) {
                val row = LinearLayout(activity).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, AndroidViewHandle.dpToPx(4), 0, AndroidViewHandle.dpToPx(4))
                }
                val titleView = TextView(activity).apply {
                    text = label
                    layoutParams = LinearLayout.LayoutParams(AndroidViewHandle.dpToPx(48), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val valueView = TextView(activity).apply {
                    text = initialValue.toString()
                    gravity = Gravity.END
                    typeface = Typeface.MONOSPACE
                    layoutParams = LinearLayout.LayoutParams(AndroidViewHandle.dpToPx(40), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val seek = SeekBar(activity).apply {
                    max = 255
                    progress = initialValue
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                            onUpdate(progress)
                            valueView.text = progress.toString()
                            updateSwatchPreview()
                        }

                        override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                        override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                    })
                }
                row.addView(titleView)
                row.addView(seek)
                row.addView(valueView)
                root.addView(row)
            }

            root.addView(previewSwatch)
            root.addView(hexLabel)
            addChannelSlider("Red", curR) { curR = it }
            addChannelSlider("Green", curG) { curG = it }
            addChannelSlider("Blue", curB) { curB = it }
            addChannelSlider("Alpha", curA) { curA = it }
            updateSwatchPreview()

            AlertDialog.Builder(activity)
                .setTitle(title.ifBlank { "Choose Color" })
                .setView(root)
                .setPositiveButton("OK") { _, _ ->
                    if (!completed) {
                        completed = true
                        onResult(RgbaColor(curR, curG, curB, curA))
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
            onResult(initialColor)
        }
    }

    override fun dispose() {}

    private fun resolveActiveActivity(): Activity? {
        AndroidActivityRegistry.resolveActiveActivity()?.let { return it }
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
