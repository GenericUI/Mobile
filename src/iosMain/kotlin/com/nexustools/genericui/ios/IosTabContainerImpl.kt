@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIScrollView
import platform.UIKit.UIView

/**
 * Native UIKit scrollable tab strip (`UIScrollView`) + content switcher container implementing
 * [TabContainerNativeImpl], supporting `TOP`/`BOTTOM`/`LEFT`/`RIGHT` tab placement and animated
 * alpha fade transitions between tab content views.
 */
class IosTabContainerImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 360.0, 420.0)).apply {
            flexDirection = FlexDirection.COLUMN
        }
    ),
    componentClassPrefix = "tab-container"
), TabContainerNativeImpl<UikitViewHandle> {

    private data class TabSlot(
        val tabImpl: IosTabImpl?,
        val contentImpl: NativeImpl<*>?,
        val isButtonTab: Boolean
    )

    val tabStripScrollView: UIScrollView = UIScrollView(frame = CGRectMake(0.0, 0.0, 360.0, 42.0)).apply {
        showsHorizontalScrollIndicator = false
    }
    val tabContentHost: UIView = UikitFlexContainerView(frame = CGRectMake(0.0, 42.0, 360.0, 378.0))

    private val slots = mutableListOf<TabSlot>()
    private var selectedIndex: Int = -1
    private var tabPosition: TabPosition = TabPosition.TOP
    private var tabBarStyle: TabBarStyle = TabBarStyle.TABS
    private var fullWidthTabs: Boolean = false
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    override val supportsNativeFadeTransition: Boolean = true

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(tabStripScrollView)
        host.addSubview(tabContentHost)
        updateTabLayoutOrder()
    }

    override fun contentContainerView(): UIView = tabContentHost

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val iosTab = tabImpl as? IosTabImpl
        val insertIdx = if (index < 0 || index > slots.size) slots.size else index
        slots.add(insertIdx, TabSlot(iosTab, contentImpl, isButtonTab = false))
        wireTabCallbacks(iosTab)
        (contentImpl.nativeWidget as? UikitViewHandle)?.uiView?.let { contentView ->
            contentView.hidden = true
            tabContentHost.addSubview(contentView)
        }
        rebuildTabStrip()
        if (selectedIndex < 0) {
            setSelectedTabIndex(0, fadeTransition = false)
        }
    }

    override fun addButtonTabNative(tabImpl: TabNativeImpl<*>, index: Int) {
        val iosTab = tabImpl as? IosTabImpl
        val insertIdx = if (index < 0 || index > slots.size) slots.size else index
        slots.add(insertIdx, TabSlot(iosTab, null, isButtonTab = true))
        wireTabCallbacks(iosTab)
        rebuildTabStrip()
    }

    override fun addTabNative(title: String, contentImpl: NativeImpl<*>, index: Int) {
        val tab = IosTabImpl(closable = false).apply {
            setTitleNative(title)
        }
        addTabNative(tab, contentImpl, index)
    }

    override fun updateTabNative(index: Int, tabImpl: TabNativeImpl<*>) {
        if (index !in slots.indices) return
        val existing = slots[index]
        val iosTab = tabImpl as? IosTabImpl
        slots[index] = existing.copy(tabImpl = iosTab)
        wireTabCallbacks(iosTab)
        rebuildTabStrip()
    }

    override fun removeTabNative(index: Int) {
        if (index !in slots.indices) return
        val removed = slots.removeAt(index)
        removed.tabImpl?.nativeWidget?.uiView?.removeFromSuperview()
        (removed.contentImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        rebuildTabStrip()
        if (slots.isEmpty()) {
            selectedIndex = -1
        } else if (selectedIndex >= slots.size) {
            setSelectedTabIndex(slots.lastIndex, fadeTransition = false)
        } else {
            syncSelectedTabContent(fadeTransition = false)
        }
    }

    override fun setTabTitleNative(index: Int, title: String) {
        slots.getOrNull(index)?.tabImpl?.setTitleNative(title)
    }

    override fun setTabPositionNative(position: TabPosition) {
        tabPosition = position
        updateTabLayoutOrder()
    }

    override fun setTabBarStyleNative(style: TabBarStyle) {
        tabBarStyle = style
    }

    override fun setFullWidthTabsNative(fullWidth: Boolean) {
        fullWidthTabs = fullWidth
        rebuildTabStrip()
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (index !in slots.indices) return
        val slot = slots[index]
        if (slot.isButtonTab) return
        selectedIndex = index
        for ((i, item) in slots.withIndex()) {
            item.tabImpl?.setSelectedNative(i == index)
        }
        syncSelectedTabContent(fadeTransition)
    }

    override fun getSelectedTabIndex(): Int = selectedIndex

    override fun setOnTabSelected(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    override fun setOnTabCloseRequested(listener: ((Int) -> Boolean)?) {
        onTabCloseRequestedListener = listener
    }

    private fun wireTabCallbacks(tab: IosTabImpl?) {
        tab ?: return
        tab.onSelectTabRequested = {
            val idx = slots.indexOfFirst { it.tabImpl === tab }
            if (idx >= 0) {
                setSelectedTabIndex(idx, fadeTransition = true)
                onTabSelectedListener?.invoke(idx)
            }
        }
        tab.setOnCloseClicked {
            val idx = slots.indexOfFirst { it.tabImpl === tab }
            if (idx >= 0 && onTabCloseRequestedListener?.invoke(idx) != false) {
                removeTabNative(idx)
            }
        }
    }

    private fun rebuildTabStrip() {
        for (sub in tabStripScrollView.subviews.filterIsInstance<UIView>()) {
            sub.removeFromSuperview()
        }
        val stripWidth = tabStripScrollView.bounds.useContents { size.width }.takeIf { it > 0.0 } ?: 360.0
        val tabW = if (fullWidthTabs && slots.isNotEmpty()) stripWidth / slots.size else 128.0
        var cursor = 0.0
        val isVertical = tabPosition == TabPosition.LEFT || tabPosition == TabPosition.RIGHT
        for (slot in slots) {
            val tabView = slot.tabImpl?.nativeWidget?.uiView ?: continue
            if (isVertical) {
                tabView.setFrame(CGRectMake(0.0, cursor, 140.0, 38.0))
                cursor += 40.0
            } else {
                tabView.setFrame(CGRectMake(cursor, 2.0, tabW, 38.0))
                cursor += tabW + 4.0
            }
            tabStripScrollView.addSubview(tabView)
        }
        if (isVertical) {
            tabStripScrollView.setContentSize(CGSizeMake(140.0, cursor))
        } else {
            tabStripScrollView.setContentSize(CGSizeMake(cursor, 42.0))
        }
    }

    private fun syncSelectedTabContent(fadeTransition: Boolean) {
        val targetBounds = tabContentHost.bounds
        for ((i, slot) in slots.withIndex()) {
            val contentView = (slot.contentImpl?.nativeWidget as? UikitViewHandle)?.uiView ?: continue
            val active = i == selectedIndex
            if (active) {
                contentView.setFrame(targetBounds)
                contentView.hidden = false
                if (fadeTransition) {
                    contentView.alpha = 0.0
                    UIView.animateWithDuration(0.18) { contentView.alpha = 1.0 }
                } else {
                    contentView.alpha = 1.0
                }
            } else {
                contentView.hidden = true
            }
        }
    }

    private fun updateTabLayoutOrder() {
        val host = uiView as UikitFlexContainerView
        tabStripScrollView.removeFromSuperview()
        tabContentHost.removeFromSuperview()
        when (tabPosition) {
            TabPosition.TOP -> {
                host.flexDirection = FlexDirection.COLUMN
                host.addSubview(tabStripScrollView)
                host.addSubview(tabContentHost)
                host.setChildPreferredSize(tabStripScrollView, 360.0, 42.0)
            }
            TabPosition.BOTTOM -> {
                host.flexDirection = FlexDirection.COLUMN
                host.addSubview(tabContentHost)
                host.addSubview(tabStripScrollView)
                host.setChildPreferredSize(tabStripScrollView, 360.0, 42.0)
            }
            TabPosition.LEFT -> {
                host.flexDirection = FlexDirection.ROW
                host.addSubview(tabStripScrollView)
                host.addSubview(tabContentHost)
                host.setChildPreferredSize(tabStripScrollView, 140.0, 360.0)
            }
            TabPosition.RIGHT -> {
                host.flexDirection = FlexDirection.ROW
                host.addSubview(tabContentHost)
                host.addSubview(tabStripScrollView)
                host.setChildPreferredSize(tabStripScrollView, 140.0, 360.0)
            }
        }
        rebuildTabStrip()
    }
}
