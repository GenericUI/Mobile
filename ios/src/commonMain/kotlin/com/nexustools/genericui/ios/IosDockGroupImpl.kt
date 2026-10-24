package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl

/**
 * iOS implementation of [DockGroupNativeImpl].
 */
class IosDockGroupImpl : IosBaseContainerNativeImpl("UIView", "dock-group"), DockGroupNativeImpl<IosNativeView> {
    private var groupMode: DockGroupMode = DockGroupMode.TABBED
    private var activeIndex: Int = 0

    override fun setGroupMode(mode: DockGroupMode) { groupMode = mode }
    override fun getGroupMode(): DockGroupMode = groupMode
    override fun setActivePanelIndex(index: Int) { activeIndex = index }
    override fun getActivePanelIndex(): Int = activeIndex
}
