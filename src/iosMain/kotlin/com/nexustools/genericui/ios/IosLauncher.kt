package com.nexustools.genericui.ios

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence
import platform.UIKit.UIViewController

/**
 * Platform launcher for the iOS UIKit backend target.
 */
object IosLauncher {

    var activeApplication: IApplication? = null
        private set

    fun prepare(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.prepareApplication(
        factory = IosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    ).also { activeApplication = it }

    fun launch(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.launch(
        factory = IosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    ).also { activeApplication = it }

    /**
     * Entry helper for Swift / Xcode `MainViewController()` hosting, returning the genuine
     * [GenericUiRootViewController] hosting the root GenericUI `UIWindow` view hierarchy.
     */
    fun createRootViewController(
        descriptor: GenericUiAppDescriptor,
        args: Array<String> = emptyArray()
    ): UIViewController {
        UikitEnvironment.registeredRootViewController = null
        launch(args, descriptor)
        val controller = UikitEnvironment.registeredRootViewController
            ?: GenericUiRootViewController()
        UikitEnvironment.registeredRootViewController = controller
        return controller
    }
}
