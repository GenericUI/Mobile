@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.style.css.ComputedStyle
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLineBreakByClipping
import platform.UIKit.NSLineBreakByTruncatingTail
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.UIColor
import platform.UIKit.UILabel
import platform.UIKit.labelColor

/**
 * Native UIKit `UILabel` implementation of [TextNativeImpl], honoring typography and foreground
 * color styling from [ComponentStyle] (`allowsTypographyStyleOverrides = true`,
 * `allowsAestheticStyleSkinning = false`).
 */
class IosTextImpl(
    val label: UILabel = UILabel(frame = CGRectMake(0.0, 0.0, 200.0, 24.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(label),
    componentClassPrefix = "text"
), TextNativeImpl<UikitViewHandle> {

    override val allowsTypographyStyleOverrides: Boolean = true
    override val allowsAestheticStyleSkinning: Boolean = false

    private var rawText: String = ""
    private var maxLinesValue: Int? = null
    private var activeStyle: ComputedStyle? = null

    init {
        label.numberOfLines = 0L
        label.lineBreakMode = NSLineBreakByWordWrapping
        label.textColor = defaultTextColor()
    }

    override fun setText(text: String) {
        rawText = text
        refreshRenderedText()
    }

    override fun getText(): String = rawText

    override fun setMaxLines(maxLines: Int?) {
        maxLinesValue = maxLines
        label.numberOfLines = (maxLines ?: 0).toLong()
        label.lineBreakMode = if (maxLines != null) NSLineBreakByTruncatingTail else NSLineBreakByWordWrapping
    }

    override fun getMaxLines(): Int? = maxLinesValue

    override fun setTextAlignment(alignment: TextAlignment?) {
        super.setTextAlignment(alignment)
        label.textAlignment = UikitGeometryAndColorUtil.resolveTextAlignment(alignment)
    }

    override fun setFontWeight(weight: FontWeight?) {
        super.setFontWeight(weight)
        refreshRenderedText()
    }

    override fun setTextTransform(transform: TextTransform?) {
        super.setTextTransform(transform)
        refreshRenderedText()
    }

    override fun setTextOverflow(overflow: TextOverflow?) {
        super.setTextOverflow(overflow)
        label.lineBreakMode = when (overflow) {
            TextOverflow.ELLIPSIS -> NSLineBreakByTruncatingTail
            TextOverflow.NOWRAP -> NSLineBreakByClipping
            else -> NSLineBreakByWordWrapping
        }
    }

    override fun onBootstrapAppearanceChanged() {
        refreshRenderedText()
    }

    override fun applyTypographyFromStyle(style: ComputedStyle) {
        activeStyle = style
        style.textAlign?.let {
            label.textAlignment = UikitGeometryAndColorUtil.resolveTextAlignmentFromCss(it)
        }
        refreshRenderedText()
    }

    private fun refreshRenderedText() {
        val style = activeStyle
        val transform = style?.textTransform ?: getTextTransform()
        val display = when (transform) {
            TextTransform.UPPERCASE -> rawText.uppercase()
            TextTransform.LOWERCASE -> rawText.lowercase()
            TextTransform.CAPITALIZE -> rawText.split(' ').joinToString(" ") { w ->
                w.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
            else -> rawText
        }
        val sizePt = style?.fontSizePx?.toDouble() ?: 15.0
        val font = UikitGeometryAndColorUtil.resolveFont(
            sizePt = sizePt,
            weight = getFontWeight(),
            weightNumeric = style?.fontWeightNumeric,
            fontFamily = style?.fontFamily,
            italic = UikitGeometryAndColorUtil.fontWeightToCssItalic(style?.fontStyle)
        )
        val color = UikitGeometryAndColorUtil.resolveBootstrapColor(getTextColorType())?.toUiColor()
            ?: style?.textColor?.toUiColor()
            ?: defaultTextColor()

        label.font = font
        label.textColor = color
        val decoration = style?.textDecoration
        if (decoration != null && decoration != CssTextDecoration.NONE) {
            label.attributedText = UikitGeometryAndColorUtil.buildAttributedText(
                text = display,
                font = font,
                color = color,
                decoration = decoration
            )
        } else {
            label.text = display
        }
    }

    private fun defaultTextColor(): UIColor =
        if (UikitGeometryAndColorUtil.isDarkActive()) UIColor.labelColor else UIColor.labelColor
}
