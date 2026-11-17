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
import com.nexustools.genericui.jvm.JvmControlTintPixelUtil
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.roundToInt

/**
 * Offscreen empty-text native Android control sprite renderer and `ThemeConfig` pixel tinter
 * backed by [JvmControlTintPixelUtil].
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
            val hovered = isHovered
            val pressed = isPressed
            val disabled = !isEnabled
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
                    drawEmptyControl = customDraw,
                    hovered = hovered,
                    pressed = pressed,
                    disabled = disabled
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
                    themeConfig = themeConfig,
                    hovered = hovered,
                    pressed = pressed
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
    ): Float = JvmControlTintPixelUtil.resolveCornerRadiusPx(width, height, borderRadiusStyle, density)

    fun clipPixelsToRoundedRect(
        pixels: IntArray,
        width: Int,
        height: Int,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float = 1f
    ) = JvmControlTintPixelUtil.clipPixelsToRoundedRect(pixels, width, height, borderRadiusStyle, density)

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
        drawEmptyControl: ((Canvas) -> Unit)? = null,
        hovered: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false
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
            JvmControlTintPixelUtil.synthesizeFallbackNativeButtonSurface(
                pixels = pixels,
                width = w,
                height = h,
                isDark = darkMode,
                borderRadiusStyle = borderRadiusStyle,
                density = density,
                roundedClip = true
            )
        }
        applyTintToPixels(
            pixels = pixels,
            width = w,
            height = h,
            type = type,
            darkMode = darkMode,
            outline = outline,
            themeConfig = themeConfig,
            borderRadiusStyle = borderRadiusStyle,
            density = density,
            hovered = hovered,
            pressed = pressed,
            disabled = disabled
        )
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
        density: Float = button?.resources?.displayMetrics?.density ?: 1f,
        hovered: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false
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
            JvmControlTintPixelUtil.synthesizeFallbackNativeButtonSurface(
                pixels = pixels,
                width = w,
                height = h,
                isDark = darkMode,
                borderRadiusStyle = borderRadiusStyle,
                density = density,
                roundedClip = true
            )
        }
        applyTintToPixels(
            pixels = pixels,
            width = w,
            height = h,
            type = type,
            darkMode = darkMode,
            outline = outline,
            themeConfig = themeConfig,
            borderRadiusStyle = borderRadiusStyle,
            density = density,
            hovered = hovered,
            pressed = pressed,
            disabled = disabled
        )
        return pixels
    }

    private fun applyTintToPixels(
        pixels: IntArray,
        width: Int,
        height: Int,
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean,
        themeConfig: ThemeConfig,
        borderRadiusStyle: BorderRadiusStyle?,
        density: Float,
        hovered: Boolean,
        pressed: Boolean,
        disabled: Boolean
    ) {
        if (type != BootstrapType.NONE) {
            val tintColor = BootstrapColorPalette.resolveColor(type, darkMode, themeConfig)
            JvmControlTintPixelUtil.tintNativeControlPixels(
                pixels = pixels,
                width = width,
                height = height,
                tint = tintColor,
                outline = outline,
                hovered = hovered,
                pressed = pressed,
                disabled = disabled,
                isLink = type == BootstrapType.LINK,
                darkMode = darkMode,
                borderRadiusStyle = borderRadiusStyle,
                density = density,
                clipToRoundedShape = (borderRadiusStyle != null)
            )
        } else if (borderRadiusStyle != null && borderRadiusStyle != BorderRadiusStyle.NONE) {
            JvmControlTintPixelUtil.clipPixelsToRoundedRect(pixels, width, height, borderRadiusStyle, density)
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
        density: Float = 1f,
        hovered: Boolean = false,
        pressed: Boolean = false,
        disabled: Boolean = false
    ) {
        JvmControlTintPixelUtil.tintNativeControlPixels(
            pixels = pixels,
            width = width,
            height = height,
            tint = tint,
            outline = outline,
            hovered = hovered,
            pressed = pressed,
            disabled = disabled,
            isLink = isLink,
            darkMode = darkMode,
            borderRadiusStyle = borderRadiusStyle,
            density = density,
            clipToRoundedShape = (borderRadiusStyle != null)
        )
    }

    fun resolveContrastingForeground(
        type: BootstrapType,
        darkMode: Boolean,
        outline: Boolean,
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig,
        hovered: Boolean = false,
        pressed: Boolean = false
    ): RgbaColor = JvmControlTintPixelUtil.resolveContrastingForeground(
        type = type,
        darkMode = darkMode,
        outline = outline,
        hovered = hovered,
        pressed = pressed,
        themeConfig = themeConfig
    )

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
        themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig,
        hovered: Boolean = false,
        pressed: Boolean = false
    ) {
        val fg = resolveContrastingForeground(
            type = type,
            darkMode = darkMode,
            outline = outline,
            themeConfig = themeConfig,
            hovered = hovered,
            pressed = pressed
        )
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
