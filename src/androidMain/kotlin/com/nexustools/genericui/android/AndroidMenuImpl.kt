package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * Android `SubMenu` / `PopupMenu` implementation of [MenuNativeImpl] supporting [MenuItemNativeImpl] items,
 * [DividerNativeImpl] (`MenuSeparator`) separators, and nested [MenuNativeImpl] submenus.
 */
class AndroidMenuImpl : AndroidBaseContainerNativeImpl("android.view.Menu", "menu"), MenuNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""
    private var iconName: String? = null
    private val childItems = mutableListOf<NativeImpl<*>>()

    val items: List<NativeImpl<*>>
        get() = childItems

    internal var onMenuChanged: (() -> Unit)? = null

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
        onMenuChanged?.invoke()
    }

    override fun getLabel(): String = labelText

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) nativeWidget.attributes["icon"] = icon else nativeWidget.attributes.remove("icon")
    }

    override fun getIcon(): String? = iconName

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        childItems.remove(childImpl)
        if (index < 0 || index >= childItems.size) {
            childItems.add(childImpl)
        } else {
            childItems.add(index, childImpl)
        }
        val childHandle = childImpl.nativeWidget as? AndroidViewHandle
        when (childImpl) {
            is DividerNativeImpl<*> -> {
                childHandle?.attributes?.set("role", "separator")
                childHandle?.attributes?.set("isMenuSeparator", "true")
            }
            is MenuNativeImpl<*> -> {
                childHandle?.attributes?.set("role", "submenu")
                childHandle?.attributes?.set("isSubmenu", "true")
                childHandle?.attributes?.set("label", childImpl.getLabel())
                if (childImpl is AndroidMenuImpl) {
                    childImpl.onMenuChanged = { onMenuChanged?.invoke() }
                }
            }
            is MenuItemNativeImpl<*> -> {
                childHandle?.attributes?.set("role", "menuitem")
                childHandle?.attributes?.set("isMenuItem", "true")
                childHandle?.attributes?.set("label", childImpl.getLabel())
                if (childImpl is AndroidMenuItemImpl) {
                    childImpl.onItemChanged = { onMenuChanged?.invoke() }
                }
            }
        }
        syncMenuMetadata()
        onMenuChanged?.invoke()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childItems.remove(childImpl)
        if (childImpl is AndroidMenuImpl) {
            childImpl.onMenuChanged = null
        }
        syncMenuMetadata()
        onMenuChanged?.invoke()
    }

    override fun clearChildrenNative() {
        for (item in childItems) {
            if (item is AndroidMenuImpl) {
                item.onMenuChanged = null
            }
        }
        super.clearChildrenNative()
        childItems.clear()
        syncMenuMetadata()
        onMenuChanged?.invoke()
    }

    fun getItemLabels(): List<String> = childItems.map { child ->
        when (child) {
            is MenuItemNativeImpl<*> -> child.getLabel()
            is MenuNativeImpl<*> -> child.getLabel()
            is DividerNativeImpl<*> -> "---"
            else -> ""
        }
    }

    fun getSeparatorCount(): Int = childItems.count { it is DividerNativeImpl<*> }

    fun getSubmenus(): List<MenuNativeImpl<*>> = childItems.filterIsInstance<MenuNativeImpl<*>>()

    fun triggerItemAt(index: Int): Boolean {
        val child = childItems.getOrNull(index) ?: return false
        if (child is DividerNativeImpl<*>) return false
        if (child is AndroidBaseNativeImpl) {
            if (!child.isEnabled()) return false
            child.dispatchClick()
            return true
        }
        return false
    }

    private fun syncMenuMetadata() {
        nativeWidget.attributes["itemCount"] = childItems.size.toString()
        nativeWidget.attributes["separatorCount"] = getSeparatorCount().toString()
        nativeWidget.attributes["submenuCount"] = getSubmenus().size.toString()
        nativeWidget.attributes["itemLabels"] = getItemLabels().joinToString("|")
    }
}
