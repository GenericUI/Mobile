package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl

/**
 * Android `SpannableStringBuilder` / `MaterialTextView` implementation of [MarkdownViewNativeImpl].
 */
class AndroidMarkdownViewImpl : AndroidBaseNativeImpl("com.google.android.material.textview.MaterialTextView", "markdown-body"), MarkdownViewNativeImpl<AndroidViewHandle> {
    override fun setMarkdown(markdown: String) {
        nativeWidget.text = markdown
    }

    override fun getMarkdown(): String = nativeWidget.text
}
