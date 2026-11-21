package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android `androidx.appcompat.widget.AppCompatButton` implementation of [ButtonNativeImpl].
 *
 * - When [BootstrapType] is [BootstrapType.NONE] (or `null`) and not an [AndroidStyledButtonImpl],
 *   preserves the 100% unmodified native Android Material/AppCompat `Button` background.
 * - When [BootstrapType] is non-`NONE` and not an [AndroidStyledButtonImpl], renders the native
 *   `Button` background drawable with NO text into an offscreen `Bitmap.createBitmap(w, h, ARGB_8888)`,
 *   tints the pixels using [BootstrapColorPalette.resolveColor] from the active `ThemeConfig`
 *   preserving native ripples/elevation/borders, draws the tinted surface, and draws contrasting
 *   text ([BootstrapColorPalette.resolveForegroundOn]) and icon on top afterward.
 */
open class AndroidButtonImpl(
    classPrefix: String = "btn"
) : AndroidBaseNativeImpl("com.google.android.material.button.MaterialButton", classPrefix), ButtonNativeImpl<AndroidViewHandle> {
    private var iconName: String? = null
    private var defaultNativeBackground: Drawable? = null
    private var capturedDefaultBackground: Boolean = false
    private var layoutListenerAttached: Boolean = false
    private var lastRenderedBgWidth: Int = -1
    private var lastRenderedBgHeight: Int = -1

    internal fun getDefaultNativeBackgroundDrawable(): Drawable? = defaultNativeBackground

    override fun createDefaultAndroidView(context: Context): View =
        AndroidControlTintRenderUtil.createNativeOrTintedButton(context) { this }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun resolveDefaultPaddingPx(): EdgeInsets {
        if (getPadding() != EdgeInsets.ZERO || getPaddingScale() != null) {
            return super.resolveDefaultPaddingPx()
        }
        if (this is AndroidStyledButtonImpl) {
            return super.resolveDefaultPaddingPx()
        }
        val (hDp, vDp) = when (getSize()) {
            BootstrapSize.SM -> 12f to 6f
            BootstrapSize.LG -> 20f to 10f
            else -> 16f to 8f
        }
        val hPx = AndroidViewHandle.dpToPx(hDp)
        val vPx = AndroidViewHandle.dpToPx(vDp)
        return EdgeInsets(vPx, hPx, vPx, hPx)
    }

    private fun applyResolvedButtonPadding(tv: TextView): Boolean {
        val pad = resolveDefaultPaddingPx()
        if (tv.paddingLeft != pad.left ||
            tv.paddingTop != pad.top ||
            tv.paddingRight != pad.right ||
            tv.paddingBottom != pad.bottom
        ) {
            tv.setPadding(pad.left, pad.top, pad.right, pad.bottom)
            return true
        }
        return false
    }

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        val type = getType()
        val isDark = AndroidViewHandle.isDarkTheme()
        val activeTheme = BootstrapColorPalette.activeThemeConfig
        if (type == null || type == BootstrapType.NONE) {
            if (this !is AndroidStyledButtonImpl || customStyleBackgroundColor == null) {
                nativeWidget.attributes["materialButtonStyle"] = "systemDefault"
                nativeWidget.attributes["useSystemControlAppearance"] = "true"
                nativeWidget.attributes.remove("bootstrapTintColor")
                nativeWidget.attributes.remove("offscreenSpriteTinted")
            }
        } else {
            val tint = BootstrapColorPalette.resolveColor(type, isDark, activeTheme)
            val fg = AndroidControlTintRenderUtil.resolveContrastingForeground(type, isDark, isOutline(), activeTheme)
            nativeWidget.attributes["materialButtonStyle"] = if (isOutline()) "outlined-${type.cssSuffix}" else "filled-${type.cssSuffix}"
            nativeWidget.attributes["useSystemControlAppearance"] = "false"
            nativeWidget.attributes["bootstrapTintColor"] = tint.toHex()
            nativeWidget.attributes["bootstrapForegroundColor"] = fg.toHex()
            nativeWidget.attributes["offscreenSpriteTinted"] = "true"
        }
    }

    /**
     * Renders the empty-text native Android button surface into an ARGB [IntArray] pixel buffer
     * and applies active `ThemeConfig` semantic tinting when [getType] is non-`NONE`.
     */
    fun renderOffscreenButtonPixels(
        width: Int = nativeWidget.bounds.width.takeIf { it > 0 } ?: 120,
        height: Int = nativeWidget.bounds.height.takeIf { it > 0 } ?: 38
    ): IntArray {
        val isDark = AndroidViewHandle.isDarkTheme()
        val type = getType() ?: BootstrapType.NONE
        val tv = nativeWidget.androidView as? TextView
        val density = tv?.resources?.displayMetrics?.density?.takeIf { it > 0f } ?: AndroidViewHandle.density()
        return AndroidControlTintRenderUtil.renderEmptyTextTintedButtonPixels(
            width = width,
            height = height,
            type = type,
            darkMode = isDark,
            outline = isOutline(),
            themeConfig = BootstrapColorPalette.activeThemeConfig,
            button = tv,
            borderRadiusStyle = getBorderRadius(),
            density = density
        )
    }

    /**
     * Renders the empty-text native Android button background into an offscreen [Bitmap] and tints
     * its pixels with [BootstrapColorPalette.resolveColor] from the active `ThemeConfig`.
     */
    fun renderOffscreenButtonBitmap(
        width: Int = nativeWidget.bounds.width.takeIf { it > 0 } ?: 120,
        height: Int = nativeWidget.bounds.height.takeIf { it > 0 } ?: 38
    ): Bitmap? {
        val isDark = AndroidViewHandle.isDarkTheme()
        val type = getType() ?: BootstrapType.NONE
        val tv = nativeWidget.androidView as? TextView
        val density = tv?.resources?.displayMetrics?.density?.takeIf { it > 0f } ?: AndroidViewHandle.density()
        return AndroidControlTintRenderUtil.renderEmptyTextTintedBitmap(
            button = tv,
            width = width,
            height = height,
            type = type,
            darkMode = isDark,
            outline = isOutline(),
            themeConfig = BootstrapColorPalette.activeThemeConfig,
            borderRadiusStyle = getBorderRadius(),
            density = density
        )
    }

    private fun refreshTintedBackgroundForSize(tv: TextView, w: Int, h: Int) {
        if (this is AndroidStyledButtonImpl) return
        val type = getType() ?: BootstrapType.NONE
        val borderRadius = getBorderRadius()
        if (type == BootstrapType.NONE && (borderRadius == null || borderRadius == BorderRadiusStyle.NONE)) {
            lastRenderedBgWidth = -1
            lastRenderedBgHeight = -1
            applyResolvedButtonPadding(tv)
            return
        }
        val expectedPad = resolveDefaultPaddingPx()
        val hadExpectedPadding = tv.paddingLeft == expectedPad.left &&
            tv.paddingTop == expectedPad.top &&
            tv.paddingRight == expectedPad.right &&
            tv.paddingBottom == expectedPad.bottom
        val isDark = AndroidViewHandle.isDarkTheme()
        val activeTheme = BootstrapColorPalette.activeThemeConfig
        val outline = isOutline()
        val density = tv.resources?.displayMetrics?.density?.takeIf { it > 0f } ?: AndroidViewHandle.density()
        if (defaultNativeBackground != null) {
            tv.background = defaultNativeBackground?.constantState?.newDrawable(tv.resources) ?: defaultNativeBackground
        }
        val tintedBmp = AndroidControlTintRenderUtil.renderEmptyTextTintedBitmap(
            button = tv,
            width = w,
            height = h,
            type = type,
            darkMode = isDark,
            outline = outline,
            themeConfig = activeTheme,
            borderRadiusStyle = borderRadius,
            density = density
        )
        if (tintedBmp != null) {
            tv.backgroundTintList = null
            tv.background = AndroidTintedButtonDrawable(tv.resources, tintedBmp)
            lastRenderedBgWidth = w
            lastRenderedBgHeight = h
        }
        val padChanged = applyResolvedButtonPadding(tv)
        if (padChanged && !hadExpectedPadding) {
            tv.post { tv.requestLayout() }
        }
    }

    override fun setText(text: String) {
        nativeWidget.text = text
        syncAndroidView()
    }

    override fun getText(): String = nativeWidget.text

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) {
            nativeWidget.attributes["icon"] = icon
        } else {
            nativeWidget.attributes.remove("icon")
        }
        syncAndroidView()
    }

    override fun getIcon(): String? = iconName

    override fun syncAndroidView() {
        super.syncAndroidView()
        if (this is AndroidStyledButtonImpl) return
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val tv = nativeWidget.androidView as? TextView ?: return@runCatching
                if (!capturedDefaultBackground) {
                    val curBg = tv.background
                    if (curBg !is AndroidTintedButtonDrawable) {
                        defaultNativeBackground = curBg?.constantState?.newDrawable(tv.resources) ?: curBg
                        capturedDefaultBackground = true
                    }
                }
                if (!layoutListenerAttached) {
                    layoutListenerAttached = true
                    tv.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                        val newW = right - left
                        val newH = bottom - top
                        val oldW = oldRight - oldLeft
                        val oldH = oldBottom - oldTop
                        if (newW > 0 && newH > 0 && (newW != oldW || newH != oldH || newW != lastRenderedBgWidth || newH != lastRenderedBgHeight)) {
                            (v as? TextView)?.let { btnView ->
                                refreshTintedBackgroundForSize(btnView, newW, newH)
                            }
                        }
                    }
                }
                tv.isAllCaps = false
                tv.gravity = Gravity.CENTER
                tv.includeFontPadding = false
                tv.setOnClickListener {
                    if (isEnabled()) {
                        dispatchClick()
                    }
                }

                val isDark = AndroidViewHandle.isDarkTheme()
                val activeTheme = BootstrapColorPalette.activeThemeConfig
                val type = getType() ?: BootstrapType.NONE
                val outline = isOutline()
                val size = getSize()

                val (minHeightDp, defaultSp) = when (size) {
                    BootstrapSize.SM -> 32f to 12f
                    BootstrapSize.LG -> 46f to 15f
                    else -> 38f to 13f
                }

                val minHPx = AndroidViewHandle.dpToPx(minHeightDp)
                tv.minHeight = minHPx
                tv.minimumHeight = minHPx
                val minWPx = if (nativeWidget.text.isNotEmpty()) 0 else minHPx
                tv.minWidth = minWPx
                tv.minimumWidth = minWPx

                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, defaultSp)
                tv.setTypeface(null, Typeface.BOLD)

                val fgColor = AndroidControlTintRenderUtil.resolveContrastingForeground(
                    type = type,
                    darkMode = isDark,
                    outline = outline,
                    themeConfig = activeTheme
                )
                val fgArgb = fgColor.toArgbInt()
                val icon = iconName
                val ctx = tv.context ?: AndroidViewHandle.activeContext
                val drawable = if (!icon.isNullOrBlank() && ctx != null) {
                    AndroidImageImpl.resolveDrawable(ctx, icon, fgArgb)
                } else {
                    null
                }
                val displayText = if (
                    drawable == null &&
                    !icon.isNullOrBlank() &&
                    !icon.startsWith("res://") &&
                    !icon.startsWith("http")
                ) {
                    if (nativeWidget.text.isNotEmpty()) "$icon ${nativeWidget.text}" else icon
                } else {
                    nativeWidget.text
                }
                tv.setHorizontallyScrolling(false)
                tv.scrollX = 0
                tv.scrollY = 0
                val targetMaxLines = if (displayText.contains('\n')) Int.MAX_VALUE else 1
                if (tv.maxLines != targetMaxLines) {
                    tv.maxLines = targetMaxLines
                }

                val borderRadius = getBorderRadius()
                if (type == BootstrapType.NONE && (borderRadius == null || borderRadius == BorderRadiusStyle.NONE)) {
                    tv.backgroundTintList = null
                    lastRenderedBgWidth = -1
                    lastRenderedBgHeight = -1
                    if (defaultNativeBackground != null && tv.background !== defaultNativeBackground) {
                        tv.background = defaultNativeBackground?.constantState?.newDrawable(tv.resources) ?: defaultNativeBackground
                    }
                    tv.setTextColor(fgArgb)
                } else {
                    val w = tv.width.takeIf { it > 0 }
                        ?: preferredSizeValue?.width?.takeIf { it > 0 }?.let { AndroidViewHandle.dpToPx(it) }
                        ?: AndroidViewHandle.dpToPx(120)
                    val h = tv.height.takeIf { it > 0 }
                        ?: preferredSizeValue?.height?.takeIf { it > 0 }?.let { AndroidViewHandle.dpToPx(it) }
                        ?: minHPx.coerceAtLeast(36)
                    refreshTintedBackgroundForSize(tv, w, h)
                    tv.setTextColor(fgArgb)
                }
                val padChanged = applyResolvedButtonPadding(tv)

                val textChanged = tv.text?.toString() != displayText
                if (textChanged) {
                    tv.text = displayText
                }
                if (drawable != null) {
                    val iconSizePx = AndroidViewHandle.dpToPx(16)
                    drawable.setBounds(0, 0, iconSizePx, iconSizePx)
                    tv.setCompoundDrawables(drawable, null, null, null)
                    tv.compoundDrawablePadding = if (nativeWidget.text.isNotEmpty()) AndroidViewHandle.dpToPx(6) else 0
                } else {
                    tv.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
                if (padChanged || textChanged) {
                    tv.requestLayout()
                }
                tv.invalidate()
                nativeWidget.onViewMutated?.invoke()
            }
        }
    }
}
