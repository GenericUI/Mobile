package com.nexustools.genericui.android

import android.graphics.drawable.GradientDrawable
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlin.math.roundToInt

/**
 * Android implementation of [StyledContainerNativeImpl] using `GradientDrawable` for
 * RGBA alpha backgrounds, linear/radial gradients, and rounded borders.
 */
class AndroidStyledContainerImpl(
    classPrefix: String = "styled-container"
) : AndroidContainerImpl(classPrefix), StyledContainerNativeImpl<AndroidViewHandle> {

    private var styledVisuals: StyledVisualSpec = StyledVisualSpec()
    private var lastComponentStyle: ComponentStyle? = null
    private var lastComputedStyle: ComputedStyle? = null

    init {
        nativeWidget.attributes["antialiasing"] = "true"
    }

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    protected fun syncVisualsToAndroidView() {
        val visuals = getStyledVisuals()
        nativeWidget.attributes["styledVisuals"] = visuals.toString()
        visuals.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgba() }
        visuals.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
        visuals.textColor?.let { nativeWidget.attributes["textColor"] = it.toCssRgba() }
        visuals.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgba() }
        visuals.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
        visuals.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
        visuals.opacity?.let { setOpacity(it) }
        visuals.padding?.let { setPadding(it) }
        if (visuals.widthPx != null || visuals.heightPx != null) {
            val w = visuals.widthPx ?: nativeWidget.bounds.width
            val h = visuals.heightPx ?: nativeWidget.bounds.height
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, w, h)
            visuals.widthPx?.let { nativeWidget.attributes["widthPx"] = it.toString() }
            visuals.heightPx?.let { nativeWidget.attributes["heightPx"] = it.toString() }
        }
        nativeWidget.androidView?.background = buildGradientDrawable(visuals)
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        styledVisuals = visuals
        syncVisualsToAndroidView()
    }

    override fun getStyledVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = getComputedStyle(),
            snapshot = styleSnapshot,
            darkTheme = isDark
        )
        return computedBase.mergeWith(styledVisuals)
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super.applyComponentStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super.applyComponentBootstrap(bootstrap)
        syncVisualsToAndroidView()
    }

    companion object {
        internal fun buildGradientDrawable(spec: StyledVisualSpec): GradientDrawable {
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            val radius = (spec.borderRadiusPx ?: 0f).coerceAtLeast(0f)
            if (radius > 0f) {
                drawable.cornerRadius = radius
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
                        drawable.gradientRadius = 200f
                    }
                    else -> {
                        spec.backgroundColor?.let { drawable.setColor(it.toArgbInt()) }
                    }
                }
            } else {
                spec.backgroundColor?.let { drawable.setColor(it.toArgbInt()) }
            }

            val borderW = (spec.borderWidthPx ?: 0f).roundToInt()
            val borderCol = spec.borderColor
            if (borderW > 0 && borderCol != null && spec.borderStyle != CssBorderStyle.NONE) {
                if (spec.borderStyle == CssBorderStyle.DASHED) {
                    drawable.setStroke(borderW, borderCol.toArgbInt(), 8f, 4f)
                } else if (spec.borderStyle == CssBorderStyle.DOTTED) {
                    drawable.setStroke(borderW, borderCol.toArgbInt(), 2f, 3f)
                } else {
                    drawable.setStroke(borderW, borderCol.toArgbInt())
                }
            }
            return drawable
        }
    }
}
