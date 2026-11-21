package com.nexustools.genericui.android

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle

/**
 * Extension-agnostic Android Activity and Application lifecycle observer registered via
 * [AndroidExtensionRuntimeBridge].
 */
interface AndroidActivityLifecycleObserver {
    fun onApplicationCreate(application: Application) {}
    fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    fun onActivityStarted(activity: Activity) {}
    fun onActivityResumed(activity: Activity) {}
    fun onActivityPaused(activity: Activity) {}
    fun onActivityStopped(activity: Activity) {}
    fun onActivityDestroyed(activity: Activity) {}
    fun onActivityNewIntent(activity: Activity, intent: Intent) {}
    fun onActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {}
    fun onRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {}
}
