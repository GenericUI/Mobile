package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.roundToInt

/**
 * Offscreen empty-text native iOS `UIButton` (`CGBitmapContext`) sprite renderer and `ThemeConfig`
 * pixel tinter.
 *
 * - When [BootstrapType] is [BootstrapType.NONE] (or `null`), standard iOS buttons use the 100%
 *   unmodified native UIKit `UIButton` (`UIButtonTypeSystem`).
 * - When [BootstrapType] is non-`NONE`, the native `UIButton` is rendered with empty title (`""`)
 *   into an offscreen bitmap context (`IntArray` ARGB pixels), the pixels are color-tinted with
 *   [BootstrapColorPalette.resolveColor] from the active [ThemeConfig] while preserving native
 *   button bevel/border/highlight shading, and the contrasting title
 *   ([BootstrapColorPalette.resolveForegroundOn]) + icon are rendered on top afterward.
 */
object IosControlTintRenderUtil {

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
        val baseGray = if (darkMode) 44 else 242
        val borderGray = if (darkMode) 82 else 198
        for (y in 0 until height) {
            val vGrad = ((0.5f - (y.toFloat() / height.coerceAtLeast(1).toFloat())) * 14f).roundToInt()
            for (x in 0 until width) {
                val isBorder = x == 0 || y == 0 || x == width - 1 || y == height - 1
                val g = (if (isBorder) borderGray else (baseGray + vGrad)).coerceIn(0, 255)
                pixels[y * width + x] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
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
                    if (isBorderBand || kotlin.math.abs(lum - avgLum) > 0.14f) {
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
