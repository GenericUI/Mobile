package com.nexustools.genericui.android

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl

/**
 * Android `ContextCompat.checkSelfPermission` + [AndroidActivityRegistry]
 * `RequestMultiplePermissions` / `ActivityCompat.requestPermissions` implementation of
 * [PermissionManagerNativeImpl].
 */
class AndroidPermissionManagerImpl(
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : PermissionManagerNativeImpl {

    private val requestedPermissions = mutableSetOf<PermissionType>()
    private val cachedStatuses = mutableMapOf<PermissionType, PermissionStatus>()

    private fun activeContext(): Context? =
        context ?: AndroidActivityRegistry.resolveActiveActivity()
            ?: AndroidPreferenceStoreImpl.applicationContext
            ?: AndroidViewHandle.activeContext

    override fun checkPermission(permission: PermissionType): PermissionStatus {
        if (permission == PermissionType.SYSTEM_TRAY) {
            return PermissionStatus.NOT_APPLICABLE
        }
        val ctx = activeContext() ?: return cachedStatuses[permission] ?: PermissionStatus.GRANTED

        if (permission == PermissionType.NOTIFICATIONS && Build.VERSION.SDK_INT < 33) {
            val enabled = runCatching { NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
                .getOrDefault(true)
            return if (enabled) PermissionStatus.GRANTED else PermissionStatus.DENIED
        }

        val manifestPermissions = mapToManifestPermissions(permission)
        if (manifestPermissions.isEmpty()) {
            return PermissionStatus.GRANTED
        }

        val allGranted = manifestPermissions.all { perm ->
            runCatching {
                ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(true)
        }
        if (allGranted) {
            cachedStatuses[permission] = PermissionStatus.GRANTED
            return PermissionStatus.GRANTED
        }

        val activity = AndroidActivityRegistry.resolveActiveActivity()
        if (activity != null && permission in requestedPermissions) {
            val shouldShowRationale = manifestPermissions.any { perm ->
                runCatching { ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) }
                    .getOrDefault(false)
            }
            if (!shouldShowRationale) {
                return PermissionStatus.PERMANENTLY_DENIED
            }
        }
        return PermissionStatus.PROMPT_REQUIRED
    }

    override fun requestPermission(permission: PermissionType, onResult: (PermissionStatus) -> Unit) {
        if (permission == PermissionType.SYSTEM_TRAY) {
            onResult(PermissionStatus.NOT_APPLICABLE)
            return
        }
        val current = checkPermission(permission)
        if (current == PermissionStatus.GRANTED || current == PermissionStatus.NOT_APPLICABLE) {
            onResult(current)
            return
        }

        val manifestPermissions = mapToManifestPermissions(permission)
        if (manifestPermissions.isEmpty()) {
            val evaluated = checkPermission(permission)
            cachedStatuses[permission] = evaluated
            onResult(evaluated)
            return
        }

        requestedPermissions.add(permission)
        val launched = AndroidActivityRegistry.requestRuntimePermissions(manifestPermissions.toTypedArray()) { results ->
            val allGranted = manifestPermissions.all { perm -> results[perm] == true }
            val finalStatus = if (allGranted) {
                PermissionStatus.GRANTED
            } else {
                val activity = AndroidActivityRegistry.resolveActiveActivity()
                val canAskAgain = activity != null && manifestPermissions.any { perm ->
                    runCatching { ActivityCompat.shouldShowRequestPermissionRationale(activity, perm) }
                        .getOrDefault(true)
                }
                if (!canAskAgain) PermissionStatus.PERMANENTLY_DENIED else PermissionStatus.DENIED
            }
            cachedStatuses[permission] = finalStatus
            onResult(finalStatus)
        }
        if (!launched) {
            val fallback = checkPermission(permission)
            cachedStatuses[permission] = fallback
            onResult(fallback)
        }
    }

    override fun dispose() {
        requestedPermissions.clear()
        cachedStatuses.clear()
    }

    private fun mapToManifestPermissions(permission: PermissionType): List<String> = when (permission) {
        PermissionType.NOTIFICATIONS -> if (Build.VERSION.SDK_INT >= 33) {
            listOf("android.permission.POST_NOTIFICATIONS")
        } else {
            emptyList()
        }
        PermissionType.SYSTEM_TRAY -> emptyList()
        PermissionType.CAMERA -> listOf(Manifest.permission.CAMERA)
        PermissionType.MICROPHONE -> listOf(Manifest.permission.RECORD_AUDIO)
        PermissionType.READ_MEDIA_STORAGE -> if (Build.VERSION.SDK_INT >= 33) {
            listOf(
                "android.permission.READ_MEDIA_IMAGES",
                "android.permission.READ_MEDIA_VIDEO",
                "android.permission.READ_MEDIA_AUDIO"
            )
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        PermissionType.WRITE_MEDIA_STORAGE -> if (Build.VERSION.SDK_INT >= 29) {
            emptyList()
        } else {
            listOf(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        PermissionType.LOCATION -> listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    }
}
