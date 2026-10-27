package com.nexustools.genericui.android

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * Android implementation of [ApplicationNativeImpl] bridging `AppCompatDelegate` night mode
 * (`MODE_NIGHT_FOLLOW_SYSTEM` for [ThemeMode.AUTO]) and [MobileLifecycleBridge].
 */
class AndroidApplicationImpl : ApplicationNativeImpl<AndroidViewHandle> {
    override val nativeApplication: AndroidViewHandle = AndroidViewHandle("android.app.Application")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    private var themeMode: ThemeMode = ThemeMode.AUTO
    private var themeColor: String? = null

    init {
        installSystemThemeDetector()
        setThemeMode(themeMode)
    }

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        val nightMode = when (mode) {
            ThemeMode.AUTO -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
        }
        nativeApplication.attributes["nightMode"] = nightMode.toString()
        nativeApplication.attributes["resolvedTheme"] = SystemThemeDetector.resolveTheme(mode).name
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
                val uiMode = ctx.resources?.configuration?.uiMode ?: 0
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
