@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIImage
import platform.UIKit.UIRectCornerAllCorners
import platform.UIKit.UIBezierPath
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsPushContext
import platform.UIKit.UIGraphicsPopContext
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Offscreen native UIKit `UIButton` (`CGBitmapContext` + `CALayer.renderInContext`) sprite
 * renderer and [ThemeConfig] pixel tinter.
 *
 * - When [BootstrapType] is [BootstrapType.NONE] (or `null`), standard iOS controls use an
 *   unmodified native `UIButton.buttonWithType(UIButtonTypeSystem)`.
 * - When [BootstrapType] is non-`NONE`, an empty-title native UIKit `UIButton` is rendered
 *   into a `CGBitmapContext` via `layer.renderInContext`, pixel-tinted using
 *   [BootstrapColorPalette.resolveColor] from the active [ThemeConfig] while preserving native
 *   bevel/border luminance, and converted into a stretchable `UIImage` applied behind
 *   [BootstrapColorPalette.resolveForegroundOn] title/icon content.
 */
object IosControlTintRenderUtil {

    fun renderEmptyTextTintedButtonImage(
        width: Int,
        height: Int,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean = false,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    ): Pair<UIImage?, IntArray> {
        val pixels = renderEmptyTextTintedButtonPixels(
            width = width,
            height = height,
            type = type,
            darkMode = darkMode,
            outline = outline,
            themeConfig = themeConfig
        )
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val rgbaBytes = ByteArray(w * h * 4)
        for (i in 0 until w * h) {
            val argb = pixels[i]
            val a = (argb ushr 24) and 0xFF
            val r = (argb ushr 16) and 0xFF
            val g = (argb ushr 8) and 0xFF
            val b = argb and 0xFF
            val off = i * 4
            rgbaBytes[off] = ((r * a) / 255).toByte()
            rgbaBytes[off + 1] = ((g * a) / 255).toByte()
            rgbaBytes[off + 2] = ((b * a) / 255).toByte()
            rgbaBytes[off + 3] = a.toByte()
        }
        val image = UikitGeometryAndColorUtil.imageBufferToUiImage(
            ImageBuffer(
                width = w,
                height = h,
                pixelFormat = PixelFormat.RGBA_8888,
                colorSpace = ImageColorSpace.SRGB,
                bytes = rgbaBytes
            )
        )?.resizableImageWithCapInsets(UIEdgeInsetsMake(8.0, 8.0, 8.0, 8.0))
        return image to pixels
    }

