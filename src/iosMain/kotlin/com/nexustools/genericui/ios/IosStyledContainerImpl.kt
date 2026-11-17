@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSNumber
import platform.Foundation.numberWithDouble
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.kCAGradientLayerAxial
import platform.QuartzCore.kCAGradientLayerRadial
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIVisualEffectView
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Full-fidelity CSS aesthetic UIKit container (`StyledContainer`) powered by [UikitFlexContainerView],
 * real `CALayer` borders/radii/shadows, `CAGradientLayer`, and `UIVisualEffectView` blur layers.
 */
class IosStyledContainerImpl(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    classPrefix: String = "styled-container",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UikitFlexContainerView(frame)),
    componentClassPrefix = classPrefix
), StyledContainerNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = false

    private var currentVisuals: StyledVisualSpec = StyledVisualSpec()
    private var gradientLayer: CAGradientLayer? = null
    private var blurEffectView: UIVisualEffectView? = null

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        currentVisuals = visuals.forBackend(layoutTarget)
        applyStyledVisuals()
    }

    override fun getStyledVisuals(): StyledVisualSpec = currentVisuals

    override fun setBackgroundGradient(gradient: Gradient?) {
        super<IosBaseContainerNativeImpl>.setBackgroundGradient(gradient)
        applyGradientLayer(gradient ?: currentVisuals.backgroundGradient)
    }

    private fun applyStyledVisuals() {
        val v = currentVisuals
        v.backgroundColor?.let { uiView.backgroundColor = it.toUiColor() }
        v.borderColor?.let { uiView.layer.borderColor = it.toCgColor() }
        v.borderWidthPx?.let { width ->
            uiView.layer.borderWidth = if (v.borderStyle == CssBorderStyle.NONE) 0.0 else width.toDouble()
        }
        v.borderRadiusPx?.let { radius ->
            uiView.layer.cornerRadius = radius.toDouble()
            uiView.clipsToBounds = radius > 0f
        }
        v.opacity?.let { uiView.alpha = it.coerceIn(0f, 1f).toDouble() }
        v.padding?.let { setPadding(it) }
        v.gapPx?.let { gap -> (uiView as? UikitFlexContainerView)?.itemGapPt = gap.toDouble() }
        v.boxShadowColor?.let { shadowColor ->
            uiView.layer.shadowColor = shadowColor.toCgColor()
            uiView.layer.shadowOpacity = 0.28f
            uiView.layer.shadowRadius = (v.boxShadowBlurPx ?: 6f).toDouble()
            uiView.layer.shadowOffset = CGSizeMake(0.0, 2.0)
        }
        if (v.widthPx != null || v.heightPx != null) {
            uiView.frame.useContents {
                val w = (v.widthPx ?: size.width.toInt()).toDouble()
                val h = (v.heightPx ?: size.height.toInt()).toDouble()
                uiView.setFrame(CGRectMake(origin.x, origin.y, w, h))
            }
        }
        applyGradientLayer(v.backgroundGradient ?: getBackgroundGradient())
        val blurPx = v.backdropBlurPx ?: v.backdropFilter?.blur?.value
        applyBackdropBlur(blurPx)
    }

    private fun applyGradientLayer(gradient: Gradient?) {
        if (gradient == null) {
            gradientLayer?.removeFromSuperlayer()
            gradientLayer = null
            return
        }
        val layer = gradientLayer ?: CAGradientLayer().also {
            uiView.layer.insertSublayer(it, atIndex = 0u)
            gradientLayer = it
        }
        layer.frame = uiView.bounds
        layer.cornerRadius = uiView.layer.cornerRadius
        when (gradient) {
            is LinearGradient -> {
                layer.type = kCAGradientLayerAxial
                layer.colors = gradient.resolvedStops.mapNotNull { it.second.toCgColor() }
                layer.locations = gradient.resolvedStops.map { NSNumber.numberWithDouble(it.first.toDouble()) }
                val rad = (gradient.angleDegrees - 90.0) * PI / 180.0
                val dx = cos(rad) * 0.5
                val dy = sin(rad) * 0.5
                layer.startPoint = CGPointMake(0.5 - dx, 0.5 - dy)
                layer.endPoint = CGPointMake(0.5 + dx, 0.5 + dy)
            }
            is RadialGradient -> {
                layer.type = kCAGradientLayerRadial
                layer.colors = gradient.resolvedStops.mapNotNull { it.second.toCgColor() }
                layer.locations = gradient.resolvedStops.map { NSNumber.numberWithDouble(it.first.toDouble()) }
                layer.startPoint = CGPointMake(0.5, 0.5)
                layer.endPoint = CGPointMake(1.0, 1.0)
            }
            else -> {
                layer.colors = emptyList<Any>()
            }
        }
    }

    private fun applyBackdropBlur(blurPx: Float?) {
        if (blurPx == null || blurPx <= 0f) {
            blurEffectView?.removeFromSuperview()
            blurEffectView = null
            return
        }
        val style = if (UikitGeometryAndColorUtil.isDarkActive()) {
            UIBlurEffectStyle.UIBlurEffectStyleDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleLight
        }
        val effect = UIBlurEffect.effectWithStyle(style)
        val view = blurEffectView ?: UIVisualEffectView(effect = effect).also {
            uiView.insertSubview(it, atIndex = 0L)
            blurEffectView = it
        }
        view.effect = effect
        view.setFrame(uiView.bounds)
        view.layer.cornerRadius = uiView.layer.cornerRadius
        view.clipsToBounds = uiView.clipsToBounds
    }
}
