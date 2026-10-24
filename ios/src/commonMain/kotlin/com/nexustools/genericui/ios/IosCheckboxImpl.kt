package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl

/**
 * iOS `UISwitch` / `UIButton` checkbox implementation of [CheckboxNativeImpl].
 */
class IosCheckboxImpl(
    val switchStyle: Boolean = false,
    val radioStyle: Boolean = false
) : IosBaseNativeImpl(if (switchStyle) "UISwitch" else "UIButton", "form-check"), CheckboxNativeImpl<IosNativeView> {
    private var checkedState: Boolean = false
    private var onCheckedChangedListener: ((Boolean) -> Unit)? = null

    override fun setLabel(label: String) {
        nativeWidget.text = label
    }

    override fun getLabel(): String = nativeWidget.text

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        onCheckedChangedListener?.invoke(checked)
    }

    override fun isChecked(): Boolean = checkedState

    override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {
        onCheckedChangedListener = listener
    }
}
