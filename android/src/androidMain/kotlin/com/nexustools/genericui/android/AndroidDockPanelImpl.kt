package com.nexustools.genericui.android

import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl

/**
 * Android implementation of [DockPanelNativeImpl].
 */
class AndroidDockPanelImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-panel"), DockPanelNativeImpl<AndroidViewHandle> {
    private var titleText: String = ""
    private var iconName: String? = null
    private var collapsedState: Boolean = false
    private var floatingState: Boolean = false
    private var headerActions: List<DockHeaderAction> = emptyList()

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
    }

    override fun getTitle(): String = titleText

    override fun setIcon(icon: String?) {
        iconName = icon
    }

    override fun setCollapsed(collapsed: Boolean) {
        collapsedState = collapsed
    }

    override fun isCollapsed(): Boolean = collapsedState

    override fun setFloating(floating: Boolean) {
        floatingState = floating
    }

    override fun isFloating(): Boolean = floatingState

    override fun setHeaderActions(actions: List<DockHeaderAction>) {
        headerActions = actions
    }
}
