package com.nexustools.genericui.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.mobile.layout.MobileAdaptiveFragmentCoordinator
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.mobile.system.MobilePipController
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.window.PipConfig

/**
 * `AppCompatActivity` subclass that hosts a GenericUI [Fragment] 1:1 on Android.
 *
 * - Owns [activityWindowImpl] ([AndroidWindowImpl]) and [activityWindow] ([Window]) so
 *   `fragment.hostingWindow` and `fragment.attachedWindow` are backed by a real `IWindow`
 *   reporting mobile navbar-as-titlebar [com.nexustools.genericui.window.TitleCapabilities].
 * - Enforces the Navbar-as-TitleBar rule: when the hosted [Fragment] sets neither `TitleBar`,
 *   `MenuBar`, nor `NavBar`, [toolbarView] is not added to [rootLayout] at all (removed from
 *   [rootLayout], not merely `View.GONE`). When `TitleBar`, `MenuBar`, or `NavBar` is set,
 *   [toolbarView] is mounted at index `0` of [rootLayout].
 */
abstract class GenericUiFragmentActivity : AppCompatActivity() {

    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    val adaptiveCoordinator: MobileAdaptiveFragmentCoordinator = MobileAdaptiveFragmentCoordinator()
    val pipController: MobilePipController = MobilePipController()

