package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * iOS `UILabel` implementation of [TextNativeImpl].
 *
 * Honors [com.nexustools.genericui.style.css.ComponentStyle] typography and text color properties
 * (`color` / `textColor`, `fontFamily`, `fontSize`, `fontWeight`, `fontStyle`, `lineHeight`,
 * `letterSpacing`, `textAlign`, `textDecoration`, `textTransform`, `textOverflow`, `whiteSpace`,
 * `wordBreak`) and [com.nexustools.genericui.style.bootstrap.ComponentBootstrap].
 */
class IosTextImpl(
    val tagName: String = "span",
    classPrefix: String = ""
) : IosBaseNativeImpl("UILabel", classPrefix), TextNativeImpl<IosNativeView> {
    private var rawText: String = ""
    private var maxLinesValue: Int? = null

    init {
        nativeWidget.metadata["tagName"] = tagName
    }

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        val compAlign = baseComponentStyle?.getTextAlign()
        val computedAlign = baseComputedStyle?.textAlign
        val bootAlign = styleSnapshot.textAlignment
        when {
            compAlign == TextAlignment.CENTER || computedAlign == "center" || bootAlign == TextAlignment.CENTER ->
                nativeWidget.metadata["textAlignment"] = "NSTextAlignmentCenter"
            compAlign == TextAlignment.END || computedAlign == "right" || computedAlign == "end" || bootAlign == TextAlignment.END ->
                nativeWidget.metadata["textAlignment"] = "NSTextAlignmentRight"
            else ->
                nativeWidget.metadata["textAlignment"] = "NSTextAlignmentLeft"
        }
        applyDisplayText()
    }

    private fun applyDisplayText() {
        val safeRawText = (rawText as String?) ?: ""
        val cssTransform = baseComponentStyle?.getTextTransform() ?: baseComputedStyle?.textTransform
        val bootTransform = styleSnapshot.textTransform
        val transformed = when {
            cssTransform == TextTransform.UPPERCASE || bootTransform == TextTransform.UPPERCASE -> safeRawText.uppercase()
            cssTransform == TextTransform.LOWERCASE || bootTransform == TextTransform.LOWERCASE -> safeRawText.lowercase()
            cssTransform == TextTransform.CAPITALIZE || bootTransform == TextTransform.CAPITALIZE ->
                safeRawText.split(' ').joinToString(" ") { w -> if (w.isEmpty()) w else w.replaceFirstChar { it.uppercaseChar() } }
            else -> safeRawText
        }
        nativeWidget.text = transformed
        nativeWidget.metadata["renderedText"] = transformed
    }

    override fun setText(text: String) {
        rawText = text
        applyDisplayText()
    }

    override fun getText(): String = nativeWidget.text

    override fun setMaxLines(maxLines: Int?) {
        maxLinesValue = maxLines
        nativeWidget.metadata["numberOfLines"] = (maxLines ?: 0).toString()
    }

    override fun getMaxLines(): Int? = maxLinesValue
}
