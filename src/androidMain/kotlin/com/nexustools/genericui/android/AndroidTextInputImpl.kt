package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatEditText
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android [AppCompatEditText] / Material [TextInputLayout] + [TextInputEditText] implementation
 * of [TextInputNativeImpl].
 *
 * Uses a thin outer [FrameLayout] host whose view identity never changes, holding a bare
 * [AppCompatEditText] when [getLabel] is `null`/blank and a Material outlined [TextInputLayout]
 * + [TextInputEditText] when a floating [label] is set.
 */
class AndroidTextInputImpl(
    val multiline: Boolean = false
) : AndroidBaseNativeImpl("com.google.android.material.textfield.TextInputEditText", "form-control"), TextInputNativeImpl<AndroidViewHandle> {
    private var labelText: String? = null
    private var readOnlyState: Boolean = false
    private var passwordState: Boolean = false
    private var multilineState: Boolean = multiline
    private var onValueChangedListener: ((String) -> Unit)? = null
    private var onSubmitListener: ((String) -> Unit)? = null
    private var suppressTextWatcher: Boolean = false

    private var bareEditText: AppCompatEditText? = null
    private var textInputLayout: TextInputLayout? = null
    private var labeledEditText: TextInputEditText? = null
    private var activeEditText: EditText? = null
    private var isShowingLabeledMode: Boolean = false
    private var initialized: Boolean = false

    init {
        initialized = true
        syncAndroidView()
    }

    private fun resolveMaterialContext(context: Context, isDark: Boolean): Context {
        val tv = TypedValue()
        val theme = context.theme
        val hasAppCompat = runCatching {
            theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true)
        }.getOrDefault(false)
        val hasMaterial = runCatching {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, tv, true)
        }.getOrDefault(false)
        if (hasAppCompat && hasMaterial) {
            return context
        }
        val styleRes = if (isDark) {
            com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar
        } else {
            com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
        }
        return ContextThemeWrapper(context, styleRes)
    }

    private fun wireEditTextListeners(editText: EditText) {
        editText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressTextWatcher || activeEditText !== editText) return
                val updated = s?.toString().orEmpty()
                if (nativeWidget.text != updated) {
                    nativeWidget.text = updated
                    onValueChangedListener?.invoke(updated)
                }
            }
        })
        editText.setOnEditorActionListener { v, actionId, _ ->
            if (activeEditText !== editText) return@setOnEditorActionListener false
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

    private fun obtainBareEditText(context: Context): AppCompatEditText =
        bareEditText ?: AppCompatEditText(context).also { edit ->
            wireEditTextListeners(edit)
            bareEditText = edit
        }

    private fun ensureActiveEditText(container: FrameLayout, wantLabeled: Boolean, isDark: Boolean): EditText {
        val existing = activeEditText
        if (existing != null && isShowingLabeledMode == wantLabeled && existing.parent != null) {
            return existing
        }

        val prevFocus = existing?.hasFocus() == true
        val prevSelStart = existing?.selectionStart ?: -1
        val prevSelEnd = existing?.selectionEnd ?: -1

        container.removeAllViews()

        val nextEditText: EditText = if (wantLabeled) {
            val matResult = runCatching {
                val matCtx = resolveMaterialContext(container.context, isDark)
                val til = textInputLayout ?: TextInputLayout(matCtx).also { layout ->
                    layout.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    layout.isHintEnabled = true
                    textInputLayout = layout
                }
                val edit = labeledEditText ?: TextInputEditText(til.context).also { input ->
                    wireEditTextListeners(input)
                    labeledEditText = input
                }
                if (edit.parent !== til) {
                    (edit.parent as? ViewGroup)?.removeView(edit)
                    til.addView(
                        edit,
                        LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    )
                }
                (til.parent as? ViewGroup)?.removeView(til)
                container.addView(
                    til,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                isShowingLabeledMode = true
                edit
            }
            matResult.getOrElse {
                val bare = obtainBareEditText(container.context)
                (bare.parent as? ViewGroup)?.removeView(bare)
                container.addView(
                    bare,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT
                    )
                )
                isShowingLabeledMode = false
                bare
            }
        } else {
            val bare = obtainBareEditText(container.context)
            (bare.parent as? ViewGroup)?.removeView(bare)
            container.addView(
                bare,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
            isShowingLabeledMode = false
            bare
        }

        activeEditText = nextEditText
        if (nextEditText.text?.toString() != nativeWidget.text) {
            suppressTextWatcher = true
            try {
                nextEditText.setText(nativeWidget.text)
            } finally {
                suppressTextWatcher = false
            }
        }
        val textLen = nextEditText.text?.length ?: 0
        if (prevSelStart in 0..textLen && prevSelEnd in 0..textLen) {
            runCatching { nextEditText.setSelection(prevSelStart, prevSelEnd) }
        }
        if (prevFocus) {
            runCatching { nextEditText.requestFocus() }
        }
        return nextEditText
    }

    override fun createDefaultAndroidView(context: Context): View = FrameLayout(context)

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun setValue(value: String) {
        nativeWidget.text = value
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val editText = activeEditText
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

    override fun setLabel(label: String?) {
        val normalized = label?.takeIf { it.isNotBlank() }
        if (labelText == normalized) return
        labelText = normalized
        if (normalized != null) {
            nativeWidget.attributes["label"] = normalized
        } else {
            nativeWidget.attributes.remove("label")
        }
        syncAndroidView()
    }

    override fun getLabel(): String? = labelText

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
        if (!initialized) return
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val container = nativeWidget.androidView as? FrameLayout ?: return@runCatching
                val isDark = AndroidViewHandle.isDarkTheme()
                val wantLabeled = !labelText.isNullOrBlank()
                val editText = ensureActiveEditText(container, wantLabeled, isDark)

                if (editText.text?.toString() != nativeWidget.text) {
                    suppressTextWatcher = true
                    try {
                        editText.setText(nativeWidget.text)
                    } finally {
                        suppressTextWatcher = false
                    }
                }

                val activeTheme = BootstrapColorPalette.activeThemeConfig
                val focusAccentArgb = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark, activeTheme).toArgbInt()
                val bareBgArgb = customStyleBackgroundColor?.toArgbInt()
                    ?: if (isDark) 0xFF161920.toInt() else 0xFFFFFFFF.toInt()
                val outlinedBoxBgArgb = customStyleBackgroundColor?.toArgbInt()
                    ?: android.graphics.Color.TRANSPARENT
                val borderArgb = customStyleBorderColor?.toArgbInt()
                    ?: if (isDark) 0xFF767A85.toInt() else 0xFF79747E.toInt()
                val textArgb = customStyleTextColor?.toArgbInt()
                    ?: if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                val hintArgb = if (isDark) 0xFF7B8498.toInt() else 0xFF777E8E.toInt()
                val labelArgb = if (isDark) 0xFF9AA4B8.toInt() else 0xFF5F6676.toInt()

                val til = textInputLayout
                if (isShowingLabeledMode && til != null) {
                    til.boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_OUTLINE
                    til.isHintEnabled = true
                    til.hint = labelText
                    til.placeholderText = nativeWidget.hint.takeIf { it.isNotEmpty() }
                    til.setPlaceholderTextColor(ColorStateList.valueOf(hintArgb))
                    val rPx = resolveEffectiveBorderRadiusPx(6f)
                    til.setBoxCornerRadii(rPx, rPx, rPx, rPx)
                    til.boxBackgroundColor = outlinedBoxBgArgb
                    val strokeStates = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                        intArrayOf(focusAccentArgb, borderArgb)
                    )
                    til.setBoxStrokeColorStateList(strokeStates)
                    val labelStates = ColorStateList(
                        arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                        intArrayOf(focusAccentArgb, labelArgb)
                    )
                    til.defaultHintTextColor = labelStates
                    til.hintTextColor = labelStates
                    til.isEnabled = isEnabled()
                    if (editText.hint != null) {
                        editText.hint = null
                    }
                } else {
                    if (editText.hint?.toString() != nativeWidget.hint) {
                        editText.hint = nativeWidget.hint
                    }
                    val bg = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = resolveEffectiveBorderRadiusPx(6f)
                        setColor(bareBgArgb)
                        val strokePx = AndroidViewHandle.dpToPx((customStyleBorderWidthPx ?: 1f).coerceAtLeast(1f)).coerceAtLeast(1)
                        setStroke(strokePx, borderArgb)
                    }
                    editText.background = bg
                    editText.setHintTextColor(hintArgb)
                    val p = getPadding()
                    if (p.left == 0 && p.top == 0 && p.right == 0 && p.bottom == 0 && getPaddingScale() == null) {
                        val hPad = AndroidViewHandle.dpToPx(10)
                        val vPad = AndroidViewHandle.dpToPx(8)
                        editText.setPadding(hPad, vPad, hPad, vPad)
                    }
                }

                editText.setTextColor(textArgb)
                val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: 14f
                editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)

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
                editText.isEnabled = isEnabled()
                editText.isFocusable = editable
                editText.isFocusableInTouchMode = editable
                editText.isCursorVisible = editable
            }
        }
    }
}
