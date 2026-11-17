@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ipados.IpadosNativeFactory
import com.nexustools.genericui.ipados.IpadosWindowImpl
import com.nexustools.genericui.window.WindowAspectRatioHelper
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowLevelNormal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IosAndIpadosWindowAlwaysOnTopAndAspectRatioTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun saveState() {
        savedActiveFactory = GenericUI.activeFactory
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
    }

    @AfterTest
    fun restoreState() {
        Application.current?.dispose()
        UikitEnvironment.registeredKeyWindow = null
        UikitEnvironment.registeredRootViewController = null
        GenericUI.activeFactory = savedActiveFactory
    }

    @Test
    fun iosWindowAppliesWindowLevelAnd16By9ClampExceptInMaximizeAndFullscreen() {
        val impl = IosWindowImpl()
        assertFalse(impl.isAlwaysOnTop())

        impl.setAlwaysOnTop(true)
        assertTrue(impl.isAlwaysOnTop())
        impl.showWindow()
        val uiWindow = impl.uiWindow
        assertNotNull(uiWindow)
        assertEquals(UIWindowLevelAlert, uiWindow.windowLevel)

        impl.setAlwaysOnTop(false)
        assertFalse(impl.isAlwaysOnTop())
        assertEquals(UIWindowLevelNormal, uiWindow.windowLevel)

        impl.setWindowSize(Size(1280, 800))
        impl.setAspectRatio(16.0 / 9.0)
        assertEquals(16.0 / 9.0, impl.getAspectRatio() ?: 0.0, 1e-6)
        assertEquals(Size(1280, 720), impl.getWindowSize())

        impl.setMinWindowSize(Size(640, 360))
        impl.setWindowSize(Size(960, 900))
        assertEquals(Size(960, 540), impl.getWindowSize())

        impl.setWindowState(WindowState.MAXIMIZED)
        impl.setWindowSize(Size(1920, 1200))
        assertEquals(Size(1920, 1200), impl.getWindowSize())

        impl.setWindowState(WindowState.NORMAL)
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))

        impl.setFullscreen(true)
        assertTrue(impl.isFullscreen())
        impl.setWindowSize(Size(2560, 1600))
        assertEquals(Size(2560, 1600), impl.getWindowSize())

        impl.setFullscreen(false)
        assertFalse(impl.isFullscreen())
        assertEquals(WindowState.NORMAL, impl.getWindowState())
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))

        impl.setAspectRatio(null)
        assertNull(impl.getAspectRatio())
        impl.closeWindow()
        impl.dispose()
    }

    @Test
    fun ipadosWindowSupportsStageManagerFreeform16By9ClampingAndUnconstrainedFullscreen() {
        val impl = IpadosWindowImpl()
        impl.setMinWindowSize(Size(480, 270))
        impl.setMaxWindowSize(Size(1600, 1000))
        impl.setAlwaysOnTop(true)
        impl.showWindow()
        val uiWindow = impl.uiWindow
        assertNotNull(uiWindow)
        assertEquals(UIWindowLevelAlert, uiWindow.windowLevel)

        impl.setAlwaysOnTop(false)
        assertEquals(UIWindowLevelNormal, uiWindow.windowLevel)

        impl.setWindowSize(Size(1280, 800))
        impl.setAspectRatio(16.0 / 9.0)
        assertEquals(Size(1280, 720), impl.getWindowSize())

        // Stage Manager width drag clamps height to 16:9 within maxWindowSize
        impl.notifySceneGeometryChanged(1600, 720)
        assertEquals(Size(1600, 900), impl.getWindowSize())

        // Stage Manager height drag clamps width to 16:9
        impl.notifySceneGeometryChanged(1600, 540)
        assertEquals(Size(960, 540), impl.getWindowSize())

        // Oversized request is clamped to maxWindowSize and 16:9
        impl.setWindowSize(Size(2000, 2000))
        assertEquals(Size(1600, 900), impl.getWindowSize())

        // Maximize & Fullscreen are unconstrained
        impl.setWindowState(WindowState.MAXIMIZED)
        impl.setWindowSize(Size(1920, 1200))
        assertEquals(Size(1920, 1200), impl.getWindowSize())

        impl.setWindowState(WindowState.NORMAL)
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))
        assertTrue(impl.getWindowSize().width <= 1600 && impl.getWindowSize().height <= 1000)

        impl.setFullscreen(true)
        assertTrue(impl.isFullscreen())
        impl.notifySceneGeometryChanged(2048, 2732)
        assertEquals(Size(2048, 2732), impl.getWindowSize())

        impl.setFullscreen(false)
        assertFalse(impl.isFullscreen())
        assertEquals(WindowState.NORMAL, impl.getWindowState())
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))

        impl.closeWindow()
        impl.dispose()
    }

    @Test
    fun commonWindowIntegratesAlwaysOnTopAnd16By9AcrossIosAndIpadosFactories() {
        for (factory in listOf(IosNativeFactory(), IpadosNativeFactory())) {
            GenericUI.initialize(factory)
            val window = Window(title = "Apple Ratio ${factory.backendName}", initialSize = Size(1280, 800))
            window.addChild(Container())
            window.show(factory)

            val impl = window.nativeImpl as IosWindowImpl
            window.setAlwaysOnTop(true)
            assertTrue(window.isAlwaysOnTop)
            assertTrue(impl.isAlwaysOnTop())
            assertEquals(UIWindowLevelAlert, impl.uiWindow?.windowLevel)

            window.setAspectRatio(16, 9)
            assertEquals(Size(1280, 720), window.getWindowSize())
            window.setMinWindowSize(Size(640, 360))
            window.setWindowSize(Size(960, 900))
            assertEquals(Size(960, 540), window.getWindowSize())

            window.setWindowState(WindowState.MAXIMIZED)
            window.setWindowSize(Size(1920, 1200))
            assertEquals(Size(1920, 1200), window.getWindowSize())

            window.restore()
            assertEquals(WindowState.NORMAL, window.getWindowState())
            assertEquals(Size(960, 540), window.getWindowSize())

            window.setFullscreen(true)
            assertTrue(window.isFullscreen())
            window.setWindowSize(Size(2560, 1600))
            assertEquals(Size(2560, 1600), window.getWindowSize())

            window.setFullscreen(false)
            assertFalse(window.isFullscreen())
            assertEquals(WindowState.NORMAL, window.getWindowState())
            assertEquals(Size(960, 540), window.getWindowSize())

            window.close()
            window.dispose()
            UikitEnvironment.registeredKeyWindow = null
            UikitEnvironment.registeredRootViewController = null
        }
    }
}
