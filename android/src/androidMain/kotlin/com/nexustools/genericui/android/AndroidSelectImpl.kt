package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl

/**
 * Android `androidx.appcompat.widget.AppCompatSpinner` / `MaterialAutoCompleteTextView` implementation of [SelectNativeImpl].
 */
class AndroidSelectImpl : AndroidBaseNativeImpl("androidx.appcompat.widget.AppCompatSpinner", "form-select"), SelectNativeImpl<AndroidViewHandle> {
    private var optionsList: List<Pair<String, String>> = emptyList()
    private var selectedValue: String? = null
    private var onSelectionChangedListener: ((String) -> Unit)? = null

    override fun setOptions(options: List<Pair<String, String>>) {
        optionsList = options.toList()
        if (selectedValue == null && optionsList.isNotEmpty()) {
            selectedValue = optionsList.first().first
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
