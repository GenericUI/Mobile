@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import platform.AVFoundation.AVAuthorizationStatusAuthorized
import platform.AVFoundation.AVAuthorizationStatusDenied
import platform.AVFoundation.AVAuthorizationStatusNotDetermined
import platform.AVFoundation.AVAuthorizationStatusRestricted
import platform.AVFoundation.AVCaptureDevice
import platform.AVFoundation.AVMediaTypeAudio
import platform.AVFoundation.AVMediaTypeVideo
import platform.AVFoundation.authorizationStatusForMediaType
import platform.AVFoundation.requestAccessForMediaType
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedAlways
import platform.CoreLocation.kCLAuthorizationStatusAuthorizedWhenInUse
import platform.CoreLocation.kCLAuthorizationStatusDenied
import platform.CoreLocation.kCLAuthorizationStatusNotDetermined
import platform.CoreLocation.kCLAuthorizationStatusRestricted
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusDenied
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHAuthorizationStatusNotDetermined
import platform.Photos.PHAuthorizationStatusRestricted
import platform.Photos.PHPhotoLibrary
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

/**
 * Genuine Apple iOS/iPadOS permission manager querying `AVCaptureDevice`, `PHPhotoLibrary`,
 * `CLLocationManager`, and `UNUserNotificationCenter`.
 */
class IosPermissionManagerImpl : PermissionManagerNativeImpl {
    override var isDisposed: Boolean = false
    private var lastNotificationStatus: PermissionStatus = PermissionStatus.PROMPT_REQUIRED

    override fun checkPermission(permission: PermissionType): PermissionStatus {
        return when (permission) {
            PermissionType.SYSTEM_TRAY -> PermissionStatus.NOT_APPLICABLE
            PermissionType.CAMERA -> mapAvStatus(
                runCatching { AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeVideo) }
                    .getOrDefault(AVAuthorizationStatusNotDetermined)
            )
            PermissionType.MICROPHONE -> mapAvStatus(
                runCatching { AVCaptureDevice.authorizationStatusForMediaType(AVMediaTypeAudio) }
                    .getOrDefault(AVAuthorizationStatusNotDetermined)
            )
            PermissionType.READ_MEDIA_STORAGE,
            PermissionType.WRITE_MEDIA_STORAGE -> mapPhotoStatus(
                runCatching { PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite) }
                    .getOrDefault(PHAuthorizationStatusNotDetermined)
            )
            PermissionType.LOCATION -> mapLocationStatus(
                runCatching { CLLocationManager().authorizationStatus }
                    .getOrDefault(kCLAuthorizationStatusNotDetermined)
            )
            PermissionType.NOTIFICATIONS -> {
                runCatching {
                    UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                        if (settings != null) {
                            lastNotificationStatus = when (settings.authorizationStatus) {
                                UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional -> PermissionStatus.GRANTED
                                UNAuthorizationStatusDenied -> PermissionStatus.PERMANENTLY_DENIED
                                else -> PermissionStatus.PROMPT_REQUIRED
                            }
                        }
                    }
                }
                lastNotificationStatus
            }
        }
    }

    override fun requestPermission(
        permission: PermissionType,
        onResult: (PermissionStatus) -> Unit
    ) {
        when (permission) {
            PermissionType.SYSTEM_TRAY -> onResult(PermissionStatus.NOT_APPLICABLE)
            PermissionType.CAMERA -> {
                val res = runCatching {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeVideo) { granted ->
                        val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                        dispatch_async(dispatch_get_main_queue()) { onResult(status) }
                    }
                }
                if (res.isFailure) onResult(PermissionStatus.DENIED)
            }
            PermissionType.MICROPHONE -> {
                val res = runCatching {
                    AVCaptureDevice.requestAccessForMediaType(AVMediaTypeAudio) { granted ->
                        val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                        dispatch_async(dispatch_get_main_queue()) { onResult(status) }
                    }
                }
                if (res.isFailure) onResult(PermissionStatus.DENIED)
            }
            PermissionType.READ_MEDIA_STORAGE,
            PermissionType.WRITE_MEDIA_STORAGE -> {
                val res = runCatching {
                    PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { raw ->
                        val mapped = mapPhotoStatus(raw)
                        dispatch_async(dispatch_get_main_queue()) { onResult(mapped) }
                    }
                }
                if (res.isFailure) onResult(PermissionStatus.DENIED)
            }
            PermissionType.LOCATION -> {
                val res = runCatching {
                    CLLocationManager().requestWhenInUseAuthorization()
                }
                if (res.isFailure) {
                    onResult(PermissionStatus.DENIED)
                } else {
                    onResult(checkPermission(PermissionType.LOCATION))
                }
            }
            PermissionType.NOTIFICATIONS -> {
                val res = runCatching {
                    val options = UNAuthorizationOptionAlert or UNAuthorizationOptionBadge or UNAuthorizationOptionSound
                    UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(options) { granted, _ ->
                        val status = if (granted) PermissionStatus.GRANTED else PermissionStatus.DENIED
                        lastNotificationStatus = status
                        dispatch_async(dispatch_get_main_queue()) { onResult(status) }
                    }
                }
                if (res.isFailure) onResult(PermissionStatus.DENIED)
            }
        }
    }

    private fun mapAvStatus(status: Long): PermissionStatus = when (status) {
        AVAuthorizationStatusAuthorized -> PermissionStatus.GRANTED
        AVAuthorizationStatusDenied -> PermissionStatus.PERMANENTLY_DENIED
        AVAuthorizationStatusRestricted -> PermissionStatus.DENIED
        AVAuthorizationStatusNotDetermined -> PermissionStatus.PROMPT_REQUIRED
        else -> PermissionStatus.PROMPT_REQUIRED
    }

    private fun mapPhotoStatus(status: Long): PermissionStatus = when (status) {
        PHAuthorizationStatusAuthorized, PHAuthorizationStatusLimited -> PermissionStatus.GRANTED
        PHAuthorizationStatusDenied -> PermissionStatus.PERMANENTLY_DENIED
        PHAuthorizationStatusRestricted -> PermissionStatus.DENIED
        PHAuthorizationStatusNotDetermined -> PermissionStatus.PROMPT_REQUIRED
        else -> PermissionStatus.PROMPT_REQUIRED
    }

    private fun mapLocationStatus(status: Int): PermissionStatus = when (status) {
        kCLAuthorizationStatusAuthorizedAlways, kCLAuthorizationStatusAuthorizedWhenInUse -> PermissionStatus.GRANTED
        kCLAuthorizationStatusDenied -> PermissionStatus.PERMANENTLY_DENIED
        kCLAuthorizationStatusRestricted -> PermissionStatus.DENIED
        kCLAuthorizationStatusNotDetermined -> PermissionStatus.PROMPT_REQUIRED
        else -> PermissionStatus.PROMPT_REQUIRED
    }

    override fun dispose() {
        isDisposed = true
    }
}
