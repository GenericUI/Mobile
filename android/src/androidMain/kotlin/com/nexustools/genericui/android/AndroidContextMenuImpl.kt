package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl

/**
 * Android `PopupMenu` / context menu implementation of [ContextMenuNativeImpl].
 */
class AndroidContextMenuImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.widget.PopupMenu", "dropdown-menu"), ContextMenuNativeImpl<AndroidViewHandle> {
    override fun showAt(anchor: NativeImpl<*>?, x: Int, y: Int) { setVisible(true) }
    override fun hide() { setVisible(false) }
}
