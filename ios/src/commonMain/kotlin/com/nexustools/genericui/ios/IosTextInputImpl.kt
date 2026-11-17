package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl

/**
 * iOS `UITextField` / `UITextView` implementation of [TextInputNativeImpl].
 */
class IosTextInputImpl(
    multiline: Boolean = false
) : IosBaseNativeImpl(if (multiline) "UITextView" else "UITextField", "form-control"), TextInputNativeImpl<IosNativeView> {
    private var labelText: String? = null
    private var captionLabelView: IosNativeView? = null
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null

    override fun setValue(value: String) {
        nativeWidget.text = value
    }

    override fun getValue(): String = nativeWidget.text

    override fun setPlaceholder(placeholder: String) {
        nativeWidget.placeholder = placeholder
    }

    override fun getPlaceholder(): String = nativeWidget.placeholder

    override fun setLabel(label: String?) {
        val normalized = label?.takeIf { it.isNotBlank() }
        labelText = normalized
        if (normalized != null) {
            nativeWidget.metadata["label"] = normalized
            val caption = captionLabelView ?: IosNativeView("UILabel").also { lbl ->
                lbl.accessibilityIdentifier = "textinput-caption-label"
                lbl.metadata["role"] = "top-border-caption"
                captionLabelView = lbl
                nativeWidget.addSubview(lbl)
            }
            caption.text = normalized
            caption.isHidden = false
        } else {
            nativeWidget.metadata.remove("label")
            captionLabelView?.let { lbl ->
                lbl.text = ""
                lbl.isHidden = true
                lbl.removeFromSuperview()
            }
            captionLabelView = null
        }
    }

    override fun getLabel(): String? = labelText

    override fun setReadOnly(readOnly: Boolean) {
        nativeWidget.metadata["isEditable"] = (!readOnly).toString()
    }

    override fun setPasswordMode(password: Boolean) {
        nativeWidget.metadata["isSecureTextEntry"] = password.toString()
    }

    override fun setMultiline(multiline: Boolean) {
        nativeWidget.metadata["multiline"] = multiline.toString()
    }

    override fun setOnValueChanged(listener: ((String) -> Unit)?) { onValueChangedListener = listener }
    override fun setOnSubmit(listener: ((String) -> Unit)?) { onSubmitListener = listener }
}
