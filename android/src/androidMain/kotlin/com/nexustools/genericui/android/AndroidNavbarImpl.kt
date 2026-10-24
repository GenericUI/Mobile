package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * Android `com.google.android.material.appbar.MaterialToolbar` implementation of [NavbarNativeImpl].
 * Synchronizes directly with [GenericUiFragmentActivity]'s top toolbar when bound.
 */
class AndroidNavbarImpl : AndroidBaseContainerNativeImpl("com.google.android.material.appbar.MaterialToolbar", "navbar"), NavbarNativeImpl<AndroidViewHandle> {
    override fun setBrandTitle(title: String) {
        nativeWidget.text = title
    }

    override fun getBrandTitle(): String = nativeWidget.text
}
