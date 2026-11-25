package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import com.nexustools.genericui.core.AbstractContainer
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.layout.MobileSafeAreaTracker
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowAspectRatioHelper
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Android `Window` / `GenericUiFragmentActivity` implementation of [WindowNativeImpl].
 *
 * Implements mobile `TitleBar` and `MenuBar` as an optional top [AndroidNavbarImpl] (`isTitleBarRequired = false`).
 * When neither `TitleBar` nor `MenuBar` is set, no navbar is shown at the top of the view hierarchy.
 * When a `TitleBar` or `MenuBar` is set, renders a top [AndroidNavbarImpl] with the window title on the left,
 * [TitleBar] custom components detached from any body parent and placed in the navbar, and (when a `MenuBar` is set)
 * a right-aligned dropdown menu button ([mobileMenuBarDropdownButton]) that activates [mobileMenuBarPopupMenu].
 */
class AndroidWindowImpl : AndroidBaseContainerNativeImpl("android.view.Window", "window"), WindowNativeImpl<AndroidViewHandle> {
    override val supportsCustomTitleBar: Boolean = true
    override val supportsEmbeddedMenuBarInTitleBar: Boolean = false
    override val supportsMultiWindow: Boolean = false
    override val supportsNavbarMenuBar: Boolean = true
    override val supportsMobileNavbarMenuBar: Boolean = true

    override fun getTitleCapabilities(): TitleCapabilities = TitleCapabilities(
        isSupported = true,
        isEmulated = true,
        supportsCustomTitleBar = true,
        supportsCustomComponents = true,
        supportsLeadingComponents = true,
        supportsCenterComponents = true,
        supportsTrailingComponents = true,
        supportsEmbeddedMenuBar = true,
        supportsNavbarMenuBar = true,
        supportsWindowIcon = true,
        supportsWindowTitle = true,
        supportsMinimizeButton = false,
        supportsMaximizeButton = false,
        supportsCloseButton = false,
        supportsCollapseToggle = false,
        supportsControlsPlacement = false,
        supportsCustomHeight = false,
        supportsSystemAeroOrBackdrop = false,
        isToolWindowTitleBar = false,
        isTitleBarRequired = false,
        titleBarRendersAsNavbar = true,
        menuBarRendersAsNavbar = true,
        menuBarMergesIntoTitleNavbar = true
    )

    val safeAreaTracker: MobileSafeAreaTracker = MobileSafeAreaTracker()

    private var titleText: String = "GenericUI"
    private var windowSize: Size = Size(412, 915)
    private var minWindowSize: Size? = null
    private var windowState: WindowState = WindowState.NORMAL
    private var stateBeforeFullscreen: WindowState = WindowState.NORMAL
    private var fullscreenFlag: Boolean = false
    private var alwaysOnTopFlag: Boolean = false
    private var aspectRatioValue: Double? = null

    init {
        nativeWidget.attributes["always-on-top"] = "false"
        nativeWidget.isWindowRootHost = true
    }

    private var titleBarInstructions: TitleBar? = null
    private var menuBarImpl: MenuBarNativeImpl<*>? = null
    private var navBarImpl: NativeImpl<*>? = null
    private val explicitNavBarBaselineItems = mutableListOf<NativeImpl<*>>()
    private var bottomNavImpl: NativeImpl<*>? = null

    var synthesizedMenuBarNavbar: AndroidNavbarImpl? = null
        private set
    var mobileMenuBarDropdownButton: AndroidDropdownImpl? = null
        private set
    var mobileMenuBarPopupMenu: AndroidContextMenuImpl? = null
        private set

    val activeNavbar: AndroidNavbarImpl?
        get() = (navBarImpl as? AndroidNavbarImpl) ?: synthesizedMenuBarNavbar

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null
    internal var onChromeMutated: (() -> Unit)? = null

