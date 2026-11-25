package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.css.CssWhiteSpace
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android `androidx.appcompat.widget.AppCompatTextView` implementation of [TextNativeImpl].
 *
 * Honors [com.nexustools.genericui.style.css.ComponentStyle] typography and text color properties
 * (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`,
 * `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`,
 * `wordBreak`) and [com.nexustools.genericui.style.bootstrap.ComponentBootstrap].
 */
class AndroidTextImpl(
    private val tagName: String = "span",
    classPrefix: String = ""
) : AndroidBaseNativeImpl("com.google.android.material.textview.MaterialTextView", classPrefix), TextNativeImpl<AndroidViewHandle> {
    private var rawText: String = ""
    private var maxLinesValue: Int? = null

    init {
        nativeWidget.attributes["tagName"] = tagName
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AppCompatTextView(context)

    override fun shouldApplyBaseBackgroundDrawable(): Boolean =
        if (isBadge() || customStyleBackgroundColor != null || customStyleBorderColor != null || (customStyleBorderWidthPx ?: 0f) > 0f) {
            false
        } else {
            super.shouldApplyBaseBackgroundDrawable()
        }

    override fun setText(text: String) {
        rawText = text
        nativeWidget.text = resolveTransformedText(text)
        syncAndroidView()
    }

    override fun getText(): String = nativeWidget.text

    override fun setMaxLines(maxLines: Int?) {
        maxLinesValue = maxLines
        nativeWidget.maxLines = maxLines ?: Int.MAX_VALUE
        nativeWidget.attributes["maxLines"] = (maxLines ?: Int.MAX_VALUE).toString()
        syncAndroidView()
    }

    override fun getMaxLines(): Int? = maxLinesValue

    private fun resolveTransformedText(input: String): String {
        val cssTransform = baseComponentStyle?.getTextTransform() ?: baseComputedStyle?.textTransform
        val bootTransform = styleSnapshot.textTransform
        return when {
            cssTransform == TextTransform.UPPERCASE || bootTransform == TextTransform.UPPERCASE -> input.uppercase()
            cssTransform == TextTransform.LOWERCASE || bootTransform == TextTransform.LOWERCASE -> input.lowercase()
            cssTransform == TextTransform.CAPITALIZE || bootTransform == TextTransform.CAPITALIZE ->
                input.split(' ').joinToString(" ") { w -> if (w.isEmpty()) w else w.replaceFirstChar { it.uppercaseChar() } }
            else -> input
        }
    }

    private fun isBadge(): Boolean =
        componentClassPrefix.contains("badge") ||
            nativeWidget.classes.contains("badge")

    private fun resolveHeadingLevel(): Int? {
        val attrLevel = nativeWidget.attributes["data-heading-level"]?.toIntOrNull()
        if (attrLevel != null && attrLevel in 1..6) return attrLevel
        val tagLower = (nativeWidget.attributes["tagName"] ?: tagName).trim().lowercase()
        if (tagLower.length == 2 && tagLower[0] == 'h' && tagLower[1] in '1'..'6') {
            return tagLower[1] - '0'
        }
        val prefixLower = componentClassPrefix.trim().lowercase()
        if (prefixLower.length == 2 && prefixLower[0] == 'h' && prefixLower[1] in '1'..'6') {
            return prefixLower[1] - '0'
        }
        return null
    }

    override fun syncAndroidView() {
        val safeRawText = (rawText as String?) ?: ""
        val displayStr = resolveTransformedText(safeRawText)
        nativeWidget.text = displayStr
        nativeWidget.attributes["renderedText"] = displayStr
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val tv = nativeWidget.androidView as? TextView ?: return@runCatching
                if (tv.text?.toString() != nativeWidget.text) {
                    tv.text = nativeWidget.text
                }
                val compStyle = baseComponentStyle
                val computed = baseComputedStyle
                val whiteSpace = compStyle?.getWhiteSpace()?.cssValue ?: computed?.whiteSpace
                val textOverflow = compStyle?.getTextOverflow() ?: computed?.textOverflow
                val bootOverflow = styleSnapshot.textOverflow
                val maxL = maxLinesValue ?: if (whiteSpace == CssWhiteSpace.NOWRAP.cssValue || bootOverflow == TextOverflow.ELLIPSIS || bootOverflow == TextOverflow.NOWRAP) 1 else null
                if (maxL != null && maxL > 0 && maxL < Int.MAX_VALUE) {
                    tv.maxLines = maxL
                    tv.ellipsize = if (textOverflow == TextOverflow.WRAP) null else TextUtils.TruncateAt.END
                } else {
                    tv.maxLines = Int.MAX_VALUE
                    tv.ellipsize = if (textOverflow == TextOverflow.ELLIPSIS || bootOverflow == TextOverflow.ELLIPSIS) {
                        TextUtils.TruncateAt.END
                    } else null
                }

                val isDark = AndroidViewHandle.isDarkTheme()
                val activeTheme = BootstrapColorPalette.activeThemeConfig
                if (isBadge()) {
                    val badgeType = getType()?.takeIf { it != BootstrapType.NONE } ?: BootstrapType.PRIMARY
                    val bgArgb = customStyleBackgroundColor?.toArgbInt()
                        ?: BootstrapColorPalette.resolveColor(badgeType, isDark, activeTheme).toArgbInt()
                    val fgArgb = customStyleTextColor?.toArgbInt()
                        ?: BootstrapColorPalette.resolveForegroundOn(badgeType, isDark, activeTheme).toArgbInt()
                    val pill = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        val radiusDp = customStyleBorderRadiusPx?.takeIf { it >= 0f } ?: 999f
                        cornerRadius = AndroidViewHandle.dpToPxFloat(radiusDp)
                        setColor(bgArgb)
                        val bw = (customStyleBorderWidthPx ?: 0f).coerceAtLeast(0f)
                        val bc = customStyleBorderColor
                        if (bw > 0f && bc != null) {
                            setStroke(AndroidViewHandle.dpToPx(bw).coerceAtLeast(1), bc.toArgbInt())
                        }
                    }
                    tv.background = pill
                    val p = getPadding()
                    if (p.left == 0 && p.top == 0 && p.right == 0 && p.bottom == 0 && getPaddingScale() == null) {
                        val hPad = AndroidViewHandle.dpToPx(8)
                        val vPad = AndroidViewHandle.dpToPx(3)
                        tv.setPadding(hPad, vPad, hPad, vPad)
                    }
                    val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: 11f
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)
                    tv.setTypeface(null, Typeface.BOLD)
                    tv.setTextColor(fgArgb)
                    tv.gravity = Gravity.CENTER
                } else {
                    val headingLevel = resolveHeadingLevel()
                    val defaultSp = when (headingLevel) {
                        1 -> 22f
                        2 -> 19f
                        3 -> 17f
                        4 -> 15f
                        5 -> 14f
                        6 -> 13f
                        else -> 14f
                    }
                    val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: defaultSp
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)

                    val fw = styleSnapshot.fontWeight
                    val numericWeight = customStyleFontWeightNumeric
                    val isBold = headingLevel != null ||
                        fw == FontWeight.BOLD ||
                        fw == FontWeight.SEMIBOLD ||
                        fw == FontWeight.BOLDER ||
                        (numericWeight != null && numericWeight >= 600)
                    val isItalic = (compStyle?.getFontStyle() ?: computed?.fontStyle) == CssFontStyle.ITALIC
                    val tfStyle = when {
                        isBold && isItalic -> Typeface.BOLD_ITALIC
                        isBold -> Typeface.BOLD
                        isItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    val familyName = compStyle?.getFontFamily()?.resolvedPrimaryName() ?: computed?.fontFamily
                    val baseTf = if (!familyName.isNullOrBlank()) Typeface.create(familyName, tfStyle) else null
                    tv.setTypeface(baseTf, tfStyle)

                    val textDecoration = compStyle?.getTextDecoration() ?: computed?.textDecoration
                    var flags = tv.paintFlags and (Paint.UNDERLINE_TEXT_FLAG or Paint.STRIKE_THRU_TEXT_FLAG).inv()
                    if (textDecoration == CssTextDecoration.UNDERLINE) {
                        flags = flags or Paint.UNDERLINE_TEXT_FLAG
                    } else if (textDecoration == CssTextDecoration.LINE_THROUGH) {
                        flags = flags or Paint.STRIKE_THRU_TEXT_FLAG
                    }
                    tv.paintFlags = flags

                    val letterSpacingPx = (compStyle?.getLetterSpacing() ?: computed?.letterSpacing)?.resolvePx()
                    if (letterSpacingPx != null && spSize > 0f) {
                        tv.letterSpacing = (letterSpacingPx / spSize).coerceIn(-0.2f, 1.0f)
                    }

                    val hasCustomSurface = customStyleBackgroundColor != null ||
                        customStyleBorderColor != null ||
                        (customStyleBorderWidthPx ?: 0f) > 0f
                    if (hasCustomSurface) {
                        val surface = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            val r = (customStyleBorderRadiusPx ?: 0f).coerceAtLeast(0f)
                            if (r > 0f) {
                                cornerRadius = AndroidViewHandle.dpToPxFloat(r)
                            }
                            customStyleBackgroundColor?.let { setColor(it.toArgbInt()) }
                            val bw = (customStyleBorderWidthPx ?: 0f).coerceAtLeast(0f)
                            val bc = customStyleBorderColor
                            if (bw > 0f && bc != null) {
                                setStroke(AndroidViewHandle.dpToPx(bw).coerceAtLeast(1), bc.toArgbInt())
                            }
                        }
                        tv.background = surface
                        val pad = getPadding()
                        if (pad.left != 0 || pad.top != 0 || pad.right != 0 || pad.bottom != 0) {
                            tv.setPadding(
                                AndroidViewHandle.dpToPx(pad.left),
                                AndroidViewHandle.dpToPx(pad.top),
                                AndroidViewHandle.dpToPx(pad.right),
                                AndroidViewHandle.dpToPx(pad.bottom)
                            )
                        }
                    }

                    val compAlign = compStyle?.getTextAlign()
                    val computedAlign = computed?.textAlign
                    val bootAlign = styleSnapshot.textAlignment
                    tv.gravity = when {
                        compAlign == TextAlignment.CENTER || computedAlign == "center" || bootAlign == TextAlignment.CENTER ->
                            if (hasCustomSurface) Gravity.CENTER else Gravity.CENTER_HORIZONTAL
                        compAlign == TextAlignment.END || computedAlign == "right" || computedAlign == "end" || bootAlign == TextAlignment.END -> Gravity.END
                        else -> Gravity.START
                    }

                    val themeColors = activeTheme.resolveEffectiveColors(isDark)
                    val defaultTextColor = themeColors.onSurface ?: themeColors.onBackground ?: if (isDark) RgbaColor(235, 235, 240) else RgbaColor(28, 28, 30)
                    val textArgb = customStyleTextColor?.toArgbInt()
                        ?: styleSnapshot.textColorType?.takeIf { it != BootstrapType.NONE }?.let {
                            BootstrapColorPalette.resolveColor(it, isDark, activeTheme).toArgbInt()
                        }
                        ?: defaultTextColor.toArgbInt()
                    tv.setTextColor(textArgb)
                }
                nativeWidget.onViewMutated?.invoke()
            }
        }
    }
}
