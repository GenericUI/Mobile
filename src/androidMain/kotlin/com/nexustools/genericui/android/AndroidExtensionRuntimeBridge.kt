package com.nexustools.genericui.android

import android.app.Activity
import android.app.Application
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Runtime extension bridge for Android allowing first-party and third-party GenericUI extensions
 * to observe `Application`/`Activity` lifecycle callbacks, ensure notification channels, promote
 * `android.app.Service` instances to foreground services via [ServiceCompat.startForeground], update
 * ongoing notifications via [NotificationManagerCompat], and safely start/bind services across API levels
 * without coupling core `:mobile` to extension classes.
 */
object AndroidExtensionRuntimeBridge {

    /**
     * Action button descriptor for ongoing foreground service notifications, triggering a service
     * [PendingIntent] (`PendingIntent.getService` with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`).
     */
    data class NotificationActionSpec(
        val title: CharSequence,
        val serviceIntent: Intent,
        val requestCode: Int = 0,
        val iconResId: Int = 0,
        val flags: Int = defaultPendingIntentFlags()
    )

    private val lifecycleObservers = CopyOnWriteArrayList<AndroidActivityLifecycleObserver>()

    @Volatile
    var applicationInstance: Application? = null
        private set

    fun registerActivityLifecycleObserver(observer: AndroidActivityLifecycleObserver) {
        if (observer !in lifecycleObservers) {
            lifecycleObservers.add(observer)
        }
    }

    fun unregisterActivityLifecycleObserver(observer: AndroidActivityLifecycleObserver) {
        lifecycleObservers.remove(observer)
    }

    fun registeredLifecycleObservers(): List<AndroidActivityLifecycleObserver> = lifecycleObservers.toList()

    fun resolveApplicationContext(): Context? =
        AndroidActivityRegistry.resolveActiveActivity()?.applicationContext
            ?: AndroidViewHandle.activeContext?.applicationContext
            ?: AndroidPreferenceStoreImpl.applicationContext?.applicationContext
            ?: applicationInstance?.applicationContext

    fun notifyApplicationCreate(application: Application) {
        applicationInstance = application
        for (observer in lifecycleObservers) {
            runCatching { observer.onApplicationCreate(application) }
        }
    }

