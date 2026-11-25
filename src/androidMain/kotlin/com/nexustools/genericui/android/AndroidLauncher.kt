package com.nexustools.genericui.android

import com.nexustools.genericui.component.fragment.FragmentRegistry
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.ui.ThemeMode
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

        ensureApplicationConfigured(descriptor)

        for ((fragmentClass, fragmentId) in descriptor.fragmentIds) {
            val route = descriptor.fragmentRoutes[fragmentClass].orEmpty()
            @Suppress("UNCHECKED_CAST")
            FragmentRegistry.registerMetadata(fragmentClass as KClass<IFragment>, fragmentId, route)
        }
        for ((fragmentClass, route) in descriptor.fragmentRoutes) {
            if (!descriptor.fragmentIds.containsKey(fragmentClass)) {
                @Suppress("UNCHECKED_CAST")
                val typedClass = fragmentClass as KClass<IFragment>
                FragmentRegistry.registerMetadata(
                    typedClass,
                    FragmentRegistry.resolveFragmentId(typedClass),
                    route
                )
            }
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

    internal fun ensureApplicationConfigured(descriptor: GenericUiAppDescriptor) {
        runCatching {
            val app = Application.current
                ?: (descriptor.applicationFactory?.invoke(null) as? Application)
                ?: Application(
                    title = descriptor.windowTitle,
                    initialThemeColor = descriptor.themeColor
                )
            val mode = when (descriptor.themeMode.trim().lowercase()) {
                "dark" -> ThemeMode.DARK
                "light" -> ThemeMode.LIGHT
                else -> ThemeMode.AUTO
            }
            app.setThemeMode(mode)
            descriptor.precompiledStyleSheetInstaller?.invoke(app)
            descriptor.globalStyleSheetResourceId?.let { app.addStyleSheet(it) }
            descriptor.globalStyleSheetPath?.takeIf { it.isNotBlank() }?.let { app.addStyleSheet(it) }
        }
    }
}
