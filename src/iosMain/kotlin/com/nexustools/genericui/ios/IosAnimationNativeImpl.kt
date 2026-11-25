@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.system.DefaultAnimationNativeImpl
import com.nexustools.genericui.threading.PlatformReentrantLock
import com.nexustools.genericui.threading.withLock
import kotlinx.cinterop.ObjCAction
import platform.Foundation.NSDate
import platform.Foundation.NSRunLoop
import platform.Foundation.NSRunLoopCommonModes
import platform.Foundation.NSThread
import platform.Foundation.NSTimer
import platform.Foundation.dateWithTimeIntervalSinceNow
import platform.Foundation.distantFuture
import platform.QuartzCore.CACurrentMediaTime
import platform.QuartzCore.CADisplayLink
import platform.QuartzCore.CAFrameRateRangeMake
import platform.UIKit.UIScreen
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.objc.sel_registerName

/**
 * iOS & iPadOS (`:mobile:iosMain`) [AnimationNativeImpl] driven by QuartzCore's [CADisplayLink]
 * attached to `NSRunLoop.mainRunLoop` in `NSRunLoopCommonModes` with full 120Hz ProMotion
 * (`CAFrameRateRangeMake(60f, maxFps, maxFps)` from `UIScreen.mainScreen.maximumFramesPerSecond`).
 *
 * Automatically pauses the display link at 0% CPU whenever no VBlank consumers remain active
 * and invalidates it cleanly on [dispose].
 */
class IosAnimationNativeImpl : DefaultAnimationNativeImpl() {

    private class DisplayLinkTarget(
        var onTick: ((CADisplayLink) -> Unit)? = null
    ) : NSObject() {
        @ObjCAction
        fun onDisplayLink(link: CADisplayLink?) {
            if (link != null) {
                onTick?.invoke(link)
            }
        }
    }

    private val stateLock = PlatformReentrantLock()
    private var wantsVBlankActive: Boolean = false
    private var displayLink: CADisplayLink? = null
    private var displayLinkTarget: DisplayLinkTarget? = null
    private var fallbackTimer: NSTimer? = null
    private var fallbackPaused: Boolean = true

    internal val hasDisplayLink: Boolean
        get() = stateLock.withLock { displayLink != null || fallbackTimer != null }

    internal val isDisplayLinkPaused: Boolean
        get() = stateLock.withLock {
            val link = displayLink
            if (link != null) link.isPaused() else fallbackPaused
        }

    @Suppress("USELESS_CAST")
    override fun getRefreshRateHz(): Double =
        (UIScreen.mainScreen as UIScreen?)?.maximumFramesPerSecond?.toDouble()?.takeIf { it > 0.0 } ?: 60.0

    override fun onActiveVBlankWorkChanged(hasActiveWork: Boolean) {
        stateLock.withLock {
            wantsVBlankActive = hasActiveWork && !isDisposed
        }
        runOnMainThread {
            syncDisplayLinkState()
        }
    }

    private fun syncDisplayLinkState() {
        val (shouldRun, disposed) = stateLock.withLock {
            (wantsVBlankActive && !isDisposed) to isDisposed
        }
        if (disposed) {
            teardownDisplayLink()
            return
        }
        if (shouldRun) {
            val maxFps = getRefreshRateHz().toFloat().coerceAtLeast(1f)
            val minFps = minOf(24f, maxFps)
            val existing = displayLink
            if (existing == null && fallbackTimer == null) {
                val target = DisplayLinkTarget { link ->
                    if (!isDisposed) {
                        onNativeVBlankTick(link.timestamp * 1000.0)
                    }
                }
                @Suppress("USELESS_CAST")
                val link = CADisplayLink.displayLinkWithTarget(
                    target = target,
                    selector = SEL_ON_DISPLAY_LINK
                ) as CADisplayLink?
                if (link != null) {
                    applyPreferredFrameRate(link, minFps, maxFps)
                    link.setPaused(false)
                    link.addToRunLoop(NSRunLoop.mainRunLoop, forMode = NSRunLoopCommonModes)
                    stateLock.withLock {
                        if (isDisposed || !wantsVBlankActive) {
                            link.setPaused(true)
                            if (isDisposed) {
                                link.invalidate()
                                target.onTick = null
                            } else {
                                displayLinkTarget = target
                                displayLink = link
                            }
                        } else {
                            displayLinkTarget = target
                            displayLink = link
                        }
                    }
                } else {
                    target.onTick = null
                    val intervalSec = 1.0 / maxFps.toDouble()
                    val timer = NSTimer.scheduledTimerWithTimeInterval(intervalSec, repeats = true) { _ ->
                        val pausedNow = stateLock.withLock { fallbackPaused || isDisposed }
                        if (!pausedNow) {
                            onNativeVBlankTick(CACurrentMediaTime() * 1000.0)
                        }
                    }
                    NSRunLoop.mainRunLoop.addTimer(timer, forMode = NSRunLoopCommonModes)
                    stateLock.withLock {
                        if (isDisposed) {
                            timer.invalidate()
                            fallbackPaused = true
                        } else if (!wantsVBlankActive) {
                            fallbackTimer = timer
                            fallbackPaused = true
                            timer.setFireDate(NSDate.distantFuture())
                        } else {
                            fallbackTimer = timer
                            fallbackPaused = false
                        }
                    }
                }
            } else if (existing != null) {
                applyPreferredFrameRate(existing, minFps, maxFps)
                existing.setPaused(false)
            } else {
                val timer = stateLock.withLock {
                    fallbackPaused = false
                    fallbackTimer
                }
                timer?.setFireDate(NSDate.dateWithTimeIntervalSinceNow(1.0 / maxFps.toDouble()))
            }
        } else {
            displayLink?.setPaused(true)
            val timer = stateLock.withLock {
                fallbackPaused = true
                fallbackTimer
            }
            timer?.setFireDate(NSDate.distantFuture())
        }
    }

    private fun applyPreferredFrameRate(link: CADisplayLink, minFps: Float, maxFps: Float) {
        if (link.respondsToSelector(SEL_SET_PREFERRED_FRAME_RATE_RANGE)) {
            link.preferredFrameRateRange = CAFrameRateRangeMake(minFps, maxFps, maxFps)
        } else {
            link.preferredFramesPerSecond = maxFps.toLong()
        }
    }

    private fun teardownDisplayLink() {
        val (link, target, timer) = stateLock.withLock {
            val l = displayLink
            val t = displayLinkTarget
            val tm = fallbackTimer
            displayLink = null
            displayLinkTarget = null
            fallbackTimer = null
            fallbackPaused = true
            Triple(l, t, tm)
        }
        target?.onTick = null
        link?.setPaused(true)
        link?.invalidate()
        timer?.invalidate()
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                block()
            }
        }
    }

    internal fun dispatchNativeVBlankTimestampMsForTest(timestampMs: Double): Int =
        onNativeVBlankTick(timestampMs)

    override fun dispose() {
        stateLock.withLock {
            wantsVBlankActive = false
        }
        super.dispose()
        runOnMainThread {
            teardownDisplayLink()
        }
    }

    private companion object {
        val SEL_ON_DISPLAY_LINK get() = sel_registerName("onDisplayLink:")
        val SEL_SET_PREFERRED_FRAME_RATE_RANGE get() = sel_registerName("setPreferredFrameRateRange:")
    }
}
