@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleDatabaseNativeImpl
import com.nexustools.genericui.apple.AppleHttpClientNativeImpl
import com.nexustools.genericui.apple.AppleImageIoCodecNativeImpl
import com.nexustools.genericui.apple.AppleNetworkMonitorNativeImpl
import com.nexustools.genericui.apple.AppleNotificationManagerNativeImpl
import com.nexustools.genericui.apple.ApplePreferenceStoreNativeImpl
import com.nexustools.genericui.apple.AppleResourceResolverNativeImpl
import com.nexustools.genericui.apple.AppleTcpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUdpSocketNativeImpl
import com.nexustools.genericui.apple.AppleUiThreadDispatcherNativeImpl
import com.nexustools.genericui.apple.AppleUiTimerNativeImpl
import com.nexustools.genericui.apple.AppleUserDirectoryNativeImpl
import com.nexustools.genericui.apple.AppleWebSocketNativeImpl
import com.nexustools.genericui.component.layout.LayoutBackendTarget
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
import com.nexustools.genericui.nativeimpl.dock.DockAreaNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.dock.DockWorkspaceNativeImpl
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
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
import com.nexustools.genericui.nativeimpl.surface.StyledComponentNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.net.HttpClientNativeImpl
import com.nexustools.genericui.nativeimpl.net.TcpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.UdpSocketNativeImpl
import com.nexustools.genericui.nativeimpl.net.WebSocketNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ApplicationNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.shell.ContextMenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.nativeimpl.system.AnimationNativeImpl
import com.nexustools.genericui.nativeimpl.system.DatabaseNativeImpl
import com.nexustools.genericui.nativeimpl.system.NetworkMonitorNativeImpl
import com.nexustools.genericui.nativeimpl.system.NotificationManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PermissionManagerNativeImpl
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.nativeimpl.system.SystemTrayNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl
import com.nexustools.genericui.resources.ResourceResolverNativeImpl
import com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl
import com.nexustools.genericui.window.TitleCapabilities

/**
 * Shared UIKit / Apple `:apple` [NativeFactory] hosting both [BackendTarget.IOS] and
 * [BackendTarget.IPadOS].
 *
 * All Apple Foundation / Dispatch / Network / ImageIO / SQLite / NSUserDefaults services are
 * delegated directly to `:apple`, and every UI widget wraps genuine `platform.UIKit.*` views.
 */
