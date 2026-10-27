package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.theme.SystemThemeDetector

/**
 * iOS `UIButton` (`UIButton.Configuration`) implementation of [ButtonNativeImpl].
 *
 * When `type` is `null` or [BootstrapType.NONE], preserves the default native iOS `UIButton`
 * system appearance (`UIButton.Configuration.plain()` / system tint). When a semantic [BootstrapType]
 * (`DANGER`, `PRIMARY`, `SUCCESS`, etc.) is set, configures `UIButton.Configuration.filled()`
 * (or `.bordered()` when `outline == true`) with the semantic color palette.
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

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val btnType = styleSnapshot.type?.takeIf { it != BootstrapType.NONE }
        val tintColor = customStyleBackgroundColor ?: BootstrapColorPalette.resolveTintOrNull(btnType, isDark)
        if (tintColor == null) {
            nativeWidget.metadata["uiButtonConfiguration"] = "systemDefault"
        } else {
            val fgColor = customStyleTextColor ?: if (styleSnapshot.outline) {
                tintColor
            } else {
                btnType?.let { BootstrapColorPalette.resolveForegroundOn(it) } ?: BootstrapColorPalette.resolveForegroundOn(BootstrapType.PRIMARY)
            }
            nativeWidget.metadata["uiButtonConfiguration"] = if (styleSnapshot.outline) "bordered" else "filled"
            nativeWidget.metadata["baseBackgroundColor"] = tintColor.toCssHex()
            nativeWidget.metadata["baseForegroundColor"] = fgColor.toCssHex()
            nativeWidget.backgroundColor = if (styleSnapshot.outline) null else tintColor
            nativeWidget.tintColor = fgColor
        }

        when (styleSnapshot.size) {
            BootstrapSize.SM -> nativeWidget.metadata["uiButtonControlSize"] = "small"
            BootstrapSize.LG -> nativeWidget.metadata["uiButtonControlSize"] = "large"
            BootstrapSize.MD, null -> nativeWidget.metadata["uiButtonControlSize"] = "medium"
        }
    }
}
