package com.nexustools.genericui.android

import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * Android implementation of [StyledButtonNativeImpl] supporting base, `:hover`, `:focus`,
 * `:active`, and `:disabled` [StyledVisualSpec] states via `GradientDrawable`.
 */
class AndroidStyledButtonImpl(
    classPrefix: String = "styled-btn"
) : AndroidButtonImpl(classPrefix), StyledButtonNativeImpl<AndroidViewHandle> {

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

    internal var resolvedStyledIconDrawable: Drawable? = null
        private set

    init {
        nativeWidget.attributes["antialiasing"] = "true"
    }

    override fun createDefaultAndroidView(context: Context): View =
        AndroidStyledButtonView(context) { this }

    override fun syncAndroidView() {
        super.syncAndroidView()
        if (!isSyncingVisuals) {
            syncCurrentVisuals()
        }
    }

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    fun getStyledVisuals(): StyledVisualSpec = resolveCurrentVisuals()

    protected fun syncVisualsToAndroidView() {
        syncCurrentVisuals()
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
        nativeWidget.attributes["styledButtonVisuals"] = current.toString()
        current.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgbaString() }
        current.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
        current.textColor?.let {
            nativeWidget.attributes["textColor"] = it.toCssRgbaString()
            nativeWidget.attributes["iconTint"] = it.toCssRgbaString()
        }
        current.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgbaString() }
        current.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
        current.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
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

        val effectiveFgArgb = (current.textColor ?: RgbaColor.WHITE).toArgbInt()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val tv = nativeWidget.androidView as? TextView ?: return@runCatching
                if (Build.VERSION.SDK_INT >= 31) {
                    tv.setRenderEffect(null)
                }
                if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
                    tv.elevation = AndroidViewHandle.dpToPxFloat(current.boxShadowBlurPx)
                }
                tv.isAllCaps = false
                tv.gravity = Gravity.CENTER
                tv.minWidth = 0
                tv.minimumWidth = 0
                tv.minHeight = 0
                tv.minimumHeight = 0

                if (nativeWidget.layerPassThrough) {
                    tv.setOnClickListener(null)
                    tv.isClickable = false
                } else {
                    tv.isClickable = isEnabled()
                    tv.setOnClickListener {
                        if (isEnabled()) {
                            dispatchClick()
                        }
                    }
                }
                if (!isFocusable()) {
                    applyFocusableStateToView(tv, false)
                }

                tv.backgroundTintList = null
                val gradDrawable = AndroidStyledContainerImpl.buildGradientDrawable(current)
                val styledView = tv as? AndroidStyledButtonView
                if (current.hasBackdropFilter && styledView != null) {
                    tv.background = null
                    styledView.setWillNotDraw(false)
                    val scaledBlurPx = rawBlurPx?.takeIf { it > 0f }?.let { AndroidViewHandle.dpToPxFloat(it) }
                    val rawCornerRadiusPx = (current.borderRadiusPx ?: 0f).coerceAtLeast(0f)
                    styledView.backdropController.backdropBlurPx = scaledBlurPx ?: 0f
                    styledView.backdropController.backdropFilter = current.backdropFilter?.let { f ->
                        if (scaledBlurPx != null) f.copy(blur = CssLength.px(scaledBlurPx)) else f
                    } ?: scaledBlurPx?.let { CssFilter.blur(CssLength.px(it)) }
                    styledView.backdropController.backdropCornerRadiusPx = if (rawCornerRadiusPx > 0f) {
                        AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
                    } else {
                        0f
                    }
                    styledView.backdropController.backdropOverlayDrawable = gradDrawable
                } else {
                    if (styledView != null) {
                        styledView.backdropController.backdropBlurPx = 0f
                        styledView.backdropController.backdropFilter = null
                        styledView.backdropController.backdropCornerRadiusPx = 0f
                        styledView.backdropController.backdropOverlayDrawable = null
                    }
                    tv.background = gradDrawable
                }
                tv.setTextColor(effectiveFgArgb)

                val displayText = nativeWidget.text
                if (tv.text?.toString() != displayText) {
                    tv.text = displayText
                }

                val icon = getIcon()
                val ctx = tv.context ?: AndroidViewHandle.activeContext
                val iconDrawable = if (!icon.isNullOrBlank() && ctx != null) {
                    AndroidImageImpl.resolveDrawable(ctx, icon, effectiveFgArgb)
                } else {
                    null
                }
                if (iconDrawable != null && displayText.isEmpty()) {
                    resolvedStyledIconDrawable = iconDrawable
                    tv.setCompoundDrawables(null, null, null, null)
                } else if (iconDrawable != null) {
                    resolvedStyledIconDrawable = null
                    val iconSizePx = AndroidViewHandle.dpToPx(16)
                    iconDrawable.setBounds(0, 0, iconSizePx, iconSizePx)
                    tv.setCompoundDrawables(iconDrawable, null, null, null)
                    tv.compoundDrawablePadding = AndroidViewHandle.dpToPx(6)
                } else {
                    resolvedStyledIconDrawable = null
                    tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }

                val parentVg = tv.parent
                if (parentVg is AndroidFlexContainerView) {
                    val lp = (tv.layoutParams as? AndroidFlexContainerView.LayoutParams)
                        ?: AndroidFlexContainerView.buildLayoutParamsFor(nativeWidget)
                    AndroidFlexContainerView.applyHandleToLayoutParams(nativeWidget, lp)
                    tv.layoutParams = lp
                    tv.requestLayout()
                }
                tv.invalidate()
                nativeWidget.onViewMutated?.invoke()
            }
        }
    }

    override fun setBaseVisuals(visuals: StyledVisualSpec) {
        baseVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        hoverVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        focusVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        activeVisuals = visuals
        syncCurrentVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        disabledVisuals = visuals
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
        super<AndroidButtonImpl>.setFocusedState(coerced)
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
            super<AndroidButtonImpl>.setFocusedState(false)
        }
        syncCurrentVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super<AndroidButtonImpl>.applyComponentStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<AndroidButtonImpl>.applyComponentBootstrap(bootstrap)
        syncVisualsToAndroidView()
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = getComputedStyle(),
            snapshot = styleSnapshot,
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
}
