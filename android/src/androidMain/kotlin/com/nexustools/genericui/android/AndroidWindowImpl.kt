package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.WindowState
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.layout.MobileSafeAreaTracker
import com.nexustools.genericui.mobile.system.MobilePipController
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.TitleBar
import com.nexustools.genericui.window.WindowConfiguration

/**
 * Android `Window` / `GenericUiFragmentActivity` implementation of [WindowNativeImpl].
 */
class AndroidWindowImpl : AndroidBaseContainerNativeImpl("android.view.Window", "window"), WindowNativeImpl<AndroidViewHandle> {
    override val supportsCustomTitleBar: Boolean = false
    override val supportsEmbeddedMenuBarInTitleBar: Boolean = false
    override val supportsMultiWindow: Boolean = false

    val safeAreaTracker: MobileSafeAreaTracker = MobileSafeAreaTracker()
    val pipController: MobilePipController = MobilePipController()

    private var titleText: String = "GenericUI"
    private var windowSize: Size = Size(412, 915)
    private var windowState: WindowState = WindowState.NORMAL
    private var fullscreenFlag: Boolean = false

    private var navBarImpl: NativeImpl<*>? = null
    private var bottomNavImpl: NativeImpl<*>? = null

    private var onResizeListener: ((WindowConfiguration) -> Unit)? = null
    private var onFocusListener: ((Boolean) -> Unit)? = null
    private var onCloseRequestedListener: (() -> Boolean)? = null

    override fun setTitle(title: String) {
        titleText = title
        AndroidFileBrowserDialogImpl.activeHostActivity?.title = title
    }

    override fun getTitle(): String = titleText

    override fun setWindowSize(size: Size) {
        windowSize = size
        nativeWidget.bounds = Rect(0, 0, size.width, size.height)
        onResizeListener?.invoke(WindowConfiguration(widthPx = size.width, heightPx = size.height))
    }

    override fun getWindowSize(): Size = windowSize

    override fun setWindowState(state: WindowState) { windowState = state }
    override fun getWindowState(): WindowState = windowState

    override fun setFullscreen(fullscreen: Boolean, cutoutMode: DisplayCutoutMode) {
        fullscreenFlag = fullscreen
        nativeWidget.attributes["fullscreen"] = fullscreen.toString()
        nativeWidget.attributes["cutoutMode"] = cutoutMode.name
    }

    override fun isFullscreen(): Boolean = fullscreenFlag

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        // Intentional zero-CPU no-op on :android (supportsCustomTitleBar == false)
    }

    override fun setMenuBarNative(menuBarImpl: MenuBarNativeImpl<*>?) {}

    override fun setNavBarNative(navBarImpl: NativeImpl<*>?) {
        this.navBarImpl?.let { removeChildNative(it) }
        this.navBarImpl = navBarImpl
        if (navBarImpl != null) addChildNative(navBarImpl, 0)
    }

    override fun setBottomNavigationBarNative(bottomNavImpl: NativeImpl<*>?) {
        this.bottomNavImpl?.let { removeChildNative(it) }
        this.bottomNavImpl = bottomNavImpl
        if (bottomNavImpl != null) addChildNative(bottomNavImpl, -1)
    }

    override fun enterPictureInPicture(config: PipConfig): Boolean {
        pipController.configure(config)
        val activity = AndroidFileBrowserDialogImpl.activeHostActivity as? GenericUiFragmentActivity
        if (activity != null) {
            return activity.enterFragmentPictureInPicture(config)
        }
        pipController.onPictureInPictureModeChanged(true)
        return true
    }

    override fun exitPictureInPicture() {
        pipController.onPictureInPictureModeChanged(false)
    }

    override fun isInPictureInPicture(): Boolean = pipController.isInPictureInPictureMode

    override fun showWindow() {
        setVisible(true)
        onFocusListener?.invoke(true)
    }

    override fun closeWindow() {
        val allow = onCloseRequestedListener?.invoke() ?: true
        if (allow) {
            setVisible(false)
            AndroidFileBrowserDialogImpl.activeHostActivity?.finish()
        }
    }

    override fun setOnWindowResize(listener: ((WindowConfiguration) -> Unit)?) { onResizeListener = listener }
    override fun setOnWindowFocusChanged(listener: ((Boolean) -> Unit)?) { onFocusListener = listener }
    override fun setOnWindowCloseRequested(listener: (() -> Boolean)?) { onCloseRequestedListener = listener }
}
