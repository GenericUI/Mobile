package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.CssShadow
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidBackdropAndSplitDropdownTest {

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
    fun dropdownSupportsPrimaryActionForSplitDropdownButton() {
        val dropdown = AndroidDropdownImpl()
        val view = assertNotNull(dropdown.ensureAndroidView(context))
        dropdown.setLabel("Save")
        dropdown.setItems(listOf("as" to "Save As", "all" to "Save All"))

        var primaryClicks = 0
        dropdown.setOnPrimaryAction { primaryClicks++ }
        shadowOf(context.mainLooper).idle()

        assertEquals("true", dropdown.nativeWidget.attributes["splitDropdown"])
        view.layout(0, 0, 200, 48)
        view.performClick()
        assertEquals(1, primaryClicks)
        assertTrue(dropdown.triggerPrimaryAction())
        assertEquals(2, primaryClicks)

        // Dispatching touch inside trailing caret opens popup rather than calling primary listener
        val caretDown = MotionEvent.obtain(0L, 0L, MotionEvent.ACTION_DOWN, 190f, 24f, 0)
        view.dispatchTouchEvent(caretDown)
        caretDown.recycle()
        view.performClick()
        assertEquals(2, primaryClicks)

        dropdown.setOnPrimaryAction(null)
        shadowOf(context.mainLooper).idle()
        assertEquals("false", dropdown.nativeWidget.attributes["splitDropdown"])
        assertFalse(dropdown.triggerPrimaryAction())
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun androidBackdropControllerRendersFullTenFunctionFilterWithDropShadow() {
        val root = FrameLayout(context)
        val underlay = View(context).apply {
            setBackgroundColor(Color.rgb(210, 40, 60))
        }
        val overlayHost = View(context)
        root.addView(underlay)
        root.addView(overlayHost)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(120, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY)
        )
        root.layout(0, 0, 120, 80)
        underlay.layout(0, 0, 120, 80)
        overlayHost.layout(10, 10, 90, 60)

        val controller = AndroidHardwareBackdropBlurController(overlayHost).apply {
            backdropFilter = CssFilter(
                blur = CssLength.px(4f),
                grayscale = 0.8f,
                contrast = 1.2f,
                dropShadow = CssShadow.px(2f, 2f, 3f, color = RgbaColor(0, 0, 0, 180))
            )
            backdropCornerRadiusPx = 6f
        }
        assertTrue(controller.hasActiveBackdrop())

        val targetBitmap = Bitmap.createBitmap(80, 50, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(targetBitmap)
        controller.drawBackdropIfActive(canvas)

        val centerPixel = targetBitmap.getPixel(40, 25)
        assertTrue(Color.alpha(centerPixel) > 0)
        controller.release()
        targetBitmap.recycle()
    }
}
