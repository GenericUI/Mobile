package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android `SpannableStringBuilder` / `AppCompatTextView` implementation of [MarkdownViewNativeImpl].
 *
 * Honors [com.nexustools.genericui.style.css.ComponentStyle] typography and text color properties
 * (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`,
 * `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`,
 * `wordBreak`) and [com.nexustools.genericui.style.bootstrap.ComponentBootstrap].
 */
class AndroidMarkdownViewImpl : AndroidBaseNativeImpl("com.google.android.material.textview.MaterialTextView", "markdown-body"), MarkdownViewNativeImpl<AndroidViewHandle> {
    private var rawMarkdown: String = ""

    override fun createDefaultAndroidView(context: Context): View =
        AppCompatTextView(context)

    override fun setMarkdown(markdown: String) {
        rawMarkdown = markdown
        nativeWidget.text = markdown
        syncAndroidView()
    }

    override fun getMarkdown(): String = rawMarkdown

    override fun syncAndroidView() {
        val safeRawMarkdown = (rawMarkdown as String?) ?: ""
        val compStyle = baseComponentStyle
        val computed = baseComputedStyle
        val cssTransform = compStyle?.getTextTransform() ?: computed?.textTransform
        val bootTransform = styleSnapshot.textTransform
        val displayMarkdown = when {
            cssTransform == TextTransform.UPPERCASE || bootTransform == TextTransform.UPPERCASE -> safeRawMarkdown.uppercase()
            cssTransform == TextTransform.LOWERCASE || bootTransform == TextTransform.LOWERCASE -> safeRawMarkdown.lowercase()
            cssTransform == TextTransform.CAPITALIZE || bootTransform == TextTransform.CAPITALIZE ->
                safeRawMarkdown.split(' ').joinToString(" ") { w -> if (w.isEmpty()) w else w.replaceFirstChar { it.uppercaseChar() } }
            else -> safeRawMarkdown
        }
        nativeWidget.text = displayMarkdown
        nativeWidget.attributes["renderedText"] = displayMarkdown
        super.syncAndroidView()
        runCatching {
            val tv = nativeWidget.androidView as? TextView ?: return@runCatching
            if (tv.text?.toString() != displayMarkdown) {
                tv.text = displayMarkdown
            }
            val spSize = customStyleFontSizePx?.takeIf { it > 0f } ?: 14f
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, spSize)

            val fw = styleSnapshot.fontWeight
            val numericWeight = customStyleFontWeightNumeric
            val isBold = fw == FontWeight.BOLD || fw == FontWeight.SEMIBOLD || fw == FontWeight.BOLDER ||
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

            val compAlign = compStyle?.getTextAlign()
            val computedAlign = computed?.textAlign
            val bootAlign = styleSnapshot.textAlignment
            tv.gravity = when {
                compAlign == TextAlignment.CENTER || computedAlign == "center" || bootAlign == TextAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
                compAlign == TextAlignment.END || computedAlign == "right" || computedAlign == "end" || bootAlign == TextAlignment.END -> Gravity.END
                else -> Gravity.START
            }

            val isDark = AndroidViewHandle.isDarkTheme()
            val activeTheme = BootstrapColorPalette.activeThemeConfig
            val themeColors = activeTheme.resolveEffectiveColors(isDark)
            val defaultTextColor = themeColors.onSurface ?: themeColors.onBackground ?: if (isDark) RgbaColor(235, 235, 240) else RgbaColor(28, 28, 30)
            val textArgb = customStyleTextColor?.toArgbInt()
                ?: (styleSnapshot.textColorType ?: styleSnapshot.type)?.takeIf { it != BootstrapType.NONE }?.let {
                    BootstrapColorPalette.resolveColor(it, isDark, activeTheme).toArgbInt()
                }
                ?: defaultTextColor.toArgbInt()
            tv.setTextColor(textArgb)
        }
    }
}
