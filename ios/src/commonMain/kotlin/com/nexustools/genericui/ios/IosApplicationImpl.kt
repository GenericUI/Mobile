package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * iOS `UIApplication` + `UISceneDelegate` implementation of [ApplicationNativeImpl],
 * wired to [MobileLifecycleBridge] and `UITraitCollection.userInterfaceStyle`.
 */
class IosApplicationImpl : ApplicationNativeImpl<IosNativeView> {
    override val nativeApplication: IosNativeView = IosNativeView("UIApplication")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()

    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var themeColor: String? = null

    init {
        installSystemThemeHooks()
        applyUserInterfaceStyle(themeMode)
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        applyUserInterfaceStyle(mode)
        SystemThemeDetector.notifyThemeChanged()
    }

    private fun applyUserInterfaceStyle(mode: ThemeMode) {
        nativeApplication.metadata["themeMode"] = mode.name
        nativeApplication.metadata["overrideUserInterfaceStyle"] = when (mode) {
            ThemeMode.AUTO -> "UIUserInterfaceStyleUnspecified"
            ThemeMode.DARK -> "UIUserInterfaceStyleDark"
            ThemeMode.LIGHT -> "UIUserInterfaceStyleLight"
        }
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

    companion object {
        var systemUserInterfaceStyle: String = "UIUserInterfaceStyleDark"

        fun installSystemThemeHooks() {
            SystemThemeDetector.backendDetector = {
                if (systemUserInterfaceStyle == "UIUserInterfaceStyleLight") {
                    ThemeMode.LIGHT
                } else {
                    ThemeMode.DARK
                }
            }
        }
    }
}
