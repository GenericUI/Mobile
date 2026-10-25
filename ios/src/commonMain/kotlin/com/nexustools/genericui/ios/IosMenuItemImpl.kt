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
    private var iconName: String? = null
    private var shortcutVal: KeyShortcut? = null
    private var checkedState: Boolean = false

    internal var onItemChanged: (() -> Unit)? = null

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
        onItemChanged?.invoke()
    }

    override fun getLabel(): String = labelText

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) nativeWidget.metadata["sfSymbol"] = icon else nativeWidget.metadata.remove("sfSymbol")
    }

    override fun getIcon(): String? = iconName

    override fun setShortcut(shortcut: KeyShortcut?) {
        shortcutVal = shortcut
        if (shortcut != null) {
            nativeWidget.metadata["keyCommand"] = shortcut.toString()
        } else {
            nativeWidget.metadata.remove("keyCommand")
        }
    }

    override fun getShortcut(): KeyShortcut? = shortcutVal

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        nativeWidget.metadata["checked"] = checked.toString()
    }

    override fun isChecked(): Boolean = checkedState

    fun trigger(): Boolean {
        if (!isEnabled()) return false
        dispatchClick()
        return true
    }
}
