package com.nexustools.genericui.android

import androidx.appcompat.app.AppCompatDelegate
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl

/**
 * Android implementation of [ApplicationNativeImpl] bridging `AppCompatDelegate` night mode
 * and [MobileLifecycleBridge].
 */
class AndroidApplicationImpl : ApplicationNativeImpl<AndroidViewHandle> {
    override val nativeApplication: AndroidViewHandle = AndroidViewHandle("android.app.Application")
    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    private var themeMode: ThemeMode = ThemeMode.DARK
    private var themeColor: String? = null

    override fun setThemeMode(mode: ThemeMode) {
        themeMode = mode
        val nightMode = when (mode) {
            ThemeMode.LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.DARK -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.SYSTEM -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        nativeApplication.attributes["nightMode"] = nightMode.toString()
    }

    override fun getThemeMode(): ThemeMode = themeMode

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
}