    fun renderEmptyTextTintedButtonPixels(
        width: Int,
        height: Int,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean = false,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    ): IntArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        renderEmptySystemButtonToBitmapContext(pixels, w, h, darkMode)
        if (type != BootstrapType.NONE) {
            val tintColor = BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
            tintNativeControlPixels(
                pixels = pixels,
                width = w,
                height = h,
                tint = tintColor,
                outline = outline,
                isLink = type == BootstrapType.LINK,
                darkMode = darkMode
            )
        }
        return pixels
    }

    private fun renderEmptySystemButtonToBitmapContext(
        pixels: IntArray,
        width: Int,
        height: Int,
        darkMode: Boolean
    ) {
        val rgba = ByteArray(width * height * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB()
        var capturedOpaque = false
        if (colorSpace != null) {
            try {
                rgba.usePinned { pinned ->
                    val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
                    val ctx = CGBitmapContextCreate(
                        data = pinned.addressOf(0),
                        width = width.toULong(),
                        height = height.toULong(),
                        bitsPerComponent = 8uL,
                        bytesPerRow = (width * 4).toULong(),
                        space = colorSpace,
                        bitmapInfo = bitmapInfo
                    )
                    if (ctx != null) {
                        try {
                            val config = UIButtonConfiguration.grayButtonConfiguration().apply {
                                title = ""
                            }
                            val offscreenBtn = UIButton.buttonWithConfiguration(config, primaryAction = null)
                            offscreenBtn.setFrame(CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
                            offscreenBtn.setNeedsLayout()
                            offscreenBtn.layoutIfNeeded()
                            offscreenBtn.layer.renderInContext(ctx)

                            val centerOffset = ((height / 2) * width + (width / 2)) * 4
                            val centerAlpha = rgba.getOrNull(centerOffset + 3)?.toInt()?.and(0xFF) ?: 0
                            if (centerAlpha > 16) {
                                capturedOpaque = true
                            } else {
                                UIGraphicsPushContext(ctx)
                                try {
                                    val path = UIBezierPath.bezierPathWithRoundedRect(
                                        CGRectMake(0.5, 0.5, (width - 1).coerceAtLeast(1).toDouble(), (height - 1).coerceAtLeast(1).toDouble()),
                                        cornerRadius = 8.0
                                    )
                                    val fill = if (darkMode) UIColor.colorWithWhite(0.24, 1.0) else UIColor.colorWithWhite(0.94, 1.0)
                                    val stroke = if (darkMode) UIColor.colorWithWhite(0.36, 1.0) else UIColor.colorWithWhite(0.76, 1.0)
                                    fill.setFill()
                                    path.fill()
                                    path.setLineWidth(1.0)
                                    stroke.setStroke()
                                    path.stroke()
                                    capturedOpaque = true
                                } finally {
                                    UIGraphicsPopContext()
                                }
                            }
                        } finally {
                            CGContextRelease(ctx)
                        }
                    }
                }
            } finally {
                CGColorSpaceRelease(colorSpace)
            }
        }

        if (capturedOpaque) {
            for (i in 0 until width * height) {
                val off = i * 4
                val r = rgba[off].toInt() and 0xFF
                val g = rgba[off + 1].toInt() and 0xFF
                val b = rgba[off + 2].toInt() and 0xFF
                val a = rgba[off + 3].toInt() and 0xFF
                if (a > 0) {
                    val ur = ((r * 255) / a).coerceIn(0, 255)
                    val ug = ((g * 255) / a).coerceIn(0, 255)
                    val ub = ((b * 255) / a).coerceIn(0, 255)
                    pixels[i] = (a shl 24) or (ur shl 16) or (ug shl 8) or ub
                } else {
                    pixels[i] = 0
                }
            }
        } else {
            val baseGray = if (darkMode) 56 else 242
            for (i in 0 until width * height) {
                pixels[i] = (0xFF shl 24) or (baseGray shl 16) or (baseGray shl 8) or baseGray
            }
        }
    }

    fun tintNativeControlPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        tint: RgbaColor,
        outline: Boolean,
        isLink: Boolean,
        darkMode: Boolean
    ) {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return
        if (isLink) {
            for (i in pixels.indices) {
                pixels[i] = 0x00000000
            }
            return
        }

        var lumSum = 0.0
        var lumCount = 0
        for (y in 2 until (height - 2).coerceAtLeast(3)) {
            for (x in 2 until (width - 2).coerceAtLeast(3)) {
                val argb = pixels[y * width + x]
                val a = (argb ushr 24) and 0xFF
                if (a > 32) {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    lumSum += (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255.0
                    lumCount++
                }
            }
        }
        val avgLum = if (lumCount > 0) (lumSum / lumCount).toFloat().coerceIn(0.15f, 0.92f) else if (darkMode) 0.25f else 0.85f
        val borderThickness = 2

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) continue

                val r = (argb ushr 16) and 0xFF
                val g = (argb ushr 8) and 0xFF
                val b = argb and 0xFF
                val lum = ((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f).coerceIn(0f, 1f)
                val delta = (lum - avgLum) * 0.55f

                val isBorderBand = x < borderThickness ||
                    x >= width - borderThickness ||
                    y < borderThickness ||
                    y >= height - borderThickness

                if (outline) {
                    if (isBorderBand || abs(lum - avgLum) > 0.14f) {
                        val nr = (tint.r + (delta * 90f).roundToInt()).coerceIn(0, 255)
                        val ng = (tint.g + (delta * 90f).roundToInt()).coerceIn(0, 255)
                        val nb = (tint.b + (delta * 90f).roundToInt()).coerceIn(0, 255)
                        pixels[idx] = (a shl 24) or (nr shl 16) or (ng shl 8) or nb
                    } else {
                        val subtleAlpha = (a * 0.10f).roundToInt().coerceIn(0, 32)
                        pixels[idx] = (subtleAlpha shl 24) or (tint.r shl 16) or (tint.g shl 8) or tint.b
                    }
                } else {
                    val shadeFactor = if (isBorderBand) -0.14f else delta
                    val nr = (tint.r + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val ng = (tint.g + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val nb = (tint.b + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val outA = ((a * tint.a) / 255).coerceIn(0, 255)
                    pixels[idx] = (outA shl 24) or (nr shl 16) or (ng shl 8) or nb
                }
            }
        }
    }

    fun resolveContrastingForeground(
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    ): RgbaColor = when {
        type == BootstrapType.NONE -> {
            val colors = themeConfig.resolveEffectiveColors(darkMode)
            colors.onSurface ?: colors.onBackground ?: if (darkMode) RgbaColor(235, 235, 240) else RgbaColor(28, 28, 30)
        }
        type == BootstrapType.LINK -> BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, darkMode, themeConfig)
        outline -> BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
        else -> BootstrapColorPalette.resolveForegroundOn(type, darkMode, themeConfig)
    }
}