    fun notifyActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityCreated(activity, savedInstanceState) }
        }
    }

    fun notifyActivityStarted(activity: Activity) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityStarted(activity) }
        }
    }

    fun notifyActivityResumed(activity: Activity) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityResumed(activity) }
        }
    }

    fun notifyActivityPaused(activity: Activity) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityPaused(activity) }
        }
    }

    fun notifyActivityStopped(activity: Activity) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityStopped(activity) }
        }
    }

    fun notifyActivityDestroyed(activity: Activity) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityDestroyed(activity) }
        }
    }

    fun notifyActivityNewIntent(activity: Activity, intent: Intent) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityNewIntent(activity, intent) }
        }
    }

    fun notifyActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onActivityResult(activity, requestCode, resultCode, data) }
        }
    }

    fun notifyRequestPermissionsResult(
        activity: Activity,
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        for (observer in lifecycleObservers) {
            runCatching { observer.onRequestPermissionsResult(activity, requestCode, permissions, grantResults) }
        }
    }

    /**
     * Resolves an [Intent] targeting the host `@MainAppFragment`'s Activity (falling back to the
     * package's launcher intent or [GenericUiFragmentActivity]).
     */
    fun resolveMainActivityLaunchIntent(context: Context): Intent {
        val mainActivityCls = AndroidActivityRegistry.mainActivityClass
        val baseIntent = if (mainActivityCls != null) {
            Intent(context, mainActivityCls)
        } else {
            context.packageManager?.getLaunchIntentForPackage(context.packageName)
                ?: Intent(context, GenericUiFragmentActivity::class.java)
        }
        baseIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return baseIntent
    }

    /**
     * Builds a tap [PendingIntent] opening `@MainAppFragment`'s Activity cleanly with
     * `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.
     */
    fun createMainActivityContentPendingIntent(
        context: Context,
        requestCode: Int = 0,
        intentModifier: ((Intent) -> Unit)? = null
    ): PendingIntent {
        val launchIntent = resolveMainActivityLaunchIntent(context)
        intentModifier?.invoke(launchIntent)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launchIntent,
            defaultPendingIntentFlags()
        )
    }

    /**
     * Builds a [PendingIntent.getService] action intent with `FLAG_IMMUTABLE | FLAG_UPDATE_CURRENT`.
     */
    fun createServiceActionPendingIntent(
        context: Context,
        serviceIntent: Intent,
        requestCode: Int = 0,
        flags: Int = defaultPendingIntentFlags()
    ): PendingIntent = PendingIntent.getService(context, requestCode, serviceIntent, flags)

    /**
     * Creates or updates a [NotificationChannel] on API 26+ (no-op on older platforms), defaulting
     * to low-importance non-intrusive foreground service notifications.
     */
    @JvmOverloads
    fun ensureNotificationChannel(
        context: Context,
        channelId: String,
        channelName: CharSequence,
        description: String = "",
        importance: Int = NotificationManagerCompat.IMPORTANCE_LOW,
        showBadge: Boolean = false,
        enableVibration: Boolean = false,
        soundUri: Uri? = null,
        audioAttributes: AudioAttributes? = null
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
        val channel = NotificationChannel(channelId, channelName, importance).apply {
            if (description.isNotBlank()) {
                this.description = description
            }
            setShowBadge(showBadge)
            enableVibration(enableVibration)
            setSound(soundUri, audioAttributes)
        }
        nm.createNotificationChannel(channel)
    }

    /**
     * Constructs an ongoing foreground service [Notification] with dynamic title/text/subText,
     * customizable service action buttons, and a tap [PendingIntent] targeting `@MainAppFragment`'s Activity.
     */
    @JvmOverloads
    fun buildForegroundNotification(
        context: Context,
        channelId: String,
        title: CharSequence,
        text: CharSequence,
        subText: CharSequence? = null,
        smallIconResId: Int = 0,
        ongoing: Boolean = true,
        onlyAlertOnce: Boolean = true,
        silent: Boolean = true,
        priority: Int = NotificationCompat.PRIORITY_LOW,
        category: String = NotificationCompat.CATEGORY_SERVICE,
        contentIntent: PendingIntent? = createMainActivityContentPendingIntent(context),
        actions: List<NotificationActionSpec> = emptyList(),
        autoCancel: Boolean = false
    ): Notification {
        val resolvedIcon = if (smallIconResId != 0) {
            smallIconResId
        } else {
            resolveSmallIconResource(context)
        }
        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(resolvedIcon)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(onlyAlertOnce)
            .setSilent(silent)
            .setPriority(priority)
            .setCategory(category)
            .setAutoCancel(autoCancel)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (!subText.isNullOrBlank()) {
            builder.setSubText(subText)
        }
        if (contentIntent != null) {
            builder.setContentIntent(contentIntent)
        }
        for (action in actions) {
            val actionPending = PendingIntent.getService(
                context,
                action.requestCode,
                action.serviceIntent,
                action.flags
            )
            builder.addAction(
                NotificationCompat.Action.Builder(action.iconResId, action.title, actionPending).build()
            )
        }
        return builder.build()
    }

    /**
     * Promotes [service] into the foreground safely via [ServiceCompat.startForeground].
     */
    @JvmOverloads
    fun promoteToForegroundService(
        service: Service,
        notificationId: Int,
        notification: Notification,
        foregroundServiceType: Int = 0
    ): Boolean = runCatching {
        ServiceCompat.startForeground(service, notificationId, notification, foregroundServiceType)
        true
    }.getOrDefault(false)

    /**
     * Posts or updates an ongoing notification via [NotificationManagerCompat] (with [NotificationManager]
     * fallback), safely handling runtime permission rejections on API 33+.
     */
    fun notifyOngoing(
        context: Context,
        notificationId: Int,
        notification: Notification
    ): Boolean = try {
        NotificationManagerCompat.from(context).notify(notificationId, notification)
        true
    } catch (_: SecurityException) {
        false
    } catch (_: Throwable) {
        runCatching {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
            nm.notify(notificationId, notification)
            true
        }.getOrDefault(false)
    }

    fun cancelNotification(context: Context, notificationId: Int) {
        runCatching {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }

    fun startServiceCompat(context: Context, intent: Intent): ComponentName? =
        runCatching { context.startService(intent) }.getOrNull()

    fun startForegroundServiceCompat(context: Context, intent: Intent): ComponentName? = runCatching {
        ContextCompat.startForegroundService(context, intent)
        intent.component
    }.getOrNull()

    @JvmOverloads
    fun bindServiceCompat(
        context: Context,
        intent: Intent,
        connection: ServiceConnection,
        flags: Int = Context.BIND_AUTO_CREATE
    ): Boolean = runCatching {
        context.bindService(intent, connection, flags)
    }.getOrDefault(false)

    fun unbindServiceSafe(context: Context, connection: ServiceConnection) {
        runCatching { context.unbindService(connection) }
    }

    fun resolveSmallIconResource(context: Context, iconHint: String? = null): Int {
        val pkg = context.packageName
        if (!iconHint.isNullOrBlank()) {
            val clean = iconHint.trim()
                .removePrefix("res://")
                .removePrefix("@")
                .substringBeforeLast('.')
                .replace('/', '_')
                .replace('-', '_')
                .lowercase()
            val stem = clean.substringAfterLast('_')
            for (candidate in listOf(clean, "app_drawable_$stem", "gui_drawable_$stem", stem)) {
                if (candidate.isEmpty()) continue
                val id = runCatching { context.resources.getIdentifier(candidate, "drawable", pkg) }.getOrDefault(0)
                if (id != 0) return id
            }
        }
        val candidates = listOf("ic_notification", "app_drawable_icon", "gui_drawable_icon", "icon")
        for (name in candidates) {
            val id = runCatching { context.resources.getIdentifier(name, "drawable", pkg) }.getOrDefault(0)
            if (id != 0) return id
        }
        val pkgIcon = context.applicationInfo?.icon ?: 0
        if (pkgIcon != 0) return pkgIcon
        return android.R.drawable.ic_dialog_info
    }

    fun defaultPendingIntentFlags(): Int =
        PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
}
