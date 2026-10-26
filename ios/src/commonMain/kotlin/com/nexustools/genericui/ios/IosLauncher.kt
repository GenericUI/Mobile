package com.nexustools.genericui.ios

import com.nexustools.genericui.component.shell.IApplication
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence

/**
 * Platform launcher for the iOS UIKit (`:mobile:ios`) backend target.
 */
object IosLauncher {

    /**
     * Prepares the [IApplication] for the iOS backend without starting the lifecycle event loop.
     */
    fun prepare(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.prepareApplication(
        factory = IosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    )

    /**
     * Prepares and launches the [IApplication] on the iOS backend.
     */
    fun launch(
        args: Array<String> = emptyArray(),
        descriptor: GenericUiAppDescriptor
    ): IApplication = GenericUiLaunchSequence.launch(
        factory = IosNativeFactory(),
        args = args,
        descriptor = descriptor,
        isSystemTarget = true
    )

    /**
     * Entry helper for Swift / Xcode `MainViewController()` hosting.
     */
    fun createRootViewController(
        descriptor: GenericUiAppDescriptor,
        args: Array<String> = emptyArray()
    ): IApplication = launch(args, descriptor)
}
