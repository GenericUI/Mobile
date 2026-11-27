package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.widget.EditText
import android.widget.FrameLayout
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlin.math.min

/**
 * Android implementation of [StyledTextInputNativeImpl] supporting base, `:hover`, `:focus`,
 * `:active`, and `:disabled` [StyledVisualSpec] states via `GradientDrawable`, pill radii,
 * backdrop blur, custom typography, placeholder colors, and focus rings without Bootstrap tinting.
 */
class AndroidStyledTextInputImpl(
    multiline: Boolean = false,
    classPrefix: String = if (multiline) "styled-textarea" else "styled-textfield"
) : AndroidTextInputImpl(multiline, classPrefix), StyledTextInputNativeImpl<AndroidViewHandle> {

    private var baseVisuals: StyledVisualSpec = StyledVisualSpec()
    private var hoverVisuals: StyledVisualSpec? = null
    private var focusVisuals: StyledVisualSpec? = null
    private var activeVisuals: StyledVisualSpec? = null
    private var disabledVisuals: StyledVisualSpec? = null

    private var lastComponentStyle: ComponentStyle? = null
    private var lastComputedStyle: ComputedStyle? = null

    private var hovered: Boolean = false
    private var focused: Boolean = false
    private var activePressed: Boolean = false
    private var isSyncingVisuals: Boolean = false
    private var styledInitialized: Boolean = false

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        styledInitialized = true
        syncCurrentVisuals()
        syncAndroidView()
    }

    override fun allowsBootstrapColorForStyledSpec(): Boolean = false

    override fun resolveDefaultPaddingPx(): EdgeInsets = EdgeInsets.ZERO

    override fun createHostContainerView(context: Context): FrameLayout =
        AndroidStyledTextInputHostView(context) { this }

    override fun onEditTextCreated(editText: EditText) {
        editText.setOnHoverListener { _, event ->
            if (!isEnabled() || nativeWidget.layerPassThrough) return@setOnHoverListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> setHoveredState(true)
                MotionEvent.ACTION_HOVER_EXIT -> setHoveredState(false)
            }
            false
        }
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        if (styledInitialized && !isSyncingVisuals) {
            syncCurrentVisuals()
        }
    }

    override fun applyEditTextVisualStyling(
        container: FrameLayout,
        editText: EditText,
        isDark: Boolean
    ) {
        if (!styledInitialized) return
        val current = resolveCurrentVisuals()
        applyStyledVisualsToViews(container, editText, current, isDark)
    }

    private fun syncCurrentVisuals() {
        if (!styledInitialized || isSyncingVisuals) return
        isSyncingVisuals = true
        try {
            syncCurrentVisualsLocked()
        } finally {
            isSyncingVisuals = false
        }
    }

    private fun syncCurrentVisualsLocked() {
        val current = resolveCurrentVisuals()
        nativeWidget.attributes["styledTextInputVisuals"] = current.toString()
        current.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("backgroundColor")
        current.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
            ?: nativeWidget.attributes.remove("backgroundGradient")
        current.textColor?.let { nativeWidget.attributes["textColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("textColor")
        current.placeholderColor?.let { nativeWidget.attributes["placeholderColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("placeholderColor")
        current.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("borderColor")
        current.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
            ?: nativeWidget.attributes.remove("borderWidthPx")
        current.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
            ?: nativeWidget.attributes.remove("borderRadiusPx")
        current.fontFamily?.let { nativeWidget.attributes["fontFamily"] = it }
            ?: nativeWidget.attributes.remove("fontFamily")
        current.fontSizePx?.let { nativeWidget.attributes["fontSizePx"] = it.toString() }
            ?: nativeWidget.attributes.remove("fontSizePx")
        current.fontWeightNumeric?.let { nativeWidget.attributes["fontWeightNumeric"] = it.toString() }
            ?: nativeWidget.attributes.remove("fontWeightNumeric")
        current.focusRingColor?.let { nativeWidget.attributes["focusRingColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("focusRingColor")
        current.focusRingWidthPx?.let { nativeWidget.attributes["focusRingWidthPx"] = it.toString() }
            ?: nativeWidget.attributes.remove("focusRingWidthPx")
        current.opacity?.let { if (nativeWidget.alpha != it) setOpacity(it) }
        current.padding?.let { if (nativeWidget.padding != it) nativeWidget.padding = it }

        val rawBlurPx = current.backdropBlurPx ?: current.backdropFilter?.blur?.resolvePx()
        if (rawBlurPx != null && rawBlurPx > 0f) {
            nativeWidget.attributes["backdropBlurPx"] = rawBlurPx.toString()
            nativeWidget.attributes["androidRenderEffect"] = "blur(${rawBlurPx}px)"
        } else {
            nativeWidget.attributes.remove("backdropBlurPx")
            nativeWidget.attributes.remove("androidRenderEffect")
        }
        if (current.backdropFilter != null) {
            nativeWidget.attributes["backdropFilter"] = current.backdropFilter.toCssString()
        } else {
            nativeWidget.attributes.remove("backdropFilter")
        }

        if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
            nativeWidget.attributes["boxShadowBlurPx"] = current.boxShadowBlurPx.toString()
        } else {
            nativeWidget.attributes.remove("boxShadowBlurPx")
        }
        if (current.boxShadowColor != null) {
            nativeWidget.attributes["boxShadowColor"] = current.boxShadowColor.toCssRgbaString()
        } else {
            nativeWidget.attributes.remove("boxShadowColor")
        }

        if (current.widthPx != null || current.heightPx != null) {
            val w = current.widthPx ?: nativeWidget.bounds.width
            val h = current.heightPx ?: nativeWidget.bounds.height
            if (current.widthPx != null && current.widthPx > 0) {
                nativeWidget.hasExplicitPreferredWidth = true
            }
            if (current.heightPx != null && current.heightPx > 0) {
                nativeWidget.hasExplicitPreferredHeight = true
            }
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, w, h)
            current.widthPx?.let { nativeWidget.attributes["widthPx"] = it.toString() }
            current.heightPx?.let { nativeWidget.attributes["heightPx"] = it.toString() }
        }

        AndroidViewHandle.runOnMainThread {
            runCatching {
                val container = nativeWidget.androidView as? FrameLayout ?: return@runCatching
                val editText = activeEditText ?: return@runCatching
                val isDark = AndroidViewHandle.isDarkTheme()
                applyStyledVisualsToViews(container, editText, current, isDark)
                val parentVg = container.parent
                if (parentVg is AndroidFlexContainerView) {
                    val lp = (container.layoutParams as? AndroidFlexContainerView.LayoutParams)
                        ?: AndroidFlexContainerView.buildLayoutParamsFor(nativeWidget)
                    AndroidFlexContainerView.applyHandleToLayoutParams(nativeWidget, lp)
                    container.layoutParams = lp
                    container.requestLayout()
                }
                container.invalidate()
                nativeWidget.onViewMutated?.invoke()
            }
        }
    }

    private fun applyStyledVisualsToViews(
        container: FrameLayout,
        editText: EditText,
        current: StyledVisualSpec,
        isDark: Boolean
    ) {
        if (Build.VERSION.SDK_INT >= 31) {
            container.setRenderEffect(null)
        }
        if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
            container.elevation = AndroidViewHandle.dpToPxFloat(current.boxShadowBlurPx)
        } else {
            container.elevation = 0f
        }
        if (current.boxShadowColor != null && Build.VERSION.SDK_INT >= 28) {
            val argb = current.boxShadowColor.toArgbInt()
            container.outlineAmbientShadowColor = argb
            container.outlineSpotShadowColor = argb
        }

        val minW = current.minWidthPx?.let { AndroidViewHandle.dpToPx(it) } ?: 0
        val minH = current.minHeightPx?.let { AndroidViewHandle.dpToPx(it) } ?: 0
        container.minimumWidth = minW
        container.minimumHeight = minH
        if (minW > 0) {
            editText.minWidth = minW
        }
        if (minH > 0) {
            editText.minHeight = minH
        } else if (multilineState) {
            editText.minLines = 3
        }

        val defaultTextColor = if (isDark) RgbaColor(0xEC, 0xEF, 0xF4) else RgbaColor(0x1C, 0x1B, 0x1F)
        val defaultHintColor = if (isDark) RgbaColor(0x7B, 0x84, 0x98) else RgbaColor(0x77, 0x7E, 0x8E)
        val defaultBgColor = if (isDark) RgbaColor(0x16, 0x19, 0x20) else RgbaColor.WHITE
        val defaultBorderColor = if (isDark) RgbaColor(0x76, 0x7A, 0x85) else RgbaColor(0x79, 0x74, 0x7E)
        val defaultLabelColor = if (isDark) RgbaColor(0x9A, 0xA4, 0xB8) else RgbaColor(0x5F, 0x66, 0x76)

        val textArgb = (current.textColor ?: defaultTextColor).toArgbInt()
        val hintArgb = (current.placeholderColor ?: defaultHintColor).toArgbInt()

        val hasExplicitSurface = current.hasCustomBackground ||
            current.borderColor != null ||
            current.borderWidthPx != null ||
            current.hasFocusRing
        val surfaceSpec = when {
            !hasExplicitSurface -> current.copy(
                backgroundColor = defaultBgColor,
                borderColor = defaultBorderColor,
                borderWidthPx = 1f,
                borderRadiusPx = current.borderRadiusPx ?: 6f
            )
            (current.borderWidthPx ?: 0f) > 0f &&
                current.borderColor == null &&
                current.borderStyle != CssBorderStyle.NONE &&
                current.borderStyle != CssBorderStyle.HIDDEN -> current.copy(
                borderColor = defaultBorderColor
            )
            else -> current
        }

        val gradDrawable = AndroidStyledVisualUtil.buildGradientDrawable(surfaceSpec)
        val rawBlurPx = current.backdropBlurPx ?: current.backdropFilter?.blur?.resolvePx()
        val rawCornerRadiusPx = (surfaceSpec.borderRadiusPx ?: 0f).coerceAtLeast(0f)
        val hostView = container as? AndroidStyledTextInputHostView

        val til = textInputLayout
        if (isShowingLabeledMode && til != null) {
            til.boxBackgroundMode = com.google.android.material.textfield.TextInputLayout.BOX_BACKGROUND_OUTLINE
            til.isHintEnabled = true
            til.hint = labelText
            til.placeholderText = nativeWidget.hint.takeIf { it.isNotEmpty() }
            til.setPlaceholderTextColor(ColorStateList.valueOf(hintArgb))
            val rPx = AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
            til.setBoxCornerRadii(rPx, rPx, rPx, rPx)
            til.boxBackgroundColor = surfaceSpec.backgroundColor?.toArgbInt() ?: Color.TRANSPARENT
            val strokeColor = (surfaceSpec.borderColor ?: defaultBorderColor).toArgbInt()
            val focusStrokeColor = (current.focusRingColor ?: surfaceSpec.borderColor ?: defaultBorderColor).toArgbInt()
            til.setBoxStrokeColorStateList(
                ColorStateList(
                    arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                    intArrayOf(focusStrokeColor, strokeColor)
                )
            )
            val labelStateColor = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_focused), intArrayOf()),
                intArrayOf((current.textColor ?: defaultLabelColor).toArgbInt(), defaultLabelColor.toArgbInt())
            )
            til.defaultHintTextColor = labelStateColor
            til.hintTextColor = labelStateColor
            til.isEnabled = isEnabled()
            if (editText.hint != null) {
                editText.hint = null
            }
            if (hostView != null) {
                if (current.hasBackdropFilter) {
                    container.background = null
                    val scaledBlurPx = rawBlurPx?.takeIf { it > 0f }?.let { AndroidViewHandle.dpToPxFloat(it) }
                    hostView.backdropController.backdropBlurPx = scaledBlurPx ?: 0f
                    hostView.backdropController.backdropFilter = current.backdropFilter?.let { f ->
                        if (scaledBlurPx != null) f.copy(blur = CssLength.px(scaledBlurPx)) else f
                    } ?: scaledBlurPx?.let { CssFilter.blur(CssLength.px(it)) }
                    hostView.backdropController.backdropCornerRadiusPx = rPx
                    hostView.backdropController.backdropOverlayDrawable = null
                } else {
                    hostView.backdropController.backdropBlurPx = 0f
                    hostView.backdropController.backdropFilter = null
                    hostView.backdropController.backdropCornerRadiusPx = 0f
                    hostView.backdropController.backdropOverlayDrawable = null
                    container.background = null
                }
            }
        } else {
            if (editText.hint?.toString() != nativeWidget.hint) {
                editText.hint = nativeWidget.hint
            }
            editText.setHintTextColor(hintArgb)
            editText.background = null
            editText.backgroundTintList = null
            if (hostView != null && current.hasBackdropFilter) {
                container.background = null
                val scaledBlurPx = rawBlurPx?.takeIf { it > 0f }?.let { AndroidViewHandle.dpToPxFloat(it) }
                hostView.backdropController.backdropBlurPx = scaledBlurPx ?: 0f
                hostView.backdropController.backdropFilter = current.backdropFilter?.let { f ->
                    if (scaledBlurPx != null) f.copy(blur = CssLength.px(scaledBlurPx)) else f
                } ?: scaledBlurPx?.let { CssFilter.blur(CssLength.px(it)) }
                hostView.backdropController.backdropCornerRadiusPx = AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
                hostView.backdropController.backdropOverlayDrawable = gradDrawable
            } else {
                if (hostView != null) {
                    hostView.backdropController.backdropBlurPx = 0f
                    hostView.backdropController.backdropFilter = null
                    hostView.backdropController.backdropCornerRadiusPx = 0f
                    hostView.backdropController.backdropOverlayDrawable = null
                }
                container.background = gradDrawable
            }

            val effPad = current.padding ?: getPadding().takeIf { it != EdgeInsets.ZERO }
            if (effPad != null) {
                editText.setPadding(
                    AndroidViewHandle.dpToPx(effPad.left),
                    AndroidViewHandle.dpToPx(effPad.top),
                    AndroidViewHandle.dpToPx(effPad.right),
                    AndroidViewHandle.dpToPx(effPad.bottom)
                )
            } else if (getPaddingScale() == null) {
                val hPad = AndroidViewHandle.dpToPx(12)
                val vPad = AndroidViewHandle.dpToPx(8)
                editText.setPadding(hPad, vPad, hPad, vPad)
            }
        }

        if (hostView != null) {
            hostView.focusRingColorArgb = current.focusRingColor?.takeIf { isFocusedState() && (current.focusRingWidthPx ?: 2f) > 0f }?.toArgbInt()
            hostView.focusRingWidthPx = AndroidViewHandle.dpToPxFloat((current.focusRingWidthPx ?: 2f).coerceAtLeast(1f))
            hostView.cornerRadiusPx = AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
            hostView.setWillNotDraw(!current.hasBackdropFilter && hostView.focusRingColorArgb == null)
        }

        editText.setTextColor(textArgb)
        val spSize = current.fontSizePx?.takeIf { it > 0f }
            ?: customStyleFontSizePx?.takeIf { it > 0f }
            ?: 14f
        editText.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)

        val weight = current.fontWeightNumeric ?: customStyleFontWeightNumeric
        val isBold = (weight != null && weight >= 600) ||
            styleSnapshot.fontWeight == FontWeight.BOLD ||
            styleSnapshot.fontWeight == FontWeight.SEMIBOLD ||
            styleSnapshot.fontWeight == FontWeight.BOLDER
        val tfStyle = if (isBold) Typeface.BOLD else Typeface.NORMAL
        val family = current.fontFamily?.takeIf { it.isNotBlank() }
            ?: baseComponentStyle?.getFontFamily()?.resolvedPrimaryName()
            ?: baseComputedStyle?.fontFamily
        val tf = if (!family.isNullOrBlank()) Typeface.create(family, tfStyle) else null
        editText.setTypeface(tf, tfStyle)
    }

    override fun setBaseVisuals(visuals: StyledVisualSpec) {
        baseVisuals = sanitizeIncomingStyledVisuals(visuals)
        syncCurrentVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        hoverVisuals = visuals?.let { sanitizeIncomingStyledVisuals(it) }
        syncCurrentVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        focusVisuals = visuals?.let { sanitizeIncomingStyledVisuals(it) }
        syncCurrentVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        activeVisuals = visuals?.let { sanitizeIncomingStyledVisuals(it) }
        syncCurrentVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        disabledVisuals = visuals?.let { sanitizeIncomingStyledVisuals(it) }
        syncCurrentVisuals()
    }

    override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisuals

    override fun setHoveredState(hovered: Boolean) {
        val coerced = if (!isEnabled()) false else hovered
        this.hovered = coerced
        nativeWidget.attributes["hovered"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isHoveredState(): Boolean = if (!isEnabled()) false else hovered

    override fun setFocusedState(focused: Boolean) {
        val coerced = if (!isEnabled() || !isFocusable()) false else focused
        val visualsChanged = this.focused != coerced || nativeWidget.attributes["focused"] != coerced.toString()
        this.focused = coerced
        nativeWidget.attributes["focused"] = coerced.toString()
        super<AndroidTextInputImpl>.setFocusedState(coerced)
        if (visualsChanged) {
            syncCurrentVisuals()
        }
    }

    override fun isFocusedState(): Boolean = super<AndroidTextInputImpl>.isFocusedState()

    override fun setActivePressedState(active: Boolean) {
        val coerced = if (!isEnabled()) false else active
        this.activePressed = coerced
        nativeWidget.attributes["activePressed"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isActivePressedState(): Boolean = if (!isEnabled()) false else activePressed

    override fun setEnabled(enabled: Boolean) {
        super<AndroidTextInputImpl>.setEnabled(enabled)
        if (!enabled) {
            this.hovered = false
            this.focused = false
            this.activePressed = false
            nativeWidget.attributes["hovered"] = "false"
            nativeWidget.attributes["focused"] = "false"
            nativeWidget.attributes["activePressed"] = "false"
            super<AndroidTextInputImpl>.setFocusedState(false)
        }
        syncCurrentVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super<AndroidTextInputImpl>.applyComputedStyle(style)
        syncCurrentVisuals()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super<AndroidTextInputImpl>.applyComponentStyle(style)
        syncCurrentVisuals()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<AndroidTextInputImpl>.applyComponentBootstrap(bootstrap)
        syncCurrentVisuals()
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = baseComputedStyle,
            snapshot = snapshotForStyledVisuals(),
            darkTheme = isDark
        )
        var effective = computedBase.mergeWith(baseVisuals)
        if (!isEnabled()) {
            val computedDisabled = lastComputedStyle?.disabledStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            return effective.mergeWith(computedDisabled).mergeWith(disabledVisuals)
                .forBackend(LayoutBackendTarget.ANDROID)
        }
        if (hovered) {
            val computedHover = lastComputedStyle?.hoverStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedHover).mergeWith(hoverVisuals)
        }
        if (focused) {
            val computedFocus = lastComputedStyle?.focusStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedFocus).mergeWith(focusVisuals)
        }
        if (activePressed) {
            val computedActive = lastComputedStyle?.activeStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective = effective.mergeWith(computedActive).mergeWith(activeVisuals)
        }
        return effective.forBackend(LayoutBackendTarget.ANDROID)
    }

    private class AndroidStyledTextInputHostView(
        context: Context,
        private val implProvider: () -> AndroidStyledTextInputImpl?
    ) : FrameLayout(context) {
        internal val backdropController = AndroidHardwareBackdropBlurController(this)
        internal var focusRingColorArgb: Int? = null
        internal var focusRingWidthPx: Float = 2f
        internal var cornerRadiusPx: Float = 0f
        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        private val ringRect = RectF()

        override fun onDetachedFromWindow() {
            super.onDetachedFromWindow()
            backdropController.release()
        }

        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            val impl = implProvider()
            if (impl?.nativeWidget?.layerPassThrough == true) return false
            if (event != null && impl != null && impl.isEnabled()) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> impl.setActivePressedState(true)
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> impl.setActivePressedState(false)
                }
            }
            return super.dispatchTouchEvent(event)
        }

        override fun onDraw(canvas: Canvas) {
            backdropController.drawBackdropIfActive(canvas)
            backdropController.drawOverlayDrawable(canvas)
            super.onDraw(canvas)
        }

        override fun dispatchDraw(canvas: Canvas) {
            super.dispatchDraw(canvas)
            val ringColor = focusRingColorArgb
            if (ringColor != null && width > 0 && height > 0) {
                val inset = focusRingWidthPx / 2f
                val clampedRadius = min(cornerRadiusPx, min(width, height) / 2f)
                ringPaint.color = ringColor
                ringPaint.strokeWidth = focusRingWidthPx
                ringRect.set(inset, inset, width - inset, height - inset)
                canvas.drawRoundRect(ringRect, clampedRadius, clampedRadius, ringPaint)
            }
        }
    }
}
