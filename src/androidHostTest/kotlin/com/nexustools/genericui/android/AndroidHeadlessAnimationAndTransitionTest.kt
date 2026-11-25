package com.nexustools.genericui.android

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidHeadlessAnimationAndTransitionTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        Animator.cancelAllAnimations()
        Application.current?.dispose()
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        Application.current?.dispose()
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
        GenericUI.activeFactory = savedActiveFactory
    }

    @Test
    fun headlessAndroidFactoryProvidesAndroidAnimationNativeImplWithSafeVirtualClockFallback() {
        val factory = AndroidNativeFactory()
        val scheduler = factory.createAnimationScheduler()
        assertTrue(scheduler is AndroidAnimationNativeImpl)
        assertEquals(60.0, scheduler.getRefreshRateHz())
        assertFalse(scheduler.isChoreographerFrameScheduled)

        GenericUI.initialize(factory)
        val values = mutableListOf<Float>()
        var completed = false
        var wasCancelled: Boolean? = null

        val handle = Animator.animateFloat(
            from = 0f,
            to = 200f,
            durationMs = 200,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { v -> values.add(v) },
            onComplete = { cancelled ->
                completed = true
                wasCancelled = cancelled
            }
        )
        assertTrue(handle.isRunning)

        Animator.advanceTimeBy(100.0)
        assertTrue(values.isNotEmpty())
        assertTrue(abs(values.last() - 100f) < 0.05f, "Expected ~100f at midpoint, got ${values.last()}")
        assertFalse(completed)

        Animator.flushAll()
        assertEquals(200f, values.last())
        assertTrue(completed)
        assertEquals(false, wasCancelled)
        assertFalse(handle.isRunning)
        scheduler.dispose()
    }

    @Test
    fun headlessAndroidBaseNativeImplReturnsFalseWithoutAndroidView() {
        val impl = AndroidContainerImpl()
        val handled = impl.runNativeTransition(
            property = "opacity",
            fromValue = "1.0",
            toValue = "0.0",
            spec = CssTransitionSpec("opacity", 150f, CssTimingFunction.LINEAR),
            onComplete = {}
        )
        assertFalse(handled, "Headless AndroidBaseNativeImpl without an attached androidView must return false")
        impl.dispose()

        val comp = Container()
        comp.transitionController.updateTransitionCss("opacity 200ms linear")
        comp.opacity = 1f
        comp.opacity = 0f
        assertTrue(comp.transitionController.hasActiveSoftwareTransition("opacity"))

        Animator.advanceTimeBy(100.0)
        assertTrue(abs(comp.transitionController.currentFrameState.opacity - 0.5f) < 0.05f)
        Animator.flushAll()
        assertEquals(0f, comp.transitionController.currentFrameState.opacity)
        comp.dispose()
    }
}
