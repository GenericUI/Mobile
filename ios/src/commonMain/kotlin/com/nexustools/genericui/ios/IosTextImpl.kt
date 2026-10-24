package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * iOS `UILabel` implementation of [TextNativeImpl].
 */
class IosTextImpl(
    val tagName: String = "span",
    classPrefix: String = ""
) : IosBaseNativeImpl("UILabel", classPrefix), TextNativeImpl<IosNativeView> {
    private var maxLinesValue: Int? = null

    override fun setText(text: String) {
        nativeWidget.text = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setMaxLines(maxLines: Int?) {
        maxLinesValue = maxLines
        nativeWidget.metadata["numberOfLines"] = (maxLines ?: 0).toString()
    }

    override fun getMaxLines(): Int? = maxLinesValue
}
