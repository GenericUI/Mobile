package com.nexustools.genericui.android

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Android `com.google.android.material.tabs.TabLayout` + `androidx.viewpager2.widget.ViewPager2`
 * implementation of [TabContainerNativeImpl].
 */
class AndroidTabContainerImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "nav-tabs"), TabContainerNativeImpl<AndroidViewHandle> {
    override val supportsNativeFadeTransition: Boolean = true

    private val tabImpls = mutableListOf<TabNativeImpl<*>?>()
    private val tabTitles = mutableListOf<String>()
    private val tabContents = mutableListOf<NativeImpl<*>>()
    private var selectedIndexVal: Int = 0
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val title = (tabImpl as? AndroidTabImpl)?.titleVal ?: ""
        if (index < 0 || index >= tabTitles.size) {
            tabImpls.add(tabImpl)
            tabTitles.add(title)
            tabContents.add(contentImpl)
            addChildNative(tabImpl, -1)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, tabImpl)
            tabTitles.add(index, title)
            tabContents.add(index, contentImpl)
            addChildNative(tabImpl, index)
            addChildNative(contentImpl, index)
        }
        nativeWidget.metadata["tabTitles"] = tabTitles.joinToString("|")
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        if (index < 0 || index >= tabTitles.size) {
            tabImpls.add(null)
            tabTitles.add(title)
            tabContents.add(contentImpl)
            addChildNative(contentImpl, -1)
        } else {
            tabImpls.add(index, null)
            tabTitles.add(index, title)
            tabContents.add(index, contentImpl)
            addChildNative(contentImpl, index)
        }
        nativeWidget.metadata["tabTitles"] = tabTitles.joinToString("|")
    }

    override fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {
        if (index in tabImpls.indices) {
            tabImpls[index] = tabImpl
            (tabImpl as? AndroidTabImpl)?.let { tabTitles[index] = it.titleVal }
            nativeWidget.metadata["tabTitles"] = tabTitles.joinToString("|")
        }
    }

    override fun removeTabNative(index: Int) {
        if (index !in tabTitles.indices) return
        val removedTab = tabImpls.removeAt(index)
        removedTab?.let { removeChildNative(it) }
        tabTitles.removeAt(index)
        val removedContent = tabContents.removeAt(index)
        removeChildNative(removedContent)
        selectedIndexVal = selectedIndexVal.coerceIn(0, (tabTitles.size - 1).coerceAtLeast(0))
        nativeWidget.metadata["tabTitles"] = tabTitles.joinToString("|")
    }

    override fun setTabTitleNative(index: Int, title: String) {
        if (index in tabTitles.indices) {
            tabTitles[index] = title
            tabImpls[index]?.setTitleNative(title)
            nativeWidget.metadata["tabTitles"] = tabTitles.joinToString("|")
        }
    }

    override fun setTabPositionNative(position: TabPosition) {
        nativeWidget.metadata["tabPosition"] = position.name
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
        nativeWidget.metadata["tabBarStyle"] = style.name
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (tabTitles.isNotEmpty() && index !in tabTitles.indices) return
        selectedIndexVal = index
        tabContents.forEachIndexed { idx, impl ->
            impl.setVisible(idx == index)
        }
        onTabSelectedListener?.invoke(index)
    }

    override fun getSelectedTabIndex(): Int = selectedIndexVal

    override fun setOnTabSelected(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    override fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {
        onTabCloseRequestedListener = listener
    }
}
