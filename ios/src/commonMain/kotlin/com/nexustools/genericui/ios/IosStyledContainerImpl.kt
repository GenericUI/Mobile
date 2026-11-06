package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * iOS `UIView` / `CAGradientLayer` / `UIVisualEffectView` implementation of [StyledContainerNativeImpl].
 */
class IosStyledContainerImpl(
    classPrefix: String = "styled-container"
) : IosContainerImpl(classPrefix), StyledContainerNativeImpl<IosNativeView> {

    private var styledVisuals: StyledVisualSpec = StyledVisualSpec()
    private var lastComponentStyle: ComponentStyle? = null
    private var lastComputedStyle: ComputedStyle? = null

    init {
        nativeWidget.metadata["antialiasing"] = "true"
    }

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    protected fun syncVisualsToIosView() {
        val visuals = getStyledVisuals()
        nativeWidget.metadata["styledVisuals"] = visuals.toString()
        visuals.backgroundColor?.let {
            nativeWidget.backgroundColor = it
            nativeWidget.metadata["backgroundColor"] = it.toCssRgbaString()
        }
        visuals.backgroundGradient?.let {
            nativeWidget.metadata["backgroundGradient"] = it.toCssString()
        }
        visuals.textColor?.let {
            nativeWidget.tintColor = it
            nativeWidget.metadata["textColor"] = it.toCssRgbaString()
        }
        visuals.borderColor?.let {
            nativeWidget.metadata["borderColor"] = it.toCssRgbaString()
        }
        visuals.borderWidthPx?.let {
            nativeWidget.borderWidth = it
            nativeWidget.metadata["borderWidthPx"] = it.toString()
        }
        visuals.borderRadiusPx?.let {
            nativeWidget.cornerRadius = it
            nativeWidget.metadata["borderRadiusPx"] = it.toString()
        }
        visuals.opacity?.let { setOpacity(it) }
        visuals.padding?.let { setPadding(it) }

        val blurPx = visuals.backdropBlurPx ?: visuals.backdropFilter?.blur?.resolvePx()
        if (blurPx != null && blurPx > 0f) {
            nativeWidget.metadata["backdropBlurPx"] = blurPx.toString()
            nativeWidget.metadata["iosBlurEffect"] = "UIBlurEffect"
        } else {
            nativeWidget.metadata.remove("backdropBlurPx")
            nativeWidget.metadata.remove("iosBlurEffect")
        }

        if (visuals.backdropFilter != null) {
            nativeWidget.metadata["backdropFilter"] = visuals.backdropFilter.toCssString()
        } else {
            nativeWidget.metadata.remove("backdropFilter")
        }

        if (visuals.boxShadowBlurPx != null && visuals.boxShadowBlurPx > 0f) {
            nativeWidget.metadata["boxShadowBlurPx"] = visuals.boxShadowBlurPx.toString()
            nativeWidget.metadata["shadowRadiusPt"] = visuals.boxShadowBlurPx.toString()
        } else {
            nativeWidget.metadata.remove("boxShadowBlurPx")
        }
        if (visuals.boxShadowColor != null) {
            nativeWidget.metadata["boxShadowColor"] = visuals.boxShadowColor.toCssRgbaString()
        } else {
            nativeWidget.metadata.remove("boxShadowColor")
        }

        if (visuals.widthPx != null || visuals.heightPx != null) {
            val cur = nativeWidget.frame
            val w = visuals.widthPx ?: cur.width
            val h = visuals.heightPx ?: cur.height
            nativeWidget.frame = Rect(cur.x, cur.y, w, h)
            visuals.widthPx?.let { nativeWidget.metadata["widthPx"] = it.toString() }
            visuals.heightPx?.let { nativeWidget.metadata["heightPx"] = it.toString() }
        }
    }

    override fun setStyledVisuals(visuals: StyledVisualSpec) {
        styledVisuals = visuals
        syncVisualsToIosView()
    }

    override fun getStyledVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = getComputedStyle(),
            snapshot = styleSnapshot,
            darkTheme = isDark
        )
        return computedBase.mergeWith(styledVisuals).forBackend(LayoutBackendTarget.IOS)
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        lastComputedStyle = style
        super.applyComputedStyle(style)
        syncVisualsToIosView()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        lastComponentStyle = style
        super.applyComponentStyle(style)
        syncVisualsToIosView()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super.applyComponentBootstrap(bootstrap)
        syncVisualsToIosView()
    }
}
