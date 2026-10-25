package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.layout.MobileSafeAreaTracker
import com.nexustools.genericui.mobile.system.MobilePipController
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * iOS `UIWindow` + root `UIViewController` implementation of [WindowNativeImpl],
 * integrating [MobileSafeAreaTracker] (Dynamic Island / Home Indicator / keyboard safe areas)
 * and [MobilePipController] (`AVPictureInPictureController`).
 *
 * Implements mobile `MenuBar` as a top [IosNavbarImpl] with the window title on the left,
 * [TitleBar] custom components on the right (to the left of the dropdown menu button), and a
 * right-aligned dropdown menu button ([mobileMenuBarDropdownButton]) that activates the `MenuBar`
 * as a menu ([mobileMenuBarPopupMenu]) on the right of the navbar.
 */
class IosWindowImpl : IosBaseContainerNativeImpl("UIWindow", "window"), WindowNativeImpl<IosNativeView> {
    override val supportsCustomTitleBar: Boolean = false
    override val supportsEmbeddedMenuBarInTitleBar: Boolean = false
    override val supportsMultiWindow: Boolean = false
    override val supportsNavbarMenuBar: Boolean = true
    override val supportsMobileNavbarMenuBar: Boolean = true

    val safeAreaTracker: MobileSafeAreaTracker = MobileSafeAreaTracker()
    val pipController: MobilePipController = MobilePipController()

    private var titleText: String = "GenericUI"
    private var windowSize: Size = Size(393, 852)
    private var windowState: WindowState = WindowState.NORMAL
    private var fullscreenFlag: Boolean = false

    private var titleBarInstructions: TitleBar? = null
    private var menuBarImpl: MenuBarNativeImpl<*>? = null
    private var navBarImpl: NativeImpl<*>? = null
    private val explicitNavBarBaselineItems = mutableListOf<NativeImpl<*>>()
    private var bottomNavImpl: NativeImpl<*>? = null

    var synthesizedMenuBarNavbar: IosNavbarImpl? = null
        private set
    var mobileMenuBarDropdownButton: IosDropdownImpl? = null
        private set
    var mobileMenuBarPopupMenu: IosContextMenuImpl? = null
        private set

