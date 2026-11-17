@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ipados

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ios.GenericUiRootViewController
import com.nexustools.genericui.ios.IosWindowImpl
import com.nexustools.genericui.ios.UikitEnvironment
import com.nexustools.genericui.ios.UikitViewHandle
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.TitleCapabilities
import com.nexustools.genericui.window.WindowAspectRatioHelper
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIBarButtonItem
import platform.UIKit.UIBarButtonItemGroup
import platform.UIKit.UIInterfaceOrientationMaskAll
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.UIWindowSceneGeometryPreferencesIOS
import platform.objc.sel_registerName

/**
 * iPadOS multi-window `UIWindowScene`-aware implementation of [WindowNativeImpl].
 *
 * Key iPadOS differentiators over base [IosWindowImpl]:
 * - [supportsMultiWindow] = `true`, bound to live [UIWindowScene] + [UISceneSizeRestrictions]
 * - [DEFAULT_TITLE_CAPABILITIES] sets `supportsNavbarMenuBar = false`, `menuBarRendersAsNavbar = false`,
 *   `menuBarMergesIntoTitleNavbar = false`, `supportsCustomComponents = true`,
 *   `supportsLeadingComponents = true`, `supportsCenterComponents = true`,
 *   `supportsTrailingComponents = true`
 * - Binds `TitleBar` custom component slots to `UINavigationItem.leadingItemGroups`,
 *   `centerItemGroups`, and `trailingItemGroups`
 * - Feeds `MenuBar` into [IpadosMenuBarBridge] (`UIMenuSystem.mainSystem`) instead of an
 *   emulated hamburger navbar button.
 */
