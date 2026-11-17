package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.Gravity
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Offscreen empty-text native Android control sprite renderer and `ThemeConfig` pixel tinter.
 *
 * - When [BootstrapType] is [BootstrapType.NONE] (or `null`), standard Android controls render
 *   their 100% unmodified native Material / AppCompat `android.widget.Button` background and text.
 * - When [BootstrapType] is non-`NONE`, the native `android.widget.Button` background drawable is
 *   rendered with NO text (`""`) into an offscreen `Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)`,
 *   the pixels are color-tinted with [BootstrapColorPalette.resolveColor] from the active [ThemeConfig]
 *   while preserving native elevation/bevel/border shading, the tinted surface is drawn onto the
 *   button [Canvas], and contrasting text ([BootstrapColorPalette.resolveForegroundOn]) + icon are
 *   drawn on top afterward.
 */
object AndroidControlTintRenderUtil {

    fun createNativeOrTintedButton(
        context: Context,
        implProvider: () -> AndroidButtonImpl?
    ): AppCompatButton = object : AppCompatButton(context) {
        private var paintingOffscreenEmpty: Boolean = false

        init {
            isAllCaps = false
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
        }

        override fun onDraw(canvas: Canvas) {
            if (paintingOffscreenEmpty) {
                super.onDraw(canvas)
                return
            }
            val impl = implProvider()
            if (impl == null || impl is AndroidStyledButtonImpl) {
                super.onDraw(canvas)
                return
            }
            val type = impl.getType() ?: BootstrapType.NONE
            val borderRadius = impl.getBorderRadius()
            if (type == BootstrapType.NONE && (borderRadius == null || borderRadius == BorderRadiusStyle.NONE)) {
                super.onDraw(canvas)
                return
            }

            val w = width
            val h = height
            if (w <= 0 || h <= 0) {
                super.onDraw(canvas)
                return
            }

            val isDark = AndroidViewHandle.isDarkTheme()
            val outline = impl.isOutline()
            val themeConfig = BootstrapColorPalette.activeThemeConfig
            val density = resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
            val defaultBg = impl.getDefaultNativeBackgroundDrawable()

            val savedText = text
            val savedCompoundDrawables = compoundDrawables
            paintingOffscreenEmpty = true
            val offscreenBitmap: Bitmap? = try {
                text = ""
                setCompoundDrawables(null, null, null, null)
                val customDraw: ((Canvas) -> Unit)? = if (defaultBg != null) {
                    { offscreenCanvas ->
                        defaultBg.state = drawableState
                        defaultBg.setBounds(0, 0, w, h)
                        defaultBg.draw(offscreenCanvas)
                    }
                } else if (background !is AndroidTintedButtonDrawable) {
                    { offscreenCanvas -> super.onDraw(offscreenCanvas) }
                } else {
                    null
                }
                renderEmptyTextTintedBitmap(
                    button = this,
                    width = w,
                    height = h,
                    type = type,
                    darkMode = isDark,
                    outline = outline,
                    themeConfig = themeConfig,
                    borderRadiusStyle = borderRadius,
                    density = density,
                    drawEmptyControl = customDraw
                )
            } finally {
                text = savedText
                setCompoundDrawables(
                    savedCompoundDrawables.getOrNull(0),
                    savedCompoundDrawables.getOrNull(1),
                    savedCompoundDrawables.getOrNull(2),
                    savedCompoundDrawables.getOrNull(3)
                )
                paintingOffscreenEmpty = false
            }

            if (offscreenBitmap != null) {
                canvas.drawBitmap(offscreenBitmap, 0f, 0f, null)
                drawContrastingTextAndIconOverlay(
                    canvas = canvas,
                    button = this,
                    width = w,
                    height = h,
                    label = savedText?.toString().orEmpty(),
                    iconDrawable = savedCompoundDrawables.getOrNull(0),
                    type = type,
                    darkMode = isDark,
                    outline = outline,
                    themeConfig = themeConfig
                )
            } else {
                super.onDraw(canvas)
            }
        }
    }

    fun resolveCornerRadiusPx(
        width: Int,
        height: Int,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float = 1f
    ): Float {
        val safeDensity = density.coerceAtLeast(0.5f)
        val maxR = min(width, height) / 2f
        return when (borderRadiusStyle) {
            BorderRadiusStyle.NONE -> 0f
            BorderRadiusStyle.PILL, BorderRadiusStyle.CIRCLE -> maxR
            null -> min(maxR, 4f * safeDensity)
            else -> min(maxR, borderRadiusStyle.radiusPx * safeDensity)
        }
    }

