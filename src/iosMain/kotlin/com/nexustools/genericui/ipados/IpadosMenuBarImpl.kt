@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.ios.IosMenuBarImpl

/**
 * iPadOS [MenuBarNativeImpl] forwarding menu additions, removals, and structural changes
 * straight into [IpadosMenuBarBridge] (`UIMenuSystem.mainSystem`).
 */
class IpadosMenuBarImpl : IosMenuBarImpl() {

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        super.addChildNative(childImpl, index)
        IpadosMenuBarBridge.registerAndRebuild(this)
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        IpadosMenuBarBridge.registerAndRebuild(this)
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        IpadosMenuBarBridge.registerAndRebuild(this)
    }
}
