package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl

/**
 * iOS `UIMenuBuilder` implementation of [MenuBarNativeImpl] (used on iPadOS hardware keyboard menu bar).
 */
class IosMenuBarImpl : IosBaseContainerNativeImpl("UIMenuBar", "menubar"), MenuBarNativeImpl<IosNativeView>
