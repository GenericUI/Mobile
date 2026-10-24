package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl

/**
 * Android collapsible `LinearLayout` (`LayoutTransition`) implementation of [CollapseNativeImpl].
 */
class AndroidCollapseImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "collapse"), CollapseNativeImpl<AndroidViewHandle> {
    private var expandedState: Boolean = false
    private var onExpandedChangedListener: ((Boolean) -> Unit)? = null

    override fun setTitle(title: String) {
        nativeWidget.text = title
    }

    override fun setExpanded(expanded: Boolean) {
        expandedState = expanded
        nativeWidget.visibility = expanded
        onExpandedChangedListener?.invoke(expanded)
    }

    override fun isExpanded(): Boolean = expandedState

    override fun setOnExpandedChanged(listener: ((Boolean) -> Unit)?) {
        onExpandedChangedListener = listener
    }
}
