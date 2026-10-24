package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.navigation.NavbarNativeImpl

/**
 * iOS `UINavigationBar` implementation of [NavbarNativeImpl].
 */
class IosNavbarImpl : IosBaseContainerNativeImpl("UINavigationBar", "navbar"), NavbarNativeImpl<IosNativeView> {
    private var brandTitleText: String = ""

    override fun setBrandTitle(title: String) {
        brandTitleText = title
        nativeWidget.text = title
    }

    override fun getBrandTitle(): String = brandTitleText
}
