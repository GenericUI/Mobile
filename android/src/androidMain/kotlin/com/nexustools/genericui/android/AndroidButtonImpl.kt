package com.nexustools.genericui.android

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl

/**
 * Android `com.google.android.material.button.MaterialButton` implementation of [ButtonNativeImpl].
 *
 * When `type == null`, preserves the unstyled native Android Material button appearance.
 * When `type != null` (e.g. [BootstrapType.DANGER], [BootstrapType.PRIMARY]), tints the button
 * using [com.nexustools.genericui.style.color.BootstrapColorPalette].
 */
class AndroidButtonImpl(
    classPrefix: String = "btn"
) : AndroidBaseNativeImpl("com.google.android.material.button.MaterialButton", classPrefix), ButtonNativeImpl<AndroidViewHandle> {
    private var iconName: String? = null

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        val type = getType()
        if (type == null || type == BootstrapType.NONE) {
            if (customStyleBackgroundColor == null) {
                nativeWidget.attributes["materialButtonStyle"] = "systemDefault"
            }
        } else {
            nativeWidget.attributes["materialButtonStyle"] = if (isOutline()) "outlined-${type.cssSuffix}" else "filled-${type.cssSuffix}"
        }
    }

    override fun setText(text: String) {
        nativeWidget.text = text
        (nativeWidget.androidView as? android.widget.TextView)?.text = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) nativeWidget.metadata["icon"] = icon else nativeWidget.metadata.remove("icon")
    }

    override fun getIcon(): String? = iconName
}
