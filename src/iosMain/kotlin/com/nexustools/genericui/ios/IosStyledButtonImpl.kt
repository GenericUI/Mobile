@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNumber
import platform.Foundation.numberWithDouble
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.kCAGradientLayerAxial
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeCustom
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.setContentEdgeInsets
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-fidelity CSS aesthetic UIKit button (`StyledButton`) wrapping a custom `UIButton`,
 * `CAGradientLayer`, state visuals (`base`, `hover`, `focus`, `active`, `disabled`),
 * shadows, borders, and custom typography styling.
 */
class IosStyledButtonImpl(
    classPrefix: String = "styled-btn",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS,
    val button: UIButton = UIButton.buttonWithType(UIButtonTypeCustom).apply {
        setFrame(CGRectMake(0.0, 0.0, 120.0, 44.0))
    }
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(button),
    componentClassPrefix = classPrefix
), StyledButtonNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = true

    private var buttonText: String = ""
    private var buttonIcon: String? = null
    private var baseVisuals: StyledVisualSpec = StyledVisualSpec()
    private var hoverVisuals: StyledVisualSpec? = null
    private var focusVisuals: StyledVisualSpec? = null
    private var activeVisuals: StyledVisualSpec? = null
    private var disabledVisuals: StyledVisualSpec? = null
    private var gradientLayer: CAGradientLayer? = null

    init {
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
        baseVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        hoverVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        focusVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        activeVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        disabledVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getDisabledVisuals(): StyledVisualSpec? = disabledVisuals

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        applyResolvedVisuals()
    }

    override fun setHoveredState(hovered: Boolean) {
        super<IosBaseNativeImpl>.setHoveredState(hovered)
        applyResolvedVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        super<IosBaseNativeImpl>.setFocusedState(focused)
        applyResolvedVisuals()
    }

    override fun setActivePressedState(active: Boolean) {
        super<IosBaseNativeImpl>.setActivePressedState(active)
        applyResolvedVisuals()
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        var resolved = baseVisuals
        if (!isEnabled() && disabledVisuals != null) {
            return resolved.mergeWith(disabledVisuals)
        }
        if (isHoveredState() && hoverVisuals != null) {
            resolved = resolved.mergeWith(hoverVisuals)
        }
        if (isFocusedState() && focusVisuals != null) {
            resolved = resolved.mergeWith(focusVisuals)
        }
        if (isActivePressedState() && activeVisuals != null) {
            resolved = resolved.mergeWith(activeVisuals)
        }
        return resolved.forBackend(layoutTarget)
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

    private fun applyResolvedVisuals() {
        val v = resolveCurrentVisuals()
        v.backgroundColor?.let { button.backgroundColor = it.toUiColor() }
        v.textColor?.let {
            val fg = it.toUiColor()
            button.setTitleColor(fg, forState = UIControlStateNormal)
            button.tintColor = fg
        }
        v.borderColor?.let { button.layer.borderColor = it.toCgColor() }
        v.borderWidthPx?.let { button.layer.borderWidth = it.toDouble() }
        v.borderRadiusPx?.let {
            button.layer.cornerRadius = it.toDouble()
            button.clipsToBounds = it > 0f
        }
        v.opacity?.let { button.alpha = it.coerceIn(0f, 1f).toDouble() }
        v.padding?.let { p ->
            button.setContentEdgeInsets(
                UIEdgeInsetsMake(p.top.toDouble(), p.left.toDouble(), p.bottom.toDouble(), p.right.toDouble())
            )
        }
        v.boxShadowColor?.let { shadow ->
            button.layer.shadowColor = shadow.toCgColor()
            button.layer.shadowOpacity = 0.25f
            button.layer.shadowRadius = (v.boxShadowBlurPx ?: 6f).toDouble()
            button.layer.shadowOffset = CGSizeMake(0.0, 2.0)
        }
        if (v.widthPx != null || v.heightPx != null) {
            button.frame.useContents {
                val w = (v.widthPx ?: size.width.toInt()).toDouble()
                val h = (v.heightPx ?: size.height.toInt()).toDouble()
                button.setFrame(CGRectMake(origin.x, origin.y, w, h))
            }
        }
        val grad = v.backgroundGradient as? LinearGradient
        if (grad == null) {
            gradientLayer?.removeFromSuperlayer()
            gradientLayer = null
        } else {
            val layer = gradientLayer ?: CAGradientLayer().also {
                button.layer.insertSublayer(it, atIndex = 0u)
                gradientLayer = it
            }
            layer.type = kCAGradientLayerAxial
            layer.frame = button.bounds
            layer.cornerRadius = button.layer.cornerRadius
            layer.colors = grad.resolvedStops.mapNotNull { it.second.toCgColor() }
            layer.locations = grad.resolvedStops.map { NSNumber.numberWithDouble(it.first.toDouble()) }
            val rad = (grad.angleDegrees - 90.0) * PI / 180.0
            val dx = cos(rad) * 0.5
            val dy = sin(rad) * 0.5
            layer.startPoint = CGPointMake(0.5 - dx, 0.5 - dy)
            layer.endPoint = CGPointMake(0.5 + dx, 0.5 + dy)
        }
    }
}
