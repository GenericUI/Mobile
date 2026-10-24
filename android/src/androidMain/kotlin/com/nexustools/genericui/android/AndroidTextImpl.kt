package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Android `com.google.android.material.textview.MaterialTextView` implementation of [TextNativeImpl].
 */
class AndroidTextImpl(
    tagName: String = "span",
    classPrefix: String = ""
) : AndroidBaseNativeImpl("com.google.android.material.textview.MaterialTextView", classPrefix), TextNativeImpl<AndroidViewHandle> {
    private var maxLinesValue: Int? = null

    init {
        nativeWidget.metadata["tagName"] = tagName
    }

    override fun setText(text: String) {
        nativeWidget.text = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setMaxLines(maxLines: Int?) {
        maxLinesValue = maxLines
        nativeWidget.metadata["maxLines"] = (maxLines ?: Int.MAX_VALUE).toString()
    }

    override fun getMaxLines(): Int? = maxLinesValue
}
