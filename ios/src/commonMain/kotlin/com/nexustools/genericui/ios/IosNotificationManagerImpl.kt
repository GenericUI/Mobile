package com.nexustools.genericui.ios

import com.nexustools.genericui.component.system.Notification
import com.nexustools.genericui.component.system.NotificationChannel
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl

/**
 * iOS `UNUserNotificationCenter` implementation of [NotificationManagerNativeImpl].
 */
class IosNotificationManagerImpl : NotificationManagerNativeImpl {
    private val channels = mutableListOf<NotificationChannel>()
    private val notifications = mutableListOf<Notification>()

    override fun registerChannel(channel: NotificationChannel) { channels.add(channel) }
    override fun showNotification(notification: Notification, onActionClicked: ((String) -> Unit)?) { notifications.add(notification) }
    override fun cancelNotification(notificationId: String) {}
    override fun dispose() { notifications.clear() }
}
