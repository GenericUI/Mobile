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
    classPrefix: String = "styled-container",
    tagName: String = "div"
) : AndroidContainerImpl(classPrefix, tagName), StyledContainerNativeImpl<AndroidViewHandle> {

    private var styledVisuals: StyledVisualSpec = StyledVisualSpec()
    private var lastComponentStyle: ComponentStyle? = null
    private var lastComputedStyle: ComputedStyle? = null

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        syncAndroidView()
    }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    private fun applyVisualsToAndroidView(visuals: StyledVisualSpec) {
        if (nativeWidget.androidView == null) return
        val rawBlurPx = visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx()
        val rawCornerRadiusPx = (visuals.borderRadiusPx ?: 0f).coerceAtLeast(0f)
        val shadowBlurPx = visuals.boxShadowBlurPx
        val shadowColor = visuals.boxShadowColor

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
                val gradDrawable = buildGradientDrawable(visuals)
                val flexView = view as? AndroidFlexContainerView
                if (visuals.hasBackdropFilter && flexView != null) {
                    view.background = null
                    flexView.setWillNotDraw(false)
                    val scaledBlurPx = rawBlurPx?.takeIf { it > 0f }?.let { AndroidViewHandle.dpToPxFloat(it) }
                    flexView.backdropBlurPx = scaledBlurPx ?: 0f
                    flexView.backdropFilter = visuals.backdropFilter?.let { f ->
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
        styledVisuals = sanitizeIncomingStyledVisuals(visuals)
        syncVisualsToAndroidView()
    }

    override fun getStyledVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = getComputedStyle(),
            snapshot = snapshotForStyledVisuals(),
            darkTheme = isDark
        )
        return computedBase.mergeWith(styledVisuals).forBackend(LayoutBackendTarget.ANDROID)
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
        internal fun buildGradientDrawable(spec: StyledVisualSpec): GradientDrawable =
            AndroidStyledVisualUtil.buildGradientDrawable(spec)
    }
}
