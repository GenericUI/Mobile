package com.nexustools.genericui.ios

/**
 * Extension-agnostic UIKit / iOS / iPadOS lifecycle observer registered with
 * [UikitExtensionLifecycleBridge].
 */
interface UikitExtensionLifecycleObserver {
    fun onApplicationLaunched() {}
    fun onSceneWillEnterForeground() {}
    fun onSceneDidBecomeActive() {}
    fun onSceneWillResignActive() {}
    fun onSceneDidEnterBackground() {}
    fun onLowMemoryWarning() {}
}
