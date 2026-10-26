package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeFactory
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
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
import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl
import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabContainerNativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.nativeimpl.overlay.PopoverNativeImpl
import com.nexustools.genericui.nativeimpl.shell.*
import com.nexustools.genericui.nativeimpl.surface.CollapseNativeImpl
import com.nexustools.genericui.nativeimpl.system.*
import com.nexustools.genericui.nativeimpl.typography.MarkdownViewNativeImpl
import com.nexustools.genericui.nativeimpl.typography.TextNativeImpl

/**
 * iOS UIKit / CoreGraphics / EAGL / MoltenVK / AVFoundation implementation of [NativeFactory].
 *
 * Uses `UIDocumentPickerViewController` in file and folder modes ([IosFileBrowserDialogImpl]),
 * `UICollectionView` + `MobileLazyViewportPool` for true lazy loading/unloading ([IosLazyListImpl],
 * [IosLazyGridImpl], [IosTreeViewImpl]), `CADisplayLink` VBlank scheduling with `deltaMs` ([IosCanvas2DImpl],
 * [IosGLCanvasImpl], [IosVKCanvasImpl]), and `ImageIO` / `CGImageSource` hooks for [ImageLoader].
 */
class IosNativeFactory : NativeFactory {
    override val backendName: String = "ios"

    init {
        ImageLoader.activeNativeCodec = IosImageIoCodecImpl()
    }

    // Shell & Window
    override fun createApplication(): ApplicationNativeImpl<*> = IosApplicationImpl()
    override fun createWindow(): WindowNativeImpl<*> = IosWindowImpl()
    override fun createMenuBar(): MenuBarNativeImpl<*> = IosMenuBarImpl()
    override fun createMenu(): MenuNativeImpl<*> = IosMenuImpl()
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = IosMenuItemImpl(checkable, radio)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = IosContextMenuImpl()
    override fun createSplitPane(): SplitPaneNativeImpl<*> = IosSplitPaneImpl()

    // Dockable Panel System (GIMP / Photoshop Studio)
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = IosDockWorkspaceImpl()
    override fun createDockArea(): DockAreaNativeImpl<*> = IosDockAreaImpl()
    override fun createDockGroup(): DockGroupNativeImpl<*> = IosDockGroupImpl()
    override fun createDockPanel(): DockPanelNativeImpl<*> = IosDockPanelImpl()
    override fun createDockWindow(): DockWindowNativeImpl<*> = IosDockWindowImpl()
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = IosToolPaletteImpl()

    // Layout & Printable Book Documents
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = IosContainerImpl(classPrefix)
    override fun createBookContainer(): BookContainerNativeImpl<*> = IosBookContainerImpl()
    override fun createScrollView(): ScrollViewNativeImpl<*> = IosScrollViewImpl()
    override fun createDivider(): DividerNativeImpl<*> = IosDividerImpl()

    // Typography & Content
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = IosTextImpl(tagName, classPrefix)
    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = IosMarkdownViewImpl()
    override fun createImage(): ImageNativeImpl<*> = IosImageImpl()
    override fun createTable(): TableNativeImpl<*> = IosTableImpl()

    // Action & Form
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> = IosButtonImpl(classPrefix)
    override fun createDropdown(): DropdownNativeImpl<*> = IosDropdownImpl()
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = IosTextInputImpl(multiline)
    override fun createSelect(): SelectNativeImpl<*> = IosSelectImpl()
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = IosCheckboxImpl(switchStyle, radioStyle)
    override fun createRangeSlider(): RangeSliderNativeImpl<*> = IosRangeSliderImpl()

    // Surface, Tabs, Navigation, Feedback & Overlay
    override fun createCollapse(): CollapseNativeImpl<*> = IosCollapseImpl()
    override fun createTab(closable: Boolean): TabNativeImpl<*> = IosTabImpl(closable)
    override fun createTabContainer(): TabContainerNativeImpl<*> = IosTabContainerImpl()
    override fun createNavbar(): NavbarNativeImpl<*> = IosNavbarImpl()
    override fun createBottomNavigationBar(): BottomNavigationBarNativeImpl<*> = IosBottomNavigationBarImpl()
    override fun createProgressBar(): ProgressBarNativeImpl<*> = IosProgressBarImpl()
    override fun createSpinner(): SpinnerNativeImpl<*> = IosSpinnerImpl()
    override fun createPopover(): PopoverNativeImpl<*> = IosPopoverImpl()

    // Dialogs & Native File/Folder Browser
    override fun createDialogWindow(): DialogWindowNativeImpl<*> = IosDialogWindowImpl()
    override fun createFileBrowserDialog(): FileBrowserDialogNativeImpl = IosFileBrowserDialogImpl()
    override fun createStandardDialogs(): StandardDialogsNativeImpl = IosStandardDialogsImpl()

    // Lazy Virtualized Lists, Grids & Trees
    override fun createLazyList(): LazyListNativeImpl<*> = IosLazyListImpl()
    override fun createLazyGrid(): LazyGridNativeImpl<*> = IosLazyGridImpl()
    override fun createTreeView(): TreeViewNativeImpl<*> = IosTreeViewImpl()

    // System Services
    override fun createUiTimerScheduler(): UiTimerNativeImpl = IosUiTimerImpl()
    override fun createPermissionManager(): PermissionManagerNativeImpl = IosPermissionManagerImpl()
    override fun createSystemTray(): SystemTrayNativeImpl = IosSystemTrayImpl()
    override fun createNotificationManager(): NotificationManagerNativeImpl = IosNotificationManagerImpl()
    override fun createNetworkMonitor(): NetworkMonitorNativeImpl = IosNetworkMonitorImpl()
    override fun createPreferenceStore(appName: String): PreferenceStoreNativeImpl = IosPreferenceStoreImpl(appName)
    override fun createConfigDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        IosUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, isCache = false)
    override fun createCacheDirectory(appName: String, subdirectory: String): UserDirectoryNativeImpl =
        IosUserDirectoryNativeImpl(appName = appName, subdirectory = subdirectory, isCache = true)
    override fun createResourceResolver(): com.nexustools.genericui.resources.ResourceResolverNativeImpl =
        IosResourceResolverImpl()
}
