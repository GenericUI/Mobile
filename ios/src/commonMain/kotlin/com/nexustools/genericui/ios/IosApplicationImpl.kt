package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl

/**
 * iOS `UIApplication` + `UISceneDelegate` implementation of [ApplicationNativeImpl],
 * wired to [MobileLifecycleBridge].
 */
class IosApplicationImpl : ApplicationNativeImpl<IosNativeView> {
    override val nativeApplication: IosNativeView = IosNativeView("UIApplication")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()

    private var themeMode: ThemeMode = ThemeMode.DARK
    private var themeColor: String? = null

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        nativeApplication.metadata["overrideUserInterfaceStyle"] = mode.name
    }

    override fun getThemeMode(): ThemeMode = themeMode

    override fun setThemeColor(colorHex: String?) {
        val clean = colorHex?.trim()?.takeIf { it.isNotEmpty() }
        themeColor = clean
        if (clean != null) {
            nativeApplication.metadata["tintColor"] = clean
            nativeApplication.metadata["themeColor"] = clean
        } else {
            nativeApplication.metadata.remove("tintColor")
            nativeApplication.metadata.remove("themeColor")
        }
    }

    override fun getThemeColor(): String? = themeColor

    override fun runEventLoop(onStarted: () -> Unit) {
        lifecycleBridge.dispatchCreate(null)
        lifecycleBridge.dispatchStart()
        lifecycleBridge.dispatchResume()
        onStarted()
    }

    override fun exitApplication(exitCode: Int) {
        lifecycleBridge.dispatchStop()
        lifecycleBridge.dispatchDestroy()
    }

    override fun dispose() {
        lifecycleBridge.dispatchDestroy()
    }
}
