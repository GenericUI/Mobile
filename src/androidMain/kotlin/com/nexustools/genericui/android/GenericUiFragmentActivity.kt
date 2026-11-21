package com.nexustools.genericui.android

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
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
import com.nexustools.genericui.component.fragment.helper.FragmentHostSystem
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
import com.nexustools.genericui.nativeimpl.shell.MenuBarNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
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
    @Volatile
    private var isSyncingFragmentChrome: Boolean = false
    @Volatile
    private var reportedOsPipMode: Boolean? = null
    private var hookedHostSystem: FragmentHostSystem? = null
    private var previousHostActiveFragmentChanged: ((Fragment?) -> Unit)? = null
    private var installedHostActiveFragmentChanged: ((Fragment?) -> Unit)? = null

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
                val originalThemeResId = runCatching {
                    packageManager.getActivityInfo(componentName, 0).themeResource
                }.getOrDefault(0).takeIf { it != 0 } ?: applicationInfo.theme
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
        AndroidExtensionRuntimeBridge.notifyActivityCreated(this, savedInstanceState)
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
            setTag(TAG_ACTIVITY_CONTENT_HOST, true)
            setBackgroundColor(windowBgArgb)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        val bottomNav = BottomNavigationView(this).apply {
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
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
        ViewCompat.setOnApplyWindowInsetsListener(bottomNav) { v, _ ->
            v.setPadding(v.paddingLeft, 0, v.paddingRight, 0)
            WindowInsetsCompat.CONSUMED
        }

        // Navbar-as-TitleBar Rule: do NOT add toolbar to root unless TitleBar, MenuBar, or NavBar is set
        root.addView(content)
        root.addView(bottomNav)
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, windowInsets ->
            val inOsPip = isEffectivelyInOsPictureInPicture()
            if (inOsPip || hostedFragment?.let { resolveEffectiveFullscreen(it) } == true) {
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
        wireWindowChromeMutationObserver()

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

    private fun wireWindowChromeMutationObserver() {
        activityWindowImpl.onChromeMutated = {
            if (!isSyncingFragmentChrome) {
                val frag = hostedFragment
                if (frag != null) {
                    AndroidViewHandle.runOnMainThread {
                        if (!isSyncingFragmentChrome && hostedFragment === frag && !isDestroyed) {
                            syncFragmentChrome(frag)
                        }
                    }
                }
            }
        }
    }

    private fun unhookFragmentHostSystem() {
        val hs = hookedHostSystem
        if (hs != null && hs.onActiveFragmentChanged === installedHostActiveFragmentChanged) {
            hs.onActiveFragmentChanged = previousHostActiveFragmentChanged
        }
        hookedHostSystem = null
        previousHostActiveFragmentChanged = null
        installedHostActiveFragmentChanged = null
    }

    private fun <T : Any> resolveEffectiveChrome(
        fragment: Fragment,
        selector: (Fragment) -> T?
    ): T? {
        var current: Fragment? = fragment
        var depth = 0
        while (current != null && depth < 16) {
            selector(current)?.let { return it }
            current = (current as? FragmentHostFragment)?.currentFragment
            depth++
        }
        return null
    }

    private fun resolveEffectiveFullscreen(fragment: Fragment): Boolean {
        val activeChain = mutableListOf<Fragment>()
        var current: Fragment? = fragment
        var depth = 0
        while (current != null && depth < 16) {
            activeChain.add(current)
            current = (current as? FragmentHostFragment)?.currentFragment
            depth++
        }
        for (frag in activeChain.asReversed()) {
            if (frag is FragmentHostFragment && frag.currentFragment != null) continue
            if (frag.isFullscreen()) return true
        }
        return false
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
        unhookFragmentHostSystem()
        activityWindowImpl.onChromeMutated = null
        wireWindowChromeMutationObserver()
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
                    applySoftInputMode(resolveEffectiveSoftInputMode(fragment))
                }
            }
        }
        if (fragment is FragmentHostFragment) {
            val hs = fragment.hostSystem
            hs.onActiveFragmentSoftInputModeChanged = { mode ->
                if (hostedFragment === fragment) {
                    AndroidViewHandle.runOnMainThread {
                        applySoftInputMode(mode)
                    }
                }
            }
            val prevActiveChanged = hs.onActiveFragmentChanged
            val activeChangedWrapper: (Fragment?) -> Unit = { activeChild ->
                prevActiveChanged?.invoke(activeChild)
                if (hostedFragment === fragment) {
                    AndroidViewHandle.runOnMainThread {
                        if (hostedFragment === fragment && !isDestroyed) {
                            if (activeChild != null && hs.precompiledStyleSheet == null && hs.themeStyleSheet == null) {
                                runCatching {
                                    Application.current?.getStyleSheets()?.forEach { sheet ->
                                        activeChild.applyPrecompiledStyleSheet(sheet)
                                    }
                                }
                            }
                            mountFragmentAndroidView(fragment)
                            applySoftInputMode(resolveEffectiveSoftInputMode(fragment))
                            syncFragmentChrome(fragment)
                            contentContainer?.getChildAt(0)?.requestLayout()
                            contentContainer?.requestLayout()
                            contentContainer?.invalidate()
                        }
                    }
                }
            }
            hookedHostSystem = hs
            previousHostActiveFragmentChanged = prevActiveChanged
            installedHostActiveFragmentChanged = activeChangedWrapper
            hs.onActiveFragmentChanged = activeChangedWrapper
        }

        val initialWinConfig = buildWindowConfiguration()
        if (fragment is FragmentHostFragment) {
            runCatching {
                fragment.hostSystem.onConfigurationChanged(initialWinConfig)
            }
        }
        lifecycleBridge.attachFragment(fragment, effectiveBundle)
        applySoftInputMode(resolveEffectiveSoftInputMode(fragment))
        runCatching {
            fragment.onConfigurationChanged(initialWinConfig)
        }
        if (!(fragment is FragmentHostFragment && fragment.hostSystem.precompiledStyleSheet != null)) {
            runCatching {
                val app = Application.current
                if (app != null) {
                    for (sheet in app.getStyleSheets()) {
                        fragment.applyPrecompiledStyleSheet(sheet)
                    }
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
            if (fragmentView.parent !== container || container.childCount != 1) {
                (fragmentView.parent as? ViewGroup)?.removeView(fragmentView)
                container.removeAllViews()
                container.addView(
                    fragmentView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                )
            }
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
        AndroidExtensionRuntimeBridge.notifyActivityStarted(this)
    }

    override fun onResume() {
        super.onResume()
        AndroidViewHandle.activeContext = this
        AndroidFileBrowserDialogImpl.activeHostActivity = this
        AndroidActivityRegistry.setActiveFragmentActivity(this)
        lifecycleBridge.dispatchResume()
        AndroidExtensionRuntimeBridge.notifyActivityResumed(this)
        hostedFragment?.let { frag ->
            applySoftInputMode(resolveEffectiveSoftInputMode(frag))
            syncFragmentChrome(frag)
        }
    }

    override fun onPause() {
        AndroidExtensionRuntimeBridge.notifyActivityPaused(this)
        lifecycleBridge.dispatchPause()
        super.onPause()
    }

    override fun onStop() {
        AndroidExtensionRuntimeBridge.notifyActivityStopped(this)
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
        unhookFragmentHostSystem()
        activityWindowImpl.onChromeMutated = null
        hostedFragment?.let { prev ->
            prev.onSoftInputModeChanged = null
            if (prev is FragmentHostFragment) {
                prev.hostSystem.onActiveFragmentSoftInputModeChanged = null
            }
        }
        lifecycleBridge.dispatchDestroy()
        AndroidExtensionRuntimeBridge.notifyActivityDestroyed(this)
        hostedFragment = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        AndroidExtensionRuntimeBridge.notifyActivityNewIntent(this, intent)
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

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        reportedOsPipMode = isInPictureInPictureMode
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    internal fun isEffectivelyInOsPictureInPicture(): Boolean =
        reportedOsPipMode ?: (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val winConfig = buildWindowConfiguration(newConfig)
        if (com.nexustools.genericui.threading.GenericUiThreading.isSplitThreadingEnabled) {
            com.nexustools.genericui.threading.GenericUiThreading.runOnLogic {
                lifecycleBridge.dispatchConfigurationChanged(winConfig)
                runOnUiThread {
                    hostedFragment?.let { syncFragmentChrome(it) }
                }
            }
        } else {
            lifecycleBridge.dispatchConfigurationChanged(winConfig)
            hostedFragment?.let { syncFragmentChrome(it) }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        activityWindow.dispatchWindowFocusChanged(hasFocus)
        hostedFragment?.onWindowFocusChanged(hasFocus)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        AndroidActivityRegistry.dispatchPermissionResults(requestCode, permissions, grantResults)
        AndroidExtensionRuntimeBridge.notifyRequestPermissionsResult(this, requestCode, permissions, grantResults)
    }

    @Deprecated("Deprecated in Android Activity, used to bridge Storage Access Framework document picker results")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        AndroidFileBrowserDialogImpl.dispatchActivityResult(this, requestCode, resultCode, data)
        AndroidExtensionRuntimeBridge.notifyActivityResult(this, requestCode, resultCode, data)
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
        if (isSyncingFragmentChrome) return
        isSyncingFragmentChrome = true
        try {
            bindFragmentToActivityWindow(fragment)
            val container = contentContainer
            if (container != null && activityWindow.isWindowShown) {
                val fragmentImpl = fragment.nativeImpl as? AndroidBaseNativeImpl
                val fragmentView = fragmentImpl?.nativeWidget?.androidView
                if (fragmentView == null || fragmentView.parent !== container) {
                    mountFragmentAndroidView(fragment)
                }
            }

            val inOsPip = isEffectivelyInOsPictureInPicture()
            val fullscreen = resolveEffectiveFullscreen(fragment)
            val suppressChrome = fullscreen || inOsPip
            val cutoutMode = resolveEffectiveChrome(fragment) { f ->
                f.getDisplayCutoutMode().takeIf { it != DisplayCutoutMode.DEFAULT }
            } ?: DisplayCutoutMode.DEFAULT

            val effectiveTitle = fragment.pageTitle.ifBlank {
                (fragment as? FragmentHostFragment)?.let { host ->
                    host.hostSystem.resolveWindowTitle(host.currentFragment)
                }.orEmpty()
            }.ifBlank {
                resolveEffectiveChrome(fragment) { f -> f.pageTitle.takeIf { it.isNotBlank() } }.orEmpty()
            }
            if (effectiveTitle.isNotBlank()) {
                activityWindow.setTitle(effectiveTitle)
                activityWindowImpl.setTitle(effectiveTitle)
            }

            val navBar = resolveEffectiveChrome(fragment) { it.getNavBar() }
            val titleBar = resolveEffectiveChrome(fragment) { it.getTitleBar() }
            val menuBar = resolveEffectiveChrome(fragment) { it.getMenuBar() }
            val bottomNav = resolveEffectiveChrome(fragment) { it.getBottomNavigationBar() }

            if (activityWindow.isFullscreen() != fullscreen) {
                activityWindow.setFullscreen(fullscreen, cutoutMode)
            }
            activityWindow.setTitleBar(titleBar)
            activityWindow.setMenuBar(menuBar)
            activityWindow.setNavBar(if (suppressChrome) null else navBar)
            activityWindow.setBottomNavigationBar(if (suppressChrome) null else bottomNav)

            activityWindowImpl.setFullscreen(fullscreen, cutoutMode)
            activityWindowImpl.applyTitleBarInstructions(titleBar)
            activityWindowImpl.setMenuBarNative(menuBar?.ensureNativeImpl() as? MenuBarNativeImpl<*>)
            activityWindowImpl.setNavBarNative(if (suppressChrome) null else navBar?.ensureNativeImpl())
            activityWindowImpl.setBottomNavigationBarNative(if (suppressChrome) null else bottomNav?.ensureChromeNativeImpl())

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
                    } else if (!inOsPip) {
                        insetsController.show(WindowInsetsCompat.Type.systemBars())
                    }
                    rootLayout?.let { ViewCompat.requestApplyInsets(it) }
                }
            }

            val showTopToolbar = !suppressChrome && (navBar != null || titleBar != null || menuBar != null)
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
                        runCatching { runOnUiThread { syncToolbarWithNavbar(tb, activeNavbarImpl, fragment, effectiveTitle) } }
                    }
                    syncToolbarWithNavbar(tb, activeNavbarImpl, fragment, effectiveTitle)
                } else {
                    if (root != null && tb.parent === root) {
                        root.removeView(tb)
                    }
                    tb.visibility = View.GONE
                }
            }

            bottomNavigationView?.let { bn ->
                if (!suppressChrome && bottomNav != null) {
                    bn.visibility = View.VISIBLE
                    val bottomNavImpl = bottomNav.ensureChromeNativeImpl() as? AndroidBottomNavigationBarImpl
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
        } finally {
            isSyncingFragmentChrome = false
        }
    }

    private fun syncToolbarWithNavbar(
        tb: MaterialToolbar,
        navbarImpl: AndroidNavbarImpl?,
        fragment: Fragment,
        fallbackTitle: String = fragment.pageTitle
    ) {
        runCatching {
            if (navbarImpl == null) {
                removeStaleNavbarFlexViews(tb, null)
                tb.title = fallbackTitle
                return@runCatching
            }
            val brand = navbarImpl.getBrandTitle()
            if (navbarImpl.items.isNotEmpty()) {
                tb.title = brand
                val navView = navbarImpl.ensureAndroidView(this)
                removeStaleNavbarFlexViews(tb, navView)
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
                removeStaleNavbarFlexViews(tb, null)
                tb.title = brand.ifBlank { fallbackTitle }
            }
        }
    }

    private fun removeStaleNavbarFlexViews(tb: MaterialToolbar, keepView: View?) {
        for (i in tb.childCount - 1 downTo 0) {
            val child = tb.getChildAt(i)
            if (child is AndroidFlexContainerView && child !== keepView) {
                tb.removeViewAt(i)
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
        runCatching {
            val declared = runCatching {
                packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
                    .requestedPermissions
                    ?.toSet()
                    .orEmpty()
            }.getOrDefault(emptySet())
            val missing = mutableListOf<String>()
            if (Build.VERSION.SDK_INT >= 33) {
                val postNotifications = "android.permission.POST_NOTIFICATIONS"
                if ((declared.isEmpty() || postNotifications in declared) &&
                    checkSelfPermission(postNotifications) != PackageManager.PERMISSION_GRANTED
                ) {
                    missing.add(postNotifications)
                }
            }
            if (Build.VERSION.SDK_INT >= 36) {
                val localNetwork = "android.permission.ACCESS_LOCAL_NETWORK"
                if (localNetwork in declared &&
                    checkSelfPermission(localNetwork) != PackageManager.PERMISSION_GRANTED
                ) {
                    missing.add(localNetwork)
                }
            }
            if (missing.isNotEmpty()) {
                val array = missing.toTypedArray()
                if (!launchRuntimePermissions(array)) {
                    requestPermissions(array, 1001)
                }
            }
        }
    }

    private fun bindFragmentToActivityWindow(fragment: Fragment) {
        var current: Fragment? = fragment
        var depth = 0
        while (current != null && depth < 16) {
            if (current.attachedWindow !== activityWindow || current.hostingWindow !== activityWindow) {
                runCatching {
                    val attachedWinField = Fragment::class.java.getDeclaredField("attachedWindow")
                    attachedWinField.isAccessible = true
                    attachedWinField.set(current, activityWindow)
                }
                runCatching {
                    val attachedIWinField = Fragment::class.java.getDeclaredField("attachedIWindow")
                    attachedIWinField.isAccessible = true
                    attachedIWinField.set(current, activityWindow)
                }
            }
            current = (current as? FragmentHostFragment)?.currentFragment
            depth++
        }
    }

    private fun bundleToFragmentBundle(bundle: Bundle): FragmentBundle =
        AndroidActivityRegistry.bundleToFragmentBundle(bundle)

    companion object {
        internal const val TAG_VIEW_SCROLL_CALLBACK: Int = 0x47550001
        internal const val TAG_ACTIVITY_CONTENT_HOST: Int = 0x47554948
    }
}