    val activeNavbar: IosNavbarImpl?
        get() = (navBarImpl as? IosNavbarImpl) ?: synthesizedMenuBarNavbar

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.metadata["navigationItem.title"] = title
        rebuildMobileMenuBarNavbar()
    }

    override fun getTitle(): String = titleText

    override fun setWindowSize(size: Size) {
        windowSize = size
        nativeWidget.frame = Rect(0, 0, size.width, size.height)
        onResizeListener?.invoke(WindowConfiguration(widthPx = size.width, heightPx = size.height))
    }

    override fun getWindowSize(): Size = windowSize

    override fun setWindowState(state: WindowState) {
        windowState = state
    }

    override fun getWindowState(): WindowState = windowState

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        fullscreenFlag = fullscreen
        nativeWidget.metadata["prefersStatusBarHidden"] = fullscreen.toString()
        nativeWidget.metadata["prefersHomeIndicatorAutoHidden"] = fullscreen.toString()
        rebuildMobileMenuBarNavbar()
    }

    override fun isFullscreen(): Boolean = fullscreenFlag

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        titleBarInstructions = titleBar
        rebuildMobileMenuBarNavbar()
    }

    fun getTitleBarInstructions(): TitleBar? = titleBarInstructions

    override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {
        (this.menuBarImpl as? IosMenuBarImpl)?.onStructureChanged = null
        this.menuBarImpl = menuBarImpl
        (menuBarImpl as? IosMenuBarImpl)?.onStructureChanged = {
            rebuildMobileMenuBarNavbar()
        }
        rebuildMobileMenuBarNavbar()
    }

    override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {
        this.navBarImpl?.let { removeChildNative(it) }
        this.navBarImpl = navBarImpl
        explicitNavBarBaselineItems.clear()
        if (navBarImpl is IosNavbarImpl) {
            explicitNavBarBaselineItems.addAll(navBarImpl.items)
        }
        if (navBarImpl != null && !fullscreenFlag) {
            addChildNative(navBarImpl, 0)
        }
        rebuildMobileMenuBarNavbar()
    }

    override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {
        this.bottomNavImpl?.let { removeChildNative(it) }
        this.bottomNavImpl = bottomNavImpl
        if (bottomNavImpl != null) {
            addChildNative(bottomNavImpl, -1)
        }
    }

    fun rebuildMobileMenuBarNavbar() {
        val mb = menuBarImpl as? IosMenuBarImpl
        if (mb != null && !fullscreenFlag) {
            val navbar: IosNavbarImpl = if (navBarImpl is IosNavbarImpl) {
                synthesizedMenuBarNavbar?.let { removeChildNative(it) }
                synthesizedMenuBarNavbar = null
                navBarImpl as IosNavbarImpl
            } else {
                synthesizedMenuBarNavbar ?: IosNavbarImpl().also { synthesizedMenuBarNavbar = it }
            }

            if (navbar.nativeWidget.superview !== nativeWidget) {
                addChildNative(navbar, 0)
            }
            navbar.setVisible(true)

            val effectiveTitle = if (titleBarInstructions?.showWindowTitle == false) "" else titleText
            navbar.setBrandTitle(effectiveTitle)
            navbar.nativeWidget.metadata["titlePlacement"] = "left"
            navbar.nativeWidget.metadata["brandPlacement"] = "left"

            navbar.clearChildrenNative()
            for (baselineItem in explicitNavBarBaselineItems) {
                navbar.addChildNative(baselineItem, -1)
            }

            val tb = titleBarInstructions
            val customComponents = if (tb != null) {
                tb.leadingComponents + tb.centerComponents + tb.trailingComponents
            } else {
                emptyList()
            }
            for (comp in customComponents) {
                val compImpl = comp.nativeImpl ?: if (comp.isVisible) comp.ensureNativeImpl() else null
                if (compImpl != null) {
                    compImpl.addClass("navbar-custom-item")
                    compImpl.setAttribute("navbarPlacement", "right-before-menu")
                    compImpl.setAttribute("positionRelativeToMenuButton", "left")
                    navbar.addChildNative(compImpl, -1)
                }
            }

            val dropdown = IosDropdownImpl()
            dropdown.setClass("dropdown dropdown-toggle mobile-menubar-trigger ms-auto")
            dropdown.setLabel("☰")
            dropdown.setAttribute("navbarPlacement", "right")
            dropdown.setAttribute("role", "menubar-dropdown")

            val popupMenu = IosContextMenuImpl()
            popupMenu.setVisible(false)

            val dropdownEntries = mutableListOf<Pair<String, String>>()
            val actionByKey = linkedMapOf<String, IosBaseNativeImpl>()
            var itemCounter = 0

            fun collectMenuNode(node: NativeImpl<*>, parentMenuLabel: String?) {
                when (node) {
                    is IosMenuImpl -> {
                        popupMenu.sourceMenus.add(node)
                        val menuLabel = node.getLabel()
                        val nextPrefix = when {
                            parentMenuLabel.isNullOrBlank() -> menuLabel
                            menuLabel.isBlank() -> parentMenuLabel
                            else -> "$parentMenuLabel > $menuLabel"
                        }
                        for (child in node.items) {
                            collectMenuNode(child, nextPrefix)
                        }
                    }
                    is DividerNativeImpl<*> -> {
                        val sepId = "sep_${itemCounter++}"
                        dropdownEntries.add(sepId to "---")
                        val proxyDivider = IosDividerImpl()
                        popupMenu.addChildNative(proxyDivider, -1)
                    }
                    is MenuItemNativeImpl<*> -> {
                        val itemId = "item_${itemCounter++}"
                        val itemLabel = node.getLabel()
                        val displayLabel = if (parentMenuLabel.isNullOrBlank()) {
                            itemLabel
                        } else {
                            "$parentMenuLabel > $itemLabel"
                        }
                        dropdownEntries.add(itemId to displayLabel)
                        val checkable = (node as? IosMenuItemImpl)?.checkable ?: false
                        val radio = (node as? IosMenuItemImpl)?.radio ?: false
                        val proxyItem = IosMenuItemImpl(checkable = checkable, radio = radio)
                        proxyItem.setLabel(itemLabel)
                        proxyItem.setIcon(node.getIcon())
                        proxyItem.setShortcut(node.getShortcut())
                        proxyItem.setChecked(node.isChecked())
                        proxyItem.setEnabled(node.isEnabled())
                        proxyItem.setAttribute("menuPath", displayLabel)
                        if (node is IosBaseNativeImpl) {
                            proxyItem.setOnClick {
                                node.dispatchClick()
                                proxyItem.setChecked(node.isChecked())
                            }
                            actionByKey[itemId] = node
                            actionByKey[itemLabel] = node
                            actionByKey[displayLabel] = node
                        }
                        popupMenu.addChildNative(proxyItem, -1)
                    }
                }
            }

            for (topChild in mb.items) {
                collectMenuNode(topChild, null)
            }

            dropdown.setItems(dropdownEntries)
            dropdown.setOnItemSelected { selectedKey ->
                val target = actionByKey[selectedKey]
                    ?: selectedKey.toIntOrNull()?.let { idx ->
                        dropdownEntries.getOrNull(idx)?.let { actionByKey[it.first] }
                    }
                if (target != null && target.isEnabled()) {
                    target.dispatchClick()
                    popupMenu.hide()
                }
            }
            dropdown.setOnClick {
                popupMenu.showAt(dropdown, 0, 0)
            }

            navbar.addChildNative(dropdown, -1)
            mobileMenuBarDropdownButton = dropdown
            mobileMenuBarPopupMenu = popupMenu
        } else {
            synthesizedMenuBarNavbar?.let { removeChildNative(it) }
            synthesizedMenuBarNavbar = null
            mobileMenuBarDropdownButton = null
            mobileMenuBarPopupMenu = null

            val explicitNavbar = navBarImpl as? IosNavbarImpl
            if (explicitNavbar != null) {
                if (fullscreenFlag) {
                    removeChildNative(explicitNavbar)
                } else {
                    if (explicitNavbar.nativeWidget.superview !== nativeWidget) {
                        addChildNative(explicitNavbar, 0)
                    }
                    explicitNavbar.clearChildrenNative()
                    for (baselineItem in explicitNavBarBaselineItems) {
                        explicitNavbar.addChildNative(baselineItem, -1)
                    }
                }
            }
        }
    }

    override fun enterPictureInPicture(config: PipConfig): Boolean {
        pipController.configure(config)
        pipController.onPictureInPictureModeChanged(true)
        return true
    }

    override fun exitPictureInPicture() {
        pipController.onPictureInPictureModeChanged(false)
    }

    override fun isInPictureInPicture(): Boolean = pipController.isInPictureInPictureMode

    override fun showWindow() {
        setVisible(true)
        bringToFront()
        onFocusListener?.invoke(true)
    }

    override fun closeWindow() {
        val allow = onCloseRequestedListener?.invoke() ?: true
        if (allow) {
            setVisible(false)
        }
    }

    override fun configureAsToolModalWindow(
        ownerWindow: WindowNativeImpl<*>?,
        showInTaskbar: Boolean,
        minimizable: Boolean,
        alwaysOnTopOfOwner: Boolean
    ) {
        setAttribute("tool-modal", "true")
        setAttribute("show-in-taskbar", showInTaskbar.toString())
        setAttribute("minimizable", minimizable.toString())
        setAttribute("always-on-top-of-owner", alwaysOnTopOfOwner.toString())
    }

    override fun bringToFront() {
        setAttribute("broughtToFront", "true")
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) { onResizeListener = listener }
    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) { onFocusListener = listener }
    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) { onCloseRequestedListener = listener }
}
