@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.geometry.EdgeInsets
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIApplication
import platform.UIKit.UIScreen
import platform.UIKit.UITraitCollection
import platform.UIKit.UIUserInterfaceStyle
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.currentTraitCollection
import platform.posix.memcpy

/**
 * Shared UIKit environment utilities that safely interact with `UIApplication`, active
 * `UIWindow` / `UIViewController` instances, screen metrics, and `NSData` bridging both inside
 * full UIKit app bundles and inside headless Kotlin/Native `.kexe` unit test runners.
 */
object UikitEnvironment {
    var registeredKeyWindow: UIWindow? = null
    var registeredRootViewController: UIViewController? = null

    fun safeSharedApplication(): UIApplication? =
        runCatching { UIApplication.sharedApplication }.getOrNull()

    fun hasHostedUiApplication(): Boolean {
        val app = safeSharedApplication() ?: return false
        val hasDelegate = runCatching { app.delegate != null }.getOrDefault(false)
        val hasScenes = runCatching { app.connectedScenes.isNotEmpty() }.getOrDefault(false)
        return hasDelegate || hasScenes
    }

    fun activeKeyWindow(): UIWindow? {
        registeredKeyWindow?.let { return it }
        val app = safeSharedApplication() ?: return null
        return app.keyWindow ?: (app.windows.firstOrNull() as? UIWindow)
    }

    fun activeRootViewController(): UIViewController? =
        registeredRootViewController ?: activeKeyWindow()?.rootViewController

    fun topMostViewController(): UIViewController? {
        var current = activeRootViewController() ?: return null
        while (true) {
            val next = current.presentedViewController ?: break
            current = next
        }
        return current
    }

    /**
     * Presents [viewController] modally on the active host controller only when the host view
     * hierarchy is attached to a live `UIWindow` so unit tests without UIApplicationMain never fail.
     */
    fun presentIfWindowAttached(
        viewController: UIViewController,
        animated: Boolean = true,
        completion: (() -> Unit)? = null
    ): Boolean {
        val presenter = topMostViewController() ?: return false
        if (presenter.view.window == null && registeredKeyWindow == null) {
            return false
        }
        presenter.presentViewController(viewController, animated = animated, completion = completion)
        return true
    }

    fun mainScreenScale(): Float =
        runCatching { UIScreen.mainScreen.scale.toFloat() }
            .getOrNull()
            ?.takeIf { it > 0f }
            ?: 1f

    fun mainScreenBounds(): CValue<CGRect> =
        runCatching { UIScreen.mainScreen.bounds }
            .getOrElse { CGRectMake(0.0, 0.0, 390.0, 844.0) }

    fun readSafeAreaInsets(): EdgeInsets {
        val window = activeKeyWindow()
        val view = activeRootViewController()?.viewIfLoaded
        val insets = when {
            window != null -> window.safeAreaInsets
            view != null -> view.safeAreaInsets
            else -> return EdgeInsets(0, 0, 0, 0)
        }
        return insets.useContents {
            EdgeInsets(
                top = top.toInt(),
                right = right.toInt(),
                bottom = bottom.toInt(),
                left = left.toInt()
            )
        }
    }

    fun isSystemDarkMode(): Boolean {
        val traitStyle = runCatching {
            activeKeyWindow()?.traitCollection?.userInterfaceStyle
                ?: UITraitCollection.currentTraitCollection.userInterfaceStyle
        }.getOrNull()
        return traitStyle == UIUserInterfaceStyle.UIUserInterfaceStyleDark
    }

    fun detectSystemThemeMode(): ThemeMode =
        if (isSystemDarkMode()) ThemeMode.DARK else ThemeMode.LIGHT

    fun bytesToNSData(bytes: ByteArray): NSData {
        if (bytes.isEmpty()) return NSData()
        return bytes.usePinned { pinned ->
            NSData.dataWithBytes(bytes = pinned.addressOf(0), length = bytes.size.toULong())
        }
    }

    fun nsDataToBytes(data: NSData?): ByteArray {
        val length = data?.length?.toInt() ?: 0
        if (length <= 0 || data == null) return ByteArray(0)
        val out = ByteArray(length)
        val ptr = data.bytes ?: return out
        out.usePinned { pinned ->
            memcpy(pinned.addressOf(0), ptr, length.toULong())
        }
        return out
    }
}
