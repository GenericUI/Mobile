package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransitionSpec
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowChoreographer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidAnimationAndTransitionTest {

    private lateinit var context: Context
    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        ShadowChoreographer.reset()
        Animator.cancelAllAnimations()
        Application.current?.dispose()
        context = RuntimeEnvironment.getApplication()
        AndroidViewHandle.activeContext = context
        AndroidPreferenceStoreImpl.applicationContext = context
    }

    private fun drainLogicAndMainLooper() {
        shadowOf(context.mainLooper).idle()
        if (GenericUiThreading.isSplitThreadingEnabled) {
            val latch = CountDownLatch(1)
            GenericUiThreading.runOnLogic { latch.countDown() }
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
            while (!latch.await(5, TimeUnit.MILLISECONDS) && System.nanoTime() < deadline) {
                shadowOf(context.mainLooper).idle()
            }
        }
        shadowOf(context.mainLooper).idle()
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        Application.current?.dispose()
        shadowOf(context.mainLooper).idle()
        ShadowChoreographer.reset()
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
        GenericUI.activeFactory = savedActiveFactory
    }

    @Test
    fun robolectricValueAnimatorAndChoreographerDriveProgressAndStopAtIdle() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val scheduler = factory.createAnimationScheduler() as AndroidAnimationNativeImpl
        assertTrue(scheduler.getRefreshRateHz() > 0.0)
        assertFalse(scheduler.isChoreographerFrameScheduled)

        val updates = mutableListOf<Float>()
        var completed = false
        var wasCancelled: Boolean? = null

        val handle = Animator.animateFloat(
            from = 0f,
            to = 100f,
            durationMs = 150,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { v -> updates.add(v) },
            onComplete = { cancelled ->
                completed = true
                wasCancelled = cancelled
            }
        )
        assertTrue(handle.isRunning)

        shadowOf(context.mainLooper).idleFor(250, TimeUnit.MILLISECONDS)
        drainLogicAndMainLooper()
        assertTrue(updates.isNotEmpty(), "Expected ValueAnimator to emit frame updates")
        assertTrue(completed, "Expected ValueAnimator completion callback")
        assertEquals(false, wasCancelled)
        assertFalse(handle.isRunning)

        // Verify Choreographer VBlank lifecycle directly on AndroidAnimationNativeImpl
        var vblankTicks = 0
        val watcherId = scheduler.watchVBlank { _, _, _ -> vblankTicks++ }
        assertTrue(scheduler.isChoreographerFrameScheduled)
        shadowOf(context.mainLooper).idleFor(60, TimeUnit.MILLISECONDS)
        assertTrue(vblankTicks > 0, "Expected Choreographer to drive VBlank watcher ticks")
        scheduler.unwatchVBlank(watcherId)
        assertFalse(scheduler.isChoreographerFrameScheduled)
        val countAtUnwatch = vblankTicks
        shadowOf(context.mainLooper).idleFor(60, TimeUnit.MILLISECONDS)
        assertEquals(countAtUnwatch, vblankTicks, "Expected zero VBlank ticks after unwatch")
        scheduler.dispose()
    }

    @Test
    fun androidBaseNativeImplAnimatesOpacityAndTransformWithoutPrematureSnapAndRejectsUnhandledProps() {
        val impl = AndroidContainerImpl()
        val view = impl.nativeWidget.androidView
        assertNotNull(view)
        impl.setOpacity(1f)
        assertEquals(1f, view.alpha)

        var opacityCompleted = false
        val handledOpacity = impl.runNativeTransition(
            property = "opacity",
            fromValue = "1.0",
            toValue = "0.0",
            spec = CssTransitionSpec("opacity", 160f, CssTimingFunction(0.4f, 0f, 0.2f, 1f), delayMs = 40f),
            onComplete = { opacityCompleted = true }
        )
        assertTrue(handledOpacity)

        // AbstractComponent invokes setOpacity(target) immediately after runNativeTransition; must not snap view.alpha
        impl.setOpacity(0f)
        assertEquals(1f, view.alpha, "Android View.alpha must not snap to 0f before delayed transition begins")

        var transformCompleted = false
        val handledTransform = impl.runNativeTransition(
            property = "transform",
            fromValue = "none",
            toValue = "translate(30px, 15px) scale(1.5, 1.5) rotate(45deg)",
            spec = CssTransitionSpec("transform", 160f, CssTimingFunction.EASE_OUT),
            onComplete = { transformCompleted = true }
        )
        assertTrue(handledTransform)

        // Unhandled properties must return false so CssTransitionController handles them
        assertFalse(
            impl.runNativeTransition(
                property = "background-color",
                fromValue = "rgb(255, 0, 0)",
                toValue = "rgb(0, 0, 255)",
                spec = CssTransitionSpec("background-color", 150f, CssTimingFunction.LINEAR),
                onComplete = {}
            )
        )

        shadowOf(context.mainLooper).idleFor(300, TimeUnit.MILLISECONDS)
        assertTrue(opacityCompleted, "Expected opacity transition completion")
        assertTrue(transformCompleted, "Expected transform transition completion")
        assertEquals(0f, view.alpha, 0.01f)
        assertEquals(30f, view.translationX, 0.5f)
        assertEquals(15f, view.translationY, 0.5f)
        assertEquals(1.5f, view.scaleX, 0.01f)
        assertEquals(45f, view.rotation, 0.5f)
        impl.dispose()
    }

    @Test
    fun cssTransitionControllerFallsBackForNonNativePropertyWithAndroidFactory() {
        ShadowChoreographer.setPaused(true)
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val window = Window(title = "Android Fallback Test")
        val comp = Container()
        window.addChild(comp)
        try {
            window.show(factory)
            drainLogicAndMainLooper()

            comp.transitionController.updateTransitionCss("background-color 200ms linear")
            comp.transitionController.onStyleChanged(
                previous = ComputedStyle(backgroundColor = RgbaColor.RED),
                next = ComputedStyle(backgroundColor = RgbaColor.BLUE)
            )
            drainLogicAndMainLooper()
            assertTrue(comp.transitionController.hasActiveSoftwareTransition("background-color"))

            Animator.advanceTimeBy(100.0)
            drainLogicAndMainLooper()
            val mid = comp.transitionController.currentFrameState.backgroundColor
            val expectedMidR = (RgbaColor.RED.r + RgbaColor.BLUE.r) / 2
            val expectedMidB = (RgbaColor.RED.b + RgbaColor.BLUE.b) / 2
            assertTrue(
                mid != null &&
                    kotlin.math.abs(mid.r - expectedMidR) <= 25 &&
                    kotlin.math.abs(mid.b - expectedMidB) <= 25,
                "Expected midpoint blended background-color near ($expectedMidR, ..., $expectedMidB), got $mid"
            )

            Animator.flushAll()
            drainLogicAndMainLooper()
            assertFalse(comp.transitionController.hasActiveSoftwareTransition("background-color"))
            assertEquals(RgbaColor.BLUE, comp.transitionController.currentFrameState.backgroundColor)
        } finally {
            Animator.cancelAllAnimations()
            runCatching { comp.dispose() }
            runCatching { window.close() }
            runCatching { window.dispose() }
            shadowOf(context.mainLooper).idle()
        }
    }
}