open class IosNativeFactory(
    override val backendTarget: BackendTarget = BackendTarget.IOS
) : NativeFactory {

    override val backendName: String
        get() = backendTarget.id

    override val layoutFactory: LayoutFactory = MobileLayoutFactory

    override val displayDensityScale: Float
        get() = UikitEnvironment.mainScreenScale()

    override val defaultTitleCapabilities: TitleCapabilities
        get() = TitleCapabilities(
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

    override val supportsMultipleWindows: Boolean get() = false
    override val supportsToolModalWindows: Boolean get() = false
    override val supportsSystemTray: Boolean get() = false

    override fun supportsTcp(): Boolean = true
    override fun supportsUdp(): Boolean = true

    init {
        ImageLoader.activeNativeCodec = AppleImageIoCodecNativeImpl(httpClient = AppleHttpClientNativeImpl())
        IosApplicationImpl.installSystemThemeHooks()
    }

    // Shell & Window
    override fun createApplication(): ApplicationNativeImpl<*> = IosApplicationImpl()
    override fun createWindow(): WindowNativeImpl<*> = IosWindowImpl()
    override fun createMenuBar(): MenuBarNativeImpl<*> = IosMenuBarImpl()
    override fun createMenu(): MenuNativeImpl<*> = IosMenuImpl()
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> =
        IosMenuItemImpl(checkable, radio)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = IosContextMenuImpl()

    // Dockable Panel System
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = IosDockWorkspaceImpl()
    override fun createDockArea(): DockAreaNativeImpl<*> = IosDockAreaImpl()
    override fun createDockGroup(): DockGroupNativeImpl<*> = IosDockGroupImpl()
    override fun createDockPanel(): DockPanelNativeImpl<*> = IosDockPanelImpl()
    override fun createDockWindow(): DockWindowNativeImpl<*> = IosDockWindowImpl()
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = IosToolPaletteImpl()

    // Layout & Document
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> =
        IosContainerImpl(classPrefix = classPrefix)
    override fun createStyledContainer(classPrefix: String, tagName: String): StyledContainerNativeImpl<*> =
        IosStyledContainerImpl(
            classPrefix = classPrefix,
            tagName = tagName,
            layoutTarget = LayoutBackendTarget.fromBackendTarget(backendTarget)
        )
    override fun createStyledComponent(classPrefix: String, tagName: String): StyledComponentNativeImpl<*> =
        IosStyledComponentImpl(
            classPrefix = classPrefix,
            tagName = tagName,
            layoutTarget = LayoutBackendTarget.fromBackendTarget(backendTarget)
        )
    override fun createBookContainer(): BookContainerNativeImpl<*> = IosBookContainerImpl()
    override fun createScrollView(): ScrollViewNativeImpl<*> = IosScrollViewImpl()
    override fun createDivider(): DividerNativeImpl<*> = IosDividerImpl()

    // Typography & Content
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> =
        IosTextImpl(classPrefix = classPrefix)
    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = IosMarkdownViewImpl()
    override fun createImage(): ImageNativeImpl<*> = IosImageImpl()
    override fun createTable(): TableNativeImpl<*> = IosTableImpl()

    // Action & Form
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> =
        IosButtonImpl(classPrefix = classPrefix)
    override fun createStyledButton(classPrefix: String): StyledButtonNativeImpl<*> =
        IosStyledButtonImpl(
            classPrefix = classPrefix,
            layoutTarget = LayoutBackendTarget.fromBackendTarget(backendTarget)
        )
    override fun createDropdown(): DropdownNativeImpl<*> = IosDropdownImpl()
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> =
        IosTextInputImpl(initialMultiline = multiline)
    override fun createSelect(): SelectNativeImpl<*> = IosSelectImpl()
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> =
        IosCheckboxImpl(switchStyle = switchStyle, radioStyle = radioStyle)
    override fun createRangeSlider(): RangeSliderNativeImpl<*> = IosRangeSliderImpl()

    // Surface, Tabs, Navigation, Feedback & Overlay
    override fun createTab(closable: Boolean): TabNativeImpl<*> = IosTabImpl(closable = closable)
    override fun createTabContainer(): TabContainerNativeImpl<*> = IosTabContainerImpl()
    override fun createNavbar(): NavbarNativeImpl<*> = IosNavbarImpl()
    override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = IosBottomNavigationBarImpl()
    override fun createProgressBar(): ProgressBarNativeImpl<*> = IosProgressBarImpl()
    override fun createSpinner(): SpinnerNativeImpl<*> = IosSpinnerImpl()
    override fun createPopover(): PopoverNativeImpl<*> = IosPopoverImpl()

    // Dialogs & File/Folder Browser
    override fun createDialogWindow(): DialogWindowNativeImpl<*> = IosDialogWindowImpl()
    override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = IosFileBrowserDialogImpl()
    override fun createStandardDialogs(): StandardDialogsNativeImpl = IosStandardDialogsImpl()

    // Lazy Virtualized Collections
    override fun createLazyList(): LazyListNativeImpl<*> = IosLazyListImpl()
    override fun createLazyGrid(): LazyGridNativeImpl<*> = IosLazyGridImpl()
    override fun createTreeView(): TreeViewNativeImpl<*> = IosTreeViewImpl()

    // System Services & Async I/O / Database wired directly to :apple
    override fun createUiThreadDispatcher(): UiThreadDispatcherNativeImpl =
        AppleUiThreadDispatcherNativeImpl()

    override fun createUiTimerScheduler(): UiTimerNativeImpl =
        AppleUiTimerNativeImpl()

    override fun createAnimationScheduler(): AnimationNativeImpl =
        IosAnimationNativeImpl()

    override fun createPermissionManager(): PermissionManagerNativeImpl =
        IosPermissionManagerImpl()

    override fun createSystemTray(): SystemTrayNativeImpl =
        IosSystemTrayImpl()

    override fun createNotificationManager(): NotificationManagerNativeImpl =
        AppleNotificationManagerNativeImpl()

    override fun createNetworkMonitor(): NetworkMonitorNativeImpl =
        AppleNetworkMonitorNativeImpl()

    override fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl =
        ApplePreferenceStoreNativeImpl(
            appName = appName,
            userDirectory = createConfigDirectory(appName, "preferences")
        )

    override fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AppleUserDirectoryNativeImpl(
            appName = appName,
            subdirectory = subdirectory.ifEmpty { "config" },
            useCachesDirectory = false
        )

    override fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        AppleUserDirectoryNativeImpl(
            appName = appName,
            subdirectory = subdirectory.ifEmpty { "cache" },
            useCachesDirectory = true
        )

    override fun createFileSystem(): FileSystemNativeImpl =
        IosFileSystemImpl()

    override fun createDatabase(
        databaseName: String,
        filePath: String?,
        version: Int
    ): DatabaseNativeImpl {
        val resolvedPath = filePath ?: createConfigDirectory(databaseName, "database")
            .resolveFilePath("$databaseName.sqlite")
        return AppleDatabaseNativeImpl(
            databaseName = databaseName,
            filePath = resolvedPath,
            version = version
        )
    }

    override fun createResourceResolver(): ResourceResolverNativeImpl =
        AppleResourceResolverNativeImpl()

    // Async Networking wired directly to :apple
    override fun createTcpSocket(): TcpSocketNativeImpl =
        AppleTcpSocketNativeImpl()

    override fun createUdpSocket(): UdpSocketNativeImpl =
        AppleUdpSocketNativeImpl()

    override fun createHttpClient(): HttpClientNativeImpl =
        AppleHttpClientNativeImpl()

    override fun createWebSocket(): WebSocketNativeImpl =
        AppleWebSocketNativeImpl()
}
