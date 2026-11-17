@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSAttributedString
import platform.Foundation.NSMutableAttributedString
import platform.Foundation.appendAttributedString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.UITextView
import platform.UIKit.labelColor

/**
 * Native UIKit `UITextView` (`editable = false`) implementation of [MarkdownViewNativeImpl]
 * converting Markdown blocks into styled `NSAttributedString` runs while honoring
 * [ComponentStyle] typography overrides.
 */
class IosMarkdownViewImpl(
    val textView: UITextView = UITextView(frame = CGRectMake(0.0, 0.0, 320.0, 200.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(textView),
    componentClassPrefix = "markdown-view"
), MarkdownViewNativeImpl<UikitViewHandle> {

    override val allowsTypographyStyleOverrides: Boolean = true
    override val allowsAestheticStyleSkinning: Boolean = false

    private var rawMarkdown: String = ""
    private var baseFontSizePt: Double = 15.0
    private var baseFontFamily: String? = null
    private var baseTextColor: UIColor = UIColor.labelColor
    private var textTransformValue: TextTransform? = null

    init {
        textView.setEditable(false)
        textView.selectable = true
        textView.backgroundColor = UIColor.clearColor
        textView.textColor = baseTextColor
    }

    override fun setMarkdown(markdown: String) {
        rawMarkdown = markdown
        renderAttributedMarkdown()
    }

    override fun getMarkdown(): String = rawMarkdown

    override fun applyTypographyFromStyle(style: ComputedStyle) {
        style.fontSizePx?.let { baseFontSizePt = it.toDouble() }
        style.fontFamily?.let { baseFontFamily = it }
        style.textColor?.let { baseTextColor = it.toUiColor() }
        style.textTransform?.let { textTransformValue = it }
        style.textAlign?.let {
            textView.textAlignment = UikitGeometryAndColorUtil.resolveTextAlignmentFromCss(it)
        }
        renderAttributedMarkdown()
    }

    private fun renderAttributedMarkdown() {
        val result = NSMutableAttributedString()
        val lines = rawMarkdown.lines()
        for ((index, rawLine) in lines.withIndex()) {
            val line = rawLine.trimEnd()
            val (baseClean, sizeMul, isBold, isCode) = when {
                line.startsWith("### ") -> Quad(line.removePrefix("### "), 1.15, true, false)
                line.startsWith("## ") -> Quad(line.removePrefix("## "), 1.30, true, false)
                line.startsWith("# ") -> Quad(line.removePrefix("# "), 1.50, true, false)
                line.startsWith("- ") || line.startsWith("* ") -> Quad("• ${line.drop(2)}", 1.0, false, false)
                line.startsWith("`") && line.endsWith("`") && line.length >= 2 -> Quad(line.trim('`'), 0.95, false, true)
                else -> Quad(line.replace("**", ""), 1.0, line.contains("**"), false)
            }
            val cleanText = when (textTransformValue) {
                TextTransform.UPPERCASE -> baseClean.uppercase()
                TextTransform.LOWERCASE -> baseClean.lowercase()
                TextTransform.CAPITALIZE -> baseClean.split(' ').joinToString(" ") { w ->
                    w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                else -> baseClean
            }
            val font = UikitGeometryAndColorUtil.resolveFont(
                sizePt = baseFontSizePt * sizeMul,
                weightNumeric = if (isBold) 700 else 400,
                fontFamily = if (isCode) "Menlo" else baseFontFamily
            )
            val attrs = LinkedHashMap<Any?, Any?>()
            NSFontAttributeName?.let { attrs[it] = font }
            NSForegroundColorAttributeName?.let { attrs[it] = baseTextColor }
            val lineStr = if (index < lines.lastIndex) "$cleanText\n" else cleanText
            result.appendAttributedString(NSAttributedString.create(string = lineStr, attributes = attrs))
        }
        textView.attributedText = result
    }

    private data class Quad(val text: String, val scale: Double, val bold: Boolean, val code: Boolean)
}
