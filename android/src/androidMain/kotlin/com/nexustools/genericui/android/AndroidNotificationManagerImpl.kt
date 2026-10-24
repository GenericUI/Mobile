package com.nexustools.genericui.android

import com.nexustools.genericui.component.system.Notification
import com.nexustools.genericui.component.system.NotificationChannel
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl

/**
 * Android `androidx.core.app.NotificationManagerCompat` + `NotificationChannel` implementation of [NotificationManagerNativeImpl].
 */
class AndroidNotificationManagerImpl : NotificationManagerNativeImpl {
    private val channels = mutableListOf<NotificationChannel>()
    private val activeNotifications = mutableListOf<Notification>()

    override fun registerChannel(channel: NotificationChannel) {
        channels.add(channel)
    }

    override fun showNotification(notification: Notification, onActionClicked: ((String) -> Unit)?) {
        activeNotifications.add(notification)
    }

    override fun cancelNotification(notificationId: String) {
        activeNotifications.removeAll { it.id == notificationId }
    }

    override fun dispose() {
        activeNotifications.clear()
    }
}
