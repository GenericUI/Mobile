package com.nexustools.genericui.android

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
        channels.removeAll { it.id == channel.id }
        channels.add(channel)
        runCatching {
            val ctx = AndroidViewHandle.activeContext ?: return@runCatching
            ensureAndroidChannel(ctx, channel)
        }
    }

    override fun showNotification(notification: Notification, onActionClicked: ((String) -> Unit)?) {
        activeNotifications.removeAll { it.id == notification.id }
        activeNotifications.add(notification)
        runCatching {
            val ctx = AndroidViewHandle.activeContext ?: return@runCatching
            val channel = channels.firstOrNull { it.id == notification.channelId }
                ?: NotificationChannel(
                    id = notification.channelId,
                    name = notification.channelId.replaceFirstChar { it.uppercase() },
                    description = "Notifications",
                    highImportance = true
                )
            ensureAndroidChannel(ctx, channel)

            val smallIconRes = resolveSmallIconRes(ctx, notification.iconPathOrUrl)
            val launchIntent = ctx.packageManager?.getLaunchIntentForPackage(ctx.packageName)
                ?: Intent(ctx, GenericUiFragmentActivity::class.java)
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
            val contentPendingIntent = PendingIntent.getActivity(
                ctx,
                notification.id.hashCode(),
                launchIntent,
                flags
            )

            val builder = NotificationCompat.Builder(ctx, notification.channelId)
                .setSmallIcon(smallIconRes)
                .setContentTitle(notification.title)
                .setContentText(notification.body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(notification.body))
                .setPriority(
                    if (channel.highImportance) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT
                )
                .setAutoCancel(true)
                .setContentIntent(contentPendingIntent)

            NotificationManagerCompat.from(ctx).notify(notification.id.hashCode(), builder.build())
        }
    }

    override fun cancelNotification(notificationId: String) {
        activeNotifications.removeAll { it.id == notificationId }
        runCatching {
            val ctx = AndroidViewHandle.activeContext ?: return@runCatching
            NotificationManagerCompat.from(ctx).cancel(notificationId.hashCode())
        }
    }

    override fun dispose() {
        activeNotifications.clear()
        runCatching {
            val ctx = AndroidViewHandle.activeContext ?: return@runCatching
            NotificationManagerCompat.from(ctx).cancelAll()
        }
    }

    private fun ensureAndroidChannel(context: Context, channel: NotificationChannel) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val importance = if (channel.highImportance) {
                NotificationManager.IMPORTANCE_HIGH
            } else {
                NotificationManager.IMPORTANCE_DEFAULT
            }
            val androidChannel = android.app.NotificationChannel(channel.id, channel.name, importance).apply {
                if (channel.description.isNotBlank()) {
                    description = channel.description
                }
            }
            nm.createNotificationChannel(androidChannel)
        }
    }

    private fun resolveSmallIconRes(context: Context, iconHint: String?): Int {
        if (!iconHint.isNullOrBlank()) {
            val cleanName = iconHint.trim()
                .removePrefix("res://drawable/")
                .removePrefix("@drawable/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
            val id = runCatching {
                context.resources.getIdentifier(cleanName, "drawable", context.packageName)
            }.getOrDefault(0)
            if (id != 0) return id
        }
        val notifIconId = runCatching {
            context.resources.getIdentifier("ic_notification", "drawable", context.packageName)
        }.getOrDefault(0)
        if (notifIconId != 0) return notifIconId
        val appIconId = runCatching {
            context.resources.getIdentifier("icon", "drawable", context.packageName)
        }.getOrDefault(0)
        if (appIconId != 0) return appIconId
        val pkgIcon = context.applicationInfo?.icon ?: 0
        if (pkgIcon != 0) return pkgIcon
        return android.R.drawable.ic_dialog_info
    }
}
