@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import com.nexustools.genericui.system.style.StyledWidgetVisualController
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIView

internal fun applyIosStyledFilterAttribute(
    name: String,
    value: String?,
    getBase: () -> StyledVisualSpec,
    setBase: (StyledVisualSpec) -> Unit
) {
    when (name.trim().lowercase()) {
        "backdrop-filter", "-webkit-backdrop-filter", "backdropfilter" -> {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
            val parsed = trimmed?.let { CssFilter.parse(it) }
            setBase(
                getBase().copy(
                    backdropBlurPx = parsed?.blur?.resolvePx(),
                    backdropFilter = parsed
                )
            )
        }
        "backdropblurpx", "data-backdrop-blur-px" -> {
            val px = value?.trim()?.removeSuffix("px")?.toFloatOrNull()
            val cur = getBase()
            val nextBf = cur.backdropFilter?.copy(
                blur = px?.takeIf { it > 0f }?.let { CssLength.px(it) }
            )
            setBase(cur.copy(backdropBlurPx = px, backdropFilter = nextBf))
        }
        "filter" -> {
            val trimmed = value?.trim()?.takeIf { it.isNotEmpty() }
            val parsed = trimmed?.let { CssFilter.parse(it) }
            setBase(getBase().copy(filter = parsed))
        }
    }
}

private class UikitStyledComponentView(
    frame: CValue<CGRect>
) : UIView(frame = frame) {
    var onLayoutSubviewsCallback: (() -> Unit)? = null

    override fun layoutSubviews() {
        super.layoutSubviews()
        onLayoutSubviewsCallback?.invoke()
    }

    override fun didMoveToWindow() {
        super.didMoveToWindow()
        onLayoutSubviewsCallback?.invoke()
    }
}

/**
 * Native UIKit `UIView` implementation of [StyledComponentNativeImpl] backed by
 * `:system`'s [StyledWidgetVisualController], `:apple`'s [com.nexustools.genericui.apple.style.AppleStyledSurfaceHelper]
 * (via [IosStyledVisualUtil]), and [UikitBackdropBlurController] supporting all 10 W3C
 * `backdrop-filter` functions and `:hover`, `:focus`, `:active`, `:disabled` pseudo-states.
 */
