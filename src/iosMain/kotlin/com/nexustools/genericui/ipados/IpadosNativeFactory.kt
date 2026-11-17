package com.nexustools.genericui.ipados

import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.ios.IosNativeFactory
import com.nexustools.genericui.mobile.layout.MobileLayoutFactory
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.TitleCapabilities

/**
 * iPadOS [NativeFactory] extending [IosNativeFactory] for [BackendTarget.IPadOS].
 *
 * - Uses [MobileLayoutFactory] so layouts respond smoothly from Slide Over (~320pt) through
 *   Split View and Stage Manager / iPadOS 26 freeform multi-window desktops.
 * - Enables multi-window `UIWindowScene` management (`supportsMultipleWindows = true`) and
 *   desktop-class `UIMenuSystem.mainSystem` menu bars (`supportsSystemMenuBar = true`), while
 *   reusing every `:apple` OS service and real UIKit widget from `IosNativeFactory`.
 */
class IpadosNativeFactory : IosNativeFactory(backendTarget = BackendTarget.IPadOS) {

    override val layoutFactory: LayoutFactory = MobileLayoutFactory

    override val defaultTitleCapabilities: TitleCapabilities
        get() = IpadosWindowImpl.DEFAULT_TITLE_CAPABILITIES

    override val supportsMultipleWindows: Boolean = true
    override val supportsToolModalWindows: Boolean = true
    override val supportsSystemMenuBar: Boolean = true
    override val supportsSystemTray: Boolean = false

    override fun createWindow(): WindowNativeImpl<*> = IpadosWindowImpl()

    override fun createMenuBar(): MenuBarNativeImpl<*> = IpadosMenuBarImpl()
}
