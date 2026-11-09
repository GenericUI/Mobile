package com.nexustools.genericui.ios

import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.theme.ThemeConfig

/**
 * iOS `UIApplication` + `UISceneDelegate` implementation of [ApplicationNativeImpl],
 * wired to [MobileLifecycleBridge], [ThemeConfig], and `UITraitCollection.userInterfaceStyle`.
 */
class IosApplicationImpl : ApplicationNativeImpl<IosNativeView> {
    override val nativeApplication: IosNativeView = IosNativeView("UIApplication")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()

    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var themeColor: String? = null
    private var themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig

    init {
        installSystemThemeHooks()
        applyUserInterfaceStyle(themeMode)
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        applyUserInterfaceStyle(mode)
        SystemThemeDetector.notifySystemThemeChanged()
    }

    private fun applyUserInterfaceStyle(mode: ThemeMode) {
        nativeApplication.metadata["themeMode"] = mode.name
        nativeApplication.metadata["overrideUserInterfaceStyle"] = when (mode) {
            ThemeMode.AUTO -> "UIUserInterfaceStyleUnspecified"
            ThemeMode.DARK -> "UIUserInterfaceStyleDark"
            ThemeMode.LIGHT -> "UIUserInterfaceStyleLight"
        }
        val isDark = SystemThemeDetector.resolveTheme(mode) == ThemeMode.DARK
        applyThemeConfigMetadata(themeConfig, isDark)
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

    override fun setThemeConfig(config: ThemeConfig) {
        themeConfig = config
        GenericUI.defaultThemeConfig = config
        if (config.mode != ThemeMode.AUTO) {
            themeMode = config.mode
        }
        val isDark = SystemThemeDetector.resolveTheme(themeMode) == ThemeMode.DARK
        applyThemeConfigMetadata(config, isDark)
    }

    override fun getThemeConfig(): ThemeConfig = themeConfig

    private fun applyThemeConfigMetadata(config: ThemeConfig, isDark: Boolean) {
        val colors = config.resolveEffectiveColors(isDark)
        val primary = colors.primary ?: BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark, config)
        val surface = colors.surface ?: if (isDark) RgbaColor(28, 28, 30) else RgbaColor(255, 255, 255)
        val background = colors.background ?: if (isDark) RgbaColor(0, 0, 0) else RgbaColor(242, 242, 247)
        nativeApplication.metadata["themePrimaryColor"] = primary.toHex()
        nativeApplication.metadata["themeSurfaceColor"] = surface.toHex()
        nativeApplication.metadata["themeBackgroundColor"] = background.toHex()
        nativeApplication.backgroundColor = background
        if (themeColor == null) {
            nativeApplication.tintColor = primary
        }
    }

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
