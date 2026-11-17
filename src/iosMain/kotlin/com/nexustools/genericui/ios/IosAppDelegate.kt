@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import kotlinx.cinterop.ExportObjCClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIResponder
import platform.UIKit.UIWindow
import platform.darwin.NSObject

/**
 * Standard iOS `UIApplicationDelegate` driving root `UIWindow` creation and forwarding
 * lifecycle transitions to [IosApplicationImpl.activeInstance].
 */
@ExportObjCClass
class IosAppDelegate : UIResponder(), UIApplicationDelegateProtocol {
    private var appWindow: UIWindow? = null

    override fun window(): UIWindow? = appWindow

    override fun setWindow(window: UIWindow?) {
        appWindow = window
    }

    override fun application(
        application: UIApplication,
        didFinishLaunchingWithOptions: Map<Any?, *>?
    ): Boolean {
        val window = appWindow ?: UIWindow(frame = UikitEnvironment.mainScreenBounds()).also { appWindow = it }
        UikitEnvironment.registeredKeyWindow = window
        val rootVc = UikitEnvironment.registeredRootViewController ?: GenericUiRootViewController().also {
            UikitEnvironment.registeredRootViewController = it
        }
        window.rootViewController = rootVc
        window.makeKeyAndVisible()
        IosApplicationImpl.pendingStartCallback?.invoke()
        IosApplicationImpl.pendingStartCallback = null
        return true
    }

    override fun applicationDidBecomeActive(application: UIApplication) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchStart()
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchResume()
    }

    override fun applicationWillResignActive(application: UIApplication) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchPause()
    }

    override fun applicationDidEnterBackground(application: UIApplication) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchStop()
    }

    override fun applicationWillTerminate(application: UIApplication) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchDestroy()
    }

    override fun applicationDidReceiveMemoryWarning(application: UIApplication) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchLowMemoryWarning(1)
    }
}
