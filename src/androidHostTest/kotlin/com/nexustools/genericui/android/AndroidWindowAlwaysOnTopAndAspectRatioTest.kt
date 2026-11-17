package com.nexustools.genericui.android

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.layout.Container
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.core.GenericUiLaunchSequence
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.window.WindowAspectRatioHelper
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AndroidWindowAlwaysOnTopAndAspectRatioTest {

    private var savedActiveFactory: NativeFactory? = null

    @BeforeTest
    fun saveState() {
        savedActiveFactory = GenericUI.activeFactory
        Application.current?.dispose()
    }

    @AfterTest
    fun restoreState() {
        Application.current?.dispose()
        GenericUI.activeFactory = savedActiveFactory
    }

    @Test
    fun nativeImplTracksAlwaysOnTopAndClamps16By9WhileUnconstrainedInMaximizeAndFullscreen() {
        val impl = AndroidWindowImpl()

        assertFalse(impl.isAlwaysOnTop())
        assertEquals("false", impl.nativeWidget.attributes["always-on-top"])
        impl.setAlwaysOnTop(true)
        assertTrue(impl.isAlwaysOnTop())
        assertEquals("true", impl.nativeWidget.attributes["always-on-top"])
        impl.setAlwaysOnTop(false)
        assertFalse(impl.isAlwaysOnTop())
        assertEquals("false", impl.nativeWidget.attributes["always-on-top"])

        impl.setWindowSize(Size(1280, 800))
        impl.setAspectRatio(16.0 / 9.0)
        assertEquals(16.0 / 9.0, impl.getAspectRatio() ?: 0.0, 1e-6)
        assertEquals((16.0 / 9.0).toString(), impl.nativeWidget.attributes["aspect-ratio"])
        assertEquals(Size(1280, 720), impl.getWindowSize())

        impl.setMinWindowSize(Size(640, 360))
        impl.setWindowSize(Size(960, 900))
        assertEquals(Size(960, 540), impl.getWindowSize())

        impl.setWindowSize(Size(320, 100))
        assertEquals(Size(640, 360), impl.getWindowSize())

        impl.setWindowState(WindowState.MAXIMIZED)
        assertNull(impl.nativeWidget.attributes["aspect-ratio"])
        impl.setWindowSize(Size(1920, 1200))
        assertEquals(Size(1920, 1200), impl.getWindowSize())

        impl.setWindowState(WindowState.NORMAL)
        assertEquals((16.0 / 9.0).toString(), impl.nativeWidget.attributes["aspect-ratio"])
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))

        impl.setFullscreen(true)
        assertTrue(impl.isFullscreen())
        assertNull(impl.nativeWidget.attributes["aspect-ratio"])
        impl.setWindowSize(Size(2560, 1600))
        assertEquals(Size(2560, 1600), impl.getWindowSize())

        impl.setFullscreen(false)
        assertFalse(impl.isFullscreen())
        assertEquals(WindowState.NORMAL, impl.getWindowState())
        assertEquals((16.0 / 9.0).toString(), impl.nativeWidget.attributes["aspect-ratio"])
        assertTrue(WindowAspectRatioHelper.matchesAspectRatio(impl.getWindowSize(), 16.0 / 9.0))

        impl.setAspectRatio(null)
        assertNull(impl.getAspectRatio())
        assertNull(impl.nativeWidget.attributes["aspect-ratio"])
        impl.dispose()
    }

    @Test
    fun windowAndLaunchSequencePropagateAlwaysOnTopAnd16By9ToAndroidBackend() {
        val factory = AndroidNativeFactory()
        GenericUI.initialize(factory)

        val window = Window(title = "Android Ratio", initialSize = Size(1280, 800))
        window.addChild(Container())
        window.show(factory)

        val impl = window.nativeImpl as AndroidWindowImpl
        window.setAlwaysOnTop(true)
        assertTrue(window.isAlwaysOnTop)
        assertTrue(impl.isAlwaysOnTop())
        assertEquals("true", impl.nativeWidget.attributes["always-on-top"])

        window.setAspectRatio(16, 9)
        assertEquals(Size(1280, 720), window.getWindowSize())
        window.setMinWindowSize(Size(640, 360))
        window.setWindowSize(Size(960, 900))
        assertEquals(Size(960, 540), window.getWindowSize())

        window.setWindowState(WindowState.MAXIMIZED)
        assertNull(impl.nativeWidget.attributes["aspect-ratio"])
        window.setWindowSize(Size(1920, 1200))
        assertEquals(Size(1920, 1200), window.getWindowSize())

        window.restore()
        assertEquals(WindowState.NORMAL, window.getWindowState())
        assertEquals(Size(960, 540), window.getWindowSize())
        assertEquals((16.0 / 9.0).toString(), impl.nativeWidget.attributes["aspect-ratio"])

        window.setFullscreen(true)
        assertTrue(window.isFullscreen())
        assertNull(impl.nativeWidget.attributes["aspect-ratio"])
        window.setWindowSize(Size(2560, 1600))
        assertEquals(Size(2560, 1600), window.getWindowSize())

        window.setFullscreen(false)
        assertFalse(window.isFullscreen())
        assertEquals(WindowState.NORMAL, window.getWindowState())
        assertEquals(Size(960, 540), window.getWindowSize())
        assertEquals((16.0 / 9.0).toString(), impl.nativeWidget.attributes["aspect-ratio"])

        window.dispose()

        class DummyAndroidFragment : Fragment()
        val descriptor = GenericUiAppDescriptor(
            appId = "com.example.androidcaster",
            appName = "Android Caster",
            windowTitle = "Android Caster",
            windowWidth = 1280,
            windowHeight = 800,
            windowAlwaysOnTop = true,
            windowAspectRatio = 16.0 / 9.0,
            mainFragmentClass = DummyAndroidFragment::class,
            mainFragmentFactory = { _ -> DummyAndroidFragment() }
        )
        val app = GenericUiLaunchSequence.launch(
            factory = factory,
            descriptor = descriptor,
            isSystemTarget = true
        )
        val mainImpl = (app.mainWindow as Window).nativeImpl as AndroidWindowImpl
        assertTrue(mainImpl.isAlwaysOnTop())
        assertEquals("true", mainImpl.nativeWidget.attributes["always-on-top"])
        assertEquals(16.0 / 9.0, mainImpl.getAspectRatio() ?: 0.0, 1e-6)
        assertEquals(Size(1280, 720), app.mainWindow.getWindowSize())
        app.dispose()
    }
}