class IosStyledComponentImpl(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    classPrefix: String = "styled-component",
    tagName: String = "genericui:styled-component",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(UikitStyledComponentView(frame)),
    componentClassPrefix = classPrefix,
    initialTagName = tagName
), StyledComponentNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = true

    private val visualController = StyledWidgetVisualController(
        allowsBootstrapTypeTint = AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix(tagName, classPrefix)
    ).apply {
        componentTagName = tagName
        componentClassPrefix = classPrefix
    }
    private val chromeState = IosStyledVisualUtil.ChromeLayersState()
    internal val backdropController = UikitBackdropBlurController(uiView)

    init {
        (uiView as? UikitStyledComponentView)?.onLayoutSubviewsCallback = {
            refreshChromeAfterLayout()
        }
        actionTarget.onHoveredChanged = { hovered ->
            setHoveredState(hovered)
        }
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        syncControllerMetadata()
        visualController.baseVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getStyledVisuals(): StyledVisualSpec = visualController.baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.hoverVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = visualController.hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.focusVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = visualController.focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.activeVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = visualController.activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.disabledVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getDisabledVisuals(): StyledVisualSpec? = visualController.disabledVisuals

    override fun setHoveredState(hovered: Boolean) {
        super<IosBaseNativeImpl>.setHoveredState(hovered && isEnabled())
        visualController.hovered = isHoveredState()
        applyResolvedVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<IosBaseNativeImpl>.setFocusedState(focused && isEnabled())
        visualController.focused = isFocusedState()
        applyResolvedVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<IosBaseNativeImpl>.setActivePressedState(active && isEnabled())
        visualController.activePressed = isActivePressedState()
        applyResolvedVisuals()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        visualController.enabled = isEnabled()
        if (!enabled) {
            super<IosBaseNativeImpl>.setHoveredState(false)
            super<IosBaseNativeImpl>.setFocusedState(false)
            super<IosBaseNativeImpl>.setActivePressedState(false)
        }
        applyResolvedVisuals()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<IosBaseNativeImpl>.applyComponentBootstrap(bootstrap)
        syncControllerMetadata()
        applyResolvedVisuals()
    }

    override fun onBootstrapAppearanceChanged() {
        syncControllerMetadata()
        applyResolvedVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super<IosBaseNativeImpl>.applyComputedStyle(style)
        visualController.lastComputedStyle = style
        applyResolvedVisuals()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        super<IosBaseNativeImpl>.applyComponentStyle(style)
        visualController.lastComponentStyle = style
        visualController.invalidate()
        applyResolvedVisuals()
    }

    override fun setBackgroundGradient(gradient: Gradient?) {
        super<IosBaseNativeImpl>.setBackgroundGradient(gradient)
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            uiView,
            chromeState.gradientLayer,
            gradient ?: resolveCurrentVisuals().backgroundGradient
        )
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        syncControllerMetadata()
        return visualController.resolveCurrentVisuals(
            isDarkTheme = UikitGeometryAndColorUtil.isDarkActive(),
            backendTarget = layoutTarget,
            animatedOpacity = activeAnimatedOpacityOrNull()
        )
    }

    private fun syncControllerMetadata() {
        visualController.isCustomStyleEngine = isInCustomStyleEngineSubtree()
        visualController.bootstrapSnapshot = rawBootstrapStyleSnapshot()
        visualController.enabled = isEnabled()
        visualController.hovered = isHoveredState()
        visualController.focused = isFocusedState()
        visualController.activePressed = isActivePressedState()
        visualController.lastComponentStyle = rawLayoutComponentStyle
        visualController.lastComputedStyle = rawLayoutComputedStyle
    }

    private fun applyResolvedVisuals() {
        val v = resolveCurrentVisuals()
        IosStyledVisualUtil.applyLayerChrome(
            view = uiView,
            spec = v,
            isFocused = isFocusedState(),
            componentStyle = visualController.lastComponentStyle,
            chromeState = chromeState
        )
        if (activeAnimatedOpacityOrNull() == null) {
            v.opacity?.let { setOpacity(it) }
        }
        v.padding?.let { setPadding(it) }
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            uiView,
            chromeState.gradientLayer,
            v.backgroundGradient ?: getBackgroundGradient()
        )
        backdropController.apply(
            visuals = v,
            cornerRadiusPt = uiView.layer.cornerRadius,
            clipsToBounds = uiView.clipsToBounds,
            effectiveFilter = visualController.resolveEffectiveBackdropFilter(v)
        )
    }

    private fun refreshChromeAfterLayout() {
        val v = resolveCurrentVisuals()
        IosStyledVisualUtil.applyLayerChrome(
            view = uiView,
            spec = v,
            isFocused = isFocusedState(),
            componentStyle = visualController.lastComponentStyle,
            chromeState = chromeState
        )
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            uiView,
            chromeState.gradientLayer,
            v.backgroundGradient ?: getBackgroundGradient()
        )
        backdropController.refreshOnLayoutIfNeeded(
            visuals = v,
            cornerRadiusPt = uiView.layer.cornerRadius,
            clipsToBounds = uiView.clipsToBounds,
            effectiveFilter = visualController.resolveEffectiveBackdropFilter(v)
        )
    }

    override fun setAttribute(name: String, value: String?) {
        super<IosBaseNativeImpl>.setAttribute(name, value)
        applyIosStyledFilterAttribute(
            name = name,
            value = value,
            getBase = { visualController.baseVisuals },
            setBase = { setStyledVisuals(it) }
        )
    }

    override fun dispose() {
        (uiView as? UikitStyledComponentView)?.onLayoutSubviewsCallback = null
        backdropController.clear()
        super.dispose()
    }
}
