@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.ios.GenericUiRootViewController
import com.nexustools.genericui.ios.IosApplicationImpl
import com.nexustools.genericui.ios.UikitEnvironment
import kotlinx.cinterop.ExportObjCClass
import kotlinx.cinterop.useContents
import platform.Foundation.NSUserActivity
import platform.UIKit.UICoordinateSpaceProtocol
import platform.UIKit.UIInterfaceOrientation
import platform.UIKit.UIMenuSystem
import platform.UIKit.UIResponder
import platform.UIKit.UIScene
import platform.UIKit.UISceneConnectionOptions
import platform.UIKit.UISceneSession
import platform.UIKit.UITraitCollection
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneActivationRequestOptions
import platform.UIKit.UIWindowSceneDelegateProtocol
import platform.UIKit.UIWindowSceneGeometry

/**
 * Multi-window `UIWindowSceneDelegate` for iPadOS supporting Stage Manager, iPadOS Split View /
 * Slide Over, iPadOS 26 freeform resizable windows (`UIWindowSceneGeometry`, `sizeRestrictions`),
 * multi-window scene session activation (`requestNewWindowScene`), and live `UIMenuSystem` sync.
 */
@ExportObjCClass
class IpadosSceneDelegate : UIResponder(), UIWindowSceneDelegateProtocol {

    private var sceneWindow: UIWindow? = null

    override fun window(): UIWindow? = sceneWindow

    override fun setWindow(window: UIWindow?) {
        sceneWindow = window
    }

    override fun scene(
        scene: UIScene,
        willConnectToSession: UISceneSession,
        options: UISceneConnectionOptions
    ) {
        val windowScene = scene as? UIWindowScene ?: return
        val window = UIWindow(windowScene = windowScene)
        val rootController = UikitEnvironment.registeredRootViewController as? GenericUiRootViewController
            ?: GenericUiRootViewController()
        window.rootViewController = rootController
        sceneWindow = window

        UikitEnvironment.registeredKeyWindow = window
        UikitEnvironment.registeredRootViewController = rootController

        IpadosWindowImpl.forController(rootController)?.attachToWindowScene(windowScene, window)
        window.makeKeyAndVisible()
        UIMenuSystem.mainSystem.setNeedsRebuild()

        IosApplicationImpl.pendingStartCallback?.invoke()
        IosApplicationImpl.pendingStartCallback = null
    }

    override fun windowScene(
        windowScene: UIWindowScene,
        didUpdateEffectiveGeometry: UIWindowSceneGeometry
    ) {
        val targetWindow = sceneWindowsRegistry[windowScene] ?: return
        windowScene.coordinateSpace.bounds.useContents {
            targetWindow.notifySceneGeometryChanged(size.width.toInt(), size.height.toInt())
        }
    }

    override fun windowScene(
        windowScene: UIWindowScene,
        didUpdateCoordinateSpace: UICoordinateSpaceProtocol,
        interfaceOrientation: UIInterfaceOrientation,
        traitCollection: UITraitCollection
    ) {
        val targetWindow = sceneWindowsRegistry[windowScene] ?: return
        didUpdateCoordinateSpace.bounds.useContents {
            targetWindow.notifySceneGeometryChanged(size.width.toInt(), size.height.toInt())
        }
    }

    override fun sceneDidBecomeActive(scene: UIScene) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchStart()
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchResume()
        UIMenuSystem.mainSystem.setNeedsRebuild()
    }

    override fun sceneWillResignActive(scene: UIScene) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchPause()
    }

    override fun sceneDidEnterBackground(scene: UIScene) {
        IosApplicationImpl.activeInstance?.lifecycleBridge?.dispatchStop()
    }

    override fun sceneDidDisconnect(scene: UIScene) {
        val ws = scene as? UIWindowScene ?: return
        sceneWindowsRegistry.remove(ws)
    }

    companion object {
        fun registerWindowForScene(scene: UIWindowScene, window: IpadosWindowImpl) {
            sceneWindowsRegistry[scene] = window
        }

        fun windowForScene(scene: UIWindowScene): IpadosWindowImpl? = sceneWindowsRegistry[scene]

        fun requestNewWindowScene(
            activityType: String = "com.nexustools.genericui.window",
            onError: ((String) -> Unit)? = null
        ): Boolean {
            val app = UikitEnvironment.safeSharedApplication() ?: return false
            if (!app.supportsMultipleScenes) return false
            val activity = NSUserActivity(activityType = activityType)
            val options = UIWindowSceneActivationRequestOptions()
            app.requestSceneSessionActivation(
                sceneSession = null,
                userActivity = activity,
                options = options,
                errorHandler = { error ->
                    if (error != null) {
                        onError?.invoke(error.localizedDescription)
                    }
                }
            )
            return true
        }
    }
}

private val sceneWindowsRegistry = LinkedHashMap<UIWindowScene, IpadosWindowImpl>()