    override fun ensureAndroidView(context: Context?): View? {
        val view = ensureOwnAndroidView(context)
        val vg = (nativeWidget.childHostViewGroup ?: (view as? ViewGroup))
        if (vg != null && context != null) {
            runCatching {
                for (childImpl in childNativeImpls.toList()) {
                    val childView = (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(context)
                    val childHandle = childImpl.nativeWidget as? AndroidViewHandle
                    val parentView = childView?.parent as? View
                    val isHostedInActivity = parentView?.getTag(GenericUiFragmentActivity.TAG_ACTIVITY_CONTENT_HOST) == true
                    if (childView != null && childHandle != null && !isHostedInActivity && childView.parent !== vg) {
                        (childView.parent as? ViewGroup)?.removeView(childView)
                        val lp = if (vg is AndroidFlexContainerView) {
                            AndroidFlexContainerView.buildLayoutParamsFor(childHandle)
                        } else {
                            ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        }
                        vg.addView(childView, lp)
                    }
                }
            }
        }
        syncAndroidView()
        return view
    }

    override fun setTitle(title: String) {
        titleText = title
        AndroidFileBrowserDialogImpl.activeHostActivity?.title = title
        rebuildMobileMenuBarNavbar()
        onChromeMutated?.invoke()
    }

    override fun getTitle(): String = titleText

    private fun isWindowedNormal(): Boolean =
        windowState == WindowState.NORMAL && !fullscreenFlag

    private fun syncAspectRatioAttribute() {
        val ratio = aspectRatioValue
        if (ratio != null && isWindowedNormal()) {
            setAttribute("aspect-ratio", ratio.toString())
        } else {
            setAttribute("aspect-ratio", null)
        }
    }

    private fun clampSizeForCurrentState(size: Size): Size {
        val ratio = aspectRatioValue
        val min = minWindowSize
        return if (ratio != null && isWindowedNormal()) {
            WindowAspectRatioHelper.clampClientSize(size, ratio, min)
        } else if (min != null) {
            WindowAspectRatioHelper.clampClientSize(size, null, min)
        } else {
            size
        }
    }

    override fun setWindowSize(size: Size) {
        val resolved = clampSizeForCurrentState(size)
        windowSize = resolved
        nativeWidget.bounds = Rect(0, 0, resolved.width, resolved.height)
        onResizeListener?.invoke(
            WindowConfiguration(
                widthPx = resolved.width,
                heightPx = resolved.height,
                windowState = windowState,
                isFullscreen = fullscreenFlag
            )
        )
    }

    override fun getWindowSize(): Size = windowSize

    override fun getMinWindowSize(): Size? = minWindowSize

    override fun setMinWindowSize(minSize: Size?) {
        minWindowSize = minSize
        if (minSize != null) {
            setAttribute("min-width", minSize.width.toString())
            setAttribute("min-height", minSize.height.toString())
            val adjusted = clampSizeForCurrentState(windowSize)
            if (adjusted != windowSize) {
                setWindowSize(adjusted)
            }
        } else {
            setAttribute("min-width", null)
            setAttribute("min-height", null)
        }
    }

    override fun setAlwaysOnTop(alwaysOnTop: Boolean) {
        alwaysOnTopFlag = alwaysOnTop
        setAttribute("always-on-top", alwaysOnTop.toString())
    }

    override fun isAlwaysOnTop(): Boolean = alwaysOnTopFlag

    override fun setAspectRatio(aspectRatio: Double?) {
        WindowAspectRatioHelper.validateAspectRatio(aspectRatio)
        aspectRatioValue = aspectRatio
        syncAspectRatioAttribute()
        if (aspectRatio != null && isWindowedNormal()) {
            val clamped = clampSizeForCurrentState(windowSize)
            if (clamped != windowSize) {
                windowSize = clamped
                nativeWidget.bounds = Rect(0, 0, clamped.width, clamped.height)
            }
        }
    }

    override fun getAspectRatio(): Double? = aspectRatioValue

    override fun setWindowState(state: WindowState) {
        if (state == WindowState.FULLSCREEN) {
            if (!fullscreenFlag) {
                setFullscreen(true)
            }
            return
        }
        windowState = state
        if (fullscreenFlag) {
            stateBeforeFullscreen = state
            setFullscreen(false)
            return
        }
        syncAspectRatioAttribute()
        if (state == WindowState.NORMAL && aspectRatioValue != null) {
            val clamped = clampSizeForCurrentState(windowSize)
            if (clamped != windowSize) {
                windowSize = clamped
                nativeWidget.bounds = Rect(0, 0, clamped.width, clamped.height)
            }
        }
    }

    override fun getWindowState(): WindowState = windowState

    private var fullscreenElementImpl: NativeImpl<*>? = null

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (fullscreen && !fullscreenFlag) {
            if (windowState != WindowState.FULLSCREEN) {
                stateBeforeFullscreen = windowState
            }
            windowState = WindowState.FULLSCREEN
        } else if (!fullscreen && fullscreenFlag) {
            if (windowState == WindowState.FULLSCREEN) {
                windowState = if (stateBeforeFullscreen == WindowState.FULLSCREEN) {
                    WindowState.NORMAL
                } else {
                    stateBeforeFullscreen
                }
            }
        }
        fullscreenFlag = fullscreen
        if (!fullscreen) {
            fullscreenElementImpl = null
            nativeWidget.attributes.remove("fullscreenElement")
        }
        nativeWidget.attributes["fullscreen"] = fullscreen.toString()
        nativeWidget.attributes["cutoutMode"] = cutoutMode.name
        syncAspectRatioAttribute()
        if (!fullscreen && windowState == WindowState.NORMAL && aspectRatioValue != null) {
            val clamped = clampSizeForCurrentState(windowSize)
            if (clamped != windowSize) {
                windowSize = clamped
                nativeWidget.bounds = Rect(0, 0, clamped.width, clamped.height)
            }
        }
        rebuildMobileMenuBarNavbar()
        onChromeMutated?.invoke()
    }

