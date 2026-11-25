package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.view.Gravity
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatButton
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared native Android `AppCompatButton` view factory, contrasting foreground resolver, and
 * Bootstrap attribute synchronization utility.
 */
object AndroidControlTintRenderUtil {

    fun createNativeOrTintedButton(
        context: Context,
        implProvider: () -> AndroidBaseNativeImpl?
    ): AppCompatButton = object : AppCompatButton(context) {
        private val measurePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        init {
            isAllCaps = false
            gravity = Gravity.CENTER
            includeFontPadding = false
            minWidth = 0
            minimumWidth = 0
            setHorizontallyScrolling(false)
        }

        override fun dispatchTouchEvent(event: MotionEvent?): Boolean {
            if (implProvider()?.nativeWidget?.layerPassThrough == true) return false
            return super.dispatchTouchEvent(event)
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            val impl = implProvider()
            if (impl != null && impl !is AndroidStyledButtonImpl && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.EXACTLY) {
                val label = text?.toString().orEmpty()
                val leftIcon = compoundDrawables.getOrNull(0)
                val iconW = leftIcon?.bounds?.width()?.takeIf { it > 0 } ?: leftIcon?.intrinsicWidth?.takeIf { it > 0 } ?: 0
                if (label.isNotEmpty() || iconW > 0) {
                    measurePaint.textSize = textSize
                    measurePaint.typeface = typeface ?: Typeface.DEFAULT_BOLD
                    val textW = if (label.isNotEmpty()) measurePaint.measureText(label) else 0f
                    val gap = if (iconW > 0 && label.isNotEmpty()) AndroidViewHandle.dpToPx(6) else 0
                    val safetyPx = AndroidViewHandle.dpToPx(4)
                    val requiredW = (textW + iconW + gap + paddingLeft + paddingRight + safetyPx).roundToInt()
                    if (requiredW > measuredWidth) {
                        setMeasuredDimension(resolveSize(requiredW, widthMeasureSpec), measuredHeight)
                    }
                }
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val impl = implProvider()
            if (impl is AndroidStyledButtonImpl) {
                val iconDrawable = impl.resolvedStyledIconDrawable
                if (iconDrawable != null && text.isNullOrEmpty() && width > 0 && height > 0) {
                    val targetSize = (min(width, height) * 0.45f).roundToInt()
                        .coerceIn(AndroidViewHandle.dpToPx(16), AndroidViewHandle.dpToPx(28))
                    val left = (width - targetSize) / 2
                    val top = (height - targetSize) / 2
                    iconDrawable.setBounds(left, top, left + targetSize, top + targetSize)
                    iconDrawable.draw(canvas)
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

    internal fun syncButtonBootstrapAttributes(
        nativeWidget: AndroidViewHandle,
        type: BootstrapType?,
        outline: Boolean,
        hasCustomStyledBackground: Boolean = false
    ) {
        val isDark = AndroidViewHandle.isDarkTheme()
        val activeTheme = BootstrapColorPalette.activeThemeConfig
        if (type == null || type == BootstrapType.NONE) {
            if (!hasCustomStyledBackground) {
                nativeWidget.attributes["materialButtonStyle"] = "systemDefault"
                nativeWidget.attributes["useSystemControlAppearance"] = "true"
                nativeWidget.attributes.remove("bootstrapTintColor")
                nativeWidget.attributes.remove("bootstrapForegroundColor")
                nativeWidget.attributes.remove("nativeButtonTinted")
                nativeWidget.attributes.remove("offscreenSpriteTinted")
            }
        } else {
            val tint = BootstrapColorPalette.resolveColor(type, isDark, activeTheme)
            val fg = resolveContrastingForeground(type, isDark, outline, activeTheme)
            nativeWidget.attributes["materialButtonStyle"] = if (outline) "outlined-${type.cssSuffix}" else "filled-${type.cssSuffix}"
            nativeWidget.attributes["useSystemControlAppearance"] = "false"
            nativeWidget.attributes["bootstrapTintColor"] = tint.toHex()
            nativeWidget.attributes["bootstrapForegroundColor"] = fg.toHex()
            nativeWidget.attributes.remove("offscreenSpriteTinted")
            nativeWidget.attributes["nativeButtonTinted"] = "true"
        }
    }
}
