@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.style.AppleStyledSurfaceHelper
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGRectMake
import platform.QuartzCore.CAGradientLayer
import platform.QuartzCore.CAShapeLayer
import platform.UIKit.UIView

/**
 * Shared UIKit visual styling adapter delegating surface chrome, per-corner radii, border
 * styles (`SOLID`/`DASHED`/`DOTTED`/`DOUBLE`), normalized linear/radial gradients, box shadows,
 * and focus rings to `:apple`'s [AppleStyledSurfaceHelper].
 */
internal object IosStyledVisualUtil {

    internal class ChromeLayersState {
        var gradientLayer: CAGradientLayer? = null
        var borderLayer: CAShapeLayer? = null
        var focusRingLayer: CAShapeLayer? = null
    }

    fun applyLayerChrome(
        view: UIView,
        spec: StyledVisualSpec,
        isFocused: Boolean = false,
        componentStyle: ComponentStyle? = null,
        chromeState: ChromeLayersState? = null
    ) {
        spec.backgroundColor?.let { view.backgroundColor = it.toUiColor() }
        if (spec.widthPx != null || spec.heightPx != null) {
            val savedTransform = view.transform
            view.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
            view.frame.useContents {
                val w = (spec.widthPx ?: size.width.toInt()).toDouble()
                val h = (spec.heightPx ?: size.height.toInt()).toDouble()
                view.setFrame(CGRectMake(origin.x, origin.y, w, h))
            }
            view.transform = savedTransform
        }

        val boundsW = view.bounds.useContents { size.width }
        val boundsH = view.bounds.useContents { size.height }
        val cornerRadii = componentStyle?.getBorderRadius()
        val fallbackRadius = spec.borderRadiusPx ?: 0f
        val clipsHint = if (cornerRadii != null || spec.borderRadiusPx != null) {
            val resolvedRadii = AppleStyledSurfaceHelper.resolveCornerRadiiPx(
                radii = cornerRadii,
                fallbackUniformPx = fallbackRadius,
                width = boundsW,
                height = boundsH
            )
            resolvedRadii.any { it > 0.0 }
        } else {
            null
        }
        if (clipsHint != null) {
            view.clipsToBounds = clipsHint
        }
        if (boundsW > 0.0 && boundsH > 0.0) {
            AppleStyledSurfaceHelper.applyLayerCornerRadii(
                layer = view.layer,
                radii = cornerRadii,
                fallbackUniformPx = fallbackRadius,
                boundsWidth = boundsW,
                boundsHeight = boundsH,
                clipsToBounds = clipsHint,
                topLeftIsMinY = true
            )
        } else if (cornerRadii != null || spec.borderRadiusPx != null) {
            val tl = (cornerRadii?.topLeft?.resolvePx(referencePx = 0f) ?: fallbackRadius).coerceAtLeast(0f).toDouble()
            val tr = (cornerRadii?.topRight?.resolvePx(referencePx = 0f) ?: fallbackRadius).coerceAtLeast(0f).toDouble()
            val br = (cornerRadii?.bottomRight?.resolvePx(referencePx = 0f) ?: fallbackRadius).coerceAtLeast(0f).toDouble()
            val bl = (cornerRadii?.bottomLeft?.resolvePx(referencePx = 0f) ?: fallbackRadius).coerceAtLeast(0f).toDouble()
            val preRadius = maxOf(tl, maxOf(tr, maxOf(br, bl)))
            view.layer.cornerRadius = preRadius
            if (preRadius > 0.0) {
                view.clipsToBounds = true
                view.layer.masksToBounds = true
            }
        } else {
            AppleStyledSurfaceHelper.applyLayerCornerRadii(
                layer = view.layer,
                radii = null,
                fallbackUniformPx = 0f,
                boundsWidth = 0.0,
                boundsHeight = 0.0,
                clipsToBounds = clipsHint,
                topLeftIsMinY = true
            )
        }

        val focusRingColor = spec.focusRingColor.takeIf { isFocused }
        val borderColor = focusRingColor ?: spec.borderColor
        val targetBorderWidth = when {
            focusRingColor != null -> (spec.focusRingWidthPx ?: 2f).coerceAtLeast(1f)
            spec.borderWidthPx != null -> spec.borderWidthPx
            else -> 0f
        }
        val nextBorder = AppleStyledSurfaceHelper.applyBorderLayer(
            hostLayer = view.layer,
            existingBorderLayer = chromeState?.borderLayer,
            boundsWidth = boundsW,
            boundsHeight = boundsH,
            borderColor = borderColor,
            borderWidthPx = targetBorderWidth,
            borderStyle = spec.borderStyle,
            radii = cornerRadii,
            fallbackUniformPx = fallbackRadius,
            topLeftIsMinY = true
        )
        chromeState?.borderLayer = nextBorder

        if (chromeState != null) {
            chromeState.focusRingLayer = AppleStyledSurfaceHelper.applyFocusRingLayer(
                hostLayer = view.layer,
                existingRingLayer = chromeState.focusRingLayer,
                isFocused = isFocused && spec.focusRingColor != null && spec.borderColor != null,
                ringColor = spec.focusRingColor,
                ringWidthPx = spec.focusRingWidthPx,
                boundsWidth = boundsW,
                boundsHeight = boundsH,
                radii = cornerRadii,
                fallbackUniformPx = fallbackRadius,
                topLeftIsMinY = true
            )
        }

        val firstShadow = componentStyle?.getBoxShadow()?.shadows?.firstOrNull()
        AppleStyledSurfaceHelper.applyLayerShadow(
            layer = view.layer,
            shadow = firstShadow,
            fallbackColor = spec.boxShadowColor,
            fallbackBlurPx = spec.boxShadowBlurPx,
            topLeftIsMinY = true
        )
    }

    fun applyGradientLayer(
        view: UIView,
        existingLayer: CAGradientLayer?,
        gradient: Gradient?
    ): CAGradientLayer? {
        val boundsW = view.bounds.useContents { size.width }
        val boundsH = view.bounds.useContents { size.height }
        return AppleStyledSurfaceHelper.applyGradientLayer(
            hostLayer = view.layer,
            existingLayer = existingLayer,
            gradient = gradient,
            boundsWidth = boundsW,
            boundsHeight = boundsH,
            cornerRadius = view.layer.cornerRadius,
            maskedCorners = view.layer.maskedCorners,
            topLeftIsMinY = true
        )
    }
}
