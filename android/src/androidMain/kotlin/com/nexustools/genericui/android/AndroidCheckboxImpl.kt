package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl

/**
 * Android `MaterialCheckBox` / `MaterialSwitch` / `MaterialRadioButton` implementation of [CheckboxNativeImpl].
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

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        onCheckedChangedListener?.invoke(checked)
    }

    override fun isChecked(): Boolean = checkedState

    override fun setLabel(label: String) {
        nativeWidget.text = label
    }

    override fun getLabel(): String = nativeWidget.text

    override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {
        onCheckedChangedListener = listener
    }
}
