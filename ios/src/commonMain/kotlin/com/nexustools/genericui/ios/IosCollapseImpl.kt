package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl

/**
 * iOS collapsible section implementation of [CollapseNativeImpl].
 */
class IosCollapseImpl : IosBaseContainerNativeImpl("UIView", "collapse"), CollapseNativeImpl<IosNativeView> {
    private var expandedState: Boolean = false
    private var onExpandedChangedListener: ((Boolean) -> Unit)? = null

    override fun setTitle(title: String) {
        nativeWidget.text = title
    }

    override fun setExpanded(expanded: Boolean) {
        expandedState = expanded
        onExpandedChangedListener?.invoke(expanded)
    }

    override fun isExpanded(): Boolean = expandedState

    override fun setOnExpandedChanged(listener: ((Boolean) -> Unit)?) {
        onExpandedChangedListener = listener
    }
}
