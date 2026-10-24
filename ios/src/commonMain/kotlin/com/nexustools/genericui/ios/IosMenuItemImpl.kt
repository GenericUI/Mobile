package com.nexustools.genericui.ios

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl

/**
 * iOS `UIAction` / `UIKeyCommand` implementation of [MenuItemNativeImpl].
 */
class IosMenuItemImpl(
    val checkable: Boolean = false,
    val radio: Boolean = false
) : IosBaseNativeImpl("UIAction", "dropdown-item"), MenuItemNativeImpl<IosNativeView> {
    private var labelText: String = ""
    private var checkedState: Boolean = false

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
    }

    override fun getLabel(): String = labelText

    override fun setIcon(icon: String?) {
        if (icon != null) nativeWidget.metadata["sfSymbol"] = icon else nativeWidget.metadata.remove("sfSymbol")
    }

    override fun setShortcut(shortcut: KeyShortcut?) {
        if (shortcut != null) {
            nativeWidget.metadata["keyCommand"] = shortcut.toString()
        } else {
            nativeWidget.metadata.remove("keyCommand")
        }
    }

    override fun setChecked(checked: Boolean) {
        checkedState = checked
    }

    override fun isChecked(): Boolean = checkedState
}
