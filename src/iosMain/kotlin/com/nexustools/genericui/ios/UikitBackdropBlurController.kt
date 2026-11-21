@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.StyledVisualSpec
import kotlinx.cinterop.useContents
import platform.UIKit.UIBlurEffect
import platform.UIKit.UIBlurEffectStyle
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationCurve
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import platform.UIKit.UIViewPropertyAnimator
import platform.UIKit.UIVisualEffectView
import kotlin.math.roundToInt

/**
 * Hardware Metal `CABackdropLayer` + `UIVisualEffectView` + paused `UIViewPropertyAnimator`
 * `fractionComplete` blur controller shared by [IosStyledContainerImpl] and [IosStyledButtonImpl]
 * (and inherited by iPadOS).
 */
class UikitBackdropBlurController(
    private val hostView: UIView
) {
    private var blurEffectView: UIVisualEffectView? = null
    private var tintOverlayView: UIView? = null
    private var intensityAnimator: UIViewPropertyAnimator? = null
    private var activeStyle: UIBlurEffectStyle? = null

    val isActive: Boolean
        get() = blurEffectView != null

    fun apply(
        visuals: StyledVisualSpec,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean
    ): Boolean {
        if (!visuals.hasBackdropFilter) {
            clear()
            return false
        }
        val effFilter = CssFilter.backdropBlurOrFilter(
            visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx(),
            visuals.backdropFilter
        )
        if (effFilter == null) {
            clear()
            return false
        }

        val isDark = UikitGeometryAndColorUtil.isDarkActive()
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
        view.clipsToBounds = clipsToBounds
        view.alpha = (effFilter.opacity ?: 1f).coerceIn(0f, 1f).toDouble()

        val blurPx = (effFilter.blur?.resolvePx() ?: visuals.backdropBlurPx ?: 0f).coerceIn(0f, 48f)
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
        overlay.backgroundColor = resolveFilterAndTintOverlayColor(effFilter, visuals.backgroundColor, isDark)
        // Host background must remain clear while backdrop glass is active so UIBlurEffect
        // samples behind the view and tintOverlayView applies translucent tint above it.
        hostView.backgroundColor = UIColor.clearColor
        return true
    }

    fun clear() {
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

    private fun resolveFilterAndTintOverlayColor(
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
