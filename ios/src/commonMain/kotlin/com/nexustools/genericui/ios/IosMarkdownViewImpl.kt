package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl

/**
 * iOS `UITextView` (`NSAttributedString` markdown) implementation of [MarkdownViewNativeImpl].
 */
class IosMarkdownViewImpl : IosBaseNativeImpl("UITextView", "markdown-body"), MarkdownViewNativeImpl<IosNativeView> {
    private var markdownSource: String = ""

    override fun setMarkdown(markdown: String) {
        markdownSource = markdown
        nativeWidget.text = markdown
    }

    override fun getMarkdown(): String = markdownSource
}
