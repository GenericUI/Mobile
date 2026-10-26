package com.nexustools.genericui.android

import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import kotlin.reflect.KClass

/**
 * Bootstrap coordinator for the `:mobile:android` backend.
 *
 * Installs the [GenericUiAppDescriptor], runs resource and extension provider installers,
 * registers all discovered fragment factories in [FragmentRegistry], executes 1:1
 * [AndroidActivityRegistry] activity registrations, and installs [AndroidActivityRegistry]
 * as the active platform fragment launcher.
 */
object AndroidLauncher {

    /**
     * Initializes the Android runtime from [descriptor] and runs [activityRegistrations].
     */
    fun initialize(
        descriptor: GenericUiAppDescriptor,
        activityRegistrations: () -> Unit = {}
    ) {
        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory(AndroidViewHandle.activeContext))
        }

        AndroidActivityRegistry.activeDescriptor = descriptor
        descriptor.resourceRegistryInstaller?.invoke()
        for (installer in descriptor.providerInstallers) {
            installer.invoke()
        }

        for ((fragmentClass, factory) in descriptor.fragmentRegistrations) {
            @Suppress("UNCHECKED_CAST")
            FragmentRegistry.register(fragmentClass as KClass<IFragment>, factory)
        }

        val mainClass = descriptor.mainFragmentClass
        val mainFactory = descriptor.mainFragmentFactory
        if (mainClass != null && mainFactory != null) {
            @Suppress("UNCHECKED_CAST")
            FragmentRegistry.register(mainClass as KClass<IFragment>, mainFactory)
        }
        if (mainClass != null && AndroidActivityRegistry.mainFragmentClass == null) {
            AndroidActivityRegistry.mainFragmentClass = mainClass
        }

        activityRegistrations.invoke()
        AndroidActivityRegistry.installAsPlatformLauncher()
    }
}
