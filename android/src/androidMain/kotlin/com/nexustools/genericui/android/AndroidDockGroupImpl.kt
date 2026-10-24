package com.nexustools.genericui.android

import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl

/**
 * Android `TabLayout` + `ViewPager2` implementation of [DockGroupNativeImpl].
 */
class AndroidDockGroupImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-group"), DockGroupNativeImpl<AndroidViewHandle> {
    private var groupMode: DockGroupMode = DockGroupMode.TABBED
    private var activeIndex: Int = 0

    override fun setGroupMode(mode: DockGroupMode) {
        groupMode = mode
        nativeWidget.metadata["groupMode"] = mode.name.lowercase()
    }

    override fun getGroupMode(): DockGroupMode = groupMode

    override fun setActivePanelIndex(index: Int) {
        activeIndex = index
    }

    override fun getActivePanelIndex(): Int = activeIndex
}
