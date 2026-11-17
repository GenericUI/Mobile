@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UISegmentedControl
import platform.UIKit.UIView

/**
 * Native UIKit dock group container switching between `TABBED` (`UISegmentedControl`),
 * `VERTICAL_SPLIT`, and `HORIZONTAL_SPLIT` child layouts.
 */
class IosDockGroupImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 280.0, 240.0)).apply {
            flexDirection = FlexDirection.COLUMN
        }
    ),
    componentClassPrefix = "dock-group"
), DockGroupNativeImpl<UikitViewHandle> {

    private val segmentedTabs = UISegmentedControl(frame = CGRectMake(0.0, 0.0, 280.0, 32.0))
    private val groupContentHost = UikitFlexContainerView(CGRectMake(0.0, 32.0, 280.0, 208.0)).apply {
        flexDirection = FlexDirection.COLUMN
    }
    private val segmentTarget = UikitControlActionTarget()

    private var groupMode: DockGroupMode = DockGroupMode.TABBED
    private var activeIndex: Int = 0

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(segmentedTabs)
        host.setChildPreferredSize(segmentedTabs, 280.0, 32.0)
        host.addSubview(groupContentHost)

        segmentedTabs.addTarget(
            target = segmentTarget,
            action = UikitControlActionTarget.SEL_VALUE_CHANGED,
            forControlEvents = UIControlEventValueChanged
        )
        segmentTarget.onValueChanged = {
            setActivePanelIndex(segmentedTabs.selectedSegmentIndex.toInt())
        }
    }

    override fun contentContainerView(): UIView = groupContentHost

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        syncGroupArrangement()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        syncGroupArrangement()
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        syncGroupArrangement()
    }

    override fun setGroupMode(mode: DockGroupMode) {
        groupMode = mode
        syncGroupArrangement()
    }

    override fun getGroupMode(): DockGroupMode = groupMode

    override fun setActivePanelIndex(index: Int) {
        activeIndex = index.coerceAtLeast(0)
        if (groupMode == DockGroupMode.TABBED && activeIndex < childImpls.size) {
            segmentedTabs.selectedSegmentIndex = activeIndex.toLong()
        }
        syncGroupArrangement()
    }

    override fun getActivePanelIndex(): Int = activeIndex

    private fun syncGroupArrangement() {
        val isTabbed = groupMode == DockGroupMode.TABBED
        segmentedTabs.hidden = !isTabbed || childImpls.size <= 1
        segmentedTabs.removeAllSegments()
        if (isTabbed) {
            for ((idx, impl) in childImpls.withIndex()) {
                val title = (impl as? IosDockPanelImpl)?.getTitle()?.ifEmpty { null } ?: "Panel ${idx + 1}"
                segmentedTabs.insertSegmentWithTitle(title, atIndex = idx.toULong(), animated = false)
            }
            if (childImpls.isNotEmpty()) {
                val clamped = activeIndex.coerceIn(0, childImpls.lastIndex)
                activeIndex = clamped
                segmentedTabs.selectedSegmentIndex = clamped.toLong()
            }
        }
        groupContentHost.flexDirection = when (groupMode) {
            DockGroupMode.HORIZONTAL_SPLIT -> FlexDirection.ROW
            else -> FlexDirection.COLUMN
        }
        for ((idx, impl) in childImpls.withIndex()) {
            val childView = (impl.nativeWidget as? UikitViewHandle)?.uiView ?: continue
            childView.hidden = isTabbed && idx != activeIndex
        }
        uiView.setNeedsLayout()
    }
}
