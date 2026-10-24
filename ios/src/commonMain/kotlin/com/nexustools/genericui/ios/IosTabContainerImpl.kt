package com.nexustools.genericui.ios

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * iOS `UITabBar` + `UISegmentedControl` tab container implementation of [TabContainerNativeImpl].
 */
class IosTabContainerImpl : IosBaseContainerNativeImpl("UITabBarController", "tab-container"), TabContainerNativeImpl<IosNativeView> {
    override val supportsNativeFadeTransition: Boolean = true

    private val tabImpls = mutableListOf<TabNativeImpl<*>?>()
    private val tabs = mutableListOf<Pair<String, NativeImpl<*>>>()
    private var selectedIndex: Int = 0
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val title = (tabImpl as? IosTabImpl)?.titleVal ?: ""
        if (index < 0 || index >= tabs.size) {
            tabImpls.add(tabImpl)
            tabs.add(title to contentImpl)
            addChildNative(tabImpl, -1)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, tabImpl)
            tabs.add(index, title to contentImpl)
            addChildNative(tabImpl, index)
            addChildNative(contentImpl, index)
        }
        if (tabs.size == 1) {
            setSelectedTabIndex(0, false)
        }
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        if (index < 0 || index >= tabs.size) {
            tabImpls.add(null)
            tabs.add(title to contentImpl)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, null)
            tabs.add(index, title to contentImpl)
            addChildNative(contentImpl, index)
        }
        if (tabs.size == 1) {
            setSelectedTabIndex(0, false)
        }
    }

    override fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {
        if (index in tabImpls.indices) {
            tabImpls[index] = tabImpl
            val title = (tabImpl as? IosTabImpl)?.titleVal ?: tabs[index].first
            tabs[index] = title to tabs[index].second
        }
    }

    override fun removeTabNative(index: Int) {
        if (index !in tabs.indices) return
        val removedTab = tabImpls.removeAt(index)
        removedTab?.let { removeChildNative(it) }
        val (_, impl) = tabs.removeAt(index)
        removeChildNative(impl)
        if (selectedIndex >= tabs.size) {
            selectedIndex = (tabs.size - 1).coerceAtLeast(0)
        }
    }

    override fun setTabTitleNative(index: Int, title: String) {
        if (index !in tabs.indices) return
        tabs[index] = title to tabs[index].second
        tabImpls[index]?.setTitleNative(title)
    }

    override fun setTabPositionNative(position: TabPosition) {
        nativeWidget.metadata["tabPosition"] = position.name
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
        nativeWidget.metadata["tabBarStyle"] = style.name
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (index !in tabs.indices) return
        selectedIndex = index
        tabs.forEachIndexed { i, (_, impl) ->
            impl.setVisible(i == index)
        }
        onTabSelectedListener?.invoke(index)
    }

    override fun getSelectedTabIndex(): Int = selectedIndex

    override fun setOnTabSelected(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    override fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {
        onTabCloseRequestedListener = listener
    }
}
