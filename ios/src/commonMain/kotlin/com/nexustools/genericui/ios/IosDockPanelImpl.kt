package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl

/**
 * iOS implementation of [DockPanelNativeImpl].
 */
class IosDockPanelImpl : IosBaseContainerNativeImpl("UIView", "dock-panel"), DockPanelNativeImpl<IosNativeView> {
    private var titleText: String = ""
    private var collapsedState: Boolean = false
    private var floatingState: Boolean = false

    override fun setTitle(title: String) { titleText = title }
    override fun getTitle(): String = titleText
    override fun setIcon(icon: String?) {}
    override fun setCollapsed(collapsed: Boolean) { collapsedState = collapsed }
    override fun isCollapsed(): Boolean = collapsedState
    override fun setFloating(floating: Boolean) { floatingState = floating }
    override fun isFloating(): Boolean = floatingState
    override fun setHeaderActions(actions: List<DockHeaderAction>) {}
}
