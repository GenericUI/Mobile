package com.nexustools.genericui.android

import android.view.View
import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.component.fragment.helper.FragmentNavigationChromeConfig
import com.nexustools.genericui.component.navigation.BottomNavigationBar
import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.component.typography.Text
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.window.TitleBar
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidFragmentChromeSyncTest {

    private class ConnectScreenFragment : Fragment() {
        init {
            pageTitle = "Connect"
            addChild(Text("Connect to Server"))
        }
    }

    private class EpisodeScreenFragment : Fragment() {
        val navBarBottom = BottomNavigationBar().apply {
            addItem("episodes", "Episodes")
            addItem("music", "Music")
        }

        init {
            pageTitle = "Episodes"
            setTitleBar(TitleBar(showWindowTitle = true))
            setBottomNavigationBar(navBarBottom)
            addChild(Text("Episode Library"))
        }
    }

    private class HostChromeActivity : GenericUiFragmentActivity() {
        val hostFragment: FragmentHostFragment = FragmentHostFragment(
            appTitle = "Caster",
            navigationChrome = FragmentNavigationChromeConfig(
                showBackButton = false,
                showForwardButton = false,
                showNavbarFallback = false
            )
        )

        override fun createFragment(): Fragment = hostFragment
    }

    @BeforeTest
    fun setUp() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
        AndroidFileBrowserDialogImpl.activeHostActivity = null
        AndroidActivityRegistry.clear()
    }

    @AfterTest
    fun tearDown() {
        Application.current?.dispose()
        GenericUI.activeFactory = null
        AndroidViewHandle.activeContext = null
        AndroidPreferenceStoreImpl.applicationContext = null
        AndroidFileBrowserDialogImpl.activeHostActivity = null
        AndroidActivityRegistry.clear()
    }

    @Test
    fun fragmentHostSwitchingAndDynamicBottomNavNeverStealsHostedFragmentView() {
        val controller = Robolectric.buildActivity(HostChromeActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(activity.mainLooper).idle()

        val contentContainer = activity.contentContainer
        assertNotNull(contentContainer)
        assertEquals(true, contentContainer.getTag(GenericUiFragmentActivity.TAG_ACTIVITY_CONTENT_HOST))

        val hostFragment = activity.hostFragment
        val connectFragment = ConnectScreenFragment()
        hostFragment.setRootFragment(connectFragment)
        shadowOf(activity.mainLooper).idle()

        val hostView = (hostFragment.nativeImpl as? AndroidBaseNativeImpl)?.nativeWidget?.androidView
        assertNotNull(hostView)
        assertEquals(1, contentContainer.childCount)
        assertSame(contentContainer, hostView.parent)
        assertEquals(View.GONE, activity.bottomNavigationView?.visibility)
        assertFalse(activity.isToolbarInRootLayout)

        val episodeScreen = EpisodeScreenFragment()
        hostFragment.pushFragment(episodeScreen)
        shadowOf(activity.mainLooper).idle()

        assertEquals(1, contentContainer.childCount)
        assertSame(contentContainer, hostView.parent)
        assertEquals(View.VISIBLE, activity.bottomNavigationView?.visibility)
        assertEquals(2, activity.bottomNavigationView?.menu?.size())
        assertTrue(activity.isToolbarInRootLayout)
        assertSame(activity.rootLayout, activity.toolbarView?.parent)

        // Mutate BottomNavigationBar dynamically while hosted: fragmentView must stay in contentContainer
        val updatedBottomNav = BottomNavigationBar().apply {
            addItem("episodes", "Episodes")
            addItem("music", "Music")
            addItem("settings", "Settings")
        }
        episodeScreen.setBottomNavigationBar(updatedBottomNav)
        shadowOf(activity.mainLooper).idle()

        assertEquals(1, contentContainer.childCount)
        assertSame(contentContainer, hostView.parent)
        assertEquals(View.VISIBLE, activity.bottomNavigationView?.visibility)
        assertEquals(3, activity.bottomNavigationView?.menu?.size())

        // Direct native bottom-nav mutation on activityWindowImpl also keeps fragmentView attached
        activity.activityWindowImpl.setBottomNavigationBarNative(updatedBottomNav.ensureNativeImpl())
        shadowOf(activity.mainLooper).idle()

        assertEquals(1, contentContainer.childCount)
        assertSame(contentContainer, hostView.parent)

        // Transitioning back clears child chrome while keeping hostView mounted
        assertTrue(hostFragment.popBackStack())
        shadowOf(activity.mainLooper).idle()

        assertEquals(1, contentContainer.childCount)
        assertSame(contentContainer, hostView.parent)
        assertEquals(View.GONE, activity.bottomNavigationView?.visibility)
        assertFalse(activity.isToolbarInRootLayout)

        controller.pause().stop().destroy()
    }
}
