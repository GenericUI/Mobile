package com.nexustools.genericui.android

import com.nexustools.genericui.component.layout.LayoutFactory
import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.mobile.layout.MobileLayoutFactory
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.nativeimpl.action.StyledButtonNativeImpl
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.DialogWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.dialog.StandardDialogsNativeImpl
import com.nexustools.genericui.nativeimpl.dock.*
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import com.nexustools.genericui.nativeimpl.form.RangeSliderNativeImpl
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import com.nexustools.genericui.nativeimpl.form.TextInputNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.layout.StyledContainerNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.*
import com.nexustools.genericui.nativeimpl.system.*
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * Android View / Material3 / OpenGL ES / Vulkan / Storage Access Framework implementation of [NativeFactory].
 *
 * Uses [GenericUiFragmentActivity] (`AppCompatActivity`) to host `Fragment` 1:1 with GenericUI's `Window`,
 * [AndroidFileBrowserDialogImpl] (`ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`)
 * to launch Android's native system file/folder browser, `RecyclerView` + `MobileLazyViewportPool` for true
 * lazy loading/unloading ([AndroidLazyListImpl], [AndroidLazyGridImpl], [AndroidTreeViewImpl]), `Choreographer`
 * VBlank scheduling with `deltaMs`, and [AndroidBitmapImageCodecImpl] for [ImageLoader].
 */
