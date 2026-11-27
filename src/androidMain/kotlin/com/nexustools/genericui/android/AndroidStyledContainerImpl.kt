package com.nexustools.genericui.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.MotionEvent
import android.view.View
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
    private var hovered: Boolean = false
    private var focused: Boolean = false
    private var activePressed: Boolean = false
    private var isSyncingVisuals: Boolean = false

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View? =
        super.createDefaultAndroidView(context)?.apply {
            setOnFocusChangeListener { _, hasFocus ->
                if (isEnabled() && isFocusable() && !nativeWidget.layerPassThrough) {
                    setFocusedState(hasFocus)
                }
            }
            setOnHoverListener { _, event ->
                if (!isEnabled() || nativeWidget.layerPassThrough) return@setOnHoverListener false
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> setHoveredState(true)
                    MotionEvent.ACTION_HOVER_EXIT -> setHoveredState(false)
                }
                false
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun allowsBackdropBlur(): Boolean = true

    fun getComputedStyle(): ComputedStyle? = lastComputedStyle

    private fun applyVisualsToAndroidView(visuals: StyledVisualSpec) {
        if (nativeWidget.androidView == null) return
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
                val hasBackdrop = CssFilter.backdropBlurOrFilter(visuals.backdropBlurPx, visuals.backdropFilter) != null
                if (hasBackdrop && flexView != null) {
                    view.background = null
                    flexView.setWillNotDraw(false)
                    AndroidStyledVisualUtil.applyBackdropControllerSpec(flexView.backdropController, visuals, gradDrawable)
                } else {
                    if (flexView != null) {
                        AndroidStyledVisualUtil.applyBackdropControllerSpec(flexView.backdropController, StyledVisualSpec.EMPTY, null)
                    }
                    view.background = gradDrawable
                }
            }
        }
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        if (!isSyncingVisuals) {
            syncVisualsToAndroidView()
        }
    }

    protected fun syncVisualsToAndroidView() {
        if (isSyncingVisuals) return
        isSyncingVisuals = true
        try {
            syncVisualsToAndroidViewLocked()
        } finally {
            isSyncingVisuals = false
        }
    }

    private fun syncVisualsToAndroidViewLocked() {
        val visuals = resolveCurrentVisuals()
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
        return AndroidStyledVisualUtil.applyBackdropAttributeOverrides(
            computedBase.mergeWith(styledVisuals),
            nativeWidget.attributes
        ).forBackend(LayoutBackendTarget.ANDROID)
    }

    fun resolveCurrentVisuals(): StyledVisualSpec {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val computedBase = StyledVisualSpec.fromComponentAndComputed(
            componentStyle = lastComponentStyle,
            computedStyle = getComputedStyle(),
            snapshot = snapshotForStyledVisuals(),
            darkTheme = isDark
        )
        return AndroidStyledVisualUtil.resolveStateVisuals(
            computedBase = computedBase,
            baseVisuals = styledVisuals,
            computedStyle = lastComputedStyle,
            attributes = nativeWidget.attributes,
            isEnabled = isEnabled(),
            hovered = hovered,
            focused = focused,
            activePressed = activePressed
        )
    }

    override fun setAttribute(name: String, value: String?) {
        AndroidStyledVisualUtil.recordBackdropAttributeOverride(nativeWidget.attributes, name, value)
        super<AndroidContainerImpl>.setAttribute(name, value)
    }

    override fun setHoveredState(hovered: Boolean) {
        val coerced = if (!isEnabled()) false else hovered
        this.hovered = coerced
        nativeWidget.attributes["hovered"] = coerced.toString()
        syncVisualsToAndroidView()
    }

    override fun isHoveredState(): Boolean = if (!isEnabled()) false else hovered

    override fun setFocusedState(focused: Boolean) {
        val coerced = if (!isEnabled() || !isFocusable()) false else focused
        this.focused = coerced
        nativeWidget.attributes["focused"] = coerced.toString()
        syncVisualsToAndroidView()
    }

    override fun isFocusedState(): Boolean = if (!isEnabled() || !isFocusable()) false else focused

    override fun setActivePressedState(active: Boolean) {
        val coerced = if (!isEnabled()) false else active
        this.activePressed = coerced
        nativeWidget.attributes["activePressed"] = coerced.toString()
        syncVisualsToAndroidView()
    }

    override fun isActivePressedState(): Boolean = if (!isEnabled()) false else activePressed

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        if (!enabled) {
            this.hovered = false
            this.focused = false
            this.activePressed = false
            nativeWidget.attributes["hovered"] = "false"
            nativeWidget.attributes["focused"] = "false"
            nativeWidget.attributes["activePressed"] = "false"
        }
        syncVisualsToAndroidView()
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
