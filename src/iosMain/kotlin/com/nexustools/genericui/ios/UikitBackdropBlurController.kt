@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.style.AppleBackdropFilterController
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.StyledVisualSpec
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGImageRetain
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIImage
import platform.Foundation.CFBridgingRelease
import platform.QuartzCore.CATransaction
import platform.QuartzCore.kCAGravityResize
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIScreen
import platform.UIKit.UIView
import platform.UIKit.UIViewAutoresizingFlexibleHeight
import platform.UIKit.UIViewAutoresizingFlexibleWidth
import kotlin.math.roundToInt

/**
 * Metal GPU CoreImage `backdrop-filter` controller shared by [IosStyledContainerImpl],
 * [IosStyledComponentImpl], and [IosStyledButtonImpl] (and inherited by iPadOS).
 *
 * Routes all 10 W3C CSS `backdrop-filter` functions (`blur`, `brightness`, `contrast`,
 * `grayscale`, `hue-rotate`, `invert`, `opacity`, `saturate`, `sepia`, `drop-shadow`)
 * through `:apple`'s [AppleBackdropFilterController] Metal-backed `CIContext` using
 * preceding-paint-order isolation, halo-padded capture at native display scale, and
 * ping-pong BGRA `IOSurface` (with `CGImage` fallback) presentation.
 */
