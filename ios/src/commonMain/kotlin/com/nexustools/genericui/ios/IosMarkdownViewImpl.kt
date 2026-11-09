package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl

/**
 * iOS `UITextView` (`NSAttributedString` markdown) implementation of [MarkdownViewNativeImpl].
 *
 * Honors [com.nexustools.genericui.style.css.ComponentStyle] typography and text color properties
 * and [com.nexustools.genericui.style.bootstrap.ComponentBootstrap].
 */
class IosMarkdownViewImpl : IosBaseNativeImpl("UITextView", "markdown-body"), MarkdownViewNativeImpl<IosNativeView> {
    private var markdownSource: String = ""

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
        applyDisplayMarkdown()
    }

    private fun applyDisplayMarkdown() {
        val safeMarkdown = (markdownSource as String?) ?: ""
        val cssTransform = baseComponentStyle?.getTextTransform() ?: baseComputedStyle?.textTransform
        val bootTransform = styleSnapshot.textTransform
        val transformed = when {
            cssTransform == TextTransform.UPPERCASE || bootTransform == TextTransform.UPPERCASE -> safeMarkdown.uppercase()
            cssTransform == TextTransform.LOWERCASE || bootTransform == TextTransform.LOWERCASE -> safeMarkdown.lowercase()
            cssTransform == TextTransform.CAPITALIZE || bootTransform == TextTransform.CAPITALIZE ->
                safeMarkdown.split(' ').joinToString(" ") { w -> if (w.isEmpty()) w else w.replaceFirstChar { it.uppercaseChar() } }
            else -> safeMarkdown
        }
        nativeWidget.text = transformed
        nativeWidget.metadata["renderedText"] = transformed
    }

    override fun setMarkdown(markdown: String) {
        markdownSource = markdown
        applyDisplayMarkdown()
    }

    override fun getMarkdown(): String = markdownSource
}
