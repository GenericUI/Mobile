package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl

/**
 * iOS `UIButton` (`UIButton.Configuration`) implementation of [ButtonNativeImpl].
 */
class IosButtonImpl(
    classPrefix: String = "btn"
) : IosBaseNativeImpl("UIButton", classPrefix), ButtonNativeImpl<IosNativeView> {
    private var iconName: String? = null

    override fun setText(text: String) {
        nativeWidget.text = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setIcon(icon: String?) {
        iconName = icon
    }

    override fun getIcon(): String? = iconName
}
