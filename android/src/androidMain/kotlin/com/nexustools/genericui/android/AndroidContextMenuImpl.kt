package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * Android `PopupMenu` / context menu implementation of [ContextMenuNativeImpl] supporting
 * [MenuItemNativeImpl] items, [DividerNativeImpl] (`MenuSeparator`) separators, and nested
 * [MenuNativeImpl] submenus.
 */
class AndroidContextMenuImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.widget.PopupMenu", "dropdown-menu"), ContextMenuNativeImpl<AndroidViewHandle> {
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
            }
            is MenuItemNativeImpl<*> -> {
                childHandle?.attributes?.set("role", "menuitem")
                childHandle?.attributes?.set("isMenuItem", "true")
                childHandle?.attributes?.set("label", childImpl.getLabel())
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
        if (child is AndroidBaseNativeImpl) {
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
        nativeWidget.attributes["popupX"] = x.toString()
        nativeWidget.attributes["popupY"] = y.toString()
        setVisible(true)
    }

    override fun hide() {
        isPopupShowing = false
        setVisible(false)
    }

    private fun syncMenuMetadata() {
        nativeWidget.attributes["itemCount"] = childItems.size.toString()
        nativeWidget.attributes["separatorCount"] = getSeparatorCount().toString()
        nativeWidget.attributes["submenuCount"] = getSubmenus().size.toString()
        nativeWidget.attributes["itemLabels"] = getItemLabels().joinToString("|")
    }
}
