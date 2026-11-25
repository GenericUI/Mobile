@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.animation.Animator
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.ipados.IpadosNativeFactory
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransitionSpec
import com.nexustools.genericui.threading.GenericUiThreading
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosAnimationAndTransitionTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun setUp() {
        savedActiveFactory = GenericUI.activeFactory
        Animator.cancelAllAnimations()
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
    }

    @AfterTest
    fun tearDown() {
        Animator.cancelAllAnimations()
        GenericUiThreading.flushAllQueues()
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
        GenericUI.activeFactory = savedActiveFactory
    }

    @Test
    fun iosAndIpadosFactoriesProvideIosAnimationNativeImplWithDisplayLinkLifecycle() {
        val iosFactory = IosNativeFactory()
        val ipadosFactory = IpadosNativeFactory()

        val ipadosScheduler = ipadosFactory.createAnimationScheduler()
        assertTrue(ipadosScheduler is IosAnimationNativeImpl)
        ipadosScheduler.dispose()

        GenericUI.initialize(iosFactory)
        val refreshHz = Animator.getRefreshRateHz()
        assertTrue(refreshHz >= 60.0, "Expected display refresh rate >= 60.0 Hz, got $refreshHz")

        val values = mutableListOf<Float>()
        var completed = false
        var wasCancelled: Boolean? = null

        val handle = Animator.animateFloat(
            from = 0f,
            to = 100f,
            durationMs = 200,
            timingFunction = CssTimingFunction.LINEAR,
            onUpdate = { v -> values.add(v) },
            onComplete = { cancelled ->
                completed = true
                wasCancelled = cancelled
            }
        )
        assertTrue(handle.isRunning)

        val scheduler = iosFactory.createAnimationScheduler() as IosAnimationNativeImpl
        assertFalse(scheduler.hasDisplayLink)
        assertTrue(scheduler.isDisplayLinkPaused)

        val standaloneValues = mutableListOf<Float>()
        var standaloneDone = false
        scheduler.startAnimation(
            spec = com.nexustools.genericui.animation.AnimationSpec(
                durationMs = 200,
                timingFunction = CssTimingFunction.LINEAR
            ),
            onUpdate = { p -> standaloneValues.add(p) },
            onComplete = { cancelled -> standaloneDone = !cancelled }
        )
        assertTrue(scheduler.hasDisplayLink)
        assertFalse(scheduler.isDisplayLinkPaused)

        scheduler.dispatchNativeVBlankTimestampMsForTest(1000.0)
        scheduler.dispatchNativeVBlankTimestampMsForTest(1083.333)
        assertTrue(standaloneValues.isNotEmpty())
        assertTrue(abs(standaloneValues.last() - 0.5f) < 0.05f, "Expected ~0.5f progress, got ${standaloneValues.last()}")
        assertFalse(standaloneDone)

        scheduler.dispatchNativeVBlankTimestampMsForTest(1250.0)
        assertTrue(standaloneDone)
        assertTrue(scheduler.isDisplayLinkPaused)

        scheduler.dispose()
        assertFalse(scheduler.hasDisplayLink)

        // Drive Animator.activeScheduler via virtual clock in unit tests
        Animator.advanceTimeBy(100.0)
        GenericUiThreading.flushAllQueues()
        assertTrue(values.isNotEmpty())
        assertTrue(abs(values.last() - 50f) < 0.05f)
        Animator.advanceTimeBy(100.0)
        GenericUiThreading.flushAllQueues()
        assertEquals(100f, values.last())
        assertTrue(completed)
        assertEquals(false, wasCancelled)
    }

    @Test
    fun iosBaseNativeImplUsesPropertyAnimatorDoesNotSnapOpacityAndRejectsUnsupportedProps() {
        val styled = IosStyledContainerImpl()
        styled.setOpacity(1f)
        var opacityCompleted = false
        val opacityHandled = styled.runNativeTransition(
            property = "opacity",
            fromValue = "1.0",
            toValue = "0.0",
            spec = CssTransitionSpec(
                property = "opacity",
                durationMs = 150f,
                timingFunction = CssTimingFunction(0.4f, 0f, 0.2f, 1f),
                delayMs = 50f
            ),
            onComplete = { opacityCompleted = true }
        )
        assertTrue(opacityHandled)

        // AbstractComponent calls setOpacity(target) right after starting native transition; must NOT snap during delay
        styled.setOpacity(0f)
        assertEquals(1f, styled.getOpacity(), "Opacity must not snap to 0f before delayed UIViewPropertyAnimator runs")

        assertTrue(
            styled.runNativeTransition(
                property = "transform",
                fromValue = "none",
                toValue = "translate(20px, 10px) scale(1.25, 1.25) rotate(45deg)",
                spec = CssTransitionSpec("transform", 120f, CssTimingFunction.EASE_OUT),
                onComplete = {}
            )
        )
        assertTrue(
            styled.runNativeTransition(
                property = "left",
                fromValue = "0px",
                toValue = "80px",
                spec = CssTransitionSpec("left", 120f, CssTimingFunction.EASE_IN_OUT),
                onComplete = {}
            )
        )
        assertTrue(
            styled.runNativeTransition(
                property = "background-color",
                fromValue = "rgb(255, 0, 0)",
                toValue = "rgba(0, 128, 255, 0.5)",
                spec = CssTransitionSpec("background-color", 120f, CssTimingFunction.EASE),
                onComplete = {}
            )
        )

        // Unsupported properties must return false so CssTransitionController falls back to VBlank CADisplayLink
        assertFalse(
            styled.runNativeTransition(
                property = "border-width",
                fromValue = "1px",
                toValue = "4px",
                spec = CssTransitionSpec("border-width", 120f, CssTimingFunction.LINEAR),
                onComplete = {}
            )
        )
        assertFalse(
            styled.runNativeTransition(
                property = "width",
                fromValue = "50%",
                toValue = "100%",
                spec = CssTransitionSpec("width", 120f, CssTimingFunction.LINEAR),
                onComplete = {}
            )
        )

        val stdContainer = IosContainerImpl()
        assertFalse(
            stdContainer.runNativeTransition(
                property = "background-color",
                fromValue = "rgb(255, 0, 0)",
                toValue = "rgb(0, 0, 255)",
                spec = CssTransitionSpec("background-color", 120f, CssTimingFunction.LINEAR),
                onComplete = {}
            )
        )
        stdContainer.dispose()

        styled.dispose()
        assertTrue(opacityCompleted, "Disposing active UIViewPropertyAnimator must invoke onComplete()")
    }

    @Test
    fun cssTransitionControllerFallsBackToIosVBlankForNonNativeProperties() {
        val factory = IosNativeFactory()
        GenericUI.initialize(factory)

        val window = Window(title = "iOS Transition Fallback Test")
        val comp = Container()
        window.addChild(comp)
        window.show(factory)

        comp.transitionController.updateTransitionCss("background-color 200ms linear")
        comp.transitionController.onStyleChanged(
            previous = ComputedStyle(backgroundColor = RgbaColor.RED),
            next = ComputedStyle(backgroundColor = RgbaColor.BLUE)
        )
        assertTrue(comp.transitionController.hasActiveSoftwareTransition("background-color"))

        Animator.advanceTimeBy(100.0)
        GenericUiThreading.flushAllQueues()
        val midBg = comp.transitionController.currentFrameState.backgroundColor
        // Bootstrap RgbaColor.RED (220,53,69) -> RgbaColor.BLUE (13,110,253) exact linear 50% midpoint is (117,82,161)
        assertTrue(midBg != null && midBg.r in 114..120 && midBg.g in 79..85 && midBg.b in 158..164, "Unexpected mid color: $midBg")

        Animator.advanceTimeBy(120.0)
        GenericUiThreading.flushAllQueues()
        assertFalse(comp.transitionController.hasActiveSoftwareTransition("background-color"))
        assertEquals(RgbaColor.BLUE, comp.transitionController.currentFrameState.backgroundColor)

        window.close()
        window.dispose()
    }
}
