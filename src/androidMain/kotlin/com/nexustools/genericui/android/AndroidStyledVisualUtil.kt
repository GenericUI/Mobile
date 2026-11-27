package com.nexustools.genericui.android

import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
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

    fun isBackdropAttributeName(name: String): Boolean =
        name.equals("backdrop-filter", ignoreCase = true) ||
            name.equals("-webkit-backdrop-filter", ignoreCase = true) ||
            name.equals("backdropFilter", ignoreCase = true) ||
            name.equals("backdropBlurPx", ignoreCase = true)

    fun recordBackdropAttributeOverride(
        attributes: MutableMap<String, String>,
        name: String,
        value: String?
    ) {
        when {
            name.equals("backdrop-filter", ignoreCase = true) ||
                name.equals("-webkit-backdrop-filter", ignoreCase = true) ||
                name.equals("backdropFilter", ignoreCase = true) -> {
                if (value == null) attributes.remove("attrBackdropFilter")
                else attributes["attrBackdropFilter"] = value
            }
            name.equals("backdropBlurPx", ignoreCase = true) -> {
                if (value == null) attributes.remove("attrBackdropBlurPx")
                else attributes["attrBackdropBlurPx"] = value
            }
        }
    }

    fun applyBackdropAttributeOverrides(
        spec: StyledVisualSpec,
        attributes: Map<String, String>
    ): StyledVisualSpec {
        val attrFilterRaw = attributes["attrBackdropFilter"]
        val attrFilter = attrFilterRaw?.let { CssFilter.parse(it) }
        val attrBlurPx = attributes["attrBackdropBlurPx"]?.toFloatOrNull()
        if (attrFilter == null && attrBlurPx == null) return spec
        return spec.copy(
            backdropFilter = attrFilter ?: spec.backdropFilter,
            backdropBlurPx = attrBlurPx ?: spec.backdropBlurPx
        )
    }

    fun resolveStateVisuals(
        computedBase: StyledVisualSpec,
        baseVisuals: StyledVisualSpec?,
        computedStyle: ComputedStyle?,
        attributes: Map<String, String>,
        isEnabled: Boolean,
        hovered: Boolean,
        focused: Boolean,
        activePressed: Boolean,
        hoverVisuals: StyledVisualSpec? = null,
        focusVisuals: StyledVisualSpec? = null,
        activeVisuals: StyledVisualSpec? = null,
        disabledVisuals: StyledVisualSpec? = null
    ): StyledVisualSpec {
        var effective = computedBase.mergeWith(baseVisuals)
        effective = if (!isEnabled) {
            val computedDisabled = computedStyle?.disabledStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
            effective.mergeWith(computedDisabled).mergeWith(disabledVisuals)
        } else {
            if (hovered) {
                val computedHover = computedStyle?.hoverStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                effective = effective.mergeWith(computedHover).mergeWith(hoverVisuals)
            }
            if (focused) {
                val computedFocus = computedStyle?.focusStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                effective = effective.mergeWith(computedFocus).mergeWith(focusVisuals)
            }
            if (activePressed) {
                val computedActive = computedStyle?.activeStyle?.let { StyledVisualSpec.fromComputedStyle(it) }
                effective = effective.mergeWith(computedActive).mergeWith(activeVisuals)
            }
            effective
        }
        return applyBackdropAttributeOverrides(effective, attributes)
            .forBackend(LayoutBackendTarget.ANDROID)
    }

    fun applyBackdropControllerSpec(
        controller: AndroidHardwareBackdropBlurController,
        spec: StyledVisualSpec,
        overlayDrawable: Drawable?
    ) {
        val effFilter = CssFilter.backdropBlurOrFilter(spec.backdropBlurPx, spec.backdropFilter)
        if (effFilter == null) {
            controller.backdropBlurPx = 0f
            controller.backdropFilter = null
            controller.backdropCornerRadiusPx = 0f
            controller.backdropOverlayDrawable = null
            return
        }
        val rawBlurPx = effFilter.blur?.resolvePx()?.takeIf { it > 0f }
        val scaledBlurPx = rawBlurPx?.let { AndroidViewHandle.dpToPxFloat(it) }
        val scaledShadow = effFilter.dropShadow?.let { sh ->
            sh.copy(
                offsetX = CssLength.px(AndroidViewHandle.dpToPxFloat(sh.offsetX.resolvePx() ?: 0f)),
                offsetY = CssLength.px(AndroidViewHandle.dpToPxFloat(sh.offsetY.resolvePx() ?: 0f)),
                blurRadius = CssLength.px(AndroidViewHandle.dpToPxFloat((sh.blurRadius.resolvePx() ?: 0f).coerceAtLeast(0f))),
                spreadRadius = CssLength.px(AndroidViewHandle.dpToPxFloat(sh.spreadRadius.resolvePx() ?: 0f))
            )
        }
        val scaledFilter = effFilter.copy(
            blur = scaledBlurPx?.let { CssLength.px(it) } ?: effFilter.blur,
            dropShadow = scaledShadow
        )
        val rawCornerRadiusPx = (spec.borderRadiusPx ?: 0f).coerceAtLeast(0f)
        controller.backdropBlurPx = scaledBlurPx ?: 0f
        controller.backdropFilter = scaledFilter
        controller.backdropCornerRadiusPx = if (rawCornerRadiusPx > 0f) {
            AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
        } else {
            0f
        }
        controller.backdropOverlayDrawable = overlayDrawable
    }
}
