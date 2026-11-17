@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.component.action.Button
import com.nexustools.genericui.component.layout.Column
import com.nexustools.genericui.component.shell.Menu
import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.MenuItem
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ios.GenericUiRootViewController
import com.nexustools.genericui.ios.IosFileSystemImpl
import com.nexustools.genericui.ios.IosMenuImpl
import com.nexustools.genericui.ios.IosMenuItemImpl
import com.nexustools.genericui.mobile.layout.MobileAdaptiveFragmentCoordinator
import com.nexustools.genericui.mobile.layout.MobileDeviceFormFactor
import com.nexustools.genericui.mobile.layout.MobileLayoutFactory
import com.nexustools.genericui.window.TitleBar
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIMenu
import platform.UIKit.UIPopoverPresentationController
import platform.UIKit.UIView
import platform.UIKit.gestureRecognizers
import platform.UIKit.interactions
import platform.UIKit.keyCommands
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class IpadosBackendTest {

    @Test
    fun factoryIdentifiesIpadosAndWiresMultiWindowAndMenuCapabilities() {
        val factory = IpadosNativeFactory()
        assertEquals(BackendTarget.IPadOS, factory.backendTarget)
        assertEquals("ipados", factory.backendName)
        assertTrue(factory.backendTarget.isApple)
        assertTrue(factory.backendTarget.isIosFamily)
        assertTrue(factory.supportsMultipleWindows)
        assertTrue(factory.supportsToolModalWindows)
        assertTrue(factory.supportsSystemMenuBar)
        assertFalse(factory.supportsSystemTray)
        assertTrue(factory.layoutFactory === MobileLayoutFactory)
        assertTrue(factory.createFileSystem() is IosFileSystemImpl)
    }

    @Test
    fun windowExposesExactTitleCapabilitiesAndBindsNavigationItemGroupsWithoutNavbarMenuBar() {
        val window = IpadosWindowImpl()
        val caps = window.getTitleCapabilities()
        assertTrue(caps.isSupported)
        assertFalse(caps.isEmulated)
        assertFalse(caps.isTitleBarRequired)
        assertTrue(caps.supportsCustomTitleBar)
        assertTrue(caps.supportsCustomComponents)
        assertTrue(caps.supportsLeadingComponents)
        assertTrue(caps.supportsCenterComponents)
        assertTrue(caps.supportsTrailingComponents)
        assertFalse(caps.supportsNavbarMenuBar)
        assertFalse(caps.menuBarRendersAsNavbar)
        assertFalse(caps.menuBarMergesIntoTitleNavbar)
        assertTrue(window.supportsMultiWindow)
        assertFalse(window.supportsNavbarMenuBar)

        window.setMinWindowSize(Size(480, 360))
        window.setMaxWindowSize(Size(1200, 900))
        window.setWindowSize(Size(320, 200))
        assertEquals(Size(480, 360), window.getWindowSize())
        window.setWindowSize(Size(1600, 1200))
        assertEquals(Size(1200, 900), window.getWindowSize())

        val titleBar = TitleBar().apply {
            addLeading(Button(text = "Sidebar"))
            addCenter(Text("Inspector"))
            addTrailing(Button(text = "Share"))
        }
        window.setTitle("iPad Studio")
        window.applyTitleBarInstructions(titleBar)
        window.dispose()
    }

    @Test
    fun menuBarFeedsIpadosMenuBarBridgeAsUiMenuHierarchy() {
        val menuBar = IpadosMenuBarImpl()
        val fileMenu = IosMenuImpl().apply { setLabel("File") }
        val newWindowItem = IosMenuItemImpl().apply {
            setLabel("New Window")
            setShortcut(KeyShortcut(key = "n", ctrlOrCmd = true))
        }
        fileMenu.addChildNative(newWindowItem, -1)
        menuBar.addChildNative(fileMenu, -1)

        val built = IpadosMenuBarBridge.lastBuiltMenus
        assertEquals(1, built.size)
        val firstUiMenu = built.first()
        assertEquals("File", firstUiMenu.title)
        assertEquals(1, firstUiMenu.children.size)

        val window = IpadosWindowImpl()
        window.setMenuBarNative(menuBar)
        assertTrue(IpadosMenuBarBridge.activeMenuBar === menuBar)
        window.dispose()
    }

    @Test
    fun pointerKeyboardInstallerRegistersKeyCommandsAndInteractionsAndSlideOverResponsiveSizing() {
        val controller = GenericUiRootViewController()
        IpadosPointerAndKeyboardInstaller.install(controller)
        assertFalse(controller.hostView.gestureRecognizers.isNullOrEmpty())
        val testView = UIView(frame = CGRectMake(0.0, 0.0, 120.0, 44.0))
        val interaction = IpadosPointerAndKeyboardInstaller.installCursorInteraction(testView)
        if (interaction != null) {
            assertTrue(interaction.view == testView || testView.interactions.isNotEmpty())
        } else {
            assertFalse(testView.gestureRecognizers.isNullOrEmpty())
        }

        IpadosPointerAndKeyboardInstaller.registerShortcut(
            controller = controller,
            shortcut = KeyShortcut(key = "k", ctrlOrCmd = true),
            title = "Quick Open"
        ) {}
        assertFalse(controller.keyCommands.isNullOrEmpty())

        val coordinator = MobileAdaptiveFragmentCoordinator()
        val slideOver = coordinator.evaluateConfiguration(widthPx = 640, heightPx = 1520, densityScale = 2f)
        assertEquals(640, slideOver.widthPx)
        assertEquals(320, slideOver.smallestScreenWidthDp)
        assertEquals(MobileDeviceFormFactor.PHONE, coordinator.formFactor)

        val fullIpad = coordinator.evaluateConfiguration(widthPx = 2048, heightPx = 2732, densityScale = 2f)
        assertEquals(2048, fullIpad.widthPx)
        assertEquals(1024, fullIpad.smallestScreenWidthDp)
        assertTrue(coordinator.formFactor.isLargeScreen)
    }

    @Test
    fun ipadosLauncherCreateRootViewControllerReturnsGenericUiRootViewController() {
        val descriptor = GenericUiAppDescriptor(
            appId = "com.nexustools.genericui.ipadosworkspace",
            appName = "IpadosWorkspace",
            windowTitle = "iPadOS Workspace"
        )
        val vc = IpadosLauncher.createRootViewController(descriptor)
        assertTrue(vc is GenericUiRootViewController)
        IpadosLauncher.activeApplication?.dispose()
    }
}
