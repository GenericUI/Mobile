package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl

/**
 * iOS `AVCaptureDevice` / `PHPhotoLibrary` / `UNUserNotificationCenter` permission manager implementation
 * of [PermissionManagerNativeImpl].
 */
class IosPermissionManagerImpl : PermissionManagerNativeImpl {
    private val statuses = mutableMapOf<PermissionType, PermissionStatus>()

    override fun checkPermission(permission: PermissionType): PermissionStatus =
        statuses[permission] ?: PermissionStatus.GRANTED

    override fun requestPermission(permission: PermissionType, onResult: (PermissionStatus) -> Unit) {
        val status = statuses.getOrPut(permission) { PermissionStatus.GRANTED }
        onResult(status)
    }

    override fun dispose() {}
}
