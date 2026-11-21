package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.HStack
import com.nexustools.genericui.component.layout.VStack
import com.nexustools.genericui.component.lazy.LazyList
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.SpacingScale
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
}
