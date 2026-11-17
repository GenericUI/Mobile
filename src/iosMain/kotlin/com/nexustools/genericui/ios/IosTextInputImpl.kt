@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.setValue
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventEditingChanged
import platform.UIKit.UIControlEventEditingDidEndOnExit
import platform.UIKit.UIFont
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UILabel
import platform.UIKit.UITextBorderStyle
import platform.UIKit.UITextField
import platform.UIKit.UITextView
import platform.UIKit.secondaryLabelColor
import platform.UIKit.separatorColor

/**
 * Native UIKit `UITextField` / `UITextView` implementation of [TextInputNativeImpl] with optional
 * floating/header `UILabel` caption (`TextField.label` parity from commit `398102f`).
 */
class IosTextInputImpl(
    initialMultiline: Boolean = false
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 240.0, if (initialMultiline) 104.0 else 44.0)).apply {
            flexDirection = FlexDirection.COLUMN
            itemGapPt = 4.0
        }
    ),
    componentClassPrefix = "text-input"
), TextInputNativeImpl<UikitViewHandle> {

    private val delegateBridge = UikitTextInputDelegate()
    private val hostContainer: UikitFlexContainerView get() = uiView as UikitFlexContainerView

    val textField: UITextField = UITextField(frame = CGRectMake(0.0, 0.0, 240.0, 40.0)).apply {
        borderStyle = UITextBorderStyle.UITextBorderStyleRoundedRect
        font = UIFont.systemFontOfSize(15.0)
    }
    val textView: UITextView = UITextView(frame = CGRectMake(0.0, 0.0, 240.0, 96.0)).apply {
        font = UIFont.systemFontOfSize(15.0)
        layer.cornerRadius = 8.0
        layer.borderWidth = 1.0
        layer.borderColor = UIColor.separatorColor.CGColor
    }
    var captionLabel: UILabel? = null
        private set

    private var currentValue: String = ""
    private var placeholderValue: String = ""
    private var labelValue: String? = null
    private var readOnlyMode: Boolean = false
    private var passwordMode: Boolean = false
    private var multilineMode: Boolean = initialMultiline
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null

    init {
        textField.delegate = delegateBridge
        textView.delegate = delegateBridge
        textField.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_EDITING_CHANGED,
            forControlEvents = UIControlEventEditingChanged
        )
        textField.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_EDITING_END_EXIT,
            forControlEvents = UIControlEventEditingDidEndOnExit
        )
        actionTarget.onEditingChanged = {
            if (!multilineMode) {
                val updated = textField.text ?: ""
                if (updated != currentValue) {
                    currentValue = updated
                    onValueChangedListener?.invoke(updated)
                }
            }
        }
        actionTarget.onEditingDidEndOnExit = {
            onSubmitListener?.invoke(currentValue)
        }
        delegateBridge.onTextChanged = { text ->
            if (multilineMode && text != currentValue) {
                currentValue = text
                onValueChangedListener?.invoke(text)
            }
        }
        delegateBridge.onSubmit = { text ->
            currentValue = text
            onSubmitListener?.invoke(text)
        }
        rebuildInputSubviews()
    }

    override fun setValue(value: String) {
        if (currentValue == value && activeInputText() == value) return
        currentValue = value
        textField.text = value
        textView.text = value
        onValueChangedListener?.invoke(value)
    }

    override fun getValue(): String = activeInputText()

    private fun activeInputText(): String =
        if (multilineMode) textView.text else (textField.text ?: currentValue)

    override fun setPlaceholder(placeholder: String) {
        placeholderValue = placeholder
        textField.placeholder = placeholder
    }

    override fun getPlaceholder(): String = placeholderValue

    override fun setLabel(label: String?) {
        val normalized = label?.takeIf { it.isNotBlank() }
        labelValue = normalized
        if (normalized == null) {
            captionLabel?.removeFromSuperview()
            captionLabel = null
        } else {
            val lbl = captionLabel ?: UILabel(frame = CGRectMake(0.0, 0.0, 240.0, 18.0)).apply {
                setValue("textinput-caption-label", forKey = "accessibilityIdentifier")
                font = UIFont.systemFontOfSize(12.0, weight = UIFontWeightMedium)
                textColor = UIColor.secondaryLabelColor
                captionLabel = this
            }
            lbl.text = normalized
        }
        rebuildInputSubviews()
    }

    override fun getLabel(): String? = labelValue

    override fun setReadOnly(readOnly: Boolean) {
        readOnlyMode = readOnly
        delegateBridge.isReadOnly = readOnly
        textView.setEditable(!readOnly)
    }

    override fun setPasswordMode(password: Boolean) {
        passwordMode = password
        textField.secureTextEntry = password
    }

    override fun setMultiline(multiline: Boolean) {
        if (multilineMode == multiline) return
        multilineMode = multiline
        if (multiline) {
            textView.text = currentValue
        } else {
            textField.text = currentValue
        }
        rebuildInputSubviews()
    }

    override fun setOnValueChanged(listener: ((String) -> Unit)?) {
        onValueChangedListener = listener
    }

    override fun setOnSubmit(listener: ((String) -> Unit)?) {
        onSubmitListener = listener
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        textField.enabled = enabled
        textView.setEditable(enabled && !readOnlyMode)
    }

    private fun rebuildInputSubviews() {
        for (sub in hostContainer.subviews.filterIsInstance<platform.UIKit.UIView>()) {
            sub.removeFromSuperview()
        }
        captionLabel?.let { lbl ->
            hostContainer.addSubview(lbl)
            hostContainer.setChildPreferredSize(lbl, 240.0, 18.0)
        }
        val inputView = if (multilineMode) textView else textField
        hostContainer.addSubview(inputView)
        val inputH = if (multilineMode) 96.0 else 40.0
        hostContainer.setChildPreferredSize(inputView, 240.0, inputH)
        val totalH = inputH + (if (captionLabel != null) 22.0 else 0.0)
        hostContainer.frame.useContents {
            val w = size.width.coerceAtLeast(240.0)
            hostContainer.setFrame(CGRectMake(origin.x, origin.y, w, totalH))
        }
        hostContainer.setNeedsLayout()
    }
}
