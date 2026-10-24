package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.dock.DockPosition
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl

/**
 * iOS implementation of [DockWorkspaceNativeImpl].
 */
class IosDockWorkspaceImpl : IosBaseContainerNativeImpl("UIView", "dock-workspace"), DockWorkspaceNativeImpl<IosNativeView> {
    private var centralImpl: NativeImpl<*>? = null
    private val dockAreas = linkedMapOf<DockPosition, DockAreaNativeImpl<*>>()

    override fun setCentralDocumentNative(centralImpl: NativeImpl<*>?) {
        this.centralImpl?.let { removeChildNative(it) }
        this.centralImpl = centralImpl
        if (centralImpl != null) addChildNative(centralImpl)
    }

    override fun attachDockAreaNative(position: DockPosition, areaImpl: DockAreaNativeImpl<*>) {
        dockAreas[position]?.let { removeChildNative(it) }
        dockAreas[position] = areaImpl
        addChildNative(areaImpl)
    }
}
