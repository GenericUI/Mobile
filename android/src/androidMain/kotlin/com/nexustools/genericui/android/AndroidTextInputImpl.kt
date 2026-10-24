package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl

/**
 * Android `com.google.android.material.textfield.TextInputEditText` implementation of [TextInputNativeImpl].
 */
class AndroidTextInputImpl(
    val multiline: Boolean = false
) : AndroidBaseNativeImpl("com.google.android.material.textfield.TextInputEditText", "form-control"), TextInputNativeImpl<AndroidViewHandle> {
    private var readOnlyState: Boolean = false
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null

    override fun setValue(value: String) {
        nativeWidget.text = value
        onValueChangedListener?.invoke(value)
    }

    override fun getValue(): String = nativeWidget.text

    override fun setPlaceholder(placeholder: String) {
        nativeWidget.hint = placeholder
    }

    override fun getPlaceholder(): String = nativeWidget.hint

    override fun setReadOnly(readOnly: Boolean) {
        readOnlyState = readOnly
    }

    override fun setPasswordMode(password: Boolean) {
        nativeWidget.metadata["inputType"] = if (password) "password" else "text"
    }

    override fun setMultiline(multiline: Boolean) {
        nativeWidget.metadata["multiline"] = multiline.toString()
    }

    override fun setOnValueChanged(listener: ((String) -> Unit)?) {
        onValueChangedListener = listener
    }

    override fun setOnSubmit(listener: ((String) -> Unit)?) {
        onSubmitListener = listener
    }
}