class IpadosWindowImpl(
    rootController: GenericUiRootViewController = GenericUiRootViewController()
) : IosWindowImpl(rootController) {

    var attachedWindowScene: UIWindowScene? = null
        private set

    private var maxWindowSize: Size? = null
    private var attachedIpadosMenuBar: IpadosMenuBarImpl? = null

    init {
        windowsByController[rootViewController] = this
        rootViewController.onBuildMenus = { builder ->
            IpadosMenuBarBridge.populateMenuBuilder(builder)
        }
        IpadosPointerAndKeyboardInstaller.install(rootViewController)
    }

    override val supportsCustomTitleBar: Boolean get() = true
    override val supportsEmbeddedMenuBarInTitleBar: Boolean get() = false
    override val supportsMultiWindow: Boolean get() = true
    override val supportsNavbarMenuBar: Boolean get() = false
    override val supportsMobileNavbarMenuBar: Boolean get() = false

    override fun getTitleCapabilities(): TitleCapabilities = DEFAULT_TITLE_CAPABILITIES

    fun attachToWindowScene(scene: UIWindowScene, window: UIWindow) {
        attachedWindowScene = scene
        uiWindow = window
        applyAlwaysOnTopToUiWindow()
        applySizeRestrictionsToScene(scene)
        scene.coordinateSpace.bounds.useContents {
            handleViewportResized(size.width.toInt(), size.height.toInt())
        }
        IpadosSceneDelegate.registerWindowForScene(scene, this)
    }

    override fun clampWindowSizeForState(target: Size, preferWidth: Boolean): Size {
        val minSize = getMinWindowSize()
        if (!isWindowedNormal()) {
            return if (isFullscreen()) target else clampToMinSize(target)
        }
        val bounded = clampBetweenMinMax(target)
        val ratio = getAspectRatio() ?: return bounded
        var clamped = WindowAspectRatioHelper.clampClientSize(
            size = bounded,
            aspectRatio = ratio,
            minSize = minSize,
            preferWidth = preferWidth
        )
        val maxSize = maxWindowSize
        if (maxSize != null) {
            if (clamped.width > maxSize.width) {
                clamped = WindowAspectRatioHelper.clampClientSize(
                    size = Size(maxSize.width, clamped.height),
                    aspectRatio = ratio,
                    minSize = minSize,
                    preferWidth = true
                )
            }
            if (clamped.height > maxSize.height) {
                clamped = WindowAspectRatioHelper.clampClientSize(
                    size = Size(clamped.width, maxSize.height),
                    aspectRatio = ratio,
                    minSize = minSize,
                    preferWidth = false
                )
            }
        }
        return clamped
    }

    override fun setWindowSize(size: Size) {
        super.setWindowSize(size)
    }

    override fun setMinWindowSize(minSize: Size?) {
        super.setMinWindowSize(minSize)
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
    }

    fun getMaxWindowSize(): Size? = maxWindowSize

    fun setMaxWindowSize(maxSize: Size?) {
        maxWindowSize = maxSize
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
        if (maxSize != null && isWindowedNormal()) {
            val current = getWindowSize()
            val clamped = clampWindowSizeForState(current)
            if (clamped != current) {
                setWindowSize(clamped)
            }
        }
    }

    override fun setAspectRatio(aspectRatio: Double?) {
        super.setAspectRatio(aspectRatio)
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
    }

    override fun setWindowState(state: WindowState) {
        super.setWindowState(state)
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
    }

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        super.setFullscreen(fullscreen, cutoutMode)
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
    }

    fun notifySceneGeometryChanged(widthPt: Int, heightPt: Int) {
        handleViewportResized(widthPt, heightPt)
    }

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        super.applyTitleBarInstructions(titleBar)
        if (titleBar == null) {
            navigationItem.leadingItemGroups = emptyList<Any>()
            navigationItem.centerItemGroups = emptyList<Any>()
            navigationItem.trailingItemGroups = emptyList<Any>()
        } else {
            navigationItem.leadingItemGroups = buildBarGroups(titleBar.leadingComponents)
            navigationItem.centerItemGroups = buildBarGroups(titleBar.centerComponents)
            navigationItem.trailingItemGroups = buildBarGroups(titleBar.trailingComponents)
        }
        updateNavigationChromeVisibility()
    }

    override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {
        val ipadosBar = menuBarImpl as? IpadosMenuBarImpl
        attachedIpadosMenuBar = ipadosBar
        navigationItem.rightBarButtonItem = null
        IpadosMenuBarBridge.registerAndRebuild(ipadosBar)
        IpadosMenuBarBridge.configureMainMenuSystemIfAvailable()
        updateNavigationChromeVisibility()
    }

    override fun showWindow() {
        val scene = attachedWindowScene ?: UikitEnvironment.safeSharedApplication()?.connectedScenes
            ?.firstOrNull { it is UIWindowScene } as? UIWindowScene
        if (uiWindow == null) {
            val size = getWindowSize()
            val win = if (scene != null) {
                UIWindow(windowScene = scene).apply {
                    setFrame(CGRectMake(0.0, 0.0, size.width.toDouble(), size.height.toDouble()))
                }
            } else {
                UIWindow(frame = CGRectMake(0.0, 0.0, size.width.toDouble(), size.height.toDouble()))
            }
            win.rootViewController = rootViewController
            uiWindow = win
            if (scene != null) {
                attachedWindowScene = scene
            }
        }
        applyAlwaysOnTopToUiWindow()
        attachedWindowScene?.let { applySizeRestrictionsToScene(it) }
        UikitEnvironment.registeredKeyWindow = uiWindow
        UikitEnvironment.registeredRootViewController = rootViewController
        uiWindow?.makeKeyAndVisible()
        layoutWindowRegions()
    }

    override fun updateNavigationChromeVisibility() {
        val hasTitleBar = navigationItem.leadingItemGroups.isNotEmpty() ||
            navigationItem.centerItemGroups.isNotEmpty() ||
            navigationItem.trailingItemGroups.isNotEmpty() ||
            getTitle().isNotEmpty()
        navigationBar.hidden = isFullscreen() || !hasTitleBar
        layoutWindowRegions()
    }

    private fun applySizeRestrictionsToScene(scene: UIWindowScene) {
        val restrictions = scene.sizeRestrictions ?: return
        val windowedNormal = isWindowedNormal()
        val ratio = getAspectRatio()
        val rawMin = getMinWindowSize()
        if (!windowedNormal) {
            restrictions.allowsFullScreen = true
            restrictions.minimumSize = CGSizeMake(
                (rawMin?.width?.toDouble() ?: 320.0).coerceAtLeast(320.0),
                (rawMin?.height?.toDouble() ?: 320.0).coerceAtLeast(320.0)
            )
            restrictions.maximumSize = CGSizeMake(100000.0, 100000.0)
            applySceneGeometryPreferences(scene, unconstrained = true)
            return
        }
        if (rawMin != null) {
            val effectiveMin = if (ratio != null) {
                WindowAspectRatioHelper.clampClientSize(rawMin, ratio, rawMin)
            } else {
                rawMin
            }
            restrictions.minimumSize = CGSizeMake(
                effectiveMin.width.toDouble().coerceAtLeast(320.0),
                effectiveMin.height.toDouble().coerceAtLeast(320.0)
            )
        }
        val rawMax = maxWindowSize
        if (rawMax != null) {
            val effectiveMax = if (ratio != null) {
                clampMaxSizeToAspectRatio(rawMax, ratio, rawMin)
            } else {
                rawMax
            }
            restrictions.maximumSize = CGSizeMake(
                effectiveMax.width.toDouble(),
                effectiveMax.height.toDouble()
            )
        } else {
            restrictions.maximumSize = CGSizeMake(100000.0, 100000.0)
        }
        applySceneGeometryPreferences(scene, unconstrained = ratio == null)
    }

    private fun applySceneGeometryPreferences(scene: UIWindowScene, unconstrained: Boolean) {
        if (!scene.respondsToSelector(sel_registerName("requestGeometryUpdateWithPreferences:errorHandler:"))) {
            return
        }
        runCatching {
            val prefs = if (unconstrained) {
                UIWindowSceneGeometryPreferencesIOS(interfaceOrientations = UIInterfaceOrientationMaskAll)
            } else {
                UIWindowSceneGeometryPreferencesIOS()
            }
            scene.requestGeometryUpdateWithPreferences(prefs, errorHandler = { _ -> })
        }
    }

    private fun clampMaxSizeToAspectRatio(maxSize: Size, ratio: Double, minSize: Size?): Size {
        var clamped = WindowAspectRatioHelper.clampClientSize(
            size = maxSize,
            aspectRatio = ratio,
            minSize = minSize,
            preferWidth = true
        )
        if (clamped.height > maxSize.height) {
            clamped = WindowAspectRatioHelper.clampClientSize(
                size = maxSize,
                aspectRatio = ratio,
                minSize = minSize,
                preferWidth = false
            )
        }
        if (clamped.width > maxSize.width) {
            clamped = WindowAspectRatioHelper.clampClientSize(
                size = maxSize,
                aspectRatio = ratio,
                minSize = minSize,
                preferWidth = true
            )
        }
        return clamped
    }

    private fun clampBetweenMinMax(target: Size): Size {
        val minApplied = clampToMinSize(target)
        val max = maxWindowSize ?: return minApplied
        return Size(
            width = minOf(minApplied.width, max.width),
            height = minOf(minApplied.height, max.height)
        )
    }

    private fun buildBarGroups(components: List<com.nexustools.genericui.core.AbstractComponent>): List<UIBarButtonItemGroup> {
        if (components.isEmpty()) return emptyList()
        val items = mutableListOf<UIBarButtonItem>()
        for (comp in components) {
            if (!comp.isVisible) continue
            val impl = runCatching { comp.ensureNativeImpl() }.getOrNull()
            val view = (impl?.nativeWidget as? UikitViewHandle)?.uiView ?: continue
            items += UIBarButtonItem(customView = view)
        }
        if (items.isEmpty()) return emptyList()
        return listOf(UIBarButtonItemGroup(barButtonItems = items, representativeItem = null))
    }

    override fun dispose() {
        windowsByController.remove(rootViewController)
        super.dispose()
    }

    companion object {
        val DEFAULT_TITLE_CAPABILITIES: TitleCapabilities = TitleCapabilities(
            isSupported = true,
            isEmulated = false,
            supportsCustomTitleBar = true,
            supportsCustomComponents = true,
            supportsLeadingComponents = true,
            supportsCenterComponents = true,
            supportsTrailingComponents = true,
            supportsEmbeddedMenuBar = false,
            supportsNavbarMenuBar = false,
            supportsWindowIcon = false,
            supportsWindowTitle = true,
            supportsMinimizeButton = false,
            supportsMaximizeButton = false,
            supportsCloseButton = false,
            supportsControlsPlacement = false,
            isTitleBarRequired = false,
            titleBarRendersAsNavbar = true,
            menuBarRendersAsNavbar = false,
            menuBarMergesIntoTitleNavbar = false
        )

        private val windowsByController = LinkedHashMap<GenericUiRootViewController, IpadosWindowImpl>()

        fun forController(controller: GenericUiRootViewController): IpadosWindowImpl? =
            windowsByController[controller]
    }
}
