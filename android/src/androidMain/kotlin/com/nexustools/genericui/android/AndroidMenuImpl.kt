package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * Android `SubMenu` / `PopupMenu` implementation of [MenuNativeImpl].
 */
class AndroidMenuImpl : AndroidBaseContainerNativeImpl("android.view.Menu", "menu"), MenuNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
    }

    override fun getLabel(): String = labelText
}
