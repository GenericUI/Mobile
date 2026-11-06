package com.nexustools.genericui.android

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.widget.TextView
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
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

    init {
        nativeWidget.attributes["antialiasing"] = "true"
    }

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    fun getStyledVisuals(): StyledVisualSpec = resolveCurrentVisuals()

    protected fun syncVisualsToAndroidView() {
        syncCurrentVisuals()
    }

    private fun syncCurrentVisuals() {
        val current = resolveCurrentVisuals()
        nativeWidget.attributes["styledButtonVisuals"] = current.toString()
        current.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgbaString() }
        current.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
        current.textColor?.let {
            nativeWidget.attributes["textColor"] = it.toCssRgbaString()
            (nativeWidget.androidView as? TextView)?.setTextColor(it.toArgbInt())
        }
        current.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgbaString() }
        current.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
        current.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
        current.opacity?.let { setOpacity(it) }
        current.padding?.let { setPadding(it) }

        val blurPx = current.backdropBlurPx ?: current.backdropFilter?.blur?.resolvePx()
        if (blurPx != null && blurPx > 0f) {
            nativeWidget.attributes["backdropBlurPx"] = blurPx.toString()
            nativeWidget.attributes["androidRenderEffect"] = "blur(${blurPx}px)"
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching {
                    nativeWidget.androidView?.setRenderEffect(
                        RenderEffect.createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                    )
                }
            }
        } else {
            nativeWidget.attributes.remove("backdropBlurPx")
            nativeWidget.attributes.remove("androidRenderEffect")
            if (Build.VERSION.SDK_INT >= 31) {
                runCatching { nativeWidget.androidView?.setRenderEffect(null) }
            }
        }

        if (current.backdropFilter != null) {
            nativeWidget.attributes["backdropFilter"] = current.backdropFilter.toCssString()
        } else {
            nativeWidget.attributes.remove("backdropFilter")
        }

        if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
            nativeWidget.attributes["boxShadowBlurPx"] = current.boxShadowBlurPx.toString()
            nativeWidget.androidView?.elevation = current.boxShadowBlurPx
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
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, w, h)
            current.widthPx?.let { nativeWidget.attributes["widthPx"] = it.toString() }
            current.heightPx?.let { nativeWidget.attributes["heightPx"] = it.toString() }
        }
        nativeWidget.androidView?.background = AndroidStyledContainerImpl.buildGradientDrawable(current)
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
        val coerced = if (!isEnabled()) false else focused
        this.focused = coerced
        nativeWidget.attributes["focused"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isFocusedState(): Boolean = if (!isEnabled()) false else focused

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
            return effective.mergeWith(computedDisabled).mergeWith(disabledVisuals).forBackend(LayoutBackendTarget.ANDROID)
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