    val activityWindow: Window = run {
        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory())
        }
        Window(title = "GenericUI").also { win ->
            win.show(GenericUI.activeFactory)
        }
    }

    val activityWindowImpl: AndroidWindowImpl =
        (activityWindow.ensureNativeImpl() as? AndroidWindowImpl) ?: AndroidWindowImpl().also { impl ->
            runCatching {
                val field = AbstractComponent::class.java.getDeclaredField("nativeImpl")
                field.isAccessible = true
                field.set(activityWindow, impl)
            }
        }

    var hostedFragment: Fragment? = null
        private set

    var rootLayout: LinearLayout? = null
        private set
    var toolbarView: MaterialToolbar? = null
        private set
    var contentContainer: FrameLayout? = null
        private set
    var bottomNavigationView: BottomNavigationView? = null
        private set

    var isToolbarInRootLayout: Boolean = false
        private set

    /**
     * Subclasses override this method to provide the root GenericUI [Fragment] hosted by this Activity.
     */
    abstract fun createFragment(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidViewHandle.activeContext = applicationContext
        AndroidPreferenceStoreImpl.applicationContext = applicationContext
        AndroidFileBrowserDialogImpl.activeHostActivity = this

        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory(applicationContext))
        }
        AndroidActivityRegistry.installAsPlatformLauncher()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val toolbar = MaterialToolbar(this).apply {
            visibility = View.GONE
        }
        val content = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val bottomNav = BottomNavigationView(this).apply {
            visibility = View.GONE
        }

        // Navbar-as-TitleBar Rule: do NOT add toolbar to root unless TitleBar, MenuBar, or NavBar is set
        root.addView(content)
        root.addView(bottomNav)
        setContentView(root)

        rootLayout = root
        toolbarView = toolbar
        contentContainer = content
        bottomNavigationView = bottomNav
        isToolbarInRootLayout = false

        val fragment = createFragment()
        val launchArgs = AndroidActivityRegistry.extractFragmentBundle(intent) ?: fragment.arguments
        val restoredBundle = savedInstanceState?.let { bundleToFragmentBundle(it) }
        attachHostedFragment(fragment, launchArgs, restoredBundle)
    }

    /**
     * Binds [fragment] to [activityWindow], sets [Fragment.arguments] from [launchArgs], dispatches
     * [MobileLifecycleBridge.attachFragment] with the merged [FragmentBundle], and synchronizes chrome.
     */
    fun attachHostedFragment(
        fragment: Fragment = createFragment(),
        launchArgs: FragmentBundle? = null,
        savedState: FragmentBundle? = null
    ): Fragment {
        hostedFragment = fragment
        val resolvedLaunchArgs = launchArgs ?: fragment.arguments
        if (resolvedLaunchArgs != null) {
            fragment.arguments = resolvedLaunchArgs
        }
        bindFragmentToActivityWindow(fragment)

        val effectiveBundle: FragmentBundle? = when {
            resolvedLaunchArgs != null && savedState != null -> FragmentBundle().apply {
                putAll(resolvedLaunchArgs)
                putAll(savedState)
            }
            resolvedLaunchArgs != null -> resolvedLaunchArgs
            else -> savedState
        }

        lifecycleBridge.attachFragment(fragment, effectiveBundle)
        syncFragmentChrome(fragment)
        return fragment
    }

    override fun onStart() {
        super.onStart()
        lifecycleBridge.dispatchStart()
    }

    override fun onResume() {
        super.onResume()
        AndroidFileBrowserDialogImpl.activeHostActivity = this
        lifecycleBridge.dispatchResume()
        hostedFragment?.let { syncFragmentChrome(it) }
    }

    override fun onPause() {
        lifecycleBridge.dispatchPause()
        super.onPause()
    }

    override fun onStop() {
        lifecycleBridge.dispatchStop()
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val fragmentBundle = FragmentBundle()
        lifecycleBridge.dispatchSaveInstanceState(fragmentBundle)
        AndroidActivityRegistry.putFragmentBundleToAndroidBundle(outState, fragmentBundle)
    }

    override fun onDestroy() {
        if (AndroidFileBrowserDialogImpl.activeHostActivity === this) {
            AndroidFileBrowserDialogImpl.activeHostActivity = null
        }
        lifecycleBridge.dispatchDestroy()
        hostedFragment = null
        super.onDestroy()
    }

    @Deprecated("Deprecated in Android Activity, delegates back press to hosted GenericUI Fragment first")
    override fun onBackPressed() {
        if (hostedFragment?.onBackPressed() == true) {
            return
        }
        if (lifecycleBridge.dispatchBackPressed()) {
            return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val metrics = resources.displayMetrics
        val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val winConfig = adaptiveCoordinator.evaluateConfiguration(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityScale = metrics.density,
            themeMode = if (isNight) ThemeMode.DARK else ThemeMode.LIGHT
        )
        lifecycleBridge.dispatchConfigurationChanged(winConfig)
        hostedFragment?.let { syncFragmentChrome(it) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        activityWindow.dispatchWindowFocusChanged(hasFocus)
        hostedFragment?.onWindowFocusChanged(hasFocus)
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        pipController.onPictureInPictureModeChanged(isInPictureInPictureMode, hostedFragment)
    }

    @Deprecated("Deprecated in Android Activity, used to bridge Storage Access Framework document picker results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AndroidFileBrowserDialogImpl.dispatchActivityResult(this, requestCode, resultCode, data)
    }

    fun enterFragmentPictureInPicture(config: PipConfig): Boolean {
        pipController.configure(config)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (num, den) = pipController.clampAspectRatio(config.aspectRatioNumerator, config.aspectRatioDenominator)
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(num, den))
                .build()
            return enterPictureInPictureMode(params)
        }
        return false
    }

    /**
     * Synchronizes optional [Fragment.getTitleBar], [Fragment.getMenuBar], [Fragment.getNavBar] ->
     * [MaterialToolbar] / [AndroidWindowImpl], [Fragment.getBottomNavigationBar] -> [BottomNavigationView],
     * and [Fragment.isFullscreen] -> [WindowInsetsControllerCompat].
     *
     * When neither `TitleBar`, `MenuBar`, nor `NavBar` is set (or when fullscreen is active),
     * [toolbarView] is removed from [rootLayout] completely. When any of them is set, [toolbarView]
     * is added at index `0` of [rootLayout].
     */
    fun syncFragmentChrome(fragment: Fragment) {
        bindFragmentToActivityWindow(fragment)
        val fullscreen = fragment.isFullscreen()
        val cutoutMode = fragment.getDisplayCutoutMode()

        if (fragment.pageTitle.isNotBlank()) {
            activityWindow.setTitle(fragment.pageTitle)
            activityWindowImpl.setTitle(fragment.pageTitle)
        }

        val navBar = fragment.getNavBar()
        val titleBar = fragment.getTitleBar()
        val menuBar = fragment.getMenuBar()
        val bottomNav = fragment.getBottomNavigationBar()

        activityWindow.setFullscreen(fullscreen, cutoutMode)
        activityWindow.setTitleBar(titleBar)
        activityWindow.setMenuBar(menuBar)
        activityWindow.setNavBar(if (fullscreen) null else navBar)
        activityWindow.setBottomNavigationBar(if (fullscreen) null else bottomNav)

        activityWindowImpl.setFullscreen(fullscreen, cutoutMode)
        activityWindowImpl.applyTitleBarInstructions(titleBar)
        activityWindowImpl.setMenuBarNative(menuBar?.ensureNativeImpl() as? MenuBarNativeImpl<*>)
        activityWindowImpl.setNavBarNative(if (fullscreen) null else navBar?.ensureNativeImpl())
        activityWindowImpl.setBottomNavigationBarNative(if (fullscreen) null else bottomNav?.ensureNativeImpl())

        runCatching {
            val win = window
            if (win != null) {
                val insetsController = WindowCompat.getInsetsController(win, win.decorView)
                if (fullscreen) {
                    insetsController.systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        win.attributes = win.attributes.apply {
                            layoutInDisplayCutoutMode = when (cutoutMode) {
                                DisplayCutoutMode.SHORT_EDGES, DisplayCutoutMode.ALWAYS ->
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                                DisplayCutoutMode.NEVER ->
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_NEVER
                                DisplayCutoutMode.DEFAULT ->
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                            }
                        }
                    }
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }
        }

        val showTopToolbar = !fullscreen && (navBar != null || titleBar != null || menuBar != null)
        isToolbarInRootLayout = showTopToolbar
        val root = rootLayout
        val tb = toolbarView
        if (tb != null) {
            if (showTopToolbar) {
                if (root != null && tb.parent !== root) {
                    (tb.parent as? ViewGroup)?.removeView(tb)
                    root.addView(tb, 0)
                }
                tb.visibility = View.VISIBLE
            } else {
                if (root != null && tb.parent === root) {
                    root.removeView(tb)
                }
                tb.visibility = View.GONE
            }
        }

        bottomNavigationView?.let { bn ->
            if (!fullscreen && bottomNav != null) {
                bn.visibility = View.VISIBLE
            } else {
                bn.visibility = View.GONE
            }
        }
    }

    private fun bindFragmentToActivityWindow(fragment: Fragment) {
        if (fragment.attachedWindow === activityWindow && fragment.hostingWindow === activityWindow) {
            return
        }
        runCatching {
            val attachedWinField = Fragment::class.java.getDeclaredField("attachedWindow")
            attachedWinField.isAccessible = true
            attachedWinField.set(fragment, activityWindow)
        }
        runCatching {
            val attachedIWinField = Fragment::class.java.getDeclaredField("attachedIWindow")
            attachedIWinField.isAccessible = true
            attachedIWinField.set(fragment, activityWindow)
        }
    }

    private fun bundleToFragmentBundle(bundle: Bundle): FragmentBundle =
        AndroidActivityRegistry.bundleToFragmentBundle(bundle)
}
