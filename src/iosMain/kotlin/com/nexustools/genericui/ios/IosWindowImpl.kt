@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.shell.MenuBar
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.system.BackendTarget
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.mobile.layout.MobileSafeAreaTracker
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowAspectRatioHelper
import com.nexustools.genericui.window.WindowConfiguration
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIMenu
import platform.UIKit.UINavigationBar
import platform.UIKit.UINavigationItem
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowLevelAlert
import platform.UIKit.UIWindowLevelNormal
import platform.UIKit.systemBackgroundColor
import kotlin.math.min

/**
 * Shared real UIKit `UIWindow` + `GenericUiRootViewController` implementation of [WindowNativeImpl].
 *
 * Subclassed by [com.nexustools.genericui.ipados.IpadosWindowImpl] to layer `UIWindowScene`,
 * `UISceneSizeRestrictions`, native `UIMenuSystem` desktop-style menu bar, and iPadOS
 * `UINavigationItem` item groups on top without duplicating core window chrome or layout logic.
 */
open class IosWindowImpl(
    val rootViewController: GenericUiRootViewController = GenericUiRootViewController()
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(rootViewController.hostView, rootViewController),
    componentClassPrefix = "window"
), WindowNativeImpl<UikitViewHandle> {

    val safeAreaTracker: MobileSafeAreaTracker get() = rootViewController.safeAreaTracker

    protected val contentHostView: UikitFlexContainerView = UikitFlexContainerView(UikitEnvironment.mainScreenBounds())
    protected val navigationBar: UINavigationBar = UINavigationBar(CGRectMake(0.0, 0.0, 390.0, 44.0))
    protected val navigationItem: UINavigationItem = UINavigationItem(title = "")

    var uiWindow: UIWindow? = null
        protected set

    private var windowTitle: String = ""
    private var windowSize: Size = readInitialScreenSize()
    private var minWindowSize: Size? = null
    private var windowState: WindowState = WindowState.NORMAL
    private var stateBeforeFullscreen: WindowState = WindowState.NORMAL
    private var fullscreen: Boolean = false
    private var alwaysOnTopFlag: Boolean = false
    private var aspectRatioValue: Double? = null
    private var fullscreenElement: NativeImpl<*>? = null
    private var activeTitleBar: TitleBar? = null
    private var attachedMenuBarImpl: MenuBarNativeImpl<*>? = null
    private var attachedNavBarImpl: NativeImpl<*>? = null
    private var attachedBottomNavImpl: NativeImpl<*>? = null
    private var resizeListener: ((WindowConfiguration) -> Unit)? = null
    private var focusListener: ((Boolean) -> Unit)? = null
    private var closeListener: (() -> Boolean)? = null
    private var windowFocusedState: Boolean = false
    private var fullscreenOverlayView: UIView? = null
    private var fullscreenOriginalSuperview: UIView? = null
    private var fullscreenOriginalIndex: Int = -1

    init {
        if (UikitEnvironment.registeredRootViewController == null) {
            UikitEnvironment.registeredRootViewController = rootViewController
        }
        navigationBar.setItems(listOf(navigationItem), animated = false)
        navigationBar.hidden = true
        contentHostView.isWindowRootHost = true
        rootViewController.hostView.addSubview(navigationBar)
        rootViewController.hostView.addSubview(contentHostView)
        rootViewController.view.backgroundColor = defaultSystemBackgroundColor()

        rootViewController.onLayoutSubviews = { layoutWindowRegions() }
        rootViewController.onViewportResized = { w, h -> handleViewportResized(w, h) }
        rootViewController.onKeyDown = { key -> onKeyDownListener?.invoke(key) }
        rootViewController.onKeyUp = { key -> onKeyUpListener?.invoke(key) }
    }

    override fun contentContainerView(): UIView = contentHostView

    override val supportsCustomTitleBar: Boolean get() = false
    override val supportsEmbeddedMenuBarInTitleBar: Boolean get() = false
    override val supportsMultiWindow: Boolean get() = false
    override val supportsNavbarMenuBar: Boolean get() = true
    override val supportsMobileNavbarMenuBar: Boolean get() = true

    override fun getTitleCapabilities(): TitleCapabilities = TitleCapabilities(
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

    override fun setTitle(title: String) {
        windowTitle = title
        rootViewController.title = title
        navigationItem.title = title
        updateNavigationChromeVisibility()
    }

    override fun getTitle(): String = windowTitle

    protected fun isWindowedNormal(): Boolean =
        windowState == WindowState.NORMAL && !fullscreen

    protected fun applyAlwaysOnTopToUiWindow() {
        val win = uiWindow ?: return
        win.windowLevel = if (alwaysOnTopFlag) UIWindowLevelAlert else UIWindowLevelNormal
    }

    protected open fun clampWindowSizeForState(target: Size, preferWidth: Boolean = true): Size {
        val ratio = aspectRatioValue
        return if (ratio != null && isWindowedNormal()) {
            WindowAspectRatioHelper.clampClientSize(
                size = target,
                aspectRatio = ratio,
                minSize = minWindowSize,
                preferWidth = preferWidth
            )
        } else {
            clampToMinSize(target)
        }
    }

    protected fun applyResolvedWindowSize(resolved: Size, notifyResize: Boolean) {
        windowSize = resolved
        uiWindow?.setFrame(CGRectMake(0.0, 0.0, resolved.width.toDouble(), resolved.height.toDouble()))
        rootViewController.hostView.setFrame(CGRectMake(0.0, 0.0, resolved.width.toDouble(), resolved.height.toDouble()))
        layoutWindowRegions()
        if (notifyResize) {
            resizeListener?.invoke(buildWindowConfiguration())
        }
    }

    override fun setWindowSize(size: Size) {
        val clamped = clampWindowSizeForState(size)
        applyResolvedWindowSize(clamped, notifyResize = true)
    }

    override fun getWindowSize(): Size = windowSize

    override fun getMinWindowSize(): Size? = minWindowSize

    override fun setMinWindowSize(minSize: Size?) {
        minWindowSize = minSize
        if (minSize != null) {
            val adjusted = clampWindowSizeForState(windowSize)
            if (adjusted != windowSize) {
                setWindowSize(adjusted)
            }
        }
    }

    override fun setAlwaysOnTop(alwaysOnTop: Boolean) {
        alwaysOnTopFlag = alwaysOnTop
        applyAlwaysOnTopToUiWindow()
    }

    override fun isAlwaysOnTop(): Boolean = alwaysOnTopFlag

    override fun setAspectRatio(aspectRatio: Double?) {
        WindowAspectRatioHelper.validateAspectRatio(aspectRatio)
        aspectRatioValue = aspectRatio
        if (aspectRatio != null && isWindowedNormal()) {
            val clamped = clampWindowSizeForState(windowSize)
            if (clamped != windowSize) {
                applyResolvedWindowSize(clamped, notifyResize = false)
            }
        }
    }

    override fun getAspectRatio(): Double? = aspectRatioValue

    protected fun clampToMinSize(target: Size): Size {
        val minSize = minWindowSize ?: return target
        return Size(
            width = maxOf(target.width, minSize.width),
            height = maxOf(target.height, minSize.height)
        )
    }

    override fun setWindowState(state: WindowState) {
        if (state == WindowState.FULLSCREEN) {
            if (!fullscreen) {
                setFullscreen(true)
            }
            return
        }
        windowState = state
        if (fullscreen) {
            stateBeforeFullscreen = state
            setFullscreen(false)
            return
        }
        rootViewController.setNeedsStatusBarAppearanceUpdate()
        if (state == WindowState.NORMAL && aspectRatioValue != null) {
            val clamped = clampWindowSizeForState(windowSize)
            if (clamped != windowSize) {
                applyResolvedWindowSize(clamped, notifyResize = false)
            }
        }
    }

    override fun getWindowState(): WindowState = windowState

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        if (fullscreen && !this.fullscreen) {
            if (windowState != WindowState.FULLSCREEN) {
                stateBeforeFullscreen = windowState
            }
            windowState = WindowState.FULLSCREEN
        } else if (!fullscreen && this.fullscreen) {
            if (windowState == WindowState.FULLSCREEN) {
                windowState = if (stateBeforeFullscreen == WindowState.FULLSCREEN) {
                    WindowState.NORMAL
                } else {
                    stateBeforeFullscreen
                }
            }
        }
        this.fullscreen = fullscreen
        rootViewController.setNeedsStatusBarAppearanceUpdate()
        updateNavigationChromeVisibility()
        if (!fullscreen && windowState == WindowState.NORMAL && aspectRatioValue != null) {
            val clamped = clampWindowSizeForState(windowSize)
            if (clamped != windowSize) {
                applyResolvedWindowSize(clamped, notifyResize = false)
            }
        } else {
            layoutWindowRegions()
        }
        if (fullscreen) {
            resizeListener?.invoke(buildWindowConfiguration())
        }
    }

    override fun isFullscreen(): Boolean = fullscreen

    override fun setFullscreenElement(elementImpl: NativeImpl<*>?, cutoutMode: DisplayCutoutMode) {
        val prevView = fullscreenOverlayView
        if (prevView != null) {
            val origParent = fullscreenOriginalSuperview
            prevView.removeFromSuperview()
            if (origParent != null && origParent !== prevView) {
                val subCount = origParent.subviews.size
                if (fullscreenOriginalIndex in 0..subCount) {
                    origParent.insertSubview(prevView, atIndex = fullscreenOriginalIndex.toLong())
                } else {
                    origParent.addSubview(prevView)
                }
            }
            fullscreenOverlayView = null
            fullscreenOriginalSuperview = null
            fullscreenOriginalIndex = -1
        }
        fullscreenElement = elementImpl
        if (elementImpl != null && elementImpl !== this) {
            val view = (elementImpl.nativeWidget as? UikitViewHandle)?.uiView
            if (view != null && view !== rootViewController.hostView && view !== contentHostView) {
                val origSuper = view.superview
                fullscreenOriginalSuperview = origSuper
                fullscreenOriginalIndex = origSuper?.subviews?.indexOf(view) ?: -1
                view.setFrame(rootViewController.hostView.bounds)
                rootViewController.hostView.addSubview(view)
                fullscreenOverlayView = view
            }
        }
        setFullscreen(elementImpl != null, cutoutMode)
    }

    override fun getFullscreenElement(): NativeImpl<*>? = fullscreenElement

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        activeTitleBar = titleBar
        titleBar?.setHostEmbedsComponents(getTitleCapabilities().canEmbedComponentsInTitleBar)
        if (titleBar != null && titleBar.showWindowTitle) {
            navigationItem.title = windowTitle
        }
        updateNavigationChromeVisibility()
    }

    override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {
        attachedMenuBarImpl = menuBarImpl
        if (menuBarImpl is IosMenuBarImpl) {
            val menu = menuBarImpl.buildRootUiMenu(windowTitle.ifEmpty { "Menu" })
            val hamburgerBtn = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                setImage(UikitGeometryAndColorUtil.systemIconImage("menu"), forState = platform.UIKit.UIControlStateNormal)
                setTitle(if (UikitGeometryAndColorUtil.systemIconImage("menu") == null) "☰" else null, forState = platform.UIKit.UIControlStateNormal)
                showsMenuAsPrimaryAction = true
                this.menu = menu
            }
            navigationItem.rightBarButtonItem = UIBarButtonItem(customView = hamburgerBtn)
        } else {
            navigationItem.rightBarButtonItem = null
        }
        updateNavigationChromeVisibility()
    }

    override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {
        (attachedNavBarImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        attachedNavBarImpl = navBarImpl
        (navBarImpl?.nativeWidget as? UikitViewHandle)?.uiView?.let { barView ->
            rootViewController.hostView.addSubview(barView)
        }
        layoutWindowRegions()
    }

    override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {
        (attachedBottomNavImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        attachedBottomNavImpl = bottomNavImpl
        (bottomNavImpl?.nativeWidget as? UikitViewHandle)?.uiView?.let { barView ->
            rootViewController.hostView.addSubview(barView)
        }
        layoutWindowRegions()
    }

    override fun showWindow() {
        if (uiWindow == null) {
            val win = UIWindow(frame = CGRectMake(0.0, 0.0, windowSize.width.toDouble(), windowSize.height.toDouble()))
            win.rootViewController = rootViewController
            uiWindow = win
        }
        applyAlwaysOnTopToUiWindow()
        UikitEnvironment.registeredKeyWindow = uiWindow
        UikitEnvironment.registeredRootViewController = rootViewController
        if (UikitEnvironment.hasHostedUiApplication()) {
            uiWindow?.makeKeyAndVisible()
        } else {
            uiWindow?.hidden = false
        }
        if (!windowFocusedState) {
            windowFocusedState = true
            focusListener?.invoke(true)
        }
        layoutWindowRegions()
    }

    override fun closeWindow() {
        if (closeListener?.invoke() == false) return
        if (windowFocusedState) {
            windowFocusedState = false
            focusListener?.invoke(false)
        }
        uiWindow?.hidden = true
        if (UikitEnvironment.registeredKeyWindow === uiWindow) {
            UikitEnvironment.registeredKeyWindow = null
        }
    }

    override fun bringToFront() {
        if (UikitEnvironment.hasHostedUiApplication()) {
            uiWindow?.makeKeyAndVisible()
        } else {
            uiWindow?.hidden = false
        }
        if (!windowFocusedState) {
            windowFocusedState = true
            focusListener?.invoke(true)
        }
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) {
        resizeListener = listener
    }

    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) {
        focusListener = listener
    }

    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) {
        closeListener = listener
    }

    protected open fun updateNavigationChromeVisibility() {
        val caps = getTitleCapabilities()
        val hasTitleBar = activeTitleBar != null
        val hasMenuBar = attachedMenuBarImpl != null && caps.supportsNavbarMenuBar
        val needsNav = !fullscreen && caps.requiresChrome(hasTitleBar = hasTitleBar, hasMenuBar = hasMenuBar)
        navigationBar.hidden = !needsNav
        layoutWindowRegions()
    }

    protected open fun layoutWindowRegions() {
        val hostBounds = rootViewController.hostView.bounds
        val totalW = hostBounds.useContents { size.width }.takeIf { it > 0.0 } ?: windowSize.width.toDouble()
        val totalH = hostBounds.useContents { size.height }.takeIf { it > 0.0 } ?: windowSize.height.toDouble()
        val safeInsets = safeAreaTracker.combinedSafeInsets
        var topCursor = safeInsets.top.toDouble()
        var bottomCursor = totalH - safeInsets.bottom.toDouble()

        if (!navigationBar.hidden) {
            val navH = activeTitleBar?.heightPx?.toDouble() ?: 44.0
            navigationBar.setFrame(CGRectMake(0.0, topCursor, totalW, navH))
            topCursor += navH
        }
        (attachedNavBarImpl?.nativeWidget as? UikitViewHandle)?.uiView?.let { navView ->
            if (!navView.hidden) {
                val navH = 52.0
                navView.setFrame(CGRectMake(0.0, topCursor, totalW, navH))
                topCursor += navH
            }
        }
        (attachedBottomNavImpl?.nativeWidget as? UikitViewHandle)?.uiView?.let { bottomView ->
            if (!bottomView.hidden) {
                val barH = 56.0
                bottomView.setFrame(CGRectMake(0.0, (bottomCursor - barH).coerceAtLeast(topCursor), totalW, barH))
                bottomCursor = (bottomCursor - barH).coerceAtLeast(topCursor)
            }
        }
        val bodyH = (bottomCursor - topCursor).coerceAtLeast(0.0)
        contentHostView.setFrame(CGRectMake(0.0, topCursor, totalW, bodyH))
        fullscreenOverlayView?.setFrame(CGRectMake(0.0, 0.0, totalW, totalH))
    }

    protected open fun handleViewportResized(widthPt: Int, heightPt: Int) {
        if (widthPt <= 0 || heightPt <= 0) return
        val requested = Size(widthPt, heightPt)
        val ratio = aspectRatioValue
        val clamped = if (ratio != null && isWindowedNormal()) {
            val deltaClamped = WindowAspectRatioHelper.enforceResizeDelta(
                previousSize = windowSize,
                requestedSize = requested,
                aspectRatio = ratio,
                minSize = minWindowSize
            )
            clampWindowSizeForState(deltaClamped, preferWidth = deltaClamped.width >= requested.width)
        } else {
            clampWindowSizeForState(requested)
        }
        if (clamped.width != windowSize.width || clamped.height != windowSize.height) {
            windowSize = clamped
            if (clamped != requested) {
                uiWindow?.setFrame(CGRectMake(0.0, 0.0, clamped.width.toDouble(), clamped.height.toDouble()))
                rootViewController.hostView.setFrame(CGRectMake(0.0, 0.0, clamped.width.toDouble(), clamped.height.toDouble()))
            }
            layoutWindowRegions()
            resizeListener?.invoke(buildWindowConfiguration())
        }
    }

    protected fun buildWindowConfiguration(): WindowConfiguration {
        val scale = UikitEnvironment.mainScreenScale()
        val smallestDp = min(windowSize.width, windowSize.height)
        val theme = SystemThemeDetector.detectSystemTheme()
        return WindowConfiguration(
            widthPx = windowSize.width,
            heightPx = windowSize.height,
            smallestScreenWidthDp = smallestDp,
            densityScale = scale,
            themeMode = theme,
            windowState = windowState,
            isFullscreen = fullscreen
        )
    }

    private fun readInitialScreenSize(): Size =
        UikitEnvironment.mainScreenBounds().useContents {
            Size(size.width.toInt().coerceAtLeast(320), size.height.toInt().coerceAtLeast(480))
        }

    private fun defaultSystemBackgroundColor(): UIColor =
        if (SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK) {
            UIColor.blackColor
        } else {
            UIColor.systemBackgroundColor
        }
}
