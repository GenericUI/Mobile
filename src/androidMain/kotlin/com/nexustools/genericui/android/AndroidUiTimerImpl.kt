package com.nexustools.genericui.android

import android.os.Handler
import android.os.Looper
import com.nexustools.genericui.nativeimpl.system.DefaultUiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl

/**
 * Android (`:mobile:android`) implementation of [UiTimerNativeImpl] backed by
 * `android.os.Handler(Looper.getMainLooper())`, with automatic fallback to
 * [DefaultUiTimerNativeImpl] when `Looper.getMainLooper()` is `null` in unit tests.
 */
class AndroidUiTimerImpl : DefaultUiTimerNativeImpl() {

    private val mainHandler: Handler? = runCatching {
        val looper = Looper.getMainLooper()
        if (looper != null) Handler(looper) else null
    }.getOrNull()

    private val activeRunnables = linkedMapOf<Int, Runnable>()

    override fun setTimeout(delayMs: Int, callback: () -> Unit): Int {
        if (isDisposed) return -1
        var timerId = -1
        timerId = super.setTimeout(delayMs) {
            activeRunnables.remove(timerId)?.let { mainHandler?.removeCallbacks(it) }
            callback()
        }
        val handler = mainHandler
        if (timerId > 0 && handler != null) {
            val normalizedDelay = delayMs.coerceAtLeast(0).toLong()
            val runnable = Runnable {
                activeRunnables.remove(timerId)
                if (isTimerActive(timerId)) {
                    super.clearTimer(timerId)
                    callback()
                }
            }
            activeRunnables[timerId] = runnable
            runCatching { handler.postDelayed(runnable, normalizedDelay) }
        }
        return timerId
    }

    override fun setInterval(intervalMs: Int, callback: () -> Unit): Int {
        if (isDisposed) return -1
        val timerId = super.setInterval(intervalMs, callback)
        val handler = mainHandler
        if (timerId > 0 && handler != null) {
            val normalizedInterval = intervalMs.coerceAtLeast(1).toLong()
            val runnable = object : Runnable {
                override fun run() {
                    if (!isDisposed && isTimerActive(timerId)) {
                        callback()
                        if (!isDisposed && isTimerActive(timerId)) {
                            runCatching { handler.postDelayed(this, normalizedInterval) }
                        }
                    }
                }
            }
            activeRunnables[timerId] = runnable
            runCatching { handler.postDelayed(runnable, normalizedInterval) }
        }
        return timerId
    }

    override fun clearTimer(timerId: Int) {
        activeRunnables.remove(timerId)?.let { runnable ->
            runCatching { mainHandler?.removeCallbacks(runnable) }
        }
        super.clearTimer(timerId)
    }

    override fun clearAllTimers() {
        val snapshot = activeRunnables.values.toList()
        activeRunnables.clear()
        val handler = mainHandler
        if (handler != null) {
            for (runnable in snapshot) {
                runCatching { handler.removeCallbacks(runnable) }
            }
        }
        super.clearAllTimers()
    }
}
