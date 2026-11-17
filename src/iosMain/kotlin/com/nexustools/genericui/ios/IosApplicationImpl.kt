@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleNetworkMonitorNativeImpl
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.theme.ThemeConfig
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointerVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.set
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObjectProtocol

/**
 * iOS & iPadOS `UIApplication` implementation of [ApplicationNativeImpl], wired to
 * `UITraitCollection.userInterfaceStyle` dark/light mode detection, [MobileLifecycleBridge],
 * [AppleNetworkMonitorNativeImpl] offline notifications, and optional `UIApplicationMain` startup.
 */
class IosApplicationImpl(
    private val launchArgs: Array<String> = emptyArray(),
    private val startNativeEventLoop: Boolean = false
) : ApplicationNativeImpl<UikitViewHandle> {

    override val nativeApplication: UikitViewHandle = UikitViewHandle(
        uiView = UikitEnvironment.activeRootViewController()?.view ?: UIView(UikitEnvironment.mainScreenBounds()),
        uiViewController = UikitEnvironment.activeRootViewController()
    )

    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    private val networkMonitor = AppleNetworkMonitorNativeImpl()
    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var themeColor: String? = null
    private var themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig
    private val appStyleSheets = mutableListOf<PrecompiledStyleSheet>()
    private var memoryWarningObserver: NSObjectProtocol? = null
    override var isDisposed: Boolean = false

    init {
        installSystemThemeHooks()
        applyUserInterfaceStyle(themeMode)
        activeInstance = this
        memoryWarningObserver = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidReceiveMemoryWarningNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue
        ) { _ ->
            lifecycleBridge.dispatchLowMemoryWarning(trimLevel = 1)
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        applyUserInterfaceStyle(mode)
        SystemThemeDetector.notifySystemThemeChanged(detectSystemThemeMode())
    }

    override fun getThemeMode(): ThemeMode = themeMode

    override fun detectSystemThemeMode(): ThemeMode = UikitEnvironment.detectSystemThemeMode()

    override fun setThemeColor(colorHex: String?) {
        themeColor = colorHex?.trim()?.takeIf { it.isNotEmpty() }
        val resolved = resolveHexColor(themeColor)
        applyWindowTintColor(resolved)
    }

    override fun getThemeColor(): String? = themeColor

    override fun setThemeConfig(config: ThemeConfig) {
        themeConfig = config
        GenericUI.defaultThemeConfig = config
        if (config.mode != ThemeMode.AUTO) {
            themeMode = config.mode
        }
        applyUserInterfaceStyle(themeMode)
    }

    override fun getThemeConfig(): ThemeConfig = themeConfig

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        if (!appStyleSheets.contains(styleSheet)) {
            appStyleSheets += styleSheet
        }
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet) {
        appStyleSheets.remove(styleSheet)
    }

    override fun isOffline(): Boolean = !networkMonitor.isOnline()

    override fun setOnOfflineChanged(listener: ((Boolean) -> Unit)?) {
        if (listener == null) {
            networkMonitor.setOnConnectivityChanged(null)
        } else {
            networkMonitor.setOnConnectivityChanged { online -> listener(!online) }
        }
    }

    override fun runEventLoop(onStarted: () -> Unit) {
        pendingStartCallback = {
            lifecycleBridge.dispatchCreate(null)
            lifecycleBridge.dispatchStart()
            lifecycleBridge.dispatchResume()
            onStarted()
        }
        if (startNativeEventLoop && UikitEnvironment.safeSharedApplication() == null) {
            memScoped {
                val argc = launchArgs.size
                val argv = allocArray<CPointerVar<ByteVar>>(argc.coerceAtLeast(1))
                for (i in launchArgs.indices) {
                    argv[i] = launchArgs[i].cstr.getPointer(this)
                }
                UIApplicationMain(argc, argv, null, "IosAppDelegate")
            }
        } else {
            pendingStartCallback?.invoke()
            pendingStartCallback = null
        }
    }

    override fun exitApplication(exitCode: Int) {
        lifecycleBridge.dispatchStop()
        lifecycleBridge.dispatchDestroy()
    }

    private fun applyUserInterfaceStyle(mode: ThemeMode) {
        val uiStyle = when (mode) {
            ThemeMode.AUTO -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.DARK -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
            ThemeMode.LIGHT -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
        }
        for (win in collectAllWindows()) {
            win.overrideUserInterfaceStyle = uiStyle
        }
        val isDark = SystemThemeDetector.resolveTheme(mode) == ThemeMode.DARK
        val colors = themeConfig.resolveEffectiveColors(isDark)
        val primary = resolveHexColor(themeColor)
            ?: colors.primary
            ?: BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark, themeConfig)
        applyWindowTintColor(primary)
    }

    private fun applyWindowTintColor(color: com.nexustools.genericui.style.color.RgbaColor?) {
        if (color == null) return
        val uiColor = color.toUiColor()
        for (win in collectAllWindows()) {
            win.tintColor = uiColor
        }
    }

    private fun collectAllWindows(): List<UIWindow> {
        val result = linkedSetOf<UIWindow>()
        UikitEnvironment.registeredKeyWindow?.let { result += it }
        val app = UikitEnvironment.safeSharedApplication()
        if (app != null) {
            app.keyWindow?.let { result += it }
            for (win in app.windows) {
                (win as? UIWindow)?.let { result += it }
            }
            for (scene in app.connectedScenes) {
                val ws = scene as? UIWindowScene ?: continue
                for (win in ws.windows) {
                    (win as? UIWindow)?.let { result += it }
                }
            }
        }
        return result.toList()
    }

    private fun resolveHexColor(hex: String?): com.nexustools.genericui.style.color.RgbaColor? {
        val clean = hex?.trim()?.removePrefix("#") ?: return null
        if (clean.length != 6 && clean.length != 8) return null
        val r = clean.substring(0, 2).toIntOrNull(16) ?: return null
        val g = clean.substring(2, 4).toIntOrNull(16) ?: return null
        val b = clean.substring(4, 6).toIntOrNull(16) ?: return null
        val a = if (clean.length == 8) clean.substring(6, 8).toIntOrNull(16) ?: 255 else 255
        return com.nexustools.genericui.style.color.RgbaColor(r, g, b, a)
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        memoryWarningObserver?.let { NSNotificationCenter.defaultCenter.removeObserver(it) }
        memoryWarningObserver = null
        networkMonitor.dispose()
        if (activeInstance === this) {
            activeInstance = null
        }
    }

    companion object {
        var activeInstance: IosApplicationImpl? = null
        var pendingStartCallback: (() -> Unit)? = null

        fun installSystemThemeHooks() {
            SystemThemeDetector.backendDetector = {
                UikitEnvironment.detectSystemThemeMode()
            }
        }
    }
}
