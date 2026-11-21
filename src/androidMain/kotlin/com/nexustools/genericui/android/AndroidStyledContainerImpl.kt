package com.nexustools.genericui.android

import android.graphics.drawable.GradientDrawable
import android.os.Build
import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.css.CssBorderStyle
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import com.nexustools.genericui.style.gradient.Gradient.RadialGradient
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlin.math.roundToInt

/**
 * Android implementation of [StyledContainerNativeImpl] using `GradientDrawable` for
 * RGBA alpha backgrounds, linear/radial gradients, rounded borders, and `RenderEffect` blur.
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

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    private fun isTextBubbleComponent(): Boolean {
        val tokens = componentClassPrefix.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return tokens.any { token ->
            val norm = token.removePrefix("genericui:")
            norm == "text-bubble" || norm == "textbubble" || norm == "gui-text-bubble"
        }
    }

    private fun applyVisualsToAndroidView(visuals: StyledVisualSpec) {
        if (nativeWidget.androidView == null) return
        val effVisuals = if (isTextBubbleComponent()) visuals.withoutBackdropFilter() else visuals
        val rawBlurPx = effVisuals.backdropBlurPx ?: effVisuals.backdropFilter?.blur?.resolvePx()
        val rawCornerRadiusPx = (effVisuals.borderRadiusPx ?: 0f).coerceAtLeast(0f)
        val shadowBlurPx = effVisuals.boxShadowBlurPx
        val shadowColor = effVisuals.boxShadowColor

        AndroidViewHandle.runOnMainThread {
            runCatching {
                val view = nativeWidget.androidView ?: return@runCatching
                if (Build.VERSION.SDK_INT >= 31) {
                    view.setRenderEffect(null)
                }
                if (shadowBlurPx != null && shadowBlurPx > 0f) {
                    view.elevation = shadowBlurPx
                }
                if (shadowColor != null && Build.VERSION.SDK_INT >= 28) {
                    val argb = shadowColor.toArgbInt()
                    view.outlineAmbientShadowColor = argb
                    view.outlineSpotShadowColor = argb
                }
                val gradDrawable = buildGradientDrawable(effVisuals)
                val flexView = view as? AndroidFlexContainerView
                if (effVisuals.hasBackdropFilter && flexView != null) {
                    view.background = null
                    flexView.setWillNotDraw(false)
                    val scaledBlurPx = rawBlurPx?.takeIf { it > 0f }?.let { AndroidViewHandle.dpToPxFloat(it) }
                    flexView.backdropBlurPx = scaledBlurPx ?: 0f
                    flexView.backdropFilter = effVisuals.backdropFilter?.let { f ->
                        if (scaledBlurPx != null) f.copy(blur = CssLength.px(scaledBlurPx)) else f
                    } ?: scaledBlurPx?.let { CssFilter.blur(CssLength.px(it)) }
                    flexView.backdropCornerRadiusPx = if (rawCornerRadiusPx > 0f) {
                        AndroidViewHandle.dpToPxFloat(rawCornerRadiusPx)
                    } else {
                        0f
                    }
                    flexView.backdropOverlayDrawable = gradDrawable
                } else {
                    if (flexView != null) {
                        flexView.backdropBlurPx = 0f
                        flexView.backdropFilter = null
                        flexView.backdropCornerRadiusPx = 0f
                        flexView.backdropOverlayDrawable = null
                    }
                    view.background = gradDrawable
                }
            }
        }
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        applyVisualsToAndroidView(getStyledVisuals())
    }

    protected fun syncVisualsToAndroidView() {
        val visuals = getStyledVisuals()
        nativeWidget.attributes["styledVisuals"] = visuals.toString()
        visuals.backgroundColor?.let { nativeWidget.attributes["backgroundColor"] = it.toCssRgbaString() }
        visuals.backgroundGradient?.let { nativeWidget.attributes["backgroundGradient"] = it.toCssString() }
        visuals.textColor?.let { nativeWidget.attributes["textColor"] = it.toCssRgbaString() }
        visuals.borderColor?.let { nativeWidget.attributes["borderColor"] = it.toCssRgbaString() }
        visuals.borderWidthPx?.let { nativeWidget.attributes["borderWidthPx"] = it.toString() }
        visuals.borderRadiusPx?.let { nativeWidget.attributes["borderRadiusPx"] = it.toString() }
        visuals.opacity?.let { setOpacity(it) }
        visuals.padding?.let { setPadding(it) }

        val blurPx = visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx()
        if (blurPx != null && blurPx > 0f) {
            nativeWidget.attributes["backdropBlurPx"] = blurPx.toString()
            nativeWidget.attributes["androidRenderEffect"] = "blur(${blurPx}px)"
        } else {
            nativeWidget.attributes.remove("backdropBlurPx")
            nativeWidget.attributes.remove("androidRenderEffect")
        }

        if (visuals.backdropFilter != null) {
            nativeWidget.attributes["backdropFilter"] = visuals.backdropFilter.toCssString()
        } else {
            nativeWidget.attributes.remove("backdropFilter")
        }

        if (visuals.boxShadowBlurPx != null && visuals.boxShadowBlurPx > 0f) {
            nativeWidget.attributes["boxShadowBlurPx"] = visuals.boxShadowBlurPx.toString()
        } else {
            nativeWidget.attributes.remove("boxShadowBlurPx")
        }
        if (visuals.boxShadowColor != null) {
            nativeWidget.attributes["boxShadowColor"] = visuals.boxShadowColor.toCssRgbaString()
        } else {
            nativeWidget.attributes.remove("boxShadowColor")
        }

        if (visuals.widthPx != null || visuals.heightPx != null) {
            val w = visuals.widthPx ?: nativeWidget.bounds.width
            val h = visuals.heightPx ?: nativeWidget.bounds.height
            if (visuals.widthPx != null && visuals.widthPx > 0) {
                nativeWidget.hasExplicitPreferredWidth = true
            }
            if (visuals.heightPx != null && visuals.heightPx > 0) {
                nativeWidget.hasExplicitPreferredHeight = true
            }
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, w, h)
            visuals.widthPx?.let { nativeWidget.attributes["widthPx"] = it.toString() }
            visuals.heightPx?.let { nativeWidget.attributes["heightPx"] = it.toString() }
            super.syncAndroidView()
        }
        applyVisualsToAndroidView(visuals)
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
        val resolved = computedBase.mergeWith(styledVisuals).forBackend(LayoutBackendTarget.ANDROID)
        return if (isTextBubbleComponent()) resolved.withoutBackdropFilter() else resolved
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super<AndroidContainerImpl>.applyComponentStyle(style)
        syncVisualsToAndroidView()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<AndroidContainerImpl>.applyComponentBootstrap(bootstrap)
        syncVisualsToAndroidView()
    }

    companion object {
        internal fun buildGradientDrawable(spec: StyledVisualSpec): GradientDrawable {
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
}
