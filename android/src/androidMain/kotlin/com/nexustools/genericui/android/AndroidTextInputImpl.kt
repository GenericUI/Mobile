package com.nexustools.genericui.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.appcompat.widget.AppCompatEditText
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl

/**
 * Android `androidx.appcompat.widget.AppCompatEditText` implementation of [TextInputNativeImpl].
 */
class AndroidTextInputImpl(
    val multiline: Boolean = false
) : AndroidBaseNativeImpl("com.google.android.material.textfield.TextInputEditText", "form-control"), TextInputNativeImpl<AndroidViewHandle> {
    private var readOnlyState: Boolean = false
    private var passwordState: Boolean = false
    private var multilineState: Boolean = multiline
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null
    private var suppressTextWatcher: Boolean = false

    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AppCompatEditText(context).apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) {
                    if (suppressTextWatcher) return
                    val updated = s?.toString().orEmpty()
                    if (nativeWidget.text != updated) {
                        nativeWidget.text = updated
                        onValueChangedListener?.invoke(updated)
                    }
                }
            })
            setOnEditorActionListener { v, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_SEND ||
                    actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_SEARCH
                ) {
                    onSubmitListener?.invoke(v.text?.toString().orEmpty())
                    true
                } else {
                    false
                }
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun setValue(value: String) {
        nativeWidget.text = value
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val editText = nativeWidget.androidView as? EditText
                if (editText != null && editText.text?.toString() != value) {
                    suppressTextWatcher = true
                    try {
                        editText.setText(value)
                        editText.setSelection(value.length.coerceAtMost(editText.text?.length ?: 0))
                    } finally {
                        suppressTextWatcher = false
                    }
                }
            }
        }
        onValueChangedListener?.invoke(value)
    }

    override fun getValue(): String = nativeWidget.text

    override fun setPlaceholder(placeholder: String) {
        nativeWidget.hint = placeholder
        syncAndroidView()
    }

    override fun getPlaceholder(): String = nativeWidget.hint

    override fun setReadOnly(readOnly: Boolean) {
        readOnlyState = readOnly
        syncAndroidView()
    }

    override fun setPasswordMode(password: Boolean) {
        passwordState = password
        nativeWidget.attributes["inputType"] = if (password) "password" else "text"
        syncAndroidView()
    }

    override fun setMultiline(multiline: Boolean) {
        multilineState = multiline
        nativeWidget.attributes["multiline"] = multiline.toString()
        syncAndroidView()
    }

    override fun setOnValueChanged(listener: ((String) -> Unit)?) {
        onValueChangedListener = listener
    }

    override fun setOnSubmit(listener: ((String) -> Unit)?) {
        onSubmitListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val editText = nativeWidget.androidView as? EditText ?: return@runCatching
                if (editText.text?.toString() != nativeWidget.text) {
                    suppressTextWatcher = true
                    try {
                        editText.setText(nativeWidget.text)
                    } finally {
                        suppressTextWatcher = false
                    }
                }
                if (editText.hint?.toString() != nativeWidget.hint) {
                    editText.hint = nativeWidget.hint
                }

                val isDark = AndroidViewHandle.isDarkTheme()
                val bgArgb = customStyleBackgroundColor?.toArgbInt()
                    ?: if (isDark) 0xFF161920.toInt() else 0xFFFFFFFF.toInt()
                val borderArgb = customStyleBorderColor?.toArgbInt()
                    ?: if (isDark) 0xFF343B4A.toInt() else 0xFFCBD2DF.toInt()
                val textArgb = customStyleTextColor?.toArgbInt()
                    ?: if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                val hintArgb = if (isDark) 0xFF7B8498.toInt() else 0xFF777E8E.toInt()

                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resolveEffectiveBorderRadiusPx(6f)
                    setColor(bgArgb)
                    val strokePx = AndroidViewHandle.dpToPx((customStyleBorderWidthPx ?: 1f).coerceAtLeast(1f)).coerceAtLeast(1)
                    setStroke(strokePx, borderArgb)
                }
                editText.background = bg
                editText.setTextColor(textArgb)
                editText.setHintTextColor(hintArgb)

                val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: 14f
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)

                val p = getPadding()
                if (p.left == 0 && p.top == 0 && p.right == 0 && p.bottom == 0 && getPaddingScale() == null) {
                    val hPad = AndroidViewHandle.dpToPx(10)
                    val vPad = AndroidViewHandle.dpToPx(8)
                    editText.setPadding(hPad, vPad, hPad, vPad)
                }

                when {
                    passwordState -> {
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        editText.transformationMethod = PasswordTransformationMethod.getInstance()
                        editText.setSingleLine(true)
                    }
                    multilineState -> {
                        editText.setSingleLine(false)
                        editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
                        editText.transformationMethod = null
                        editText.minLines = 3
                        editText.gravity = Gravity.TOP or Gravity.START
                    }
                    else -> {
                        editText.setSingleLine(true)
                        editText.inputType = InputType.TYPE_CLASS_TEXT
                        editText.transformationMethod = null
                        editText.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                        editText.imeOptions = EditorInfo.IME_ACTION_DONE
                    }
                }

                val editable = isEnabled() && !readOnlyState
                editText.isFocusable = editable
                editText.isFocusableInTouchMode = editable
                editText.isCursorVisible = editable
            }
        }
    }
}
