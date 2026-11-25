package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.component.tabs.TabPosition
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.ThemeColorSet
import com.nexustools.genericui.style.theme.ThemeConfig
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.runBlocking
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidCoreFixesTest {

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
        GenericUI.defaultThemeConfig = ThemeConfig.DEFAULT
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
    }

    @Test
    fun lazyListRapidItemCountUpdatesAndRemovalsLeaveZeroZombieViews() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val lazyList = LazyList(estimatedItemExtentPx = 48f, overscanItems = 2)
        val root = VStack().apply {
            addChild(lazyList)
        }
        val window = Window(title = "LazyZombieTest")
        window.setContent(root)
        window.show(factory)
        shadowOf(context.mainLooper).idle()

        val lazyImpl = lazyList.nativeImpl as? AndroidLazyListImpl
        assertNotNull(lazyImpl)
        val itemsHost = lazyImpl.nativeWidget.childHostViewGroup as? AndroidFlexContainerView
        assertNotNull(itemsHost)

        // Rapidly replace items on the Android Logic thread before Main Looper drains
        GenericUiThreading.runOnLogic {
            lazyList.setItems(20, onLoadItem = { idx -> Button("Batch1-$idx") })
            lazyList.setItems(12, onLoadItem = { idx -> Button("Batch2-$idx") }, onUnloadItem = { _, c -> c.dispose() })
            lazyList.setItems(0, onLoadItem = { idx -> Button("Empty-$idx") })
            lazyList.setItems(4, onLoadItem = { idx -> Button("Final-$idx") }, onUnloadItem = { _, c -> c.dispose() })
        }
        shadowOf(context.mainLooper).idle()

        assertEquals(4, lazyList.loadedItemCount)
        assertEquals(4, lazyImpl.nativeWidget.children.size)
        assertEquals(4, itemsHost.childCount)
        for (i in 0 until itemsHost.childCount) {
            val btn = itemsHost.getChildAt(i) as? android.widget.TextView
            assertNotNull(btn)
            assertEquals("Final-$i", btn.text?.toString())
        }

        // Clearing back to 0 must sweep all rows cleanly with zero zombie views
        GenericUiThreading.runOnLogic {
            lazyList.setItems(0, onLoadItem = { idx -> Button("None-$idx") })
        }
        shadowOf(context.mainLooper).idle()

        assertEquals(0, lazyList.loadedItemCount)
        assertEquals(0, lazyImpl.nativeWidget.children.size)
        assertEquals(0, itemsHost.childCount)

        window.dispose()
    }

    @Test
    fun hstackDefaultGapAndCustomGapsApplyToAndroidFlexContainerView() {
        val factory = AndroidNativeFactory(context)
        GenericUI.initialize(factory)

        val defaultHStack = HStack()
        val zeroHStack = HStack(0)
        val customHStack = HStack(20)
        val scaleHStack = HStack(SpacingScale.THREE)

        val container = VStack().apply {
            addChild(defaultHStack)
            addChild(zeroHStack)
            addChild(customHStack)
            addChild(scaleHStack)
        }
        val window = Window(title = "HStackGapTest")
        window.setContent(container)
        window.show(factory)
        shadowOf(context.mainLooper).idle()

        val defaultView = (defaultHStack.nativeImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView as? AndroidFlexContainerView
        val zeroView = (zeroHStack.nativeImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView as? AndroidFlexContainerView
        val customView = (customHStack.nativeImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView as? AndroidFlexContainerView
        val scaleView = (scaleHStack.nativeImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView as? AndroidFlexContainerView

        assertNotNull(defaultView)
        assertNotNull(zeroView)
        assertNotNull(customView)
        assertNotNull(scaleView)

        assertEquals(AndroidViewHandle.dpToPx(8), defaultView.gapPx)
        assertEquals(0, zeroView.gapPx)
        assertEquals(AndroidViewHandle.dpToPx(20), customView.gapPx)
        assertEquals(AndroidViewHandle.dpToPx(16), scaleView.gapPx)

        window.dispose()
    }

    @Test
    fun dispatchToLogicFromBackgroundThreadPostsUiWorkToMainLooper() {
        val dispatcher = AndroidUiThreadDispatcherNativeImpl()
        val mainLooperThread = context.mainLooper.thread
        val uiExecutedThread = AtomicReference<Thread?>(null)
        val logicExecutedOnLogicThread = AtomicReference<Boolean?>(null)
        val logicReportedNotUiThread = AtomicReference<Boolean?>(null)
        val uiReportedUiThread = AtomicReference<Boolean?>(null)
        val bgDoneLatch = CountDownLatch(1)
        val bgFailure = AtomicReference<Throwable?>(null)

        Thread({
            try {
                dispatcher.dispatchToLogic {
                    logicExecutedOnLogicThread.set(dispatcher.isOnLogicThread())
                    logicReportedNotUiThread.set(!dispatcher.isOnUiThread())
                    dispatcher.dispatchToUi {
                        uiExecutedThread.set(Thread.currentThread())
                        uiReportedUiThread.set(dispatcher.isOnUiThread())
                    }
                }
                // Before Main Looper idles, UI callback must NOT have executed on the background thread
                assertNull(uiExecutedThread.get())
            } catch (t: Throwable) {
                bgFailure.set(t)
            } finally {
                bgDoneLatch.countDown()
            }
        }, "Test-Bg-Coroutine-Thread").start()

        assertTrue(bgDoneLatch.await(5, TimeUnit.SECONDS))
        assertNull(bgFailure.get(), "Background thread failed: ${bgFailure.get()}")
        assertEquals(true, logicExecutedOnLogicThread.get())
        assertEquals(true, logicReportedNotUiThread.get())
        assertNull(uiExecutedThread.get(), "UI task must not drain inline on background thread")

        shadowOf(context.mainLooper).idle()
        assertEquals(mainLooperThread, uiExecutedThread.get())
        assertEquals(true, uiReportedUiThread.get())
    }

    @Test
    fun androidUdpSocketMulticastLockLifecycleHandlesJoinLeaveDisposeAndNullContext(): Unit = runBlocking {
        AndroidViewHandle.activeContext = context
        val udp = AndroidUdpSocketNativeImpl()
        assertTrue(udp.bind("0.0.0.0", 0))
        assertFalse(udp.isMulticastLockHeld())

        assertTrue(udp.joinMulticastGroup("239.255.255.250", null))
        assertTrue(udp.isMulticastLockHeld())

        // Joining a second group keeps the lock held
        assertTrue(udp.joinMulticastGroup("239.255.255.251", null))
        assertTrue(udp.isMulticastLockHeld())

        // Leaving first group keeps the lock held because second group remains
        assertTrue(udp.leaveMulticastGroup("239.255.255.250", null))
        assertTrue(udp.isMulticastLockHeld())

        // Leaving last group releases the lock
        assertTrue(udp.leaveMulticastGroup("239.255.255.251", null))
        assertFalse(udp.isMulticastLockHeld())

        // Dispose while holding a group must release cleanly
        assertTrue(udp.joinMulticastGroup("239.255.255.250", null))
        assertTrue(udp.isMulticastLockHeld())
        udp.dispose()
        assertFalse(udp.isMulticastLockHeld())

        // Null activeContext degrades safely without throwing
        AndroidViewHandle.activeContext = null
        val headlessUdp = AndroidUdpSocketNativeImpl()
        assertTrue(headlessUdp.bind("0.0.0.0", 0))
        assertTrue(headlessUdp.joinMulticastGroup("239.255.255.250", null))
        assertFalse(headlessUdp.isMulticastLockHeld())
        assertTrue(headlessUdp.leaveMulticastGroup("239.255.255.250", null))
        headlessUdp.dispose()
    }

    @Test
    fun genuineMaterialTabLayoutAndUntintedNativeProgressBarInRobolectric() {
        val pb = AndroidProgressBarImpl()
        pb.setProgress(0.5f)
        pb.setType(BootstrapType.DANGER)
        val nativePb = pb.nativeWidget.androidView as android.widget.ProgressBar
        assertEquals(500, nativePb.progress)
        assertNull(nativePb.progressTintList, "Native Android ProgressBar must leave progressTintList null")
        assertNull(nativePb.indeterminateTintList, "Native Android ProgressBar must leave indeterminateTintList null")
        assertNull(nativePb.backgroundTintList)

        val sp = AndroidSpinnerImpl()
        sp.setType(BootstrapType.PRIMARY)
        val nativeSp = sp.nativeWidget.androidView as android.widget.ProgressBar
        assertNull(nativeSp.indeterminateTintList)

        val tc = AndroidTabContainerImpl()
        tc.setType(BootstrapType.DANGER)
        val matTabLayout = tc.tabLayout
        assertNotNull(matTabLayout, "AndroidTabContainerImpl must use genuine Material TabLayout")
        assertNull(tc.nativeWidget.androidView?.backgroundTintList)

        val tab0 = AndroidTabImpl().apply { setTitleNative("Overview") }
        val tab1 = AndroidTabImpl().apply { setTitleNative("Settings") }
        val c0 = AndroidContainerImpl()
        val c1 = AndroidContainerImpl()
        var selectedCallbackIdx = -1
        tc.setOnTabSelected { idx -> selectedCallbackIdx = idx }
        tc.addTabNative(tab0, c0, -1)
        tc.addTabNative(tab1, c1, -1)
        assertEquals(2, matTabLayout.tabCount)
        assertEquals("Overview", matTabLayout.getTabAt(0)?.text?.toString())
        assertEquals("Settings", matTabLayout.getTabAt(1)?.text?.toString())

        matTabLayout.getTabAt(1)?.select()
        assertEquals(1, tc.getSelectedTabIndex())
        assertEquals(1, selectedCallbackIdx)

        tc.setTabPositionNative(TabPosition.BOTTOM)
        val root = tc.nativeWidget.androidView as android.widget.LinearLayout
        assertEquals(matTabLayout, root.getChildAt(root.childCount - 1))

        val dropdown = AndroidDropdownImpl()
        dropdown.setLabel("Filter")
        dropdown.setType(BootstrapType.NONE)
        shadowOf(context.mainLooper).idle()
        val dropdownView = dropdown.nativeWidget.androidView as? androidx.appcompat.widget.AppCompatButton
        assertNotNull(dropdownView)
        assertNull(dropdownView.backgroundTintList)
        dropdown.setType(BootstrapType.PRIMARY)
        shadowOf(context.mainLooper).idle()
        assertEquals("true", dropdown.nativeWidget.attributes["useSystemControlAppearance"])
        assertNull(dropdown.nativeWidget.attributes["nativeButtonTinted"])
        assertNull(dropdown.nativeWidget.attributes["offscreenSpriteTinted"])
        assertNull(dropdown.nativeWidget.attributes["bootstrapTintColor"])
        assertNull(dropdownView.backgroundTintList)
    }

    @Test
    fun nativeAndroidButtonUsesBackgroundTintListAndRippleGradientDrawablesInRobolectric() {
        val customPrimary = RgbaColor(200, 40, 90, 255)
        val app = AndroidApplicationImpl()
        try {
            app.setThemeConfig(
                ThemeConfig(
                    lightColors = ThemeColorSet(primary = customPrimary),
                    darkColors = ThemeColorSet(primary = customPrimary)
                )
            )
            val btn = AndroidButtonImpl()
            btn.setText("Confirm")
            btn.setType(BootstrapType.NONE)
            shadowOf(context.mainLooper).idle()

            val btnView = assertNotNull(btn.nativeWidget.androidView as? androidx.appcompat.widget.AppCompatButton)
            val defaultBg = btnView.background
            assertNull(btnView.backgroundTintList)
            assertEquals("true", btn.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(btn.nativeWidget.attributes["nativeButtonTinted"])

            // Standard filled semantic button uses native backgroundTintList
            btn.setType(BootstrapType.PRIMARY)
            shadowOf(context.mainLooper).idle()
            assertEquals("false", btn.nativeWidget.attributes["useSystemControlAppearance"])
            assertEquals("true", btn.nativeWidget.attributes["nativeButtonTinted"])
            assertEquals(customPrimary.toArgbInt(), assertNotNull(btnView.backgroundTintList).defaultColor)

            // Outlined pill button uses RippleDrawable wrapping GradientDrawable with transparent fill & rounded corners
            btn.setOutline(true)
            btn.setBorderRadius(BorderRadiusStyle.PILL)
            shadowOf(context.mainLooper).idle()
            assertNull(btnView.backgroundTintList)
            val outlineRipple = assertNotNull(btnView.background as? RippleDrawable)
            val outlineShape = assertNotNull(outlineRipple.getDrawable(0) as? GradientDrawable)
            assertEquals(Color.TRANSPARENT, outlineShape.color?.defaultColor)
            assertTrue(outlineShape.cornerRadius > 0f)
            assertEquals(customPrimary.toArgbInt(), btnView.currentTextColor)

            // Filled pill button uses RippleDrawable wrapping filled rounded GradientDrawable
            btn.setOutline(false)
            shadowOf(context.mainLooper).idle()
            assertNull(btnView.backgroundTintList)
            val filledRipple = assertNotNull(btnView.background as? RippleDrawable)
            val filledShape = assertNotNull(filledRipple.getDrawable(0) as? GradientDrawable)
            assertEquals(customPrimary.toArgbInt(), filledShape.color?.defaultColor)
            assertTrue(filledShape.cornerRadius > 0f)

            // Link button uses transparent GradientDrawable inside RippleDrawable with primary text color
            btn.setType(BootstrapType.LINK)
            btn.setBorderRadius(null)
            shadowOf(context.mainLooper).idle()
            assertNull(btnView.backgroundTintList)
            val linkRipple = assertNotNull(btnView.background as? RippleDrawable)
            val linkShape = assertNotNull(linkRipple.getDrawable(0) as? GradientDrawable)
            assertEquals(Color.TRANSPARENT, linkShape.color?.defaultColor)
            assertEquals(customPrimary.toArgbInt(), btnView.currentTextColor)

            // Reverting to NONE restores system control background and clears backgroundTintList
            btn.setType(BootstrapType.NONE)
            shadowOf(context.mainLooper).idle()
            assertNull(btnView.backgroundTintList)
            assertEquals("true", btn.nativeWidget.attributes["useSystemControlAppearance"])
            assertNull(btn.nativeWidget.attributes["nativeButtonTinted"])
            assertTrue(btnView.background !== linkRipple)
            if (defaultBg != null) {
                assertNotNull(btnView.background)
            }
        } finally {
            app.dispose()
        }
    }
}
