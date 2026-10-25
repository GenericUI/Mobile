package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * iOS `UIContextMenuInteraction` implementation of [ContextMenuNativeImpl] supporting
 * [MenuItemNativeImpl] items, [DividerNativeImpl] (`MenuSeparator`) separators, and nested
 * [MenuNativeImpl] submenus.
 */
class IosContextMenuImpl : IosBaseContainerNativeImpl("UIContextMenu", "dropdown-menu"), ContextMenuNativeImpl<IosNativeView> {
    private val childItems = mutableListOf<NativeImpl<*>>()
    internal val sourceMenus = mutableListOf<MenuNativeImpl<*>>()

    val items: List<NativeImpl<*>>
        get() = childItems

    var lastAnchor: NativeImpl<*>? = null
        private set
    var lastPopupX: Int = 0
        private set
    var lastPopupY: Int = 0
        private set
    var isPopupShowing: Boolean = false
        private set

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
            }
            is MenuItemNativeImpl<*> -> {
                childView?.metadata?.set("role", "menuitem")
                childView?.metadata?.set("isMenuItem", "true")
                childView?.metadata?.set("label", childImpl.getLabel())
            }
        }
        syncMenuMetadata()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childItems.remove(childImpl)
        syncMenuMetadata()
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        childItems.clear()
        syncMenuMetadata()
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

    fun getSubmenus(): List<MenuNativeImpl<*>> =
        (childItems.filterIsInstance<MenuNativeImpl<*>>() + sourceMenus).distinct()

    fun triggerItemAt(index: Int): Boolean {
        val child = childItems.getOrNull(index) ?: return false
        if (child is DividerNativeImpl<*>) return false
        if (child is IosBaseNativeImpl) {
            if (!child.isEnabled()) return false
            child.dispatchClick()
            hide()
            return true
        }
        return false
    }

    override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) {
        lastAnchor = anchor
        lastPopupX = x
        lastPopupY = y
        isPopupShowing = true
        nativeWidget.metadata["popupX"] = x.toString()
        nativeWidget.metadata["popupY"] = y.toString()
        setVisible(true)
    }

    override fun hide() {
        isPopupShowing = false
        setVisible(false)
    }

    private fun syncMenuMetadata() {
        nativeWidget.metadata["itemCount"] = childItems.size.toString()
        nativeWidget.metadata["separatorCount"] = getSeparatorCount().toString()
        nativeWidget.metadata["submenuCount"] = getSubmenus().size.toString()
        nativeWidget.metadata["itemLabels"] = getItemLabels().joinToString("|")
    }
}
