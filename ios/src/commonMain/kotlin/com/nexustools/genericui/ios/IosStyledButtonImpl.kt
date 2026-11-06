package com.nexustools.genericui.ios

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
 * iOS `UIButton` (`UIButtonConfigurationUpdateHandler` + `CAGradientLayer`) implementation of [StyledButtonNativeImpl].
 */
class IosStyledButtonImpl(
    classPrefix: String = "styled-btn"
) : IosButtonImpl(classPrefix), StyledButtonNativeImpl<IosNativeView> {

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
        nativeWidget.metadata["antialiasing"] = "true"
    }

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    fun getStyledVisuals(): StyledVisualSpec = resolveCurrentVisuals()

    protected fun syncVisualsToIosView() {
        syncCurrentVisuals()
    }

    private fun syncCurrentVisuals() {
        val current = resolveCurrentVisuals()
        nativeWidget.metadata["styledButtonVisuals"] = current.toString()
        current.backgroundColor?.let {
            nativeWidget.backgroundColor = it
            nativeWidget.metadata["backgroundColor"] = it.toCssRgbaString()
        }
        current.backgroundGradient?.let {
            nativeWidget.metadata["backgroundGradient"] = it.toCssString()
        }
        current.textColor?.let {
            nativeWidget.tintColor = it
            nativeWidget.metadata["textColor"] = it.toCssRgbaString()
        }
        current.borderColor?.let {
            nativeWidget.metadata["borderColor"] = it.toCssRgbaString()
        }
        current.borderWidthPx?.let {
            nativeWidget.borderWidth = it
            nativeWidget.metadata["borderWidthPx"] = it.toString()
        }
        current.borderRadiusPx?.let {
            nativeWidget.cornerRadius = it
            nativeWidget.metadata["borderRadiusPx"] = it.toString()
        }
        current.opacity?.let { setOpacity(it) }
        current.padding?.let { setPadding(it) }

        val blurPx = current.backdropBlurPx ?: current.backdropFilter?.blur?.resolvePx()
        if (blurPx != null && blurPx > 0f) {
            nativeWidget.metadata["backdropBlurPx"] = blurPx.toString()
            nativeWidget.metadata["iosBlurEffect"] = "UIBlurEffect"
        } else {
            nativeWidget.metadata.remove("backdropBlurPx")
            nativeWidget.metadata.remove("iosBlurEffect")
        }

        if (current.backdropFilter != null) {
            nativeWidget.metadata["backdropFilter"] = current.backdropFilter.toCssString()
        } else {
            nativeWidget.metadata.remove("backdropFilter")
        }

        if (current.boxShadowBlurPx != null && current.boxShadowBlurPx > 0f) {
            nativeWidget.metadata["boxShadowBlurPx"] = current.boxShadowBlurPx.toString()
            nativeWidget.metadata["shadowRadiusPt"] = current.boxShadowBlurPx.toString()
        } else {
            nativeWidget.metadata.remove("boxShadowBlurPx")
        }
        if (current.boxShadowColor != null) {
            nativeWidget.metadata["boxShadowColor"] = current.boxShadowColor.toCssRgbaString()
        } else {
            nativeWidget.metadata.remove("boxShadowColor")
        }

        if (current.widthPx != null || current.heightPx != null) {
            val cur = nativeWidget.frame
            val w = current.widthPx ?: cur.width
            val h = current.heightPx ?: cur.height
            nativeWidget.frame = Rect(cur.x, cur.y, w, h)
            current.widthPx?.let { nativeWidget.metadata["widthPx"] = it.toString() }
            current.heightPx?.let { nativeWidget.metadata["heightPx"] = it.toString() }
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
        nativeWidget.metadata["hovered"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isHoveredState(): Boolean = if (!isEnabled()) false else hovered

    override fun setFocusedState(focused: Boolean) {
        val coerced = if (!isEnabled()) false else focused
        this.focused = coerced
        nativeWidget.metadata["focused"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isFocusedState(): Boolean = if (!isEnabled()) false else focused

    override fun setActivePressedState(active: Boolean) {
        val coerced = if (!isEnabled()) false else active
        this.activePressed = coerced
        nativeWidget.metadata["activePressed"] = coerced.toString()
        syncCurrentVisuals()
    }

    override fun isActivePressedState(): Boolean = if (!isEnabled()) false else activePressed

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            this.hovered = false
            this.focused = false
            this.activePressed = false
            nativeWidget.metadata["hovered"] = "false"
            nativeWidget.metadata["focused"] = "false"
            nativeWidget.metadata["activePressed"] = "false"
        }
        syncCurrentVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncVisualsToIosView()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super.applyComponentStyle(style)
        syncVisualsToIosView()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super.applyComponentBootstrap(bootstrap)
        syncVisualsToIosView()
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
            return effective.mergeWith(computedDisabled).mergeWith(disabledVisuals).forBackend(LayoutBackendTarget.IOS)
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
        return effective.forBackend(LayoutBackendTarget.IOS)
    }
}
