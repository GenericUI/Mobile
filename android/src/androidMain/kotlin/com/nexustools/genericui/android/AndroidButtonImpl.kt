package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl

/**
 * Android `com.google.android.material.button.MaterialButton` implementation of [ButtonNativeImpl].
 */
class AndroidButtonImpl(
    classPrefix: String = "btn"
) : AndroidBaseNativeImpl("com.google.android.material.button.MaterialButton", classPrefix), ButtonNativeImpl<AndroidViewHandle> {
    private var iconName: String? = null

    override fun setText(text: String) {
        nativeWidget.text = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) nativeWidget.metadata["icon"] = icon else nativeWidget.metadata.remove("icon")
    }

    override fun getIcon(): String? = iconName
}
