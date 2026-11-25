package com.nexustools.genericui.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.Display
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import com.nexustools.genericui.animation.AnimationSpec
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.system.DefaultAnimationNativeImpl
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android (`:mobile:androidMain`) [com.nexustools.genericui.nativeimpl.system.AnimationNativeImpl]
 * backed by `android.animation.ValueAnimator` (with `PathInterpolator` / `LinearInterpolator`) for
 * timed animations and `android.view.Choreographer` (`frameTimeNanos / 1_000_000.0`) for VBlank
 * scheduling.
 *
 * Gracefully falls back to [DefaultAnimationNativeImpl]'s VBlank/virtual clock when running in
 * headless JVM host tests without a main `Looper` or when system animators are disabled.
 */
class AndroidAnimationNativeImpl(
    private val contextProvider: () -> Context? = {
        AndroidViewHandle.activeContext ?: AndroidPreferenceStoreImpl.applicationContext
    }
) : DefaultAnimationNativeImpl() {

    private val vblankLock = Any()
    private var wantsVBlankActive: Boolean = false
    private var frameScheduled: Boolean = false
    private var lastFrameWallNs: Long = 0L
    private var rapidSyntheticFrameCount: Int = 0

    internal val isChoreographerFrameScheduled: Boolean
        get() = synchronized(vblankLock) { frameScheduled }

    private val frameCallback = Choreographer.FrameCallback { frameTimeNanos ->
        val nowWallNs = System.nanoTime()
        var deferNextFrame = false
        val shouldTick = synchronized(vblankLock) {
            frameScheduled = false
            val wallDelta = nowWallNs - lastFrameWallNs
            if (lastFrameWallNs != 0L && wallDelta in 0L until 250_000L) {
                rapidSyntheticFrameCount++
                if (rapidSyntheticFrameCount >= 64) {
                    rapidSyntheticFrameCount = 0
                    deferNextFrame = true
                }
            } else {
                rapidSyntheticFrameCount = 0
            }
            lastFrameWallNs = nowWallNs
            wantsVBlankActive && !isDisposed
        }
        if (!shouldTick) return@FrameCallback
        onNativeVBlankTick(frameTimeNanos / 1_000_000.0)
        scheduleNextFrameIfNeeded(delayedFallback = deferNextFrame)
    }

    override fun getRefreshRateHz(): Double = runCatching {
        val ctx = contextProvider()
        if (ctx != null) {
            val dm = ctx.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            val dmRate = dm?.getDisplay(Display.DEFAULT_DISPLAY)?.refreshRate?.toDouble()
            if (dmRate != null && dmRate > 0.0) return@runCatching dmRate
            if (Build.VERSION.SDK_INT >= 30) {
                val dispRate = ctx.display?.refreshRate?.toDouble()
                if (dispRate != null && dispRate > 0.0) return@runCatching dispRate
            }
            @Suppress("DEPRECATION")
            val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val wmRate = wm?.defaultDisplay?.refreshRate?.toDouble()
            if (wmRate != null && wmRate > 0.0) return@runCatching wmRate
        }
        60.0
    }.getOrDefault(60.0)

    override fun startNativeTimedAnimation(
        id: Int,
        spec: AnimationSpec,
        target: NativeImpl<*>?,
        onProgress: (easedProgress: Float) -> Unit,
        onFinished: (cancelled: Boolean) -> Unit
    ): (() -> Unit)? {
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return null
        val mainThread = runCatching { mainLooper.thread }.getOrNull() ?: return null
        if (!mainThread.isAlive) return null
        if (!areNativeAnimatorsEnabled()) return null

        val cancelled = AtomicBoolean(false)
        val finished = AtomicBoolean(false)
        val animator = runCatching {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = spec.durationMs.toLong().coerceAtLeast(0L)
                startDelay = spec.delayMs.toLong().coerceAtLeast(0L)
                interpolator = if (spec.timingFunction.isLinear) {
                    LinearInterpolator()
                } else {
                    PathInterpolator(
                        spec.timingFunction.x1.coerceIn(0f, 1f),
                        spec.timingFunction.y1,
                        spec.timingFunction.x2.coerceIn(0f, 1f),
                        spec.timingFunction.y2
                    )
                }
                addUpdateListener { anim ->
                    if (!cancelled.get()) {
                        val value = (anim.animatedValue as? Float) ?: 0f
                        onProgress(value)
                    }
                }
                addListener(object : AnimatorListenerAdapter() {
                    private var wasCancelled = false

                    override fun onAnimationCancel(animation: Animator) {
                        wasCancelled = true
                    }

                    override fun onAnimationEnd(animation: Animator) {
                        if (finished.compareAndSet(false, true)) {
                            onFinished(wasCancelled || cancelled.get())
                        }
                    }
                })
            }
        }.getOrNull() ?: return null

        val startOnMain: () -> Unit = {
            if (!cancelled.get() && !isDisposed) {
                runCatching { animator.start() }.onFailure {
                    if (finished.compareAndSet(false, true)) {
                        onFinished(true)
                    }
                }
            } else if (finished.compareAndSet(false, true)) {
                onFinished(true)
            }
        }

        if (Looper.myLooper() === mainLooper) {
            startOnMain()
        } else {
            val handler = runCatching { Handler(mainLooper) }.getOrNull()
            if (handler == null || !handler.post(startOnMain)) {
                return null
            }
        }

        return {
            if (cancelled.compareAndSet(false, true)) {
                val cancelAction: () -> Unit = {
                    runCatching { animator.cancel() }
                    if (finished.compareAndSet(false, true)) {
                        onFinished(true)
                    }
                }
                if (Looper.myLooper() === mainLooper) {
                    cancelAction()
                } else {
                    val handler = runCatching { Handler(mainLooper) }.getOrNull()
                    if (handler == null || !handler.post(cancelAction)) {
                        if (finished.compareAndSet(false, true)) {
                            onFinished(true)
                        }
                    }
                }
            }
        }
    }

    override fun onActiveVBlankWorkChanged(hasActiveWork: Boolean) {
        if (hasActiveWork && !isDisposed) {
            synchronized(vblankLock) {
                wantsVBlankActive = true
            }
            scheduleNextFrameIfNeeded()
        } else {
            val wasScheduled = synchronized(vblankLock) {
                wantsVBlankActive = false
                val prev = frameScheduled
                frameScheduled = false
                prev
            }
            if (wasScheduled) {
                removeChoreographerCallback()
            }
        }
    }

    private fun scheduleNextFrameIfNeeded(delayedFallback: Boolean = false) {
        val shouldSchedule = synchronized(vblankLock) {
            wantsVBlankActive && !isDisposed && !frameScheduled
        }
        if (!shouldSchedule) return
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return
        val mainThread = runCatching { mainLooper.thread }.getOrNull() ?: return
        if (!mainThread.isAlive) return

        val postCallback: () -> Unit = {
            val stillNeeded = synchronized(vblankLock) {
                if (wantsVBlankActive && !isDisposed && !frameScheduled) {
                    frameScheduled = true
                    true
                } else {
                    false
                }
            }
            if (stillNeeded) {
                val posted = runCatching {
                    Choreographer.getInstance().postFrameCallback(frameCallback)
                    true
                }.getOrDefault(false)
                if (!posted) {
                    synchronized(vblankLock) {
                        frameScheduled = false
                    }
                }
            }
        }

        if (delayedFallback) {
            val delayMs = (1000.0 / getRefreshRateHz().coerceAtLeast(1.0)).toLong().coerceAtLeast(1L)
            runCatching { Handler(mainLooper).postDelayed(postCallback, delayMs) }
        } else if (Looper.myLooper() === mainLooper) {
            postCallback()
        } else {
            runCatching { Handler(mainLooper).post(postCallback) }
        }
    }

    private fun removeChoreographerCallback() {
        val mainLooper = runCatching { Looper.getMainLooper() }.getOrNull() ?: return
        val removeAction: () -> Unit = {
            runCatching { Choreographer.getInstance().removeFrameCallback(frameCallback) }
        }
        if (Looper.myLooper() === mainLooper) {
            removeAction()
        } else {
            runCatching { Handler(mainLooper).post(removeAction) }
        }
    }

    private fun areNativeAnimatorsEnabled(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 26) {
            ValueAnimator.areAnimatorsEnabled()
        } else {
            true
        }
    }.getOrDefault(false)

    override fun dispose() {
        val wasScheduled = synchronized(vblankLock) {
            wantsVBlankActive = false
            val prev = frameScheduled
            frameScheduled = false
            prev
        }
        super.dispose()
        if (wasScheduled) {
            removeChoreographerCallback()
        }
    }
}
