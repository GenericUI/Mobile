package com.nexustools.genericui.android

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.enums.system.PermissionStatus
import com.nexustools.genericui.enums.system.PermissionType
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageFrame
import com.nexustools.genericui.jvm.JvmDatabaseBase
import com.nexustools.genericui.jvm.JvmFileSystemBase
import com.nexustools.genericui.jvm.JvmHttpUrlConnectionClient
import com.nexustools.genericui.jvm.JvmImageCodecBase
import com.nexustools.genericui.jvm.JvmPreferenceStoreBase
import com.nexustools.genericui.jvm.JvmResourceResolverBase
import com.nexustools.genericui.jvm.JvmRfc6455WebSocketNativeImpl
import com.nexustools.genericui.jvm.JvmStreamFileReadableNativeImpl
import com.nexustools.genericui.jvm.JvmStreamFileWritableNativeImpl
import com.nexustools.genericui.jvm.JvmTcpSocketNativeImpl
import com.nexustools.genericui.jvm.JvmUdpSocketNativeImpl
import com.nexustools.genericui.jvm.JvmUiThreadDispatcherBase
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.window.TitleBar
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidJvmInheritanceAndWidgetsTest {

    @Test
    fun androidSystemServicesExtendSharedJvmBases() {
        val codec = AndroidBitmapImageCodecImpl()
        val tcp = AndroidTcpSocketNativeImpl()
        val udp = AndroidUdpSocketNativeImpl()
        val http = AndroidHttpClientNativeImpl()
        val ws = AndroidWebSocketNativeImpl()
        val dispatcher = AndroidUiThreadDispatcherNativeImpl()
        val fs = AndroidFileSystemNativeImpl()
        val readable = AndroidFileReadableNativeImpl()
        val writable = AndroidFileWritableNativeImpl()
        val prefs = AndroidPreferenceStoreImpl(appName = "TestApp")
        val db = AndroidDatabaseNativeImpl(filePath = ":memory:")
        val res = AndroidResourceResolverImpl()

        assertTrue(codec is JvmImageCodecBase)
        assertTrue(tcp is JvmTcpSocketNativeImpl)
        assertTrue(udp is JvmUdpSocketNativeImpl)
        assertTrue(http is JvmHttpUrlConnectionClient)
        assertTrue(ws is JvmRfc6455WebSocketNativeImpl)
        assertTrue(dispatcher is JvmUiThreadDispatcherBase)
        assertTrue(fs is JvmFileSystemBase)
        assertTrue(readable is JvmStreamFileReadableNativeImpl)
        assertTrue(writable is JvmStreamFileWritableNativeImpl)
        assertTrue(prefs is JvmPreferenceStoreBase)
        assertFalse(prefs.useBinaryPlist)
        assertTrue(db is JvmDatabaseBase)
        assertTrue(res is JvmResourceResolverBase)

        codec.dispose()
        tcp.dispose()
        udp.dispose()
        http.dispose()
        ws.dispose()
        fs.dispose()
        readable.dispose()
        writable.dispose()
        prefs.dispose()
        db.dispose()
    }

    @Test
    fun systemTrayThrowsUnsupportedAndOsServicesDegradeCleanlyInHostTests() {
        val tray = AndroidSystemTrayImpl()
        assertFalse(tray.isSupported)
        assertFailsWith<UnsupportedOperationException> { tray.setIcon("icon.png") }
        assertFailsWith<UnsupportedOperationException> { tray.setTooltip("Tip") }
        assertFailsWith<UnsupportedOperationException> { tray.setBadgeText("3") }
        assertFailsWith<UnsupportedOperationException> { tray.setContextMenu(null) }
        assertFailsWith<UnsupportedOperationException> { tray.show() }
        assertFailsWith<UnsupportedOperationException> { tray.hide() }
        tray.dispose()

        val monitor = AndroidNetworkMonitorImpl()
        monitor.setOnConnectivityChanged { }
        monitor.isOnline()
        monitor.dispose()

        val perms = AndroidPermissionManagerImpl()
        assertEquals(PermissionStatus.NOT_APPLICABLE, perms.checkPermission(PermissionType.SYSTEM_TRAY))
        perms.dispose()

        val book = AndroidBookContainerImpl()
        book.setDocumentTitle("Host Test Book")
        assertTrue(book.printBook("Host Test Book", PrintPageSize.A4, PrintPageOrientation.PORTRAIT, 12f))
        assertEquals("Host Test Book", book.nativeWidget.attributes["data-last-printed-title"])

        var chosenColor: RgbaColor? = null
        val initial = RgbaColor(12, 34, 56, 200)
        AndroidStandardDialogsImpl().showColorChooser(null, "Pick Color", initial) { chosenColor = it }
        assertEquals(initial, chosenColor)
    }

    @Test
    fun tabLazyListImageTreeViewAndToolPaletteStateWiringWorks() {
        val tab = AndroidTabImpl(initialClosable = true)
        var closeCount = 0
        var editedTitle = ""
        tab.setTitleNative("Doc")
        tab.setDirtyNative(true)
        tab.setPinnedNative(false)
        tab.setEditableNative(true)
        tab.setBadgeNative("4", "danger")
        tab.setOnCloseClicked { closeCount++ }
        tab.setOnTitleEdited { editedTitle = it }
        tab.dispatchCloseClicked()
        tab.dispatchTitleEdited("Renamed")
        assertEquals(1, closeCount)
        assertEquals("Renamed", editedTitle)
        assertEquals("4", tab.badgeTextVal)

        val img = AndroidImageImpl()
        val filter = CssFilter(grayscale = 0.5f, contrast = 1.2f)
        img.setCssFilter(filter)
        assertEquals(filter, img.getCssFilter())
        img.setPlaceholderUrl("res://drawable/loading_custom")
        assertEquals("res://drawable/loading_custom", img.getPlaceholderUrl())

        val lazy = AndroidLazyListImpl()
        var scrolled = false
        lazy.setOrientation(Orientation.VERTICAL)
        lazy.setEstimatedItemExtent(40f, overscanItems = 2)
        lazy.setOnViewportScrollChanged { _, _ -> scrolled = true }
        lazy.setItemCount(10) { idx -> AndroidTextImpl().apply { setText("Row $idx") } }
        lazy.scrollToIndex(3)
        assertTrue(scrolled)

        val tree = AndroidTreeViewImpl()
        var doubleClickedNode: TreeNode<*>? = null
        var contextNode: TreeNode<*>? = null
        val root = TreeNode(id = "root", label = "Root", data = "root", isExpanded = true)
        tree.setOnNodeDoubleClicked { doubleClickedNode = it }
        tree.setOnNodeContextMenu { node, _, _ -> contextNode = node }
        tree.setRootNodes(listOf(root))
        assertTrue(tree.mountedRowIndices.contains(0))

        val palette = AndroidToolPaletteImpl()
        var selectedTool = ""
        var swappedPair: Pair<RgbaColor, RgbaColor>? = null
        palette.setOnToolSelected { selectedTool = it }
        palette.setOnColorsSwapped { fg, bg -> swappedPair = fg to bg }
        palette.setTools(
            listOf(ToolPaletteItem(id = "brush", name = "Brush", iconSvgOrPath = "brush")),
            activeToolId = null
        )
        palette.setActiveToolId("brush")
        assertEquals("brush", selectedTool)
        assertEquals("brush", palette.nativeWidget.attributes["activeToolId"])
        assertEquals(null, doubleClickedNode)
        assertEquals(null, contextNode)
        assertEquals(null, swappedPair)

        val panel = AndroidDockPanelImpl()
        panel.setTitle("Layers")
        val titleBar = TitleBar(showWindowTitle = false, heightPx = 36, iconPath = "res://drawable/layers")
        panel.applyTitleBarInstructions(titleBar)
        assertEquals(titleBar, panel.getTitleBarInstructions())
        assertEquals("Layers", panel.getTitle())

        val tabContainer = AndroidTabContainerImpl()
        val tabContent = AndroidContainerImpl()
        tabContainer.addTabNative(tab, tabContent, -1)
        assertEquals(0, tabContainer.getSelectedTabIndex())
        tabContainer.removeTabNative(0)

        val tempFile = File.createTempFile("genericui_writable_test", ".bin")
        try {
            val fileWritable = AndroidFileWritableNativeImpl(tempFile)
            val payload = byteArrayOf(1, 2, 3, 4, 5)
            fileWritable.write(payload, 0, payload.size)
            assertEquals(5L, fileWritable.bytesWritten)
            fileWritable.dispose()
        } finally {
            tempFile.delete()
        }

        val frame = ImageFrame(frameIndex = 0, buffer = ImageBuffer(2, 2), durationMs = 100)
        assertEquals(0, frame.frameIndex)
    }
}
