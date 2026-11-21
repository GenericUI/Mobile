package com.nexustools.genericui.android

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidExtensionRuntimeBridgeTest {

    @AfterTest
    fun tearDown() {
        AndroidExtensionRuntimeBridge.registeredLifecycleObservers().forEach {
            AndroidExtensionRuntimeBridge.unregisterActivityLifecycleObserver(it)
        }
    }

    @Test
    fun `bridge creates notification channel, builds ongoing fgs notification with actions, and notifies safely`() {
        val context: Context = RuntimeEnvironment.getApplication()
        AndroidActivityRegistry.mainActivityClass = GenericUiFragmentActivity::class.java

        AndroidExtensionRuntimeBridge.ensureNotificationChannel(
            context = context,
            channelId = "worker_bg_channel",
            channelName = "Background Worker",
            description = "Persistent worker service",
            importance = NotificationManagerCompat.IMPORTANCE_LOW
        )
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = nm.getNotificationChannel("worker_bg_channel")
        assertNotNull(channel, "Expected notification channel 'worker_bg_channel' to be created on API 35")
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)

        val stopActionIntent = Intent("com.example.ACTION_STOP").setPackage(context.packageName)
        val notification = AndroidExtensionRuntimeBridge.buildForegroundNotification(
            context = context,
            channelId = "worker_bg_channel",
            title = "Worker Running",
            text = "Listening for background tasks",
            subText = "Active",
            actions = listOf(
                AndroidExtensionRuntimeBridge.NotificationActionSpec(
                    title = "Stop",
                    serviceIntent = stopActionIntent,
                    requestCode = 42
                )
            )
        )
        assertEquals("Worker Running", notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE)?.toString())
        assertEquals("Listening for background tasks", notification.extras.getCharSequence(NotificationCompat.EXTRA_TEXT)?.toString())
        assertEquals(1, notification.actions?.size ?: 0)
        assertEquals("Stop", notification.actions?.get(0)?.title?.toString())
        assertNotNull(notification.contentIntent, "Expected contentIntent targeting @MainAppFragment Activity")

        val posted = AndroidExtensionRuntimeBridge.notifyOngoing(context, 7001, notification)
        assertTrue(posted)
        val shadowNm = shadowOf(nm)
        assertNotNull(shadowNm.getNotification(7001), "ShadowNotificationManager must retain posted ongoing notification")
    }

    @Test
    fun `bridge dispatches activity lifecycle observers cleanly`() {
        val events = mutableListOf<String>()
        val observer = object : AndroidActivityLifecycleObserver {
            override fun onActivityNewIntent(activity: android.app.Activity, intent: Intent) {
                events.add("newIntent:${intent.action}")
            }
        }
        AndroidExtensionRuntimeBridge.registerActivityLifecycleObserver(observer)
        assertEquals(1, AndroidExtensionRuntimeBridge.registeredLifecycleObservers().size)

        val context: Context = RuntimeEnvironment.getApplication()
        val fakeService = object : Service() {
            override fun onBind(intent: Intent?) = null
        }
        assertNotNull(fakeService)
        assertNotNull(AndroidExtensionRuntimeBridge.createServiceActionPendingIntent(context, Intent("ACTION_PING")))
    }
}
