package com.nexustools.genericui.android

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.nexustools.genericui.core.GenericUI

/**
 * Base Android [Service] class for background or foreground services declared in
 * `genericui { android { services { service("...") } } }`.
 *
 * Ensures [GenericUI] is initialized with [AndroidNativeFactory] before invoking
 * [onServiceCreate] or [onServiceStartCommand].
 */
abstract class GenericUiAndroidService : Service() {

    override fun onCreate() {
        super.onCreate()
        ensureGenericUiInitialized()
        onServiceCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureGenericUiInitialized()
        return onServiceStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        onServiceDestroy()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * Called when the service is created after [GenericUI] has been initialized.
     */
    open fun onServiceCreate() {}

    /**
     * Called on each [onStartCommand] invocation. Defaults to [START_STICKY].
     */
    open fun onServiceStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    /**
     * Called when the service is being destroyed before `super.onDestroy()`.
     */
    open fun onServiceDestroy() {}

    /**
     * Ensures [GenericUI.activeFactory] is initialized with [AndroidNativeFactory].
     */
    fun ensureGenericUiInitialized() {
        val ctx = runCatching { applicationContext }.getOrNull() ?: AndroidViewHandle.activeContext
        if (ctx != null) {
            AndroidViewHandle.activeContext = ctx
            AndroidPreferenceStoreImpl.applicationContext = ctx
        }
        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory(ctx))
        }
    }
}
