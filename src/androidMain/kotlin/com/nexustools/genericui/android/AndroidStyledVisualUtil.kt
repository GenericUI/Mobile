package com.nexustools.genericui.android

import android.graphics.drawable.GradientDrawable
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient

/**
 * Shared Android surface drawable builder for styled containers, styled components, and styled buttons.
 */
internal object AndroidStyledVisualUtil {

    fun buildGradientDrawable(spec: StyledVisualSpec): GradientDrawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        val rawRadius = (spec.borderRadiusPx ?: 0f).coerceAtLeast(0f)
        if (rawRadius > 0f) {
            drawable.cornerRadius = AndroidViewHandle.dpToPxFloat(rawRadius)
        }
        val grad = spec.backgroundGradient
        if (grad != null && grad.stops.size >= 2) {
            val colors = IntArray(grad.stops.size) { grad.stops[it].color.toArgbInt() }
            drawable.colors = colors
            when (grad) {
                is LinearGradient -> {
                    val angle = ((grad.angleDegrees % 360f) + 360f) % 360f
                    drawable.orientation = when {
                        angle in 337.5f..360f || angle in 0f..22.5f -> GradientDrawable.Orientation.BOTTOM_TOP
                        angle in 22.5f..67.5f -> GradientDrawable.Orientation.BL_TR
                        angle in 67.5f..112.5f -> GradientDrawable.Orientation.LEFT_RIGHT
                        angle in 112.5f..157.5f -> GradientDrawable.Orientation.TL_BR
                        angle in 157.5f..202.5f -> GradientDrawable.Orientation.TOP_BOTTOM
                        angle in 202.5f..247.5f -> GradientDrawable.Orientation.TR_BL
                        angle in 247.5f..292.5f -> GradientDrawable.Orientation.RIGHT_LEFT
                        else -> GradientDrawable.Orientation.BR_TL
                    }
                    drawable.gradientType = GradientDrawable.LINEAR_GRADIENT
                }
                is RadialGradient -> {
                    drawable.gradientType = GradientDrawable.RADIAL_GRADIENT
                    drawable.gradientRadius = AndroidViewHandle.dpToPxFloat(200f)
                }
                else -> {
                    spec.backgroundColor?.let { drawable.setColor(it.toArgbInt()) }
                }
            }
        } else {
            spec.backgroundColor?.let { drawable.setColor(it.toArgbInt()) }
        }

        val rawBorderW = (spec.borderWidthPx ?: 0f).coerceAtLeast(0f)
        val borderW = if (rawBorderW > 0f) AndroidViewHandle.dpToPx(rawBorderW).coerceAtLeast(1) else 0
        val borderCol = spec.borderColor
        if (borderW > 0 && borderCol != null && spec.borderStyle != CssBorderStyle.NONE) {
            if (spec.borderStyle == CssBorderStyle.DASHED) {
                drawable.setStroke(
                    borderW,
                    borderCol.toArgbInt(),
                    AndroidViewHandle.dpToPxFloat(8f),
                    AndroidViewHandle.dpToPxFloat(4f)
                )
            } else if (spec.borderStyle == CssBorderStyle.DOTTED) {
                drawable.setStroke(
                    borderW,
                    borderCol.toArgbInt(),
                    AndroidViewHandle.dpToPxFloat(2f),
                    AndroidViewHandle.dpToPxFloat(3f)
                )
            } else {
                drawable.setStroke(borderW, borderCol.toArgbInt())
            }
        }
        return drawable
    }
}
