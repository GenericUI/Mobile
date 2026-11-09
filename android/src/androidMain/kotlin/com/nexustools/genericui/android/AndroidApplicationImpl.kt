package com.nexustools.genericui.android

import android.app.Activity
import android.app.ActivityManager
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
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
 * Android implementation of [ApplicationNativeImpl] bridging `AppCompatDelegate` night mode
 * (`MODE_NIGHT_FOLLOW_SYSTEM` for [ThemeMode.AUTO]), [ThemeConfig] system bar / task description
 * colors, and [MobileLifecycleBridge].
 */
class AndroidApplicationImpl : ApplicationNativeImpl<AndroidViewHandle> {
    override val nativeApplication: AndroidViewHandle = AndroidViewHandle("android.app.Application")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    val pendingTasks: MutableList<() -> Unit> = mutableListOf()
    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var themeColor: String? = null
    private var themeConfig: ThemeConfig = BootstrapColorPalette.activeThemeConfig

    init {
        installSystemThemeDetector()
        setThemeMode(themeMode)
    }

    fun invokeLater(task: () -> Unit) {
        val looper = runCatching { android.os.Looper.getMainLooper() }.getOrNull()
        if (looper != null) {
            AndroidViewHandle.runOnMainThread(task)
        } else {
            pendingTasks.add(task)
            AndroidViewHandle.runOnMainThread(task)
        }
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        val nightMode = when (mode) {
            ThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        val resolved = SystemThemeDetector.resolveTheme(mode)
        nativeApplication.attributes["nightMode"] = nightMode.toString()
        nativeApplication.attributes["resolvedTheme"] = resolved.name
        applyThemeConfigToSystemBars(themeConfig, resolved == ThemeMode.DARK)
        runCatching {
            AppCompatDelegate.setDefaultNightMode(nightMode)
        }
    }

    override fun getThemeMode(): ThemeMode = themeMode

    override fun detectSystemThemeMode(): ThemeMode = detectAndroidSystemTheme()

    override fun setThemeColor(colorHex: String?) {
        val clean = colorHex?.trim()?.takeIf { it.isNotEmpty() }
        themeColor = clean
        if (clean != null) {
            nativeApplication.attributes["themeColor"] = clean
            nativeApplication.attributes["statusBarColor"] = clean
        } else {
            nativeApplication.attributes.remove("themeColor")
            nativeApplication.attributes.remove("statusBarColor")
        }
    }

    override fun getThemeColor(): String? = themeColor

    override fun setThemeConfig(config: ThemeConfig) {
        themeConfig = config
        GenericUI.defaultThemeConfig = config
        if (config.mode != ThemeMode.AUTO) {
            themeMode = config.mode
        }
        val resolved = SystemThemeDetector.resolveTheme(themeMode)
        applyThemeConfigToSystemBars(config, resolved == ThemeMode.DARK)
    }

    override fun getThemeConfig(): ThemeConfig = themeConfig

    private fun applyThemeConfigToSystemBars(config: ThemeConfig, isDark: Boolean) {
        val colors = config.resolveEffectiveColors(isDark)
        val primary = colors.primary ?: BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark, config)
        val surface = colors.surface ?: if (isDark) RgbaColor(28, 28, 30) else RgbaColor(255, 255, 255)
        val background = colors.background ?: if (isDark) RgbaColor(18, 18, 20) else RgbaColor(245, 245, 247)
        val primaryHex = primary.toHex()
        val surfaceHex = surface.toHex()
        val backgroundHex = background.toHex()
        nativeApplication.attributes["themePrimaryColor"] = primaryHex
        nativeApplication.attributes["themeSurfaceColor"] = surfaceHex
        nativeApplication.attributes["themeBackgroundColor"] = backgroundHex
        nativeApplication.attributes["statusBarColor"] = themeColor ?: (colors.statusBar?.toHex() ?: surfaceHex)
        nativeApplication.attributes["navigationBarColor"] = surfaceHex
        nativeApplication.attributes["taskDescriptionColor"] = primaryHex
        runCatching {
            val activity = AndroidViewHandle.activeContext as? Activity ?: return@runCatching
            activity.window?.statusBarColor = (colors.statusBar ?: surface).toArgbInt()
            activity.window?.navigationBarColor = surface.toArgbInt()
            @Suppress("DEPRECATION")
            activity.setTaskDescription(ActivityManager.TaskDescription(null, null, primary.toArgbInt()))
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
        fun detectAndroidSystemTheme(): ThemeMode {
            val ctx = AndroidViewHandle.activeContext ?: AndroidPreferenceStoreImpl.applicationContext
            if (ctx != null) {
                val uiMode = runCatching { ctx.resources?.configuration?.uiMode ?: 0 }.getOrDefault(0)
                val nightBits = uiMode and Configuration.UI_MODE_NIGHT_MASK
                if (nightBits == Configuration.UI_MODE_NIGHT_NO) return ThemeMode.LIGHT
                if (nightBits == Configuration.UI_MODE_NIGHT_YES) return ThemeMode.DARK
            }
            return SystemThemeDetector.fallbackSystemTheme
        }

        internal fun installSystemThemeDetector() {
            SystemThemeDetector.backendDetector = { detectAndroidSystemTheme() }
        }
    }
}
