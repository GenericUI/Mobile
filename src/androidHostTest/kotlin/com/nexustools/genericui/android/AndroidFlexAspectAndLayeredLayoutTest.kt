package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.AspectRatioContainer
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.component.layout.LayeredContainer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapAspectRatio
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.CssEdgeLengths
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.enums.css.CssPosition
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidFlexAspectAndLayeredLayoutTest {

    private lateinit var context: Context

    @BeforeTest
    fun setUp() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        context = RuntimeEnvironment.getApplication()
        AndroidViewHandle.activeContext = context
        AndroidPreferenceStoreImpl.applicationContext = context
    }

    @AfterTest
    fun tearDown() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
    }

    @Test
    fun aspectRatio16By9Measures810HeightFor1440WidthAndStacksChildrenInBounds() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val aspectBox = AspectRatioContainer(BootstrapAspectRatio.RATIO_16X9)
        val firstLayer = Button("Video")
        val secondLayer = Button("HUD")
        aspectBox.addChild(firstLayer)
        aspectBox.addChild(secondLayer)

        val window = Window(title = "AspectTest")
        window.setContent(aspectBox)
        window.show(factory)
        shadowOf(context.mainLooper).idle()

        val aspectView = (aspectBox.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
        val firstView = (firstLayer.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
        val secondView = (secondLayer.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
        assertNotNull(aspectView)
        assertNotNull(firstView)
        assertNotNull(secondView)

        aspectView.measure(
            MeasureSpec.makeMeasureSpec(1440, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(2560, MeasureSpec.AT_MOST)
        )
        assertEquals(1440, aspectView.measuredWidth)
        assertEquals(810, aspectView.measuredHeight)

        aspectView.layout(0, 0, 1440, 810)
        assertEquals(0, firstView.left)
        assertEquals(0, firstView.top)
        assertEquals(1440, firstView.right)
        assertEquals(810, firstView.bottom)
        assertEquals(0, secondView.left)
        assertEquals(0, secondView.top)
        assertEquals(1440, secondView.right)
        assertEquals(810, secondView.bottom)

        // Also check UNSPECIFIED height spec
        aspectView.measure(
            MeasureSpec.makeMeasureSpec(1440, MeasureSpec.AT_MOST),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )
        assertEquals(1440, aspectView.measuredWidth)
        assertEquals(810, aspectView.measuredHeight)

        window.dispose()
    }

    @Test
    fun layeredContainerStacksFillAndFloatChildrenWithTranslationZAndPassThroughTouches() {
        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        AndroidViewHandle.activeContext = activity
        val factory = AndroidNativeFactory(activity)
        GenericUI.initialize(factory)

        var baseClicks = 0
        var hudButtonClicks = 0

        val layered = LayeredContainer()
        val baseBtn = Button("BaseAction").apply {
            setOnClick { baseClicks++ }
        }
        val floatBadge = Button("FloatBadge").apply {
            widthPx = 120
            heightPx = 48
        }
        val centerBadge = Button("CenterBadge").apply {
            widthPx = 100
            heightPx = 40
        }
        val hudOverlay = LayeredContainer().apply {
            val hudBtn = Button("HudAction").apply {
                widthPx = 120
                heightPx = 48
                setOnClick { hudButtonClicks++ }
            }
            addFloatingLayer(
                component = hudBtn,
                alignment = LayerAlignment.TOP_START,
                offsetXPx = 12,
                offsetYPx = 12,
                zIndex = 2
            )
        }

        layered.addFillLayer(baseBtn, zIndex = 1)
        layered.addFloatingLayer(
            component = floatBadge,
            alignment = LayerAlignment.BOTTOM_END,
            offsetXPx = -16,
            offsetYPx = -20,
            zIndex = 10
        )
        layered.addFloatingLayer(
            component = centerBadge,
            alignment = LayerAlignment.CENTER,
            zIndex = 20
        )
        layered.addFillLayer(hudOverlay, zIndex = 15, passThroughPointerEvents = true)

        val window = Window(title = "LayeredTest")
        window.setContent(layered)
        window.show(factory)
        shadowOf(activity.mainLooper).idle()

        val layeredView = (layered.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(activity)
        val baseView = (baseBtn.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(activity)
        val floatView = (floatBadge.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(activity)
        val centerView = (centerBadge.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(activity)
        val hudView = (hudOverlay.nativeImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(activity)
        assertNotNull(layeredView)
        assertNotNull(baseView)
        assertNotNull(floatView)
        assertNotNull(centerView)
        assertNotNull(hudView)

        val hostFrame = FrameLayout(activity)
        (layeredView.parent as? android.view.ViewGroup)?.removeView(layeredView)
        hostFrame.addView(layeredView, FrameLayout.LayoutParams(800, 600))
        activity.setContentView(hostFrame)
        shadowOf(activity.mainLooper).idle()

        layeredView.measure(
            MeasureSpec.makeMeasureSpec(800, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY)
        )
        layeredView.layout(0, 0, 800, 600)

        // FILL child covers (0, 0, 800, 600) with translationZ = 1
        assertEquals(0, baseView.left)
        assertEquals(0, baseView.top)
        assertEquals(800, baseView.right)
        assertEquals(600, baseView.bottom)
        assertEquals(1f, baseView.translationZ)

        // FLOAT child overlays at BOTTOM_END (-16, -20), NOT stacked vertically below baseView
        assertEquals(800 - 120 - 16, floatView.left)
        assertEquals(600 - 48 - 20, floatView.top)
        assertEquals(800 - 16, floatView.right)
        assertEquals(600 - 20, floatView.bottom)
        assertEquals(10f, floatView.translationZ)
        assertTrue(floatView.top < baseView.bottom, "FLOAT child must overlay inside container, not stack below FILL child")

        // CENTER FLOAT child overlays at center
        assertEquals(350, centerView.left)
        assertEquals(280, centerView.top)
        assertEquals(450, centerView.right)
        assertEquals(320, centerView.bottom)
        assertEquals(20f, centerView.translationZ)

        // Pass-through HUD overlay: tap on HUD button clicks hudBtn, tap on empty HUD area reaches baseBtn
        val downTime1 = SystemClock.uptimeMillis()
        val downOnHudBtn = MotionEvent.obtain(downTime1, downTime1, MotionEvent.ACTION_DOWN, 30f, 30f, 0)
        val upOnHudBtn = MotionEvent.obtain(downTime1, downTime1 + 20, MotionEvent.ACTION_UP, 30f, 30f, 0)
        assertTrue(layeredView.dispatchTouchEvent(downOnHudBtn))
        assertTrue(layeredView.dispatchTouchEvent(upOnHudBtn))
        shadowOf(activity.mainLooper).idle()
        downOnHudBtn.recycle()
        upOnHudBtn.recycle()
        assertEquals(1, hudButtonClicks)
        assertEquals(0, baseClicks)

        val downTime2 = SystemClock.uptimeMillis()
        val downOnEmptyHud = MotionEvent.obtain(downTime2, downTime2, MotionEvent.ACTION_DOWN, 250f, 450f, 0)
        val upOnEmptyHud = MotionEvent.obtain(downTime2, downTime2 + 20, MotionEvent.ACTION_UP, 250f, 450f, 0)
        assertTrue(layeredView.dispatchTouchEvent(downOnEmptyHud))
        assertTrue(layeredView.dispatchTouchEvent(upOnEmptyHud))
        shadowOf(activity.mainLooper).idle()
        downOnEmptyHud.recycle()
        upOnEmptyHud.recycle()
        assertEquals(1, baseClicks)
        assertEquals(1, hudButtonClicks)

        window.dispose()
        activityController.pause().stop().destroy()
    }

    @Test
    fun proximalRelativeAndWindowAbsoluteSignedTransformZIndexAndPointerEventsPassThroughInFlexContainers() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val relRoot = AndroidContainerImpl().apply {
            applyComponentStyle(ComponentStyle().apply {
                setPosition(CssPosition.RELATIVE)
                setWidth(CssLength.px(400f))
                setHeight(CssLength.px(300f))
            })
        }
        val staticMiddle = AndroidContainerImpl().apply {
            applyComponentStyle(ComponentStyle().apply {
                setMargin(CssEdgeLengths(top = CssLength.px(50f), left = CssLength.px(20f), right = CssLength.px(0f), bottom = CssLength.px(0f)))
                setWidth(CssLength.px(300f))
                setHeight(CssLength.px(200f))
            })
        }

        var lowerClicks = 0
        val lowerBtn = AndroidStyledButtonImpl().apply {
            setOnClick { lowerClicks++ }
            applyComponentStyle(ComponentStyle().apply {
                setPosition(CssPosition.ABSOLUTE)
                setTop(CssLength.px(20f))
                setRight(CssLength.px(10f))
                setZIndex(5)
                setWidth(CssLength.px(80f))
                setHeight(CssLength.px(30f))
            })
        }

        val upperPassThroughSc = AndroidStyledComponentImpl().apply {
            applyComponentStyle(ComponentStyle().apply {
                setPosition(CssPosition.ABSOLUTE)
                setTop(CssLength.px(20f))
                setRight(CssLength.px(10f))
                setZIndex(25)
                setWidth(CssLength.px(80f))
                setHeight(CssLength.px(30f))
                setTransform(CssTransform2D(translateX = -10f, translateY = -5f, scaleX = 1.25f))
            })
            applyComponentBootstrap(ComponentBootstrap().apply { setPointerEvents(BootstrapPointerEvents.NONE) })
        }

        staticMiddle.addChildNative(lowerBtn, -1)
        staticMiddle.addChildNative(upperPassThroughSc, -1)
        relRoot.addChildNative(staticMiddle, -1)

        val relView = relRoot.ensureAndroidView(context) as AndroidFlexContainerView
        val midView = staticMiddle.ensureAndroidView(context) as AndroidFlexContainerView
        val lowerView = lowerBtn.ensureAndroidView(context)!!
        val upperView = upperPassThroughSc.ensureAndroidView(context)!!
        shadowOf(context.mainLooper).idle()

        val activityController = Robolectric.buildActivity(Activity::class.java).setup()
        val activity = activityController.get()
        val hostFrame = FrameLayout(activity)
        hostFrame.addView(relView, FrameLayout.LayoutParams(400, 300))
        activity.setContentView(hostFrame)
        shadowOf(activity.mainLooper).idle()

        relView.measure(MeasureSpec.makeMeasureSpec(400, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(300, MeasureSpec.EXACTLY))
        relView.layout(0, 0, 400, 300)

        // staticMiddle at (20, 50); right=10 top=20 inside relRoot (400x300) => xInRel=310, yInRel=20 => local=(290, -30)
        assertEquals(290, upperView.left)
        assertEquals(-30, upperView.top)
        assertEquals(80, upperView.width)
        assertEquals(30, upperView.height)
        assertEquals(-10f, upperView.translationX)
        assertEquals(-5f, upperView.translationY)
        assertEquals(1.25f, upperView.scaleX)
        assertEquals(25f, upperView.translationZ)
        assertEquals(5f, lowerView.translationZ)

        // Touch in staticMiddle coordinates hitting lowerBtn (290+10, -30+10) passes through upperPassThroughSc?
        // Note: upperView is above lowerView in midView (z=25 > z=5), so pointer-events: none returns false and lets lowerView catch!
        val t = SystemClock.uptimeMillis()
        val down = MotionEvent.obtain(t, t, MotionEvent.ACTION_DOWN, 295f, -20f, 0)
        val up = MotionEvent.obtain(t, t + 15, MotionEvent.ACTION_UP, 295f, -20f, 0)
        // Dispatch directly to midView? Note: Android standard ViewGroup checks child bounds! Notice lowerView is also at (290, -30)..(370, 0)!
        assertTrue(midView.dispatchTouchEvent(down))
        assertTrue(midView.dispatchTouchEvent(up))
        shadowOf(activity.mainLooper).idle()
        down.recycle()
        up.recycle()
        assertEquals(1, lowerClicks, "pointer-events: none upper StyledComponent must pass clicks through to underlying lower-z Button")

        // Direct Window position: absolute + translateX(-50%)
        val win = AndroidWindowImpl()
        win.setWindowSize(Size(800, 600))
        val winAbs = AndroidStyledComponentImpl().apply {
            applyComponentStyle(ComponentStyle().apply {
                setPosition(CssPosition.ABSOLUTE)
                setTop(CssLength.px(20f))
                setLeft(CssLength.percent(50f))
                setWidth(CssLength.px(100f))
                setHeight(CssLength.px(40f))
                setTransform(CssTransform2D(translateX = -50f, translateXIsPercent = true))
            })
        }
        win.addChildNative(winAbs, -1)
        val winView = win.ensureAndroidView(context) as AndroidFlexContainerView
        val winAbsView = winAbs.ensureAndroidView(context)!!
        shadowOf(context.mainLooper).idle()
        winView.measure(MeasureSpec.makeMeasureSpec(800, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY))
        winView.layout(0, 0, 800, 600)
        assertEquals(400, winAbsView.left)
        assertEquals(20, winAbsView.top)
        assertEquals(-50f, winAbsView.translationX)

        win.dispose()
        activityController.pause().stop().destroy()
    }
}
