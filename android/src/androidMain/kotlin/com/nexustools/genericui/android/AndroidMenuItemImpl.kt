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
        if (icon != null) nativeWidget.attributes["icon"] = icon else nativeWidget.attributes.remove("icon")
    }

    override fun getIcon(): String? = iconName

    override fun setShortcut(shortcut: KeyShortcut?) {
        shortcutVal = shortcut
        if (shortcut != null) {
            nativeWidget.attributes["shortcut"] = shortcut.toString()
        } else {
            nativeWidget.attributes.remove("shortcut")
        }
    }

    override fun getShortcut(): KeyShortcut? = shortcutVal

    override fun setChecked(checked: Boolean) {
        checkedState = checked
        nativeWidget.attributes["checked"] = checked.toString()
    }

    override fun isChecked(): Boolean = checkedState

    fun trigger(): Boolean {
        if (!isEnabled()) return false
        dispatchClick()
        return true
    }
}
