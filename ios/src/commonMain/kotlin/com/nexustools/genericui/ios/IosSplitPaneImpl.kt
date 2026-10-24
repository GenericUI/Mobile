package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.nativeimpl.shell.SplitPaneNativeImpl

/**
 * iOS `UISplitViewController` / split container implementation of [SplitPaneNativeImpl].
 */
class IosSplitPaneImpl : IosBaseContainerNativeImpl("UISplitViewController", "split-pane"), SplitPaneNativeImpl<IosNativeView> {
    private var orientation: Orientation = Orientation.HORIZONTAL
    private var dividerRatio: Float = 0.5f

    override fun setOrientation(orientation: Orientation) {
        this.orientation = orientation
    }

    override fun getOrientation(): Orientation = orientation

    override fun setDividerPosition(ratio: Float) {
        dividerRatio = ratio.coerceIn(0.05f, 0.95f)
    }

    override fun getDividerPosition(): Float = dividerRatio
}
