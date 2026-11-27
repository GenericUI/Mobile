@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.system.style.StyledWidgetVisualController
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.setContentEdgeInsets

private class UikitStyledButtonView(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 120.0, 44.0)
) : UIButton(frame = frame) {
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
 * Full-fidelity CSS aesthetic UIKit button (`StyledButton`) wrapping a custom `UIButton` with
 * `:system`'s [StyledWidgetVisualController], `:apple`'s [com.nexustools.genericui.apple.style.AppleStyledSurfaceHelper]
 * (via [IosStyledVisualUtil]), normalized linear/radial gradients, state visuals (`base`, `hover`,
 * `focus`, `active`, `disabled`), shadows, borders, and [UikitBackdropBlurController].
 */
class IosStyledButtonImpl(
    classPrefix: String = "styled-btn",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS,
    val button: UIButton = UikitStyledButtonView()
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(button),
    componentClassPrefix = classPrefix
), StyledButtonNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = true

    private var buttonText: String = ""
    private var buttonIcon: String? = null
    private val visualController = StyledWidgetVisualController(
        allowsBootstrapTypeTint = true
    ).apply {
        componentClassPrefix = classPrefix
    }
    private val chromeState = IosStyledVisualUtil.ChromeLayersState()
    internal val backdropController = UikitBackdropBlurController(button)

    init {
        (button as? UikitStyledButtonView)?.onLayoutSubviewsCallback = {
            refreshChromeAfterLayout()
        }
        button.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        actionTarget.onHoveredChanged = { hovered ->
            setHoveredState(hovered)
        }
    }

    override fun setText(text: String) {
        buttonText = text
        button.setTitle(text, forState = UIControlStateNormal)
    }

    override fun getText(): String = buttonText

    override fun setIcon(icon: String?) {
        buttonIcon = icon
        button.setImage(UikitGeometryAndColorUtil.systemIconImage(icon), forState = UIControlStateNormal)
    }

    override fun getIcon(): String? = buttonIcon

    override fun isIconOnly(): Boolean = buttonText.isEmpty() && !buttonIcon.isNullOrEmpty()

    override fun setBaseVisuals(visuals: StyledVisualSpec) {
        syncControllerMetadata()
        visualController.baseVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = visualController.baseVisuals

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
            button,
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

    override fun applyTypographyFromStyle(style: ComputedStyle) {
        val font = UikitGeometryAndColorUtil.resolveFont(
            sizePt = style.fontSizePx?.toDouble() ?: 15.0,
            weight = getFontWeight(),
            weightNumeric = style.fontWeightNumeric,
            fontFamily = style.fontFamily,
            italic = UikitGeometryAndColorUtil.fontWeightToCssItalic(style.fontStyle)
        )
        button.titleLabel?.font = font
        style.textColor?.let {
            val c = it.toUiColor()
            button.setTitleColor(c, forState = UIControlStateNormal)
            button.tintColor = c
        }
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
            view = button,
            spec = v,
            isFocused = isFocusedState(),
            componentStyle = visualController.lastComponentStyle,
            chromeState = chromeState
        )
        v.textColor?.let {
            val fg = it.toUiColor()
            button.setTitleColor(fg, forState = UIControlStateNormal)
            button.tintColor = fg
        }
        if (activeAnimatedOpacityOrNull() == null) {
            v.opacity?.let { setOpacity(it) }
        }
        v.padding?.let { p ->
            button.setContentEdgeInsets(
                UIEdgeInsetsMake(p.top.toDouble(), p.left.toDouble(), p.bottom.toDouble(), p.right.toDouble())
            )
        }
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            button,
            chromeState.gradientLayer,
            v.backgroundGradient ?: getBackgroundGradient()
        )
        backdropController.apply(
            visuals = v,
            cornerRadiusPt = button.layer.cornerRadius,
            clipsToBounds = button.clipsToBounds,
            effectiveFilter = visualController.resolveEffectiveBackdropFilter(v)
        )
    }

    private fun refreshChromeAfterLayout() {
        val v = resolveCurrentVisuals()
        IosStyledVisualUtil.applyLayerChrome(
            view = button,
            spec = v,
            isFocused = isFocusedState(),
            componentStyle = visualController.lastComponentStyle,
            chromeState = chromeState
        )
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            button,
            chromeState.gradientLayer,
            v.backgroundGradient ?: getBackgroundGradient()
        )
        backdropController.refreshOnLayoutIfNeeded(
            visuals = v,
            cornerRadiusPt = button.layer.cornerRadius,
            clipsToBounds = button.clipsToBounds,
            effectiveFilter = visualController.resolveEffectiveBackdropFilter(v)
        )
    }

    override fun setAttribute(name: String, value: String?) {
        super<IosBaseNativeImpl>.setAttribute(name, value)
        applyIosStyledFilterAttribute(
            name = name,
            value = value,
            getBase = { getBaseVisuals() },
            setBase = { setBaseVisuals(it) }
        )
    }

    override fun dispose() {
        (button as? UikitStyledButtonView)?.onLayoutSubviewsCallback = null
        backdropController.clear()
        super.dispose()
    }
}