class UikitBackdropBlurController(
    private val hostView: UIView
) {
    private var filteredBackdropView: UIView? = null
    private var filteredTintOverlayView: UIView? = null
    private var lastAppliedFilter: CssFilter? = null
    private var lastAppliedBackgroundColor: RgbaColor? = null
    private var lastCapturedFilter: CssFilter? = null
    private var lastCapturedOriginX: Double = Double.NaN
    private var lastCapturedOriginY: Double = Double.NaN
    private var lastCapturedWidthPt: Double = -1.0
    private var lastCapturedHeightPt: Double = -1.0
    private var needsCaptureRetry: Boolean = false
    private var capturingUnderlay: Boolean = false

    private val outputPool = AppleBackdropFilterController.IoSurfacePingPongPool()

    val isActive: Boolean
        get() = filteredBackdropView != null

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
        if (effFilter == null || effFilter.isNone || !effFilter.hasActiveEffects) {
            clear()
            return false
        }

        lastAppliedFilter = effFilter
        lastAppliedBackgroundColor = visuals.backgroundColor
        return applyCoreImageUnderlay(effFilter, visuals.backgroundColor, cornerRadiusPt, clipsToBounds)
    }

    fun refreshOnLayoutIfNeeded(
        visuals: StyledVisualSpec,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean,
        effectiveFilter: CssFilter? = null
    ) {
        if (!isActive || capturingUnderlay) return
        val effFilter = effectiveFilter
            ?: CssFilter.backdropBlurOrFilter(
                visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx(),
                visuals.backdropFilter
            )
        if (effFilter == null || effFilter.isNone || !effFilter.hasActiveEffects) {
            clear()
            return
        }

        val w = hostView.bounds.useContents { size.width }
        val h = hostView.bounds.useContents { size.height }
        filteredBackdropView?.let { backdropView ->
            backdropView.setFrame(hostView.bounds)
            backdropView.layer.cornerRadius = cornerRadiusPt
            backdropView.layer.maskedCorners = hostView.layer.maskedCorners
            backdropView.layer.zPosition = -1000.0
            backdropView.clipsToBounds = clipsToBounds
            filteredTintOverlayView?.let { tint ->
                tint.setFrame(backdropView.bounds)
                tint.backgroundColor = if (visuals.backgroundColor != null && visuals.backgroundColor.a > 0) {
                    visuals.backgroundColor.toUiColor()
                } else {
                    UIColor.clearColor
                }
            }
            hostView.backgroundColor = UIColor.clearColor
        }
        if (w <= 0.0 || h <= 0.0) {
            needsCaptureRetry = true
            return
        }

        val root = resolveRootView()
        if (root === hostView) {
            needsCaptureRetry = true
            return
        }
        val originInRoot = hostView.convertPoint(CGPointMake(0.0, 0.0), toView = root)
        val ox = originInRoot.useContents { x }
        val oy = originInRoot.useContents { y }
        if (needsCaptureRetry ||
            lastCapturedFilter != effFilter ||
            ox != lastCapturedOriginX ||
            oy != lastCapturedOriginY ||
            w != lastCapturedWidthPt ||
            h != lastCapturedHeightPt ||
            filteredBackdropView?.layer?.contents == null
        ) {
            apply(visuals, cornerRadiusPt, clipsToBounds, effFilter)
        }
    }

    fun clear() {
        clearCoreImageUnderlay()
        lastAppliedFilter = null
        lastAppliedBackgroundColor = null
        lastCapturedFilter = null
        lastCapturedOriginX = Double.NaN
        lastCapturedOriginY = Double.NaN
        lastCapturedWidthPt = -1.0
        lastCapturedHeightPt = -1.0
        needsCaptureRetry = false
    }

    private fun applyCoreImageUnderlay(
        effFilter: CssFilter,
        backgroundColor: RgbaColor?,
        cornerRadiusPt: Double,
        clipsToBounds: Boolean
    ): Boolean {
        val backdropView = filteredBackdropView ?: UIView(frame = hostView.bounds).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            it.layer.contentsGravity = kCAGravityResize
            it.layer.zPosition = -1000.0
            hostView.insertSubview(it, atIndex = 0L)
            if (hostView is UikitFlexContainerView) {
                hostView.backdropChromeView = it
            }
            filteredBackdropView = it
        }

        val w = hostView.bounds.useContents { size.width }
        val h = hostView.bounds.useContents { size.height }
        if (w > 0.0 || h > 0.0) {
            backdropView.setFrame(hostView.bounds)
        }
        if (backdropView.superview !== hostView) {
            hostView.insertSubview(backdropView, atIndex = 0L)
        }
        backdropView.layer.zPosition = -1000.0
        backdropView.layer.cornerRadius = cornerRadiusPt
        backdropView.layer.maskedCorners = hostView.layer.maskedCorners
        backdropView.clipsToBounds = clipsToBounds
        backdropView.alpha = 1.0

        val overlay = filteredTintOverlayView ?: UIView(frame = backdropView.bounds).also {
            it.userInteractionEnabled = false
            it.autoresizingMask = UIViewAutoresizingFlexibleWidth or UIViewAutoresizingFlexibleHeight
            backdropView.addSubview(it)
            filteredTintOverlayView = it
        }
        overlay.setFrame(backdropView.bounds)
        overlay.backgroundColor = if (backgroundColor != null && backgroundColor.a > 0) {
            backgroundColor.toUiColor()
        } else {
            UIColor.clearColor
        }
        hostView.backgroundColor = UIColor.clearColor

        if (w > 0.0 && h > 0.0 && !capturingUnderlay) {
            captureAndFilterUnderlay(backdropView, effFilter, w, h)
        } else if (w <= 0.0 || h <= 0.0) {
            needsCaptureRetry = true
        }
        return true
    }

    private fun resolveRootView(): UIView {
        var root: UIView = hostView
        while (root.superview != null) {
            root = root.superview!!
        }
        return root
    }

    private fun captureAndFilterUnderlay(
        targetView: UIView,
        effFilter: CssFilter,
        widthPt: Double,
        heightPt: Double
    ) {
        val root = resolveRootView()
        if (root === hostView) {
            needsCaptureRetry = true
            return
        }

        val rectInRoot = hostView.convertRect(hostView.bounds, toView = root)
        val (rx, ry, rw, rh) = rectInRoot.useContents {
            doubleArrayOf(origin.x, origin.y, size.width, size.height)
        }
        val (rootX, rootY, rootW, rootH) = root.bounds.useContents {
            doubleArrayOf(origin.x, origin.y, size.width, size.height)
        }
        val padPt = AppleBackdropFilterController.computeBackdropPaddingPt(effFilter)
        val capX0 = maxOf(rootX, rx - padPt)
        val capY0 = maxOf(rootY, ry - padPt)
        val capX1 = minOf(rootX + rootW, rx + rw + padPt)
        val capY1 = minOf(rootY + rootH, ry + rh + padPt)
        val capW = capX1 - capX0
        val capH = capY1 - capY0
        if (capW <= 0.0 || capH <= 0.0) {
            needsCaptureRetry = true
            return
        }

        val scale = (hostView.window?.screen ?: UIScreen.mainScreen).scale.coerceAtLeast(1.0)
        capturingUnderlay = true
        val hiddenViews = ArrayList<UIView>()
        fun tempHide(v: UIView) {
            if (!v.hidden) {
                v.hidden = true
                hiddenViews.add(v)
            }
        }
        try {
            tempHide(hostView)
            tempHide(targetView)
            // Preceding-paint-order isolation: hide all siblings/branches painted AFTER hostView
            var cur: UIView = hostView
            while (cur !== root) {
                val parent = cur.superview ?: break
                val subs = parent.subviews
                val curIdx = subs.indexOf(cur)
                val curZ = cur.layer.zPosition
                for (i in subs.indices) {
                    val sib = subs[i] as? UIView ?: continue
                    if (sib === cur) continue
                    val sibZ = sib.layer.zPosition
                    if (sibZ > curZ || (sibZ == curZ && i > curIdx)) {
                        tempHide(sib)
                    }
                }
                cur = parent
            }

            UIGraphicsBeginImageContextWithOptions(CGSizeMake(capW, capH), false, scale)
            try {
                val ctx = UIGraphicsGetCurrentContext()
                if (ctx != null) {
                    CGContextTranslateCTM(ctx, -capX0, -capY0)
                    root.layer.renderInContext(ctx)
                }
                val capturedCg = UIGraphicsGetImageFromCurrentImageContext()?.CGImage
                if (capturedCg != null) {
                    val capPxH = CGImageGetHeight(capturedCg).toDouble()
                    val viewPxW = (widthPt * scale).roundToInt().coerceAtLeast(1)
                    val viewPxH = (heightPt * scale).roundToInt().coerceAtLeast(1)
                    val relLeftPx = (rx - capX0) * scale
                    val relTopPx = (ry - capY0) * scale
                    val cropCiRect = CGRectMake(
                        relLeftPx,
                        capPxH - (relTopPx + viewPxH.toDouble()),
                        viewPxW.toDouble(),
                        viewPxH.toDouble()
                    )
                    val ciInput = CIImage.imageWithCGImage(capturedCg).imageByClampingToExtent()
                    val filteredCi = AppleBackdropFilterController.renderFilteredCiImage(
                        source = ciInput,
                        filter = effFilter,
                        cropRectPx = cropCiRect,
                        scaleFactor = scale,
                        yPointsUp = true
                    )
                    val outBounds = CGRectMake(0.0, 0.0, viewPxW.toDouble(), viewPxH.toDouble())
                    val outSurface = outputPool.acquire(viewPxW, viewPxH)
                    var applied = false
                    if (outSurface != null &&
                        AppleBackdropFilterController.renderCiImageToIoSurface(filteredCi, outBounds, outSurface)
                    ) {
                        CATransaction.begin()
                        CATransaction.setDisableActions(true)
                        try {
                            targetView.layer.contentsScale = scale
                            targetView.layer.contents = AppleBackdropFilterController.ioSurfaceToLayerContents(outSurface)
                        } finally {
                            CATransaction.commit()
                        }
                        applied = true
                    }
                    if (!applied) {
                        val filteredCg = AppleBackdropFilterController.renderFilteredCiImageToCgImage(
                            ciImage = filteredCi,
                            cropRectPx = outBounds
                        )
                        if (filteredCg != null) {
                            try {
                                CATransaction.begin()
                                CATransaction.setDisableActions(true)
                                try {
                                    targetView.layer.contentsScale = scale
                                    targetView.layer.contents = CFBridgingRelease(CGImageRetain(filteredCg))
                                } finally {
                                    CATransaction.commit()
                                }
                            } finally {
                                CGImageRelease(filteredCg)
                            }
                        }
                    }
                    lastCapturedFilter = effFilter
                    lastCapturedOriginX = rx
                    lastCapturedOriginY = ry
                    lastCapturedWidthPt = widthPt
                    lastCapturedHeightPt = heightPt
                    needsCaptureRetry = false
                }
            } finally {
                UIGraphicsEndImageContext()
            }
        } finally {
            for (i in hiddenViews.lastIndex downTo 0) {
                hiddenViews[i].hidden = false
            }
            capturingUnderlay = false
        }
    }

    private fun clearCoreImageUnderlay() {
        filteredTintOverlayView?.removeFromSuperview()
        filteredTintOverlayView = null
        val existing = filteredBackdropView
        if (hostView is UikitFlexContainerView && hostView.backdropChromeView === existing) {
            hostView.backdropChromeView = null
        }
        existing?.layer?.contents = null
        existing?.removeFromSuperview()
        filteredBackdropView = null
        outputPool.releaseAll()
    }
}
