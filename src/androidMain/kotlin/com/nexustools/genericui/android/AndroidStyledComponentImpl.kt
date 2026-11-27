package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * Android [View] implementation of [StyledComponentNativeImpl] supporting full
 * [StyledVisualSpec] surfaces, `:hover`, `:focus`, `:active`, and `:disabled` visual states,
 * opacity, `pointer-events: none` touch pass-through, and hardware backdrop-filter blur.
 */
class AndroidStyledComponentImpl(
    classPrefix: String = "styled-component",
    tagName: String = "genericui:styled-component"
) : AndroidBaseNativeImpl("android.view.View", classPrefix, initialTagName = tagName), StyledComponentNativeImpl<AndroidViewHandle> {

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

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AndroidStyledComponentView(context) { this }.apply {
            isFocusable = true
            isFocusableInTouchMode = false
            setOnFocusChangeListener { _, hasFocus ->
                if (isEnabled() && isFocusable() && !nativeWidget.layerPassThrough) {
                    setFocusedState(hasFocus)
                }
            }
            setOnHoverListener { _, event ->
                if (!isEnabled() || nativeWidget.layerPassThrough) return@setOnHoverListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> setHoveredState(true)
                    MotionEvent.ACTION_HOVER_EXIT -> setHoveredState(false)
                }
                false
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun allowsBackdropBlur(): Boolean = true

    override fun syncAndroidView() {
        super.syncAndroidView()
        if (!isSyncingVisuals) {
            syncCurrentVisuals()
        }
    }

    private fun syncCurrentVisuals() {
        if (isSyncingVisuals) return
        isSyncingVisuals = true
        try {
            syncCurrentVisualsLocked()
        } finally {
            isSyncingVisuals = false
        }
    }

    private fun syncCurrentVisualsLocked() {
        val current = resolveCurrentVisuals()
        nativeWidget.attributes["styledComponentVisuals"] = current.toString()
        current.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("backgroundColor")
        current.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
            ?: nativeWidget.attributes.remove("backgroundGradient")
        current.textColor?.let { nativeWidget.attributes["textColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("textColor")
        current.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgbaString() }
            ?: nativeWidget.attributes.remove("borderColor")
        current.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
            ?: nativeWidget.attributes.remove("borderWidthPx")
        current.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
            ?: nativeWidget.attributes.remove("borderRadiusPx")
        current.opacity?.let { if (nativeWidget.alpha != it) setOpacity(it) }
        current.padding?.let { if (nativeWidget.padding != it) setPadding(it) }

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
                val view = nativeWidget.androidView ?: return@runCatching
                if (Build.VERSION.SDK_INT >= 31) {
                    view.setRenderEffect(null)
                }
                if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
                    view.elevation = AndroidViewHandle.dpToPxFloat(current.boxShadowBlurPx)
                }
                if (current.boxShadowColor != null && Build.VERSION.SDK_INT >= 28) {
                    val argb = current.boxShadowColor.toArgbInt()
                    view.outlineAmbientShadowColor = argb
                    view.outlineSpotShadowColor = argb
                }
                view.backgroundTintList = null
                val gradDrawable = AndroidStyledVisualUtil.buildGradientDrawable(current)
                val styledView = view as? AndroidStyledComponentView
                val hasBackdrop = CssFilter.backdropBlurOrFilter(current.backdropBlurPx, current.backdropFilter) != null
                if (hasBackdrop && styledView != null) {
                    view.background = null
                    styledView.setWillNotDraw(false)
                    AndroidStyledVisualUtil.applyBackdropControllerSpec(styledView.backdropController, current, gradDrawable)
                } else {
                    if (styledView != null) {
                        AndroidStyledVisualUtil.applyBackdropControllerSpec(styledView.backdropController, StyledVisualSpec.EMPTY, null)
                    }
                    view.background = gradDrawable
                }
                if (styledView != null) {
                    styledView.focusRingColorArgb = current.focusRingColor?.takeIf { focused }?.toArgbInt()
                    styledView.focusRingWidthPx = AndroidViewHandle.dpToPxFloat((current.focusRingWidthPx ?: 2f).coerceAtLeast(1f))
                    styledView.cornerRadiusPx = AndroidViewHandle.dpToPxFloat((current.borderRadiusPx ?: 0f).coerceAtLeast(0f))
                    styledView.setWillNotDraw(!hasBackdrop && styledView.focusRingColorArgb == null)
                }

                val parentVg = view.parent
                if (parentVg is AndroidFlexContainerView) {
                    val lp = (view.layoutParams as? AndroidFlexContainerView.LayoutParams)
                        ?: AndroidFlexContainerView.buildLayoutParamsFor(nativeWidget)
                    AndroidFlexContainerView.applyHandleToLayoutParams(nativeWidget, lp)
                    view.layoutParams = lp
                    view.requestLayout()
                }
                view.invalidate()
                nativeWidget.onViewMutated?.invoke()
            }
        }
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        baseVisuals = sanitizeIncomingStyledVisuals(visuals)
        syncCurrentVisuals()
    }

    override fun getStyledVisuals(): StyledVisualSpec = baseVisuals

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
        super<AndroidBaseNativeImpl>.setFocusedState(coerced)
        if (visualsChanged) {
            syncCurrentVisuals()
        }
    }

    override fun isFocusedState(): Boolean = if (!isEnabled() || !isFocusable()) false else focused

    override fun setActivePressedState(active: Boolean) {
        val coerced = if (!isEnabled()) false else active
        this.activePressed = coerced
        nativeWidget.attributes["activePressed"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isActivePressedState(): Boolean = if (!isEnabled()) false else activePressed

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            this.hovered = false
            this.focused = false
            this.activePressed = false
            nativeWidget.attributes["hovered"] = "false"
            nativeWidget.attributes["focused"] = "false"
            nativeWidget.attributes["activePressed"] = "false"
            super<AndroidBaseNativeImpl>.setFocusedState(false)
        }
        syncCurrentVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncCurrentVisuals()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super<AndroidBaseNativeImpl>.applyComponentStyle(style)
        syncCurrentVisuals()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<AndroidBaseNativeImpl>.applyComponentBootstrap(bootstrap)
        syncCurrentVisuals()
    }

    override fun setAttribute(name: String, value: String?) {
        AndroidStyledVisualUtil.recordBackdropAttributeOverride(nativeWidget.attributes, name, value)
        super<AndroidBaseNativeImpl>.setAttribute(name, value)
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = baseComputedStyle,
            snapshot = snapshotForStyledVisuals(),
            darkTheme = isDark
        )
        return AndroidStyledVisualUtil.resolveStateVisuals(
            computedBase = computedBase,
            baseVisuals = baseVisuals,
            computedStyle = lastComputedStyle,
            attributes = nativeWidget.attributes,
            isEnabled = isEnabled(),
            hovered = hovered,
            focused = focused,
            activePressed = activePressed,
            hoverVisuals = hoverVisuals,
            focusVisuals = focusVisuals,
            activeVisuals = activeVisuals,
            disabledVisuals = disabledVisuals
        )
    }

    private class AndroidStyledComponentView(
        context: Context,
        private val implProvider: () -> AndroidStyledComponentImpl?
    ) : View(context) {
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
            val ringColor = focusRingColorArgb
            if (ringColor != null && width > 0 && height > 0) {
                val inset = focusRingWidthPx / 2f
                ringPaint.color = ringColor
                ringPaint.strokeWidth = focusRingWidthPx
                ringRect.set(inset, inset, width - inset, height - inset)
                canvas.drawRoundRect(ringRect, cornerRadiusPx, cornerRadiusPx, ringPaint)
            }
        }
    }
}
