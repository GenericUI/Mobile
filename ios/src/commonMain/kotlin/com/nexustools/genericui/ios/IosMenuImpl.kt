package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * iOS `UIMenu` implementation of [MenuNativeImpl] supporting [MenuItemNativeImpl] items,
 * [DividerNativeImpl] (`MenuSeparator`) separators, and nested [MenuNativeImpl] submenus.
 */
class IosMenuImpl : IosBaseContainerNativeImpl("UIMenu", "menu"), MenuNativeImpl<IosNativeView> {
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
        if (icon != null) nativeWidget.metadata["sfSymbol"] = icon else nativeWidget.metadata.remove("sfSymbol")
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
        val childView = childImpl.nativeWidget as? IosNativeView
        when (childImpl) {
            is DividerNativeImpl<*> -> {
                childView?.metadata?.set("role", "separator")
                childView?.metadata?.set("isMenuSeparator", "true")
            }
            is MenuNativeImpl<*> -> {
                childView?.metadata?.set("role", "submenu")
                childView?.metadata?.set("isSubmenu", "true")
                childView?.metadata?.set("label", childImpl.getLabel())
                if (childImpl is IosMenuImpl) {
                    childImpl.onMenuChanged = { onMenuChanged?.invoke() }
                }
            }
            is MenuItemNativeImpl<*> -> {
                childView?.metadata?.set("role", "menuitem")
                childView?.metadata?.set("isMenuItem", "true")
                childView?.metadata?.set("label", childImpl.getLabel())
                if (childImpl is IosMenuItemImpl) {
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
        if (childImpl is IosMenuImpl) {
            childImpl.onMenuChanged = null
        }
        syncMenuMetadata()
        onMenuChanged?.invoke()
    }

    override fun clearChildrenNative() {
        for (item in childItems) {
            if (item is IosMenuImpl) {
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
        if (child is IosBaseNativeImpl) {
            if (!child.isEnabled()) return false
            child.dispatchClick()
            return true
        }
        return false
    }

    private fun syncMenuMetadata() {
        nativeWidget.metadata["itemCount"] = childItems.size.toString()
        nativeWidget.metadata["separatorCount"] = getSeparatorCount().toString()
        nativeWidget.metadata["submenuCount"] = getSubmenus().size.toString()
        nativeWidget.metadata["itemLabels"] = getItemLabels().joinToString("|")
    }
}
