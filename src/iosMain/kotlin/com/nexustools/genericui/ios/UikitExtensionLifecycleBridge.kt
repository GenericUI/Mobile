package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleReentrantLock
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationDidEnterBackgroundNotification
import platform.UIKit.UIApplicationWillEnterForegroundNotification
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.UIKit.UIBackgroundTaskIdentifier
import platform.UIKit.UIBackgroundTaskInvalid
import platform.darwin.NSObjectProtocol

/**
 * Extension-agnostic lifecycle & background task bridge for iOS/iPadOS (`UIApplication` / `UIScene`),
 * enabling extensions to subscribe to foreground/background transitions and begin/end OS background
 * tasks cleanly without coupling `:mobile` to extension code.
 */
object UikitExtensionLifecycleBridge {
    private val lock = AppleReentrantLock()
    private val observers = mutableListOf<UikitExtensionLifecycleObserver>()
    private val notificationTokens = mutableListOf<NSObjectProtocol>()
    private var notificationsInstalled: Boolean = false

    fun ensureNotificationObserversInstalled() {
        lock.withLock {
            if (notificationsInstalled) return
            notificationsInstalled = true
            val center = NSNotificationCenter.defaultCenter
            notificationTokens += center.addObserverForName(
                name = UIApplicationWillEnterForegroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ -> dispatchSceneWillEnterForeground() }
            notificationTokens += center.addObserverForName(
                name = UIApplicationDidBecomeActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ -> dispatchSceneDidBecomeActive() }
            notificationTokens += center.addObserverForName(
                name = UIApplicationWillResignActiveNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ -> dispatchSceneWillResignActive() }
            notificationTokens += center.addObserverForName(
                name = UIApplicationDidEnterBackgroundNotification,
                `object` = null,
                queue = NSOperationQueue.mainQueue
            ) { _ -> dispatchSceneDidEnterBackground() }
        }
    }

    fun registerObserver(observer: UikitExtensionLifecycleObserver) {
        lock.withLock {
            if (observer !in observers) {
                observers.add(observer)
            }
        }
    }

    fun unregisterObserver(observer: UikitExtensionLifecycleObserver) {
        lock.withLock {
            observers.remove(observer)
        }
    }

    fun registeredObservers(): List<UikitExtensionLifecycleObserver> =
        lock.withLock { observers.toList() }

    fun dispatchApplicationLaunched() {
        for (observer in registeredObservers()) {
            observer.onApplicationLaunched()
        }
    }

    fun dispatchSceneWillEnterForeground() {
        for (observer in registeredObservers()) {
            observer.onSceneWillEnterForeground()
        }
    }

    fun dispatchSceneDidBecomeActive() {
        for (observer in registeredObservers()) {
            observer.onSceneDidBecomeActive()
        }
    }

    fun dispatchSceneWillResignActive() {
        for (observer in registeredObservers()) {
            observer.onSceneWillResignActive()
        }
    }

    fun dispatchSceneDidEnterBackground() {
        for (observer in registeredObservers()) {
            observer.onSceneDidEnterBackground()
        }
    }

    fun dispatchLowMemoryWarning() {
        for (observer in registeredObservers()) {
            observer.onLowMemoryWarning()
        }
    }

    fun beginBackgroundTask(
        name: String,
        onExpiration: (() -> Unit)? = null
    ): UIBackgroundTaskIdentifier {
        return UIApplication.sharedApplication.beginBackgroundTaskWithName(
            taskName = name,
            expirationHandler = {
                onExpiration?.invoke()
            }
        )
    }

    fun endBackgroundTask(taskId: UIBackgroundTaskIdentifier) {
        if (taskId != UIBackgroundTaskInvalid) {
            UIApplication.sharedApplication.endBackgroundTask(taskId)
        }
    }
}