    override fun isFullscreen(): Boolean = fullscreenFlag

    override fun setFullscreenElement(elementImpl: NativeImpl<*>?, cutoutMode: DisplayCutoutMode) {
        fullscreenElementImpl = elementImpl
        if (elementImpl != null) {
            nativeWidget.attributes["fullscreenElement"] = elementImpl.nativeWidget.toString()
        } else {
            nativeWidget.attributes.remove("fullscreenElement")
        }
        setFullscreen(elementImpl != null, cutoutMode)
        onChromeMutated?.invoke()
    }

    override fun getFullscreenElement(): NativeImpl<*>? = fullscreenElementImpl

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        if (this.titleBarInstructions != titleBar) {
            this.titleBarInstructions?.setHostEmbedsComponents(false)
        }
        titleBarInstructions = titleBar
        titleBar?.setHostEmbedsComponents(true)
        rebuildMobileMenuBarNavbar()
        onChromeMutated?.invoke()
    }

    fun getTitleBarInstructions(): TitleBar? = titleBarInstructions

    override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {
        (this.menuBarImpl as? AndroidMenuBarImpl)?.onStructureChanged = null
        this.menuBarImpl = menuBarImpl
        (menuBarImpl as? AndroidMenuBarImpl)?.onStructureChanged = {
            rebuildMobileMenuBarNavbar()
            onChromeMutated?.invoke()
        }
        rebuildMobileMenuBarNavbar()
        onChromeMutated?.invoke()
    }

    override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {
        this.navBarImpl?.let { removeChildNative(it) }
        this.navBarImpl = navBarImpl
        explicitNavBarBaselineItems.clear()
        if (navBarImpl is AndroidNavbarImpl) {
            explicitNavBarBaselineItems.addAll(navBarImpl.items)
        }
        if (navBarImpl != null && !fullscreenFlag) {
            addChildNative(navBarImpl, 0)
        }
        rebuildMobileMenuBarNavbar()
        onChromeMutated?.invoke()
    }

    override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {
        this.bottomNavImpl?.let { removeChildNative(it) }
        this.bottomNavImpl = bottomNavImpl
        if (bottomNavImpl != null) addChildNative(bottomNavImpl, -1)
        onChromeMutated?.invoke()
    }

    fun rebuildMobileMenuBarNavbar() {
        val mb = menuBarImpl as? AndroidMenuBarImpl
        val tb = titleBarInstructions
        if ((mb != null || tb != null) && !fullscreenFlag) {
            val navbar: AndroidNavbarImpl = if (navBarImpl is AndroidNavbarImpl) {
                synthesizedMenuBarNavbar?.let { removeChildNative(it) }
                synthesizedMenuBarNavbar = null
                navBarImpl as AndroidNavbarImpl
            } else {
                synthesizedMenuBarNavbar ?: AndroidNavbarImpl().also { synthesizedMenuBarNavbar = it }
            }

            if (navbar.nativeWidget.parentHandle !== nativeWidget) {
                addChildNative(navbar, 0)
            }
            navbar.setVisible(true)

            val effectiveTitle = if (tb?.showWindowTitle == false) "" else titleText
            navbar.setBrandTitle(effectiveTitle)
            if (tb?.showWindowIcon != false && !tb?.iconPath.isNullOrBlank()) {
                navbar.nativeWidget.attributes["brandIcon"] = tb!!.iconPath!!
            } else {
                navbar.nativeWidget.attributes.remove("brandIcon")
            }
            navbar.nativeWidget.attributes["titlePlacement"] = "left"
            navbar.nativeWidget.attributes["brandPlacement"] = "left"

            navbar.clearChildrenNative()
            for (baselineItem in explicitNavBarBaselineItems) {
                navbar.addChildNative(baselineItem, -1)
            }

            if (tb != null) {
                tb.setHostEmbedsComponents(true)
                val customComponents = tb.leadingComponents + tb.centerComponents + tb.trailingComponents
                for (comp in customComponents) {
                    (comp.parent as? AbstractContainer)?.removeChild(comp)
                    comp.mountedInTitleBar = true
                    val compImpl = comp.nativeImpl ?: if (comp.isVisible) comp.ensureNativeImpl() else null
                    if (compImpl != null) {
                        (compImpl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                        compImpl.addClass("navbar-custom-item")
                        compImpl.setAttribute("mountedInTitleBar", "true")
                        compImpl.setAttribute("navbarPlacement", if (mb != null) "right-before-menu" else "right")
                        compImpl.setAttribute("positionRelativeToMenuButton", if (mb != null) "left" else "none")
                        navbar.addChildNative(compImpl, -1)
                    }
                }
            }

            if (mb != null) {
                val dropdown = AndroidDropdownImpl()
                dropdown.setClass("dropdown dropdown-toggle mobile-menubar-trigger ms-auto")
                dropdown.setLabel("☰")
                dropdown.setAttribute("navbarPlacement", "right")
                dropdown.setAttribute("role", "menubar-dropdown")

                val popupMenu = AndroidContextMenuImpl()
                popupMenu.setVisible(false)

                val dropdownEntries = mutableListOf<Pair<String, String>>()
                val actionByKey = linkedMapOf<String, AndroidBaseNativeImpl>()
                var itemCounter = 0

                fun collectMenuNode(node: NativeImpl<*>, parentMenuLabel: String?) {
                    when (node) {
                        is AndroidMenuImpl -> {
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
                            val proxyDivider = AndroidDividerImpl()
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
                            val checkable = (node as? AndroidMenuItemImpl)?.checkable ?: false
                            val radio = (node as? AndroidMenuItemImpl)?.radio ?: false
                            val proxyItem = AndroidMenuItemImpl(checkable = checkable, radio = radio)
                            proxyItem.setLabel(itemLabel)
                            proxyItem.setIcon(node.getIcon())
                            proxyItem.setShortcut(node.getShortcut())
                            proxyItem.setChecked(node.isChecked())
                            proxyItem.setEnabled(node.isEnabled())
                            proxyItem.setAttribute("menuPath", displayLabel)
                            if (node is AndroidBaseNativeImpl) {
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
                mobileMenuBarDropdownButton = null
                mobileMenuBarPopupMenu = null
            }
        } else {
            synthesizedMenuBarNavbar?.let { removeChildNative(it) }
            synthesizedMenuBarNavbar = null
            mobileMenuBarDropdownButton = null
            mobileMenuBarPopupMenu = null

            val explicitNavbar = navBarImpl as? AndroidNavbarImpl
            if (explicitNavbar != null) {
                if (fullscreenFlag) {
                    removeChildNative(explicitNavbar)
                } else {
                    if (explicitNavbar.nativeWidget.parentHandle !== nativeWidget) {
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

    override fun showWindow() {
        setVisible(true)
        bringToFront()
        onFocusListener?.invoke(true)
    }

    override fun closeWindow() {
        val allow = onCloseRequestedListener?.invoke() ?: true
        if (allow) {
            setVisible(false)
            AndroidFileBrowserDialogImpl.activeHostActivity?.finish()
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