class AndroidNativeFactory(
    val context: android.content.Context? = null
) : NativeFactory {
    override val backendName: String = "android"
    override val backendTarget: BackendTarget = BackendTarget.Android
    override val layoutFactory: LayoutFactory = MobileLayoutFactory

    override val defaultTitleCapabilities: com.nexustools.genericui.window.TitleCapabilities =
        com.nexustools.genericui.window.TitleCapabilities(
            isEmulated = true,
            supportsNavbarMenuBar = true,
            supportsWindowIcon = false,
            supportsMinimizeButton = false,
            supportsMaximizeButton = false,
            supportsCloseButton = false,
            supportsControlsPlacement = false,
            isTitleBarRequired = false,
            titleBarRendersAsNavbar = true,
            menuBarRendersAsNavbar = true,
            menuBarMergesIntoTitleNavbar = true
        )
    override val supportsMultipleWindows: Boolean = false
    override val supportsToolModalWindows: Boolean = false
    override val supportsServices: Boolean = true
    override val supportsSystemTray: Boolean = false

    override fun supportsTcp(): Boolean = true
    override fun supportsUdp(): Boolean = true

    init {
        if (context != null) {
            AndroidViewHandle.activeContext = context
            AndroidPreferenceStoreImpl.applicationContext = context.applicationContext ?: context
        }
        ImageLoader.activeNativeCodec = AndroidBitmapImageCodecImpl()
        AndroidApplicationImpl.installSystemThemeDetector()
    }

    // Shell & Window
    override fun createApplication(): ApplicationNativeImpl<*> = AndroidApplicationImpl()
    override fun createWindow(): WindowNativeImpl<*> = AndroidWindowImpl()
    override fun createMenuBar(): MenuBarNativeImpl<*> = AndroidMenuBarImpl()
    override fun createMenu(): MenuNativeImpl<*> = AndroidMenuImpl()
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = AndroidMenuItemImpl(checkable, radio)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = AndroidContextMenuImpl()

    // Dockable Panel System (GIMP / Photoshop Studio)
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = AndroidDockWorkspaceImpl()
    override fun createDockArea(): DockAreaNativeImpl<*> = AndroidDockAreaImpl()
    override fun createDockGroup(): DockGroupNativeImpl<*> = AndroidDockGroupImpl()
    override fun createDockPanel(): DockPanelNativeImpl<*> = AndroidDockPanelImpl()
    override fun createDockWindow(): DockWindowNativeImpl<*> = AndroidDockWindowImpl()
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = AndroidToolPaletteImpl()

    // Layout & Printable Book Documents
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = AndroidContainerImpl(classPrefix)
    override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
        AndroidStyledContainerImpl(classPrefix)
    override fun createBookContainer(): BookContainerNativeImpl<*> = AndroidBookContainerImpl()
    override fun createScrollView(): ScrollViewNativeImpl<*> = AndroidScrollViewImpl()
    override fun createDivider(): DividerNativeImpl<*> = AndroidDividerImpl()

    // Typography & Content
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = AndroidTextImpl(tagName, classPrefix)
    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = AndroidMarkdownViewImpl()
    override fun createImage(): ImageNativeImpl<*> = AndroidImageImpl()
    override fun createTable(): TableNativeImpl<*> = AndroidTableImpl()

    // Action & Form
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> = AndroidButtonImpl(classPrefix)
    override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> = AndroidStyledButtonImpl(classPrefix)
    override fun createDropdown(): DropdownNativeImpl<*> = AndroidDropdownImpl()
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = AndroidTextInputImpl(multiline)
    override fun createSelect(): SelectNativeImpl<*> = AndroidSelectImpl()
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = AndroidCheckboxImpl(switchStyle, radioStyle)
    override fun createRangeSlider(): RangeSliderNativeImpl<*> = AndroidRangeSliderImpl()

    // Surface, Tabs, Navigation, Feedback & Overlay
    override fun createTab(closable: Boolean): TabNativeImpl<*> = AndroidTabImpl(closable)
    override fun createTabContainer(): TabContainerNativeImpl<*> = AndroidTabContainerImpl()
    override fun createNavbar(): NavbarNativeImpl<*> = AndroidNavbarImpl()
    override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = AndroidBottomNavigationBarImpl()
    override fun createProgressBar(): ProgressBarNativeImpl<*> = AndroidProgressBarImpl()
    override fun createSpinner(): SpinnerNativeImpl<*> = AndroidSpinnerImpl()
    override fun createPopover(): PopoverNativeImpl<*> = AndroidPopoverImpl()

    // Dialogs & Native File/Folder Browser
    override fun createDialogWindow(): DialogWindowNativeImpl<*> = AndroidDialogWindowImpl()
    override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = AndroidFileBrowserDialogImpl()
    override fun createStandardDialogs(): StandardDialogsNativeImpl = AndroidStandardDialogsImpl()

    // Lazy Virtualized Lists, Grids & Trees
    override fun createLazyList(): LazyListNativeImpl<*> = AndroidLazyListImpl()
    override fun createLazyGrid(): LazyGridNativeImpl<*> = AndroidLazyGridImpl()
    override fun createTreeView(): TreeViewNativeImpl<*> = AndroidTreeViewImpl()

    // System Services
    override fun createUiTimerScheduler(): UiTimerNativeImpl = AndroidUiTimerImpl()
    override fun createPermissionManager(): PermissionManagerNativeImpl = AndroidPermissionManagerImpl()
    override fun createSystemTray(): SystemTrayNativeImpl = AndroidSystemTrayImpl()
    override fun createNotificationManager(): NotificationManagerNativeImpl = AndroidNotificationManagerImpl()
    override fun createNetworkMonitor(): NetworkMonitorNativeImpl = AndroidNetworkMonitorImpl()
    override fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl =
        AndroidPreferenceStoreImpl(appName, context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext)
    override fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AndroidUserDirectoryNativeImpl(
            appName = appName,
            subdirectory = subdirectory,
            isCache = false,
            context = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        )
    override fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AndroidUserDirectoryNativeImpl(
            appName = appName,
            subdirectory = subdirectory,
            isCache = true,
            context = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        )
    override fun createFileSystem(): FileSystemNativeImpl =
        AndroidFileSystemNativeImpl(
            context = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        )
    override fun createDatabase(
        databaseName: String,
        filePath: String?,
        version: Int
    ): DatabaseNativeImpl = AndroidDatabaseNativeImpl(
        databaseName = databaseName,
        filePath = filePath ?: "$databaseName.db",
        version = version,
        context = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
    )
    override fun createResourceResolver(): com.nexustools.genericui.resources.ResourceResolverNativeImpl =
        AndroidResourceResolverImpl(context)

    // Async Networking (TCP, UDP, HTTP/HTTPS, WebSocket)
    override fun createTcpSocket(): TcpSocketNativeImpl = AndroidTcpSocketNativeImpl()
    override fun createUdpSocket(): UdpSocketNativeImpl = AndroidUdpSocketNativeImpl()
    override fun createHttpClient(): HttpClientNativeImpl = AndroidHttpClientNativeImpl()
    override fun createWebSocket(): WebSocketNativeImpl = AndroidWebSocketNativeImpl()
}
