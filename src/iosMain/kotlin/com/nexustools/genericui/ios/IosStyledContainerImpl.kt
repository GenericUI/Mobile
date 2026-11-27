@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import com.nexustools.genericui.system.style.StyledWidgetVisualController
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake

/**
 * Full-fidelity CSS aesthetic UIKit container (`StyledContainer`) powered by [UikitFlexContainerView],
 * `:system`'s [StyledWidgetVisualController], `:apple`'s [com.nexustools.genericui.apple.style.AppleStyledSurfaceHelper]
 * (via [IosStyledVisualUtil]), and [UikitBackdropBlurController].
 */
class IosStyledContainerImpl(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    classPrefix: String = "styled-container",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS,
    tagName: String = "div"
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UikitFlexContainerView(frame)),
    componentClassPrefix = classPrefix,
    initialTagName = tagName
), StyledContainerNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = false

    private val visualController = StyledWidgetVisualController(
        allowsBootstrapTypeTint = AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix(tagName, classPrefix)
    ).apply {
        componentTagName = tagName
        componentClassPrefix = classPrefix
    }
    private val chromeState = IosStyledVisualUtil.ChromeLayersState()
    internal val backdropController = UikitBackdropBlurController(uiView)

    init {
        (uiView as? UikitFlexContainerView)?.onLayoutSubviewsCallback = {
            refreshChromeAfterLayout()
        }
        actionTarget.onHoveredChanged = { hovered ->
            setHoveredState(hovered)
        }
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        syncControllerMetadata()
        visualController.baseVisuals = visuals
        applyStyledVisuals()
    }

    override fun getStyledVisuals(): StyledVisualSpec = resolveCurrentVisuals()

    override fun setHoveredState(hovered: Boolean) {
        super<IosBaseContainerNativeImpl>.setHoveredState(hovered && isEnabled())
        visualController.hovered = isHoveredState()
        applyStyledVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<IosBaseContainerNativeImpl>.setFocusedState(focused && isEnabled())
        visualController.focused = isFocusedState()
        applyStyledVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<IosBaseContainerNativeImpl>.setActivePressedState(active && isEnabled())
        visualController.activePressed = isActivePressedState()
        applyStyledVisuals()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        visualController.enabled = isEnabled()
        if (!enabled) {
            super<IosBaseContainerNativeImpl>.setHoveredState(false)
            super<IosBaseContainerNativeImpl>.setFocusedState(false)
            super<IosBaseContainerNativeImpl>.setActivePressedState(false)
        }
        applyStyledVisuals()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<IosBaseContainerNativeImpl>.applyComponentBootstrap(bootstrap)
        syncControllerMetadata()
        applyStyledVisuals()
    }

    override fun onBootstrapAppearanceChanged() {
        syncControllerMetadata()
        applyStyledVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super<IosBaseContainerNativeImpl>.applyComputedStyle(style)
        visualController.lastComputedStyle = style
        applyStyledVisuals()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        super<IosBaseContainerNativeImpl>.applyComponentStyle(style)
        visualController.lastComponentStyle = style
        visualController.invalidate()
        applyStyledVisuals()
    }

    override fun setBackgroundGradient(gradient: Gradient?) {
        super<IosBaseContainerNativeImpl>.setBackgroundGradient(gradient)
        val v = resolveCurrentVisuals()
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            uiView,
            chromeState.gradientLayer,
            gradient ?: v.backgroundGradient
        )
    }

    fun resolveCurrentVisuals(): StyledVisualSpec {
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

    private fun applyStyledVisuals() {
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
        v.gapPx?.let { gap -> (uiView as? UikitFlexContainerView)?.itemGapPt = gap.toDouble() }
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

    override fun dispose() {
        (uiView as? UikitFlexContainerView)?.onLayoutSubviewsCallback = null
        backdropController.clear()
        super.dispose()
    }

    override fun setAttribute(name: String, value: String?) {
        super<IosBaseContainerNativeImpl>.setAttribute(name, value)
        applyIosStyledFilterAttribute(
            name = name,
            value = value,
            getBase = { visualController.baseVisuals },
            setBase = { setStyledVisuals(it) }
        )
    }
}
