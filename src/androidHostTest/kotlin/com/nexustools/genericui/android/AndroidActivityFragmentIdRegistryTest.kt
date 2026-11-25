package com.nexustools.genericui.android

import com.nexustools.genericui.component.fragment.Fragment
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AndroidActivityFragmentIdRegistryTest {

    private class SampleVideoFragment : Fragment()
    private class SampleVideoActivity : GenericUiFragmentActivity() {
        override fun createFragment(): Fragment = SampleVideoFragment()
    }

    private class FirstDupHolder {
        class SharedFragment : Fragment()
        class SharedActivity : GenericUiFragmentActivity() {
            override fun createFragment(): Fragment = SharedFragment()
        }
    }

    private class SecondDupHolder {
        class SharedFragment : Fragment()
        class SharedActivity : GenericUiFragmentActivity() {
            override fun createFragment(): Fragment = SharedFragment()
        }
    }

    @BeforeTest
    fun setup() {
        AndroidActivityRegistry.clear()
    }

    @AfterTest
    fun teardown() {
        AndroidActivityRegistry.clear()
    }

    @Test
    fun `registerFragmentId and resolveByFragmentId handle exact and unambiguous suffix matches`() {
        AndroidActivityRegistry.registerFragmentId(
            fragmentId = "com.example.SampleVideoFragment",
            activityClass = SampleVideoActivity::class.java
        )

        assertEquals(
            SampleVideoActivity::class.java,
            AndroidActivityRegistry.resolveByFragmentId("com.example.SampleVideoFragment")
        )
        assertEquals(
            SampleVideoActivity::class.java,
            AndroidActivityRegistry.resolveByFragmentId("SampleVideoFragment")
        )
        assertNull(AndroidActivityRegistry.resolveByFragmentId("NonExistentFragment"))

        AndroidActivityRegistry.registerFragmentId(
            "com.a.SharedFragment",
            FirstDupHolder.SharedActivity::class.java
        )
        AndroidActivityRegistry.registerFragmentId(
            "com.b.SharedFragment",
            SecondDupHolder.SharedActivity::class.java
        )
        assertNull(AndroidActivityRegistry.resolveByFragmentId("SharedFragment"))
        assertEquals(
            SecondDupHolder.SharedActivity::class.java,
            AndroidActivityRegistry.resolveByFragmentId("com.b.SharedFragment")
        )

        AndroidActivityRegistry.clear()
        assertNull(AndroidActivityRegistry.resolveByFragmentId("com.example.SampleVideoFragment"))
    }
}
