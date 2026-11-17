package com.nexustools.genericui.android

import android.app.Application
import android.content.Context
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor

/**
 * Base Android [Application] class for GenericUI applications.
 *
 * Initializes [AndroidNativeFactory], resolves [createAppDescriptor], registers 1:1
 * [GenericUiFragmentActivity] classes via [registerActivities] and [AndroidLauncher.initialize],
 * and installs [AndroidActivityRegistry] as the active [com.nexustools.genericui.component.fragment.FragmentPlatformLauncher].
 */
open class GenericUiAndroidApplication : Application() {

    /**
     * Returns the [GenericUiAppDescriptor] for this Android application, or `null` if none is provided.
     * Generated `GeneratedAndroidApplication` subclasses override this method.
     */
    open fun createAppDescriptor(): GenericUiAppDescriptor? = AndroidActivityRegistry.activeDescriptor

    /**
     * Registers all `@MainAppFragment` and `@AppFragment` -> [GenericUiFragmentActivity] mappings
     * in [AndroidActivityRegistry]. Generated `GeneratedAndroidApplication` subclasses override this method.
     */
    open fun registerActivities() {}

    override fun onCreate() {
        super.onCreate()
        initializeGenericUi(runCatching { applicationContext }.getOrNull() ?: this)
    }

    /**
     * Initializes [AndroidNativeFactory], [AndroidLauncher], and [AndroidActivityRegistry].
     * Can also be invoked directly in unit tests without requiring the Android framework runtime.
     */
    fun initializeGenericUi(context: Context? = null) {
        val resolvedContext = context ?: runCatching { applicationContext }.getOrNull()
        if (resolvedContext != null) {
            AndroidViewHandle.activeContext = resolvedContext
            AndroidPreferenceStoreImpl.applicationContext = resolvedContext
        }
        GenericUI.initialize(AndroidNativeFactory(resolvedContext))

        val descriptor = createAppDescriptor()
        if (descriptor != null) {
            AndroidLauncher.initialize(descriptor) {
                registerActivities()
            }
        } else {
            registerActivities()
            AndroidActivityRegistry.installAsPlatformLauncher()
        }
    }
}
