package com.nexustools.genericui.android

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.appcompat.widget.SwitchCompat
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl

/**
 * Android `AppCompatCheckBox` / `SwitchCompat` / `AppCompatRadioButton` implementation of [CheckboxNativeImpl].
 */
class AndroidCheckboxImpl(
    val switchStyle: Boolean = false,
    val radioStyle: Boolean = false
) : AndroidBaseNativeImpl(
    when {
        switchStyle -> "com.google.android.material.materialswitch.MaterialSwitch"
        radioStyle -> "com.google.android.material.radiobutton.MaterialRadioButton"
        else -> "com.google.android.material.checkbox.MaterialCheckBox"
    },
    if (switchStyle) "form-check form-switch" else "form-check"
), CheckboxNativeImpl<AndroidViewHandle> {
    private var checkedState: Boolean = false
    private var onCheckedChangedListener: ((Boolean) -> Unit)? = null
    private var suppressCheckedListener: Boolean = false

    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View {
        val button: CompoundButton = when {
            switchStyle -> SwitchCompat(context)
            radioStyle -> AppCompatRadioButton(context)
            else -> AppCompatCheckBox(context)
        }
        button.setOnCheckedChangeListener { _, isChecked ->
            if (suppressCheckedListener) return@setOnCheckedChangeListener
            if (checkedState != isChecked) {
                checkedState = isChecked
                onCheckedChangedListener?.invoke(isChecked)
            }
        }
        return button
    }

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val cb = nativeWidget.androidView as? CompoundButton
                if (cb != null && cb.isChecked != checked) {
                    suppressCheckedListener = true
                    try {
                        cb.isChecked = checked
                    } finally {
                        suppressCheckedListener = false
                    }
                }
            }
        }
        onCheckedChangedListener?.invoke(checked)
    }

    override fun isChecked(): Boolean = checkedState

    override fun setLabel(label: String) {
        nativeWidget.text = label
        syncAndroidView()
    }

    override fun getLabel(): String = nativeWidget.text

    override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {
        onCheckedChangedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val cb = nativeWidget.androidView as? CompoundButton ?: return@runCatching
                if (cb.text?.toString() != nativeWidget.text) {
                    cb.text = nativeWidget.text
                }
                if (cb.isChecked != checkedState) {
                    suppressCheckedListener = true
                    try {
                        cb.isChecked = checkedState
                    } finally {
                        suppressCheckedListener = false
                    }
                }
                val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: 14f
                cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
                val isDark = AndroidViewHandle.isDarkTheme()
                val textArgb = customStyleTextColor?.toArgbInt()
                    ?: if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                cb.setTextColor(textArgb)
            }
        }
    }
}
