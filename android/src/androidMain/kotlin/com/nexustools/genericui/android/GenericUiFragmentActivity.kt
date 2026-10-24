package com.nexustools.genericui.android

import android.app.PictureInPictureParams
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
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
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.enums.window.DisplayCutoutMode
import com.nexustools.genericui.mobile.layout.MobileAdaptiveFragmentCoordinator
import com.nexustools.genericui.mobile.system.MobileLifecycleBridge
import com.nexustools.genericui.mobile.system.MobilePipController
import com.nexustools.genericui.window.PipConfig

/**
 * `AppCompatActivity` subclass that hosts a GenericUI [Fragment] 1:1 on Android.
 *
 * Wires the complete Android `Activity` lifecycle (`onCreate`, `onStart`, `onResume`, `onPause`, `onStop`,
 * `onSaveInstanceState`, `onDestroy`, `onConfigurationChanged`, `onPictureInPictureModeChanged`, `onWindowFocusChanged`,
 * `onActivityResult` for `AndroidFileBrowserDialogImpl`), optional `Fragment.getNavBar()` -> [MaterialToolbar],
 * optional `Fragment.getBottomNavigationBar()` -> [BottomNavigationView], `Fragment.isFullscreen()` ->
 * [WindowInsetsControllerCompat], and Picture-in-Picture via [MobilePipController].
 */
abstract class GenericUiFragmentActivity : AppCompatActivity() {

    val lifecycleBridge: MobileLifecycleBridge = MobileLifecycleBridge()
    val adaptiveCoordinator: MobileAdaptiveFragmentCoordinator = MobileAdaptiveFragmentCoordinator()
    val pipController: MobilePipController = MobilePipController()

    var hostedFragment: Fragment? = null
        private set

    private var rootLayout: LinearLayout? = null
    private var toolbarView: MaterialToolbar? = null
    private var contentContainer: FrameLayout? = null
    private var bottomNavigationView: BottomNavigationView? = null

    /**
     * Subclasses override this method to provide the root GenericUI [Fragment] hosted by this Activity.
     */
    abstract fun createFragment(): Fragment

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AndroidViewHandle.activeContext = applicationContext
        AndroidFileBrowserDialogImpl.activeHostActivity = this

        if (GenericUI.activeFactory == null) {
            GenericUI.initialize(AndroidNativeFactory(applicationContext))
        }

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

        root.addView(toolbar)
        root.addView(content)
        root.addView(bottomNav)
        setContentView(root)

        rootLayout = root
        toolbarView = toolbar
        contentContainer = content
        bottomNavigationView = bottomNav

        val fragment = createFragment()
        hostedFragment = fragment

        val restoredBundle = savedInstanceState?.let { bundleToFragmentBundle(it) }
        lifecycleBridge.attachFragment(fragment, restoredBundle)
        syncFragmentChrome(fragment)
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
        for (key in fragmentBundle.keys()) {
            fragmentBundle.getString(key)?.let { outState.putString(key, it) }
        }
    }

    override fun onDestroy() {
        if (AndroidFileBrowserDialogImpl.activeHostActivity === this) {
            AndroidFileBrowserDialogImpl.activeHostActivity = null
        }
        lifecycleBridge.dispatchDestroy()
        hostedFragment = null
        super.onDestroy()
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
     * Synchronizes optional [Fragment.getNavBar] -> [MaterialToolbar],
     * [Fragment.getBottomNavigationBar] -> [BottomNavigationView], and
     * [Fragment.isFullscreen] -> [WindowInsetsControllerCompat].
     */
    fun syncFragmentChrome(fragment: Fragment) {
        val fullscreen = fragment.isFullscreen()
        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        if (fullscreen) {
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                window.attributes = window.attributes.apply {
                    layoutInDisplayCutoutMode = when (fragment.getDisplayCutoutMode()) {
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

        val navBar = fragment.getNavBar()
        toolbarView?.let { tb ->
            if (!fullscreen && navBar != null) {
                tb.visibility = View.VISIBLE
            } else {
                tb.visibility = View.GONE
            }
        }

        val bottomNav = fragment.getBottomNavigationBar()
        bottomNavigationView?.let { bn ->
            if (!fullscreen && bottomNav != null) {
                bn.visibility = View.VISIBLE
            } else {
                bn.visibility = View.GONE
            }
        }
    }

    private fun bundleToFragmentBundle(bundle: Bundle): FragmentBundle {
        val out = FragmentBundle()
        for (key in bundle.keySet()) {
            when (val v = bundle.get(key)) {
                is String -> out.putString(key, v)
                is Int -> out.putInt(key, v)
                is Long -> out.putLong(key, v)
                is Double -> out.putDouble(key, v)
                is Boolean -> out.putBoolean(key, v)
            }
        }
        return out
    }
}
