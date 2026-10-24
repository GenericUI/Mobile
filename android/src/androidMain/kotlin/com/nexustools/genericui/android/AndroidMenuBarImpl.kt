package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl

/**
 * Android `ActionMenuView` implementation of [MenuBarNativeImpl].
 */
class AndroidMenuBarImpl : AndroidBaseContainerNativeImpl("androidx.appcompat.widget.ActionMenuView", "menubar"), MenuBarNativeImpl<AndroidViewHandle>