    private fun computeRoundedRectSdf(
        x: Int,
        y: Int,
        width: Int,
        height: Int,
        radiusPx: Float
    ): Float {
        val hx = width / 2f
        val hy = height / 2f
        val rad = radiusPx.coerceIn(0f, min(hx, hy))
        val ix = (hx - rad).coerceAtLeast(0f)
        val iy = (hy - rad).coerceAtLeast(0f)
        val dx = abs((x + 0.5f) - hx) - ix
        val dy = abs((y + 0.5f) - hy) - iy
        val outsideDist = hypot(max(dx, 0f), max(dy, 0f))
        val insideDist = min(max(dx, dy), 0f)
        return outsideDist + insideDist - rad
    }

    fun clipPixelsToRoundedRect(
        pixels: IntArray,
        width: Int,
        height: Int,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float = 1f
    ) {
        if (pixels.isEmpty() || width <= 0 || height <= 0) return
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, density)
        if (radiusPx <= 0.5f) return
        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                if (a == 0) continue
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val cov = (0.5f - sdf).coerceIn(0f, 1f)
                if (cov <= 0f) {
                    pixels[idx] = 0x00000000
                } else if (cov < 1f) {
                    val newA = (a * cov).roundToInt().coerceIn(0, 255)
                    pixels[idx] = (newA shl 24) or (argb and 0x00FFFFFF)
                }
            }
        }
    }

    fun renderEmptyTextTintedBitmap(
        button: TextView?,
        width: Int,
        height: Int,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = button?.resources?.displayMetrics?.density ?: 1f,
        drawEmptyControl: ((Canvas) -> Unit)? = null
    ): Bitmap? = runCatching {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) ?: return null
        button?.resources?.displayMetrics?.densityDpi?.takeIf { it > 0 }?.let { bmp.density = it }
        val offscreenCanvas = Canvas(bmp)
        if (drawEmptyControl != null) {
            drawEmptyControl(offscreenCanvas)
        } else {
            val bg: Drawable? = button?.background
            if (bg != null && bg !is AndroidTintedButtonDrawable) {
                bg.setBounds(0, 0, w, h)
                bg.draw(offscreenCanvas)
            }
        }
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val hasPaintedPixels = pixels.any { (it ushr 24) != 0 }
        if (!hasPaintedPixels) {
            populateSyntheticNativeButtonSurface(pixels, w, h, darkMode, borderRadiusStyle, density)
        }
        if (type != BootstrapType.NONE) {
            val tintColor = BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
            tintNativeControlPixels(
                pixels = pixels,
                width = w,
                height = h,
                tint = tintColor,
                outline = outline,
                isLink = type == BootstrapType.LINK,
                darkMode = darkMode,
                borderRadiusStyle = borderRadiusStyle,
                density = density
            )
        } else if (borderRadiusStyle != null && borderRadiusStyle != BorderRadiusStyle.NONE) {
            clipPixelsToRoundedRect(pixels, w, h, borderRadiusStyle, density)
        }
        bmp.setPixels(pixels, 0, w, 0, 0, w, h)
        bmp
    }.getOrNull()

    /**
     * Renders the empty-text native Android button surface into an ARGB [IntArray] pixel buffer
     * and applies `ThemeConfig` semantic tinting. Works in both Android device/emulator runtimes
     * and uninstrumented JVM host tests.
     */
    fun renderEmptyTextTintedButtonPixels(
        width: Int,
        height: Int,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean = false,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig,
        button: TextView? = null,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = button?.resources?.displayMetrics?.density ?: 1f
    ): IntArray {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        var capturedFromBitmap = false
        runCatching {
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            if (bmp != null && button != null) {
                val canvas = Canvas(bmp)
                button.background?.takeIf { it !is AndroidTintedButtonDrawable }?.let { bg ->
                    bg.setBounds(0, 0, w, h)
                    bg.draw(canvas)
                }
                bmp.getPixels(pixels, 0, w, 0, 0, w, h)
                capturedFromBitmap = pixels.any { (it ushr 24) != 0 }
            }
        }
        if (!capturedFromBitmap) {
            populateSyntheticNativeButtonSurface(pixels, w, h, darkMode, borderRadiusStyle, density)
        }
        if (type != BootstrapType.NONE) {
            val tintColor = BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
            tintNativeControlPixels(
                pixels = pixels,
                width = w,
                height = h,
                tint = tintColor,
                outline = outline,
                isLink = type == BootstrapType.LINK,
                darkMode = darkMode,
                borderRadiusStyle = borderRadiusStyle,
                density = density
            )
        } else if (borderRadiusStyle != null && borderRadiusStyle != BorderRadiusStyle.NONE) {
            clipPixelsToRoundedRect(pixels, w, h, borderRadiusStyle, density)
        }
        return pixels
    }

    private fun populateSyntheticNativeButtonSurface(
        pixels: IntArray,
        width: Int,
        height: Int,
        darkMode: Boolean,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = 1f
    ) {
        val baseGray = if (darkMode) 56 else 232
        val borderGray = if (darkMode) 92 else 184
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, density)
        val borderBandPx = max(1f, 1f * density.coerceAtLeast(0.5f))
        for (y in 0 until height) {
            val vGrad = ((0.5f - (y.toFloat() / height.coerceAtLeast(1).toFloat())) * 16f).roundToInt()
            for (x in 0 until width) {
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val outerCov = (0.5f - sdf).coerceIn(0f, 1f)
                if (outerCov <= 0f) {
                    pixels[y * width + x] = 0x00000000
                    continue
                }
                val isBorder = sdf >= -borderBandPx
                val g = (if (isBorder) borderGray else (baseGray + vGrad)).coerceIn(0, 255)
                val outA = (outerCov * 255f).roundToInt().coerceIn(0, 255)
                pixels[y * width + x] = (outA shl 24) or (g shl 16) or (g shl 8) or g
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
        darkMode: Boolean,
        borderRadiusStyle: BorderRadiusStyle? = null,
        density: Float = 1f
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
        val safeDensity = density.coerceAtLeast(0.5f)
        val radiusPx = resolveCornerRadiusPx(width, height, borderRadiusStyle, safeDensity)
        val borderThicknessPx = (1.5f * safeDensity).coerceIn(1.5f, 6f)

        for (y in 0 until height) {
            for (x in 0 until width) {
                val idx = y * width + x
                val sdf = computeRoundedRectSdf(x, y, width, height, radiusPx)
                val outerCov = (0.5f - sdf).coerceIn(0f, 1f)
                if (outerCov <= 0f) {
                    pixels[idx] = 0x00000000
                    continue
                }

                val argb = pixels[idx]
                val a = (argb ushr 24) and 0xFF
                val delta = if (a > 32) {
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    val lum = ((0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f).coerceIn(0f, 1f)
                    (lum - avgLum) * 0.55f
                } else {
                    0f
                }

                if (outline) {
                    val strokeCov = (sdf + borderThicknessPx + 0.5f).coerceIn(0f, 1f)
                    val interiorAlpha = (0.08f * tint.a).roundToInt().coerceIn(0, 26)
                    val blendedA = ((interiorAlpha + (tint.a - interiorAlpha) * strokeCov) * outerCov).roundToInt().coerceIn(0, 255)
                    val nr = (tint.r + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    val ng = (tint.g + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    val nb = (tint.b + (delta * 60f * strokeCov).roundToInt()).coerceIn(0, 255)
                    pixels[idx] = (blendedA shl 24) or (nr shl 16) or (ng shl 8) or nb
                } else {
                    val isBorderBand = sdf >= -borderThicknessPx
                    val shadeFactor = if (isBorderBand) -0.14f else delta
                    val nr = (tint.r + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val ng = (tint.g + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val nb = (tint.b + (shadeFactor * 180f).roundToInt()).coerceIn(0, 255)
                    val outA = (outerCov * tint.a).roundToInt().coerceIn(0, 255)
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

    fun drawContrastingTextAndIconOverlay(
        canvas: Canvas,
        button: TextView,
        width: Int,
        height: Int,
        label: String,
        iconDrawable: Drawable?,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    ) {
        val fg = resolveContrastingForeground(type, darkMode, outline, themeConfig)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg.toArgbInt()
            textSize = button.textSize
            typeface = button.typeface ?: Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.LEFT
        }

        val textWidth = if (label.isNotEmpty()) paint.measureText(label) else 0f
        val iconW = iconDrawable?.bounds?.width()?.takeIf { it > 0 } ?: iconDrawable?.intrinsicWidth?.takeIf { it > 0 } ?: 0
        val iconH = iconDrawable?.bounds?.height()?.takeIf { it > 0 } ?: iconDrawable?.intrinsicHeight?.takeIf { it > 0 } ?: 0
        val gap = if (iconW > 0 && label.isNotEmpty()) AndroidViewHandle.dpToPx(6).toFloat() else 0f
        val totalW = iconW + gap + textWidth
        var cursorX = ((width - totalW) / 2f).coerceAtLeast(button.paddingLeft.toFloat())

        if (iconDrawable != null && iconW > 0 && iconH > 0) {
            val iconY = ((height - iconH) / 2f).roundToInt()
            val startX = cursorX.roundToInt()
            iconDrawable.setBounds(startX, iconY, startX + iconW, iconY + iconH)
            iconDrawable.draw(canvas)
            cursorX += iconW + gap
        }

        if (label.isNotEmpty()) {
            val fm = paint.fontMetrics
            val baselineY = (height / 2f) - ((fm.descent + fm.ascent) / 2f)
            canvas.drawText(label, cursorX, baselineY, paint)
        }
    }
}
