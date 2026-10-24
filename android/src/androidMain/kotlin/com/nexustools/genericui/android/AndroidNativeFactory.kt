package com.nexustools.genericui.android

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

    init {
        if (context != null) {
            AndroidViewHandle.activeContext = context
        }
        ImageLoader.activeNativeCodec = AndroidBitmapImageCodecImpl()
    }

    // Shell & Window
    override fun createApplication(): ApplicationNativeImpl<*> = AndroidApplicationImpl()
    override fun createWindow(): WindowNativeImpl<*> = AndroidWindowImpl()
    override fun createMenuBar(): MenuBarNativeImpl<*> = AndroidMenuBarImpl()
    override fun createMenu(): MenuNativeImpl<*> = AndroidMenuImpl()
    override fun createMenuItem(checkable: Boolean, radio: Boolean): MenuItemNativeImpl<*> = AndroidMenuItemImpl(checkable, radio)
    override fun createContextMenu(): ContextMenuNativeImpl<*> = AndroidContextMenuImpl()
    override fun createSplitPane(): SplitPaneNativeImpl<*> = AndroidSplitPaneImpl()

    // Dockable Panel System (GIMP / Photoshop Studio)
    override fun createDockWorkspace(): DockWorkspaceNativeImpl<*> = AndroidDockWorkspaceImpl()
    override fun createDockArea(): DockAreaNativeImpl<*> = AndroidDockAreaImpl()
    override fun createDockGroup(): DockGroupNativeImpl<*> = AndroidDockGroupImpl()
    override fun createDockPanel(): DockPanelNativeImpl<*> = AndroidDockPanelImpl()
    override fun createDockWindow(): DockWindowNativeImpl<*> = AndroidDockWindowImpl()
    override fun createToolPalette(): ToolPaletteNativeImpl<*> = AndroidToolPaletteImpl()

    // Layout
    override fun createContainer(classPrefix: String): ContainerNativeImpl<*> = AndroidContainerImpl(classPrefix)
    override fun createScrollView(): ScrollViewNativeImpl<*> = AndroidScrollViewImpl()
    override fun createDivider(): DividerNativeImpl<*> = AndroidDividerImpl()

    // Typography & Content
    override fun createText(tagName: String, classPrefix: String): TextNativeImpl<*> = AndroidTextImpl(tagName, classPrefix)
    override fun createMarkdownView(): MarkdownViewNativeImpl<*> = AndroidMarkdownViewImpl()
    override fun createImage(): ImageNativeImpl<*> = AndroidImageImpl()
    override fun createTable(): TableNativeImpl<*> = AndroidTableImpl()

    // Action & Form
    override fun createButton(classPrefix: String): ButtonNativeImpl<*> = AndroidButtonImpl(classPrefix)
    override fun createDropdown(): DropdownNativeImpl<*> = AndroidDropdownImpl()
    override fun createTextInput(multiline: Boolean): TextInputNativeImpl<*> = AndroidTextInputImpl(multiline)
    override fun createSelect(): SelectNativeImpl<*> = AndroidSelectImpl()
    override fun createCheckbox(switchStyle: Boolean, radioStyle: Boolean): CheckboxNativeImpl<*> = AndroidCheckboxImpl(switchStyle, radioStyle)
    override fun createRangeSlider(): RangeSliderNativeImpl<*> = AndroidRangeSliderImpl()

    // Surface, Tabs, Navigation, Feedback & Overlay
    override fun createCollapse(): CollapseNativeImpl<*> = AndroidCollapseImpl()
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
    override fun createPermissionManager(): PermissionManagerNativeImpl = AndroidPermissionManagerImpl()
    override fun createSystemTray(): SystemTrayNativeImpl = AndroidSystemTrayImpl()
    override fun createNotificationManager(): NotificationManagerNativeImpl = AndroidNotificationManagerImpl()
    override fun createNetworkMonitor(): NetworkMonitorNativeImpl = AndroidNetworkMonitorImpl()
}
