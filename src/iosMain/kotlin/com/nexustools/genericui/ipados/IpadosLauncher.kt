package com.nexustools.genericui.ipados

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence
import com.nexustools.genericui.ios.GenericUiRootViewController
import com.nexustools.genericui.ios.UikitEnvironment
import platform.UIKit.UIViewController

/**
 * Platform launcher for the iPadOS UIKit (`UIWindowScene` + `UIMenuSystem`) backend target.
 */
object IpadosLauncher {

    var activeApplication: IApplication? = null
        private set

    fun prepare(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.prepareApplication(
        factory = IpadosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    ).also { activeApplication = it }

    fun launch(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.launch(
        factory = IpadosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    ).also { activeApplication = it }

    /**
     * Entry helper for Swift / Xcode `MainViewController()` hosting on iPadOS.
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
