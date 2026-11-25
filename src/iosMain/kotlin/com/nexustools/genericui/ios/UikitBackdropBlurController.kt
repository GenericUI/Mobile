@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.style.AppleBackdropFilterController
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.StyledVisualSpec
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageView
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationCurve
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewContentMode
import platform.UIKit.UIViewPropertyAnimator
import platform.UIKit.UIVisualEffectView
import kotlin.math.roundToInt

/**
 * Dual-path UIKit `backdrop-filter` controller shared by [IosStyledContainerImpl],
 * [IosStyledComponentImpl], and [IosStyledButtonImpl] (and inherited by iPadOS):
 *
 * - Pure `blur(...)` filters use a live hardware Metal `CABackdropLayer` + `UIVisualEffectView` +
 *   paused `UIViewPropertyAnimator.fractionComplete`.
 * - Non-blur pixel ops (`brightness`, `contrast`, `grayscale`, `hue-rotate`, `invert`, `opacity`,
 *   `saturate`, `sepia`) and `drop-shadow` switch cleanly to `:apple`'s
 *   [AppleBackdropFilterController] `CIContext` CoreImage underlay pipeline covering all 10 W3C
 *   filter functions.
 */
class UikitBackdropBlurController(
    private val hostView: UIView
) {
    private var blurEffectView: UIVisualEffectView? = null
    private var tintOverlayView: UIView? = null
    private var intensityAnimator: UIViewPropertyAnimator? = null
    private var activeStyle: UIBlurEffectStyle? = null

    private var filteredBackdropView: UIImageView? = null
    private var filteredTintOverlayView: UIView? = null
    private var lastAppliedFilter: CssFilter? = null
    private var lastAppliedBackgroundColor: RgbaColor? = null
    private var lastCapturedWidthPt: Double = -1.0
    private var lastCapturedHeightPt: Double = -1.0
    private var capturingUnderlay: Boolean = false

    val isActive: Boolean
        get() = blurEffectView != null || filteredBackdropView != null

    val isUsingCoreImageUnderlay: Boolean
        get() = filteredBackdropView != null

    fun apply(
        visuals: StyledVisualSpec,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean,
        effectiveFilter: CssFilter? = null
    ): Boolean {
        val effFilter = effectiveFilter
            ?: CssFilter.backdropBlurOrFilter(
                visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx(),
                visuals.backdropFilter
            )
        if (effFilter == null) {
            clear()
            return false
        }

        val isDark = UikitGeometryAndColorUtil.isDarkActive()
        lastAppliedFilter = effFilter
        lastAppliedBackgroundColor = visuals.backgroundColor

        if (AppleBackdropFilterController.requiresFullFilterPipeline(effFilter)) {
            return applyCoreImageUnderlay(effFilter, visuals.backgroundColor, cornerRadiusPt, clipsToBounds, isDark)
        }
        return applyNativeUiVisualEffect(effFilter, visuals.backgroundColor, cornerRadiusPt, clipsToBounds, isDark)
    }

    fun refreshOnLayoutIfNeeded(
        visuals: StyledVisualSpec,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean,
        effectiveFilter: CssFilter? = null
    ) {
        if (!isActive || capturingUnderlay) return
        val w = hostView.bounds.useContents { size.width }
        val h = hostView.bounds.useContents { size.height }
        if (w <= 0.0 || h <= 0.0) return
        if (isUsingCoreImageUnderlay && (w != lastCapturedWidthPt || h != lastCapturedHeightPt)) {
            apply(visuals, cornerRadiusPt, clipsToBounds, effectiveFilter)
        } else {
            blurEffectView?.let {
                it.setFrame(hostView.bounds)
                it.layer.cornerRadius = cornerRadiusPt
                it.layer.maskedCorners = hostView.layer.maskedCorners
                it.clipsToBounds = clipsToBounds
            }
            filteredBackdropView?.let {
                it.setFrame(hostView.bounds)
                it.layer.cornerRadius = cornerRadiusPt
                it.layer.maskedCorners = hostView.layer.maskedCorners
                it.clipsToBounds = clipsToBounds
            }
        }
    }

    fun clear() {
        clearNativeBlurEffect()
        clearCoreImageUnderlay()
        lastAppliedFilter = null
        lastAppliedBackgroundColor = null
        lastCapturedWidthPt = -1.0
        lastCapturedHeightPt = -1.0
    }

    private fun applyNativeUiVisualEffect(
        effFilter: CssFilter,
        backgroundColor: RgbaColor?,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean,
        isDark: Boolean
    ): Boolean {
        clearCoreImageUnderlay()

        val style = if (isDark) {
            UIBlurEffectStyle.UIBlurEffectStyleDark
        } else {
            UIBlurEffectStyle.UIBlurEffectStyleLight
        }

        val view = blurEffectView ?: UIVisualEffectView(effect = null).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            hostView.insertSubview(it, atIndex = 0L)
            if (hostView is UikitFlexContainerView) {
                hostView.backdropChromeView = it
            }
            blurEffectView = it
        }

        val w = hostView.bounds.useContents { size.width }
        val h = hostView.bounds.useContents { size.height }
        if (w > 0.0 || h > 0.0) {
            view.setFrame(hostView.bounds)
        }
        view.layer.cornerRadius = cornerRadiusPt
        view.layer.maskedCorners = hostView.layer.maskedCorners
        view.clipsToBounds = clipsToBounds
        view.alpha = (effFilter.opacity ?: 1f).coerceIn(0f, 1f).toDouble()

        val blurPx = (effFilter.blur?.resolvePx() ?: 0f).coerceIn(0f, 48f)
        val targetFraction = if (blurPx > 0f) {
            (blurPx / 24f).coerceIn(0.08f, 1.0f).toDouble()
        } else {
            0.0
        }

        if (blurPx > 0f) {
            if (intensityAnimator == null || activeStyle != style) {
                intensityAnimator?.stopAnimation(true)
                view.effect = null
                val animator = UIViewPropertyAnimator(
                    duration = 1.0,
                    curve = UIViewAnimationCurve.UIViewAnimationCurveLinear,
                    animations = {
                        view.effect = UIBlurEffect.effectWithStyle(style)
                    }
                )
                animator.pausesOnCompletion = true
                animator.pauseAnimation()
                intensityAnimator = animator
                activeStyle = style
            }
            intensityAnimator?.fractionComplete = targetFraction
        } else {
            intensityAnimator?.stopAnimation(true)
            intensityAnimator = null
            activeStyle = null
            view.effect = null
        }

        val overlay = tintOverlayView ?: UIView(frame = view.contentView.bounds).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            view.contentView.addSubview(it)
            tintOverlayView = it
        }
        overlay.setFrame(view.contentView.bounds)
        overlay.backgroundColor = resolveGlassTintColor(effFilter, backgroundColor, isDark)
        hostView.backgroundColor = UIColor.clearColor
        return true
    }

    private fun applyCoreImageUnderlay(
        effFilter: CssFilter,
        backgroundColor: RgbaColor?,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean,
        isDark: Boolean
    ): Boolean {
        clearNativeBlurEffect()

        val imageView = filteredBackdropView ?: UIImageView(frame = hostView.bounds).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            it.contentMode = UIViewContentMode.UIViewContentModeScaleToFill
            hostView.insertSubview(it, atIndex = 0L)
            if (hostView is UikitFlexContainerView) {
                hostView.backdropChromeView = it
            }
            filteredBackdropView = it
        }

        val w = hostView.bounds.useContents { size.width }
        val h = hostView.bounds.useContents { size.height }
        if (w > 0.0 || h > 0.0) {
            imageView.setFrame(hostView.bounds)
        }
        imageView.layer.cornerRadius = cornerRadiusPt
        imageView.layer.maskedCorners = hostView.layer.maskedCorners
        imageView.clipsToBounds = clipsToBounds
        imageView.alpha = 1.0

        val overlay = filteredTintOverlayView ?: UIView(frame = imageView.bounds).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            imageView.addSubview(it)
            filteredTintOverlayView = it
        }
        overlay.setFrame(imageView.bounds)
        overlay.backgroundColor = if (backgroundColor != null && backgroundColor.a > 0) {
            backgroundColor.toUiColor()
        } else {
            UIColor.clearColor
        }
        hostView.backgroundColor = UIColor.clearColor

        if (w > 0.0 && h > 0.0 && !capturingUnderlay) {
            captureAndFilterUnderlay(imageView, effFilter, w, h)
        } else if (w > 0.0 && h > 0.0 && isDark) {
            lastCapturedWidthPt = w
            lastCapturedHeightPt = h
        }
        return true
    }

    private fun captureAndFilterUnderlay(
        targetImageView: UIImageView,
        effFilter: CssFilter,
        widthPt: Double,
        heightPt: Double
    ) {
        var root: UIView = hostView
        while (root.superview != null) {
            root = root.superview!!
        }
        if (root === hostView) {
            lastCapturedWidthPt = widthPt
            lastCapturedHeightPt = heightPt
            return
        }

        capturingUnderlay = true
        val prevHidden = hostView.hidden
        try {
            hostView.hidden = true
            val originInRoot = hostView.convertPoint(CGPointMake(0.0, 0.0), toView = root)
            val ox = originInRoot.useContents { x }
            val oy = originInRoot.useContents { y }

            UIGraphicsBeginImageContextWithOptions(CGSizeMake(widthPt, heightPt), false, 1.0)
            try {
                val ctx = UIGraphicsGetCurrentContext()
                if (ctx != null) {
                    CGContextTranslateCTM(ctx, -ox, -oy)
                    root.layer.renderInContext(ctx)
                }
                val capturedCg = UIGraphicsGetImageFromCurrentImageContext()?.CGImage
                if (capturedCg != null) {
                    val pxW = widthPt.roundToInt().coerceAtLeast(1)
                    val pxH = heightPt.roundToInt().coerceAtLeast(1)
                    val filteredCg = AppleBackdropFilterController.renderFilteredCgImage(
                        source = capturedCg,
                        filter = effFilter,
                        width = pxW,
                        height = pxH
                    )
                    if (filteredCg != null) {
                        try {
                            targetImageView.image = UIImage.imageWithCGImage(filteredCg)
                        } finally {
                            CGImageRelease(filteredCg)
                        }
                    }
                }
            } finally {
                UIGraphicsEndImageContext()
            }
            lastCapturedWidthPt = widthPt
            lastCapturedHeightPt = heightPt
        } finally {
            hostView.hidden = prevHidden
            capturingUnderlay = false
        }
    }

    private fun clearNativeBlurEffect() {
        intensityAnimator?.stopAnimation(true)
        intensityAnimator = null
        activeStyle = null
        tintOverlayView?.removeFromSuperview()
        tintOverlayView = null
        val existing = blurEffectView
        if (hostView is UikitFlexContainerView && hostView.backdropChromeView === existing) {
            hostView.backdropChromeView = null
        }
        existing?.removeFromSuperview()
        blurEffectView = null
    }

    private fun clearCoreImageUnderlay() {
        filteredTintOverlayView?.removeFromSuperview()
        filteredTintOverlayView = null
        val existing = filteredBackdropView
        if (hostView is UikitFlexContainerView && hostView.backdropChromeView === existing) {
            hostView.backdropChromeView = null
        }
        existing?.removeFromSuperview()
        filteredBackdropView = null
    }

    private fun resolveGlassTintColor(
        filter: CssFilter,
        backgroundColor: RgbaColor?,
        isDark: Boolean
    ): UIColor {
        var tint = backgroundColor ?: RgbaColor(0, 0, 0, 0)
        val br = filter.brightness
        if (br != null && br != 1f && tint.a == 0) {
            tint = if (br > 1f) {
                RgbaColor(255, 255, 255, ((br - 1f).coerceIn(0f, 1f) * 72f).roundToInt())
            } else {
                RgbaColor(0, 0, 0, ((1f - br).coerceIn(0f, 1f) * 140f).roundToInt())
            }
        }
        val sat = filter.saturate
        if (sat != null && sat > 1f && tint.a == 0) {
            val boostAlpha = (((sat - 1f).coerceIn(0f, 2f)) * (if (isDark) 18f else 14f)).roundToInt()
            if (boostAlpha > 0) {
                tint = if (isDark) RgbaColor(32, 38, 48, boostAlpha) else RgbaColor(255, 255, 255, boostAlpha)
            }
        }
        return if (tint.a > 0) tint.toUiColor() else UIColor.clearColor
    }
}
