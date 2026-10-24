package com.nexustools.genericui.android

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

/**
 * Android `MenuItem` implementation of [MenuItemNativeImpl].
 */
class AndroidMenuItemImpl(
    val checkable: Boolean = false,
    val radio: Boolean = false
) : AndroidBaseNativeImpl("android.view.MenuItem", "dropdown-item"), MenuItemNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""
    private var checkedState: Boolean = false

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
    }

    override fun getLabel(): String = labelText
    override fun setIcon(icon: String?) {}
    override fun setShortcut(shortcut: KeyShortcut?) {}
    override fun setChecked(checked: Boolean) { checkedState = checked }
    override fun isChecked(): Boolean = checkedState
}
