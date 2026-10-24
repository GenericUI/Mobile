package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * iOS `UIMenu` implementation of [MenuNativeImpl].
 */
class IosMenuImpl : IosBaseContainerNativeImpl("UIMenu", "menu"), MenuNativeImpl<IosNativeView> {
    private var labelText: String = ""

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
    }

    override fun getLabel(): String = labelText
}
