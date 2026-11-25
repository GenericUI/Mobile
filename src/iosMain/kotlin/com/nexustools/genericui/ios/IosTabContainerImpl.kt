@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.tabs.TabBarStyle
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISegmentedControl
import platform.UIKit.UIView

/**
 * Native UIKit segmented tab bar (`UISegmentedControl`) + content switcher container implementing
 * [TabContainerNativeImpl], supporting `TOP`/`BOTTOM`/`LEFT`/`RIGHT` tab placement and animated
 * alpha fade transitions between tab content views while ignoring `BootstrapType` overrides.
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

    val segmentedControl: UISegmentedControl = UISegmentedControl(frame = CGRectMake(0.0, 0.0, 360.0, SEGMENTED_HEIGHT_PT))
    val tabContentHost: UIView = UikitFlexContainerView(frame = CGRectMake(0.0, SEGMENTED_HEIGHT_PT, 360.0, 388.0))
    private val segmentTarget = UikitControlActionTarget(segmentedControl)

    private val slots = mutableListOf<TabSlot>()
    private var selectedIndex: Int = -1
    private var tabPosition: TabPosition = TabPosition.TOP
    private var tabBarStyle: TabBarStyle = TabBarStyle.TABS
    private var fullWidthTabs: Boolean = false
    private var suppressSegmentEvent: Boolean = false
    private var onTabSelectedListener: ((Int) -> Unit)? = null
    private var onTabCloseRequestedListener: ((Int) -> Boolean)? = null

    override val supportsNativeFadeTransition: Boolean = true

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(segmentedControl)
        host.addSubview(tabContentHost)
        segmentedControl.addTarget(
            target = segmentTarget,
            action = UikitControlActionTarget.SEL_VALUE_CHANGED,
            forControlEvents = UIControlEventValueChanged
        )
        segmentTarget.onValueChanged = {
            if (!suppressSegmentEvent) {
                val idx = segmentedControl.selectedSegmentIndex.toInt()
                if (idx in slots.indices) {
                    val slot = slots[idx]
                    if (slot.isButtonTab) {
                        onTabSelectedListener?.invoke(idx)
                        suppressSegmentEvent = true
                        try {
                            segmentedControl.selectedSegmentIndex =
                                if (selectedIndex in slots.indices) selectedIndex.toLong() else NO_SEGMENT_INDEX
                        } finally {
                            suppressSegmentEvent = false
                        }
                    } else {
                        setSelectedTabIndex(idx, fadeTransition = true)
                        onTabSelectedListener?.invoke(idx)
                    }
                }
            }
        }
        updateTabLayoutOrder()
    }

    override fun contentContainerView(): UIView = tabContentHost

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        if (childImpl is TabNativeImpl<*>) return
        super.addChildNative(childImpl, index)
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        updateTabLayoutOrder()
        syncSelectedTabContent(fadeTransition = false)
    }

    override fun addTabNative(tabImpl: TabNativeImpl<*>, contentImpl: NativeImpl<*>, index: Int) {
        val iosTab = tabImpl as? IosTabImpl
        val insertIdx = if (index < 0 || index > slots.size) slots.size else index
        slots.add(insertIdx, TabSlot(iosTab, contentImpl, isButtonTab = false))
        wireTabCallbacks(iosTab)
        (contentImpl.nativeWidget as? UikitViewHandle)?.uiView?.let { contentView ->
            contentView.hidden = true
            tabContentHost.addSubview(contentView)
        }
        if (selectedIndex < 0) {
            selectedIndex = 0
        }
        rebuildSegmentedControl()
        setSelectedTabIndex(selectedIndex, fadeTransition = false)
    }

    override fun addButtonTabNative(tabImpl: TabNativeImpl<*>, index: Int) {
        val iosTab = tabImpl as? IosTabImpl
        val insertIdx = if (index < 0 || index > slots.size) slots.size else index
        slots.add(insertIdx, TabSlot(iosTab, null, isButtonTab = true))
        wireTabCallbacks(iosTab)
        rebuildSegmentedControl()
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
        existing.tabImpl?.onTabUpdated = null
        val iosTab = tabImpl as? IosTabImpl
        slots[index] = existing.copy(tabImpl = iosTab)
        wireTabCallbacks(iosTab)
        rebuildSegmentedControl()
    }

    override fun removeTabNative(index: Int) {
        if (index !in slots.indices) return
        val removed = slots.removeAt(index)
        removed.tabImpl?.onTabUpdated = null
        removed.tabImpl?.nativeWidget?.uiView?.removeFromSuperview()
        (removed.contentImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        if (slots.isEmpty()) {
            selectedIndex = -1
        } else if (selectedIndex >= slots.size) {
            selectedIndex = slots.lastIndex
        }
        rebuildSegmentedControl()
        if (selectedIndex in slots.indices) {
            for ((i, item) in slots.withIndex()) {
                item.tabImpl?.setSelectedNative(i == selectedIndex)
            }
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
        rebuildSegmentedControl()
    }

    override fun setSelectedTabIndex(index: Int, fadeTransition: Boolean) {
        if (index !in slots.indices) return
        val slot = slots[index]
        if (slot.isButtonTab) return
        selectedIndex = index
        suppressSegmentEvent = true
        try {
            segmentedControl.selectedSegmentIndex = index.toLong()
        } finally {
            suppressSegmentEvent = false
        }
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
        tab.onTabUpdated = { rebuildSegmentedControl() }
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

    private fun rebuildSegmentedControl() {
        suppressSegmentEvent = true
        try {
            segmentedControl.removeAllSegments()
            segmentedControl.apportionsSegmentWidthsByContent = !fullWidthTabs
            for ((idx, slot) in slots.withIndex()) {
                val tab = slot.tabImpl
                val rawTitle = tab?.currentTitle().orEmpty()
                val displayTitle = buildString {
                    if (tab?.isPinned() == true) append("\uD83D\uDCCC ")
                    if (tab?.isDirty() == true) append("\u2022 ")
                    append(rawTitle)
                    val badge = tab?.currentBadgeText()
                    if (!badge.isNullOrEmpty()) {
                        append(" (").append(badge).append(')')
                    }
                }
                val iconImage = UikitGeometryAndColorUtil.systemIconImage(tab?.currentIcon())
                if (displayTitle.isEmpty() && iconImage != null) {
                    segmentedControl.insertSegmentWithImage(iconImage, atIndex = idx.toULong(), animated = false)
                } else {
                    segmentedControl.insertSegmentWithTitle(displayTitle, atIndex = idx.toULong(), animated = false)
                }
            }
            segmentedControl.selectedSegmentIndex =
                if (selectedIndex in slots.indices) selectedIndex.toLong() else NO_SEGMENT_INDEX
        } finally {
            suppressSegmentEvent = false
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
        val (containerW, containerH) = uiView.bounds.useContents {
            size.width.takeIf { it > 0.0 } to size.height.takeIf { it > 0.0 }
        }
        val w = containerW ?: 360.0
        val h = containerH ?: 420.0
        segmentedControl.removeFromSuperview()
        tabContentHost.removeFromSuperview()
        when (tabPosition) {
            TabPosition.TOP -> {
                host.flexDirection = FlexDirection.COLUMN
                host.addSubview(segmentedControl)
                host.addSubview(tabContentHost)
                val contentH = (h - SEGMENTED_HEIGHT_PT).coerceAtLeast(1.0)
                host.setChildPreferredSize(segmentedControl, w, SEGMENTED_HEIGHT_PT)
                host.setChildPreferredSize(tabContentHost, w, contentH)
                segmentedControl.setFrame(CGRectMake(0.0, 0.0, w, SEGMENTED_HEIGHT_PT))
                tabContentHost.setFrame(CGRectMake(0.0, SEGMENTED_HEIGHT_PT, w, contentH))
            }
            TabPosition.BOTTOM -> {
                host.flexDirection = FlexDirection.COLUMN
                host.addSubview(tabContentHost)
                host.addSubview(segmentedControl)
                val contentH = (h - SEGMENTED_HEIGHT_PT).coerceAtLeast(1.0)
                host.setChildPreferredSize(tabContentHost, w, contentH)
                host.setChildPreferredSize(segmentedControl, w, SEGMENTED_HEIGHT_PT)
                tabContentHost.setFrame(CGRectMake(0.0, 0.0, w, contentH))
                segmentedControl.setFrame(CGRectMake(0.0, contentH, w, SEGMENTED_HEIGHT_PT))
            }
            TabPosition.LEFT -> {
                host.flexDirection = FlexDirection.ROW
                host.addSubview(segmentedControl)
                host.addSubview(tabContentHost)
                val sideW = SIDE_STRIP_WIDTH_PT.coerceAtMost((w - 1.0).coerceAtLeast(60.0))
                val contentW = (w - sideW).coerceAtLeast(1.0)
                host.setChildPreferredSize(segmentedControl, sideW, SEGMENTED_HEIGHT_PT)
                host.setChildPreferredSize(tabContentHost, contentW, h)
                segmentedControl.setFrame(CGRectMake(0.0, 0.0, sideW, SEGMENTED_HEIGHT_PT))
                tabContentHost.setFrame(CGRectMake(sideW, 0.0, contentW, h))
            }
            TabPosition.RIGHT -> {
                host.flexDirection = FlexDirection.ROW
                host.addSubview(tabContentHost)
                host.addSubview(segmentedControl)
                val sideW = SIDE_STRIP_WIDTH_PT.coerceAtMost((w - 1.0).coerceAtLeast(60.0))
                val contentW = (w - sideW).coerceAtLeast(1.0)
                host.setChildPreferredSize(tabContentHost, contentW, h)
                host.setChildPreferredSize(segmentedControl, sideW, SEGMENTED_HEIGHT_PT)
                tabContentHost.setFrame(CGRectMake(0.0, 0.0, contentW, h))
                segmentedControl.setFrame(CGRectMake(contentW, 0.0, sideW, SEGMENTED_HEIGHT_PT))
            }
        }
        rebuildSegmentedControl()
    }

    private companion object {
        const val SEGMENTED_HEIGHT_PT: Double = 32.0
        const val SIDE_STRIP_WIDTH_PT: Double = 140.0
        const val NO_SEGMENT_INDEX: Long = -1L
    }
}
