package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl

/**
 * Android `ActionMenuView` implementation of [MenuBarNativeImpl].
 */
class AndroidMenuBarImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.widget.ActionMenuView", "menubar"), MenuBarNativeImpl<AndroidViewHandle> {
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
        if (childImpl is AndroidMenuImpl) {
            childImpl.onMenuChanged = { onStructureChanged?.invoke() }
        }
        onStructureChanged?.invoke()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        childMenus.remove(childImpl)
        if (childImpl is AndroidMenuImpl) {
            childImpl.onMenuChanged = null
        }
        onStructureChanged?.invoke()
    }

    override fun clearChildrenNative() {
        for (m in childMenus) {
            if (m is AndroidMenuImpl) {
                m.onMenuChanged = null
            }
        }
        super.clearChildrenNative()
        childMenus.clear()
        onStructureChanged?.invoke()
    }
}
