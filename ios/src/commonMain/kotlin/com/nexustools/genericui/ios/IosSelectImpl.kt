package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl

/**
 * iOS `UIPickerView` / `UIMenu` implementation of [SelectNativeImpl].
 */
class IosSelectImpl : IosBaseNativeImpl("UIPickerView", "form-select"), SelectNativeImpl<IosNativeView> {
    private var optionsList: List<Pair<String, String>> = emptyList()
    private var selectedValue: String? = null
    private var onSelectionChangedListener: ((String) -> Unit)? = null

    override fun setOptions(options: List<Pair<String, String>>) {
        optionsList = options
        if (selectedValue == null && options.isNotEmpty()) {
            selectedValue = options.first().first
        }
    }

    override fun setSelectedValue(value: String?) {
        selectedValue = value
        if (value != null) {
            onSelectionChangedListener?.invoke(value)
        }
    }

    override fun getSelectedValue(): String? = selectedValue

    override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {
        onSelectionChangedListener = listener
    }
}
