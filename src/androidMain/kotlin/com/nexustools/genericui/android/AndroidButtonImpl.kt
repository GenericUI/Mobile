package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
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
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeConfig

/**
 * Android `androidx.appcompat.widget.AppCompatButton` implementation of [ButtonNativeImpl].
 *
 * - When [BootstrapType] is [BootstrapType.NONE] (or `null`), `!isOutline()`, and no custom
 *   corner radius is set (and not an [AndroidStyledButtonImpl]), preserves the 100% unmodified
 *   native Android Material/AppCompat `Button` background (`backgroundTintList = null`).
 * - When [BootstrapType] is a standard filled semantic type without custom corner radius, applies
 *   native Android `ColorStateList.valueOf(tintArgb)` via `backgroundTintList` and contrasting text/icon
 *   foreground colors from [BootstrapColorPalette].
 * - For `outline`, `LINK`, or pill/custom corner radius variants, configures a native Android
 *   [GradientDrawable] wrapped in a native [RippleDrawable] with [GradientDrawable.setColor],
 *   [GradientDrawable.setStroke], and [GradientDrawable.setCornerRadius].
 */
open class AndroidButtonImpl(
    classPrefix: String = "btn"
) : AndroidBaseNativeImpl("com.google.android.material.button.MaterialButton", classPrefix), ButtonNativeImpl<AndroidViewHandle> {
    private var iconName: String? = null
    private var defaultNativeBackground: Drawable? = null
    private var capturedDefaultBackground: Boolean = false
    private var lastAppliedCustomBackground: Drawable? = null

    override fun createDefaultAndroidView(context: Context): View =
        AndroidControlTintRenderUtil.createNativeOrTintedButton(context, implProvider = { this })

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
        AndroidControlTintRenderUtil.syncButtonBootstrapAttributes(
            nativeWidget = nativeWidget,
            type = getType(),
            outline = isOutline(),
            hasCustomStyledBackground = this is AndroidStyledButtonImpl && customStyleBackgroundColor != null
        )
    }

    private fun captureDefaultBackgroundIfNeeded(tv: TextView) {
        if (capturedDefaultBackground) return
        val curBg = tv.background
        if (curBg !== lastAppliedCustomBackground) {
            defaultNativeBackground = curBg?.constantState?.newDrawable(tv.resources) ?: curBg
            capturedDefaultBackground = true
        }
    }

    private fun restoreDefaultNativeBackground(tv: TextView) {
        tv.backgroundTintList = null
        if (lastAppliedCustomBackground != null) {
            tv.background = defaultNativeBackground?.constantState?.newDrawable(tv.resources) ?: defaultNativeBackground
        }
        lastAppliedCustomBackground = null
    }

    private fun applyCustomShapeBackground(
        tv: TextView,
        type: BootstrapType,
        outline: Boolean,
        borderRadius: BorderRadiusStyle?,
        isDark: Boolean,
        activeTheme: ThemeConfig,
        fgColor: RgbaColor
    ) {
        val cornerRadiusPx = when (borderRadius) {
            BorderRadiusStyle.NONE -> 0f
            BorderRadiusStyle.PILL, BorderRadiusStyle.CIRCLE -> AndroidViewHandle.dpToPxFloat(999f)
            null -> AndroidViewHandle.dpToPxFloat(4f)
            else -> AndroidViewHandle.dpToPxFloat(borderRadius.radiusPx.toFloat())
        }
        val fillArgb: Int
        val strokeWidthPx: Int
        val strokeArgb: Int
        val rippleArgb: Int
        when {
            type == BootstrapType.LINK -> {
                val accent = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark, activeTheme)
                fillArgb = Color.TRANSPARENT
                strokeWidthPx = 0
                strokeArgb = Color.TRANSPARENT
                rippleArgb = Color.argb(44, accent.r, accent.g, accent.b)
            }
            outline -> {
                val accent = if (type != BootstrapType.NONE) {
                    BootstrapColorPalette.resolveColor(type, isDark, activeTheme)
                } else {
                    fgColor
                }
                fillArgb = Color.TRANSPARENT
                strokeWidthPx = AndroidViewHandle.dpToPx(1.5f).coerceAtLeast(1)
                strokeArgb = accent.toArgbInt()
                rippleArgb = Color.argb(48, accent.r, accent.g, accent.b)
            }
            type != BootstrapType.NONE -> {
                val fill = BootstrapColorPalette.resolveColor(type, isDark, activeTheme)
                fillArgb = fill.toArgbInt()
                strokeWidthPx = 0
                strokeArgb = Color.TRANSPARENT
                rippleArgb = Color.argb(64, fgColor.r, fgColor.g, fgColor.b)
            }
            else -> {
                val colors = activeTheme.resolveEffectiveColors(isDark)
                val neutralFill = colors.surface ?: if (isDark) RgbaColor(56, 58, 66) else RgbaColor(232, 234, 240)
                val neutralStroke = if (isDark) RgbaColor(88, 92, 102) else RgbaColor(198, 202, 210)
                fillArgb = neutralFill.toArgbInt()
                strokeWidthPx = AndroidViewHandle.dpToPx(1f).coerceAtLeast(1)
                strokeArgb = neutralStroke.toArgbInt()
                rippleArgb = Color.argb(48, fgColor.r, fgColor.g, fgColor.b)
            }
        }
        val shapeDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillArgb)
            if (cornerRadiusPx > 0f) {
                cornerRadius = cornerRadiusPx
            }
            if (strokeWidthPx > 0) {
                setStroke(strokeWidthPx, strokeArgb)
            }
        }
        val maskDrawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            if (cornerRadiusPx > 0f) {
                cornerRadius = cornerRadiusPx
            }
        }
        val rippleDrawable = RippleDrawable(
            ColorStateList.valueOf(rippleArgb),
            shapeDrawable,
            maskDrawable
        )
        tv.backgroundTintList = null
        tv.background = rippleDrawable
        lastAppliedCustomBackground = rippleDrawable
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
            val tv = nativeWidget.androidView as? TextView ?: return@runOnMainThread
            captureDefaultBackgroundIfNeeded(tv)
            tv.isAllCaps = false
            tv.gravity = Gravity.CENTER
            tv.includeFontPadding = false
            if (!nativeWidget.layerPassThrough) {
                tv.isClickable = isEnabled()
                tv.setOnClickListener {
                    if (isEnabled()) {
                        dispatchClick()
                    }
                }
            }
            if (!isFocusable()) {
                applyFocusableStateToView(tv, false)
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
            val hasCustomCornerRadius = borderRadius != null && borderRadius != BorderRadiusStyle.NONE
            when {
                type == BootstrapType.NONE && !outline && !hasCustomCornerRadius -> {
                    restoreDefaultNativeBackground(tv)
                }
                type != BootstrapType.NONE && type != BootstrapType.LINK && !outline && !hasCustomCornerRadius -> {
                    if (lastAppliedCustomBackground != null && defaultNativeBackground != null) {
                        tv.background = defaultNativeBackground?.constantState?.newDrawable(tv.resources) ?: defaultNativeBackground
                    }
                    lastAppliedCustomBackground = null
                    val tintArgb = BootstrapColorPalette.resolveColor(type, isDark, activeTheme).toArgbInt()
                    tv.backgroundTintList = ColorStateList.valueOf(tintArgb)
                }
                else -> {
                    applyCustomShapeBackground(
                        tv = tv,
                        type = type,
                        outline = outline,
                        borderRadius = borderRadius,
                        isDark = isDark,
                        activeTheme = activeTheme,
                        fgColor = fgColor
                    )
                }
            }
            tv.setTextColor(fgArgb)

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
