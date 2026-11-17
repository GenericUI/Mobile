package com.nexustools.genericui.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.navigation.NavigationBarView
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.shell.Window
import com.nexustools.genericui.core.AbstractComponent
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.enums.window.SoftInputMode
import com.nexustools.genericui.mobile.layout.MobileAdaptiveFragmentCoordinator
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.mobile.system.MobilePipController
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.window.PipConfig
import com.nexustools.genericui.window.WindowConfiguration

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
 * - Coordinates Activity-scoped runtime permission prompts, Storage Access Framework launchers,
 *   and attached scroll/tree/tab/dock hierarchy observers.
 */
abstract class GenericUiFragmentActivity : AppCompatActivity() {

    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    val adaptiveCoordinator: MobileAdaptiveFragmentCoordinator = MobileAdaptiveFragmentCoordinator()
    val pipController: MobilePipController = MobilePipController()

    val activityWindow: Window = run {
        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory())
        }
        Window(title = "GenericUI")
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

    private var suppressBottomNavListener: Boolean = false
    private var lastSyncedBottomNavSignature: String = ""
    private var initialSoftInputMode: Int = WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED
    private var capturedInitialSoftInputMode: Boolean = false
    private var currentSoftInputAdjustResize: Boolean = false

    private val runtimePermissionLauncher: ActivityResultLauncher<Array<String>>? = runCatching {
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            AndroidActivityRegistry.deliverLauncherPermissionResult(results)
        }
    }.getOrNull()

    private val safDocumentLauncher: ActivityResultLauncher<Intent>? = runCatching {
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            AndroidActivityRegistry.deliverLauncherSafResult(result.resultCode, result.data)
        }
    }.getOrNull()

    /**
     * Subclasses override this method to provide the root GenericUI [Fragment] hosted by this Activity.
     */
    abstract fun createFragment(): Fragment

    internal fun launchRuntimePermissions(permissions: Array<String>): Boolean {
        val launcher = runtimePermissionLauncher ?: return false
        return runCatching {
            launcher.launch(permissions)
            true
        }.getOrDefault(false)
    }

    internal fun launchSafIntent(intent: Intent): Boolean {
        val launcher = safDocumentLauncher ?: return false
        return runCatching {
            launcher.launch(intent)
            true
        }.getOrDefault(false)
    }

    private fun ensureMaterialAppCompatTheme() {
        runCatching {
            val tv = android.util.TypedValue()
            val hasAppCompat = theme.resolveAttribute(androidx.appcompat.R.attr.colorPrimary, tv, true) &&
                theme.resolveAttribute(androidx.appcompat.R.attr.windowActionBar, tv, true)
            val hasMaterial = theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryVariant, tv, true)
            if (!hasAppCompat || !hasMaterial) {
                val isDark = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                val materialStyle = if (isDark) {
                    com.google.android.material.R.style.Theme_MaterialComponents_NoActionBar
                } else {
                    com.google.android.material.R.style.Theme_MaterialComponents_Light_NoActionBar
                }
                val originalThemeResId = packageManager.getActivityInfo(componentName, 0).themeResource
                    .takeIf { it != 0 } ?: applicationInfo.theme
                setTheme(materialStyle)
                if (originalThemeResId != 0 && originalThemeResId != materialStyle) {
                    theme.applyStyle(originalThemeResId, true)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        ensureMaterialAppCompatTheme()
        super.onCreate(savedInstanceState)
        ensureInitialSoftInputModeCaptured()
        AndroidViewHandle.activeContext = this
        AndroidPreferenceStoreImpl.applicationContext = applicationContext
        AndroidFileBrowserDialogImpl.activeHostActivity = this
        AndroidActivityRegistry.setActiveFragmentActivity(this)

        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory(this))
        }
        AndroidActivityRegistry.activeDescriptor?.let { desc ->
            AndroidLauncher.ensureApplicationConfigured(desc)
        }
        AndroidActivityRegistry.installAsPlatformLauncher()
        requestPostNotificationsPermissionIfNeeded()

        val isDark = AndroidViewHandle.isDarkTheme()
        val windowBgArgb = if (isDark) 0xFF121318.toInt() else 0xFFF6F7FB.toInt()
        val chromeBgArgb = if (isDark) 0xFF1A1D24.toInt() else 0xFFFFFFFF.toInt()
        val chromeTitleArgb = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
        val primaryArgb = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark).toArgbInt()
        val inactiveNavArgb = if (isDark) 0xFF8892A6.toInt() else 0xFF6C757D.toInt()

        runCatching {
            val win = window
            if (win != null) {
                win.statusBarColor = chromeBgArgb
                win.navigationBarColor = chromeBgArgb
                val ic = WindowCompat.getInsetsController(win, win.decorView)
                ic.isAppearanceLightStatusBars = !isDark
                ic.isAppearanceLightNavigationBars = !isDark
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(windowBgArgb)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val toolbar = MaterialToolbar(this).apply {
            visibility = View.GONE
            setBackgroundColor(chromeBgArgb)
            setTitleTextColor(chromeTitleArgb)
            elevation = AndroidViewHandle.dpToPxFloat(3f)
        }
        val content = FrameLayout(this).apply {
            setBackgroundColor(windowBgArgb)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val bottomNav = BottomNavigationView(this).apply {
            visibility = View.GONE
            setBackgroundColor(chromeBgArgb)
            labelVisibilityMode = NavigationBarView.LABEL_VISIBILITY_LABELED
            val states = arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked)
            )
            val colors = intArrayOf(primaryArgb, inactiveNavArgb)
            val colorStateList = ColorStateList(states, colors)
            itemIconTintList = colorStateList
            itemTextColor = colorStateList
        }

        // Navbar-as-TitleBar Rule: do NOT add toolbar to root unless TitleBar, MenuBar, or NavBar is set
        root.addView(content)
        root.addView(bottomNav)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            if (hostedFragment?.isFullscreen() == true) {
                view.setPadding(0, 0, 0, 0)
            } else {
                val sysBars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val imeBottom = if (currentSoftInputAdjustResize) {
                    windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                } else {
                    0
                }
                view.setPadding(
                    sysBars.left,
                    sysBars.top,
                    sysBars.right,
                    maxOf(sysBars.bottom, imeBottom)
                )
            }
            windowInsets
        }

        rootLayout = root
        toolbarView = toolbar
        contentContainer = content
        bottomNavigationView = bottomNav
        isToolbarInRootLayout = false

        val fragment = createFragment()
        val launchArgs = AndroidActivityRegistry.extractFragmentBundle(intent) ?: fragment.arguments
        val restoredBundle = savedStateOrNull(savedInstanceState)
        attachHostedFragment(fragment, launchArgs, restoredBundle)
    }

    private fun savedStateOrNull(savedInstanceState: Bundle?): FragmentBundle? =
        savedInstanceState?.let { bundleToFragmentBundle(it) }

    private fun ensureInitialSoftInputModeCaptured() {
        if (!capturedInitialSoftInputMode) {
            runCatching {
                window?.attributes?.softInputMode?.let { mode ->
                    initialSoftInputMode = mode
                    capturedInitialSoftInputMode = true
                }
            }
        }
    }

    private fun resolveEffectiveSoftInputMode(fragment: Fragment): SoftInputMode {
        if (fragment is FragmentHostFragment) {
            val childMode = fragment.currentFragment?.softInputMode
            if (childMode != null && childMode != SoftInputMode.UNSPECIFIED) {
                return childMode
            }
        }
        return fragment.softInputMode
    }

    fun applySoftInputMode(mode: SoftInputMode) {
        ensureInitialSoftInputModeCaptured()
        val androidMode = when (mode) {
            SoftInputMode.ADJUST_RESIZE -> {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
            }
            SoftInputMode.ADJUST_PAN -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
            SoftInputMode.ADJUST_NOTHING -> WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            SoftInputMode.UNSPECIFIED -> initialSoftInputMode
        }
        @Suppress("DEPRECATION")
        currentSoftInputAdjustResize =
            (androidMode and WindowManager.LayoutParams.SOFT_INPUT_MASK_ADJUST) == WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        runCatching {
            window?.setSoftInputMode(androidMode)
            rootLayout?.let { ViewCompat.requestApplyInsets(it) }
        }
    }

    private fun buildWindowConfiguration(config: Configuration = resources.configuration): WindowConfiguration {
        val metrics = resources.displayMetrics
        val density = metrics.density.takeIf { it > 0f } ?: 1f
        val isNight = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val evaluated = adaptiveCoordinator.evaluateConfiguration(
            widthPx = metrics.widthPixels,
            heightPx = metrics.heightPixels,
            densityScale = density,
            themeMode = if (isNight) ThemeMode.DARK else ThemeMode.LIGHT
        )
        val swDp = config.smallestScreenWidthDp.takeIf { it > 0 } ?: evaluated.smallestScreenWidthDp
        return if (swDp != evaluated.smallestScreenWidthDp) {
            evaluated.copy(smallestScreenWidthDp = swDp)
        } else {
            evaluated
        }
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
        hostedFragment?.let { prev ->
            prev.onSoftInputModeChanged = null
            if (prev is FragmentHostFragment) {
                prev.hostSystem.onActiveFragmentSoftInputModeChanged = null
            }
        }
        hostedFragment = fragment
        AndroidActivityRegistry.activeDescriptor?.let { desc ->
            AndroidLauncher.ensureApplicationConfigured(desc)
        }
        if (fragment.application == null) {
            Application.current?.let { app ->
                fragment.application = app
            }
        }
        val resolvedLaunchArgs = launchArgs ?: fragment.arguments
        if (resolvedLaunchArgs != null) {
            fragment.arguments = resolvedLaunchArgs
        }
        bindFragmentToActivityWindow(fragment)
        runCatching {
            activityWindow.setContent(fragment)
        }

        val effectiveBundle: FragmentBundle? = when {
            resolvedLaunchArgs != null && savedState != null -> FragmentBundle().apply {
                putAll(resolvedLaunchArgs)
                putAll(savedState)
            }
            resolvedLaunchArgs != null -> resolvedLaunchArgs
            else -> savedState
        }

        fragment.onSoftInputModeChanged = {
            if (hostedFragment === fragment) {
                AndroidViewHandle.runOnMainThread {
                    applySoftInputMode(fragment.softInputMode)
                }
            }
        }
        if (fragment is FragmentHostFragment) {
            fragment.hostSystem.onActiveFragmentSoftInputModeChanged = { mode ->
                if (hostedFragment === fragment) {
                    AndroidViewHandle.runOnMainThread {
                        applySoftInputMode(mode)
                    }
                }
            }
        }

        lifecycleBridge.attachFragment(fragment, effectiveBundle)
        applySoftInputMode(resolveEffectiveSoftInputMode(fragment))
        runCatching {
            fragment.onConfigurationChanged(buildWindowConfiguration())
        }
        runCatching {
            val app = Application.current
            if (app != null) {
                for (sheet in app.getStyleSheets()) {
                    sheet.styleSheet.applyToTree(fragment)
                }
            }
        }
        runCatching {
            activityWindow.show(GenericUI.activeFactory)
        }
        mountFragmentAndroidView(fragment)
        syncFragmentChrome(fragment)
        return fragment
    }

    private fun mountFragmentAndroidView(fragment: Fragment) {
        runCatching {
            val container = contentContainer ?: return@runCatching
            val fragmentImpl = fragment.ensureNativeImpl() as? AndroidBaseNativeImpl ?: return@runCatching
            val fragmentView = fragmentImpl.ensureAndroidView(this) ?: return@runCatching
            (fragmentView.parent as? ViewGroup)?.removeView(fragmentView)
            container.removeAllViews()
            container.addView(
                fragmentView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            attachHostedViewHierarchyObservers(fragmentView)
        }
    }

    /**
     * Traverses [rootView] and ensures attached lazy lists/grids/trees, tabs, and dock components
     * synchronize interactive scroll listeners, double-tap/context-menu row gesture detectors,
     * tab actions, and dock containers with this Activity window.
     */
    private fun attachHostedViewHierarchyObservers(rootView: View) {
        val scrollTag = rootView.getTag(TAG_VIEW_SCROLL_CALLBACK) as? (() -> Unit)
        if (scrollTag != null) {
            rootView.setOnScrollChangeListener { _, _, _, _, _ ->
                scrollTag()
            }
        }
        if (rootView is ViewGroup) {
            for (i in 0 until rootView.childCount) {
                attachHostedViewHierarchyObservers(rootView.getChildAt(i))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        lifecycleBridge.dispatchStart()
    }

    override fun onResume() {
        super.onResume()
        AndroidViewHandle.activeContext = this
        AndroidFileBrowserDialogImpl.activeHostActivity = this
        AndroidActivityRegistry.setActiveFragmentActivity(this)
        lifecycleBridge.dispatchResume()
        hostedFragment?.let { frag ->
            applySoftInputMode(resolveEffectiveSoftInputMode(frag))
            syncFragmentChrome(frag)
        }
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
        if (AndroidActivityRegistry.resolveActiveActivity() === this) {
            AndroidActivityRegistry.setActiveFragmentActivity(null)
        }
        hostedFragment?.let { prev ->
            prev.onSoftInputModeChanged = null
            if (prev is FragmentHostFragment) {
                prev.hostSystem.onActiveFragmentSoftInputModeChanged = null
            }
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
        val winConfig = buildWindowConfiguration(newConfig)
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

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidActivityRegistry.dispatchPermissionResults(requestCode, permissions, grantResults)
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
                rootLayout?.let { ViewCompat.requestApplyInsets(it) }
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
                val activeNavbarImpl = activityWindowImpl.activeNavbar
                    ?: (navBar?.ensureNativeImpl() as? AndroidNavbarImpl)
                activeNavbarImpl?.onNavbarChanged = {
                    runCatching { runOnUiThread { syncToolbarWithNavbar(tb, activeNavbarImpl, fragment) } }
                }
                syncToolbarWithNavbar(tb, activeNavbarImpl, fragment)
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
                val bottomNavImpl = bottomNav.ensureNativeImpl() as? AndroidBottomNavigationBarImpl
                if (bottomNavImpl != null) {
                    bottomNavImpl.onBarChanged = {
                        runCatching { runOnUiThread { syncBottomNavigationBar(bn, bottomNavImpl) } }
                    }
                    syncBottomNavigationBar(bn, bottomNavImpl)
                }
            } else {
                bn.visibility = View.GONE
            }
        }
    }

    private fun syncToolbarWithNavbar(
        tb: MaterialToolbar,
        navbarImpl: AndroidNavbarImpl?,
        fragment: Fragment
    ) {
        runCatching {
            if (navbarImpl == null) {
                tb.title = fragment.pageTitle
                return@runCatching
            }
            val brand = navbarImpl.getBrandTitle()
            if (navbarImpl.items.isNotEmpty()) {
                tb.title = brand
                val navView = navbarImpl.ensureAndroidView(this)
                if (navView != null) {
                    if (navView.parent !== tb) {
                        (navView.parent as? ViewGroup)?.removeView(navView)
                        val lp = Toolbar.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        tb.addView(navView, lp)
                    }
                    navView.requestLayout()
                }
            } else {
                tb.title = brand.ifBlank { fragment.pageTitle }
            }
        }
    }

    private fun syncBottomNavigationBar(
        bn: BottomNavigationView,
        bottomNavImpl: AndroidBottomNavigationBarImpl
    ) {
        runCatching {
            val items = bottomNavImpl.itemsList
            val signature = items.joinToString("|") { "${it.first}:${it.second}:${it.third.orEmpty()}" }
            suppressBottomNavListener = true
            try {
                if (signature != lastSyncedBottomNavSignature || bn.menu.size() != items.size) {
                    lastSyncedBottomNavSignature = signature
                    bn.menu.clear()
                    items.forEachIndexed { idx, triple ->
                        val itemIdInt = idx + 1000
                        val menuItem = bn.menu.add(Menu.NONE, itemIdInt, idx, triple.second)
                        val iconSource = triple.third
                        if (!iconSource.isNullOrBlank()) {
                            val iconDrawable = AndroidImageImpl.resolveDrawable(this, iconSource, null)
                            if (iconDrawable != null) {
                                menuItem.icon = iconDrawable
                            }
                        }
                    }
                }
                val selectedKey = bottomNavImpl.getSelectedItemId()
                val selectedIdx = items.indexOfFirst { it.first == selectedKey }
                if (selectedIdx >= 0) {
                    val targetMenuId = selectedIdx + 1000
                    if (bn.selectedItemId != targetMenuId) {
                        bn.selectedItemId = targetMenuId
                    }
                }
            } finally {
                suppressBottomNavListener = false
            }

            bn.setOnItemSelectedListener { menuItem ->
                if (suppressBottomNavListener) return@setOnItemSelectedListener true
                val idx = menuItem.itemId - 1000
                val entry = bottomNavImpl.itemsList.getOrNull(idx)
                if (entry != null) {
                    bottomNavImpl.dispatchItemSelectedFromView(entry.first)
                }
                true
            }
        }
    }

    private fun requestPostNotificationsPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            runCatching {
                val perm = "android.permission.POST_NOTIFICATIONS"
                if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                    if (!launchRuntimePermissions(arrayOf(perm))) {
                        requestPermissions(arrayOf(perm), 1001)
                    }
                }
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

    companion object {
        internal const val TAG_VIEW_SCROLL_CALLBACK: Int = 0x47550001
    }
}
