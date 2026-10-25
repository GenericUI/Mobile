package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * iOS `UIMenuBuilder` implementation of [MenuBarNativeImpl] (used on iPadOS hardware keyboard menu bar
 * and synthesized into the top [IosNavbarImpl] dropdown menu on mobile).
 */
class IosMenuBarImpl : IosBaseContainerNativeImpl("UIMenuBar", "menubar"), MenuBarNativeImpl<IosNativeView> {
    private val childMenus = mutableListOf<NativeImpl<*>>()

    val items: List<NativeImpl<*>>
        get() = childMenus

    val menus: List<MenuNativeImpl<*>>
        get() = childMenus.filterIsInstance<MenuNativeImpl<*>>()

    internal var onStructureChanged: (() -> Unit)? = null

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        childMenus.remove(childImpl)
        if (index < 0 || index >= childMenus.size) {
            childMenus.add(childImpl)
        } else {
            childMenus.add(index, childImpl)
        }
        if (childImpl is IosMenuImpl) {
            childImpl.onMenuChanged = { onStructureChanged?.invoke() }
        }
        onStructureChanged?.invoke()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childMenus.remove(childImpl)
        if (childImpl is IosMenuImpl) {
            childImpl.onMenuChanged = null
        }
        onStructureChanged?.invoke()
    }

    override fun clearChildrenNative() {
        for (m in childMenus) {
            if (m is IosMenuImpl) {
                m.onMenuChanged = null
            }
        }
        super.clearChildrenNative()
        childMenus.clear()
        onStructureChanged?.invoke()
    }
}
