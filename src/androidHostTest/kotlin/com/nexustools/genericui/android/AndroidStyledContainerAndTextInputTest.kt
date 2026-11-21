package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import com.nexustools.genericui.component.layout.StyledContainer
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.gradient.Gradient.GradientStop
import com.nexustools.genericui.style.gradient.Gradient.LinearGradient
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidStyledContainerAndTextInputTest {

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
    fun styledContainerPreservesGradientAndTransparentBackgroundAndMarshalsMutationsToMainThread() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val hudContainer = StyledContainer().apply {
            addClass("card")
            bootstrap.borderRadius = BorderRadiusStyle.ROUNDED
            backgroundColor = RgbaColor.TRANSPARENT
        }
        val hudImpl = AndroidStyledContainerImpl("card").apply {
            addClass("card")
            setBorderRadius(BorderRadiusStyle.ROUNDED)
            applyComponentStyle(hudContainer.getStyle())
            setStyledVisuals(hudContainer.resolveVisualSpec())
        }
        val hudView = hudImpl.ensureAndroidView(context)
        shadowOf(context.mainLooper).idle()
        assertNotNull(hudView)
        val hudBg = hudView.background as? GradientDrawable
        assertNotNull(hudBg)
        assertEquals(Color.TRANSPARENT, hudBg.color?.defaultColor ?: Color.TRANSPARENT)

        // Calling applyComponentBootstrap / setPadding later must not apply an opaque card surface drawable
        hudImpl.setPadding(com.nexustools.genericui.geometry.EdgeInsets(4, 4, 4, 4))
        shadowOf(context.mainLooper).idle()
        val hudBgAfterPadding = hudView.background as? GradientDrawable
        assertNotNull(hudBgAfterPadding)
        assertEquals(Color.TRANSPARENT, hudBgAfterPadding.color?.defaultColor ?: Color.TRANSPARENT)

        val gradientImpl = AndroidStyledContainerImpl()
        val gradient = LinearGradient(
            angleDegrees = 90f,
            stops = listOf(
                GradientStop(RgbaColor(200, 30, 50, 255), 0f),
                GradientStop(RgbaColor(30, 80, 220, 255), 1f)
            )
        )
        val gradView = gradientImpl.ensureAndroidView(context)
        assertNotNull(gradView)

        val latch = CountDownLatch(1)
        val bgThread = Thread {
            gradientImpl.setStyledVisuals(
                com.nexustools.genericui.style.css.StyledVisualSpec(
                    backgroundGradient = gradient
                )
            )
            latch.countDown()
        }
        bgThread.start()
        assertTrue(latch.await(5, TimeUnit.SECONDS))
        shadowOf(context.mainLooper).idle()

        val gradDrawable = gradView.background as? GradientDrawable
        assertNotNull(gradDrawable)
        assertEquals(GradientDrawable.LINEAR_GRADIENT, gradDrawable.gradientType)
        assertEquals(2, gradDrawable.colors?.size)

        // Triggering syncAndroidView via setPadding must NOT clobber the linear gradient
        gradientImpl.setPadding(com.nexustools.genericui.geometry.EdgeInsets(8, 8, 8, 8))
        shadowOf(context.mainLooper).idle()
        val afterSyncDrawable = gradView.background as? GradientDrawable
        assertNotNull(afterSyncDrawable)
        assertEquals(GradientDrawable.LINEAR_GRADIENT, afterSyncDrawable.gradientType)
    }

    private fun createSplitTextureBackdrop(
        onExistingSurfaceUpdated: () -> Unit = {}
    ): Pair<FrameLayout, android.view.TextureView> {
        val host = FrameLayout(context)
        val splitColorView = android.view.View(context).apply {
            background = object : android.graphics.drawable.Drawable() {
                private val paint = android.graphics.Paint()
                override fun draw(canvas: android.graphics.Canvas) {
                    val b = bounds
                    val mid = b.left + b.width() / 2f
                    paint.color = Color.RED
                    canvas.drawRect(b.left.toFloat(), b.top.toFloat(), mid, b.bottom.toFloat(), paint)
                    paint.color = Color.BLUE
                    canvas.drawRect(mid, b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(), paint)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                @Deprecated("Deprecated in Java")
                override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
            }
        }
        host.addView(
            splitColorView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        val tv = android.view.TextureView(context).apply {
            surfaceTextureListener = object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {}
                override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean = true
                override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {
                    onExistingSurfaceUpdated()
                }
            }
        }
        host.addView(
            tv,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )
        return host to tv
    }

    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    @Test
    fun styledContainerAndStyledButtonApplyBackdropBlurAcrossHierarchyIncludingTextureView() {
        val parent = AndroidFlexContainerView(context).apply {
            isLayered = true
        }
        val (splitBackdrop, _) = createSplitTextureBackdrop()
        splitBackdrop.layoutParams = AndroidFlexContainerView.LayoutParams(200, 100).apply {
            isLayerItem = true
            layerFill = true
            layerZIndex = 0
        }
        parent.addView(splitBackdrop)

        val overlayImpl = AndroidStyledContainerImpl()
        overlayImpl.setStyledVisuals(
            com.nexustools.genericui.style.css.StyledVisualSpec(
                backgroundColor = RgbaColor(0, 0, 0, 0),
                backdropBlurPx = 6f,
                backdropFilter = com.nexustools.genericui.style.css.CssFilter.blur(
                    com.nexustools.genericui.style.css.CssLength.px(6)
                )
            )
        )
        val overlayView = overlayImpl.ensureAndroidView(context) as AndroidFlexContainerView
        overlayView.layoutParams = AndroidFlexContainerView.LayoutParams(200, 100).apply {
            isLayerItem = true
            layerFill = true
            layerZIndex = 10
        }
        parent.addView(overlayView)

        val wSpec = android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY)
        val hSpec = android.view.View.MeasureSpec.makeMeasureSpec(100, android.view.View.MeasureSpec.EXACTLY)
        parent.measure(wSpec, hSpec)
        parent.layout(0, 0, 200, 100)
        shadowOf(context.mainLooper).idle()

        assertTrue(overlayImpl.nativeWidget.attributes.containsKey("backdropBlurPx"))
        val bmp = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bmp)
        parent.draw(canvas)

        // Near x=99 (left of the red/blue seam at x=100), blur should pull blue from the right half.
        val seamPixel = bmp.getPixel(99, 50)
        val redCh = Color.red(seamPixel)
        val blueCh = Color.blue(seamPixel)
        assertTrue(blueCh > 20, "Expected backdrop blur to bleed blue across seam on Android, got R=$redCh B=$blueCh")
        assertTrue(redCh < 245, "Expected backdrop blur to attenuate pure red at seam, got R=$redCh B=$blueCh")

        // Deeply nested StyledButton inside an unblurred wrapper over the split TextureView root
        var existingListenerCalls = 0
        val nestedRoot = AndroidFlexContainerView(context).apply { isLayered = true }
        val (bgSplit, bgTextureView) = createSplitTextureBackdrop { existingListenerCalls++ }
        val originalListener = bgTextureView.surfaceTextureListener
        bgSplit.layoutParams = AndroidFlexContainerView.LayoutParams(200, 100).apply {
            isLayerItem = true
            layerFill = true
            layerZIndex = 0
        }
        nestedRoot.addView(bgSplit)

        val hudWrapper = AndroidFlexContainerView(context).apply {
            isLayered = false
        }
        hudWrapper.layoutParams = AndroidFlexContainerView.LayoutParams(200, 100).apply {
            isLayerItem = true
            layerFill = true
            layerZIndex = 10
        }
        val btnImpl = AndroidStyledButtonImpl()
        btnImpl.setBaseVisuals(
            com.nexustools.genericui.style.css.StyledVisualSpec(
                backgroundColor = RgbaColor(0, 0, 0, 0),
                borderWidthPx = 0f,
                backdropBlurPx = 8f,
                backdropFilter = com.nexustools.genericui.style.css.CssFilter.blur(
                    com.nexustools.genericui.style.css.CssLength.px(8)
                )
            )
        )
        val resolvedBtn = btnImpl.resolveCurrentVisuals()
        assertEquals(8f, resolvedBtn.backdropBlurPx)
        assertNotNull(resolvedBtn.backdropFilter)
        assertTrue(btnImpl.nativeWidget.attributes.containsKey("backdropBlurPx"))
        assertTrue(btnImpl.nativeWidget.attributes.containsKey("backdropFilter"))

        val btnView = btnImpl.ensureAndroidView(context) as AndroidStyledButtonView
        btnView.layoutParams = AndroidFlexContainerView.LayoutParams(200, 100)
        hudWrapper.addView(btnView)
        nestedRoot.addView(hudWrapper)

        nestedRoot.measure(wSpec, hSpec)
        nestedRoot.layout(0, 0, 200, 100)
        shadowOf(context.mainLooper).idle()

        val btnBmp = android.graphics.Bitmap.createBitmap(200, 100, android.graphics.Bitmap.Config.ARGB_8888)
        val btnCanvas = android.graphics.Canvas(btnBmp)
        nestedRoot.draw(btnCanvas)
        val btnSeam = btnBmp.getPixel(99, 50)
        assertTrue(Color.blue(btnSeam) > 20, "Expected nested StyledButton backdrop blur from ancestor root TextureView, got R=${Color.red(btnSeam)} B=${Color.blue(btnSeam)}")
        assertTrue(Color.red(btnSeam) < 245, "Expected nested StyledButton backdrop blur to attenuate red, got R=${Color.red(btnSeam)} B=${Color.blue(btnSeam)}")

        // Verify TextureView listener relay wraps and preserves existing SurfaceTextureListener
        assertTrue(bgTextureView.surfaceTextureListener !== originalListener)
        val mockSt = android.graphics.SurfaceTexture(0)
        bgTextureView.surfaceTextureListener?.onSurfaceTextureUpdated(mockSt)
        assertEquals(1, existingListenerCalls)
        mockSt.release()
    }

    @Test
    fun textInputConstructedWithActiveContextCreatesSingleEditTextWithMultilineState() {
        val input = AndroidTextInputImpl(multiline = true)
        shadowOf(context.mainLooper).idle()

        val host = input.nativeWidget.androidView as? FrameLayout
        assertNotNull(host)
        assertEquals(1, host.childCount, "Superclass init must not leave a throwaway AppCompatEditText")
        val editText = host.getChildAt(0) as? android.widget.EditText
        assertNotNull(editText)
        assertEquals(Gravity.TOP or Gravity.START, editText.gravity)
        assertEquals(3, editText.minLines)
    }
}
