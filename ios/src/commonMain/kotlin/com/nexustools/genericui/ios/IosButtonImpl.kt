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
 * - When `type` is `null` or [BootstrapType.NONE] and not an [IosStyledButtonImpl], renders the
 *   100% unmodified native iOS `UIButton` (`UIButtonTypeSystem` / `systemDefault`).
 * - When a semantic [BootstrapType] (`DANGER`, `PRIMARY`, `SUCCESS`, etc.) is set and not an
 *   [IosStyledButtonImpl], renders the empty-text native `UIButton` into an offscreen bitmap
 *   context via [IosControlTintRenderUtil.renderEmptyTextTintedButtonPixels], tints the pixels
 *   using [BootstrapColorPalette.resolveColor] from the active `ThemeConfig`, and renders the
 *   contrasting title ([BootstrapColorPalette.resolveForegroundOn]) and icon on top afterward.
 */
open class IosButtonImpl(
    classPrefix: String = "btn"
) : IosBaseNativeImpl("UIButton", classPrefix), ButtonNativeImpl<IosNativeView> {
    private var iconName: String? = null
    var lastOffscreenTintedPixels: IntArray? = null
        private set

    override fun setText(text: String) {
        nativeWidget.text = text
        nativeWidget.metadata["overlayTitle"] = text
    }

    override fun getText(): String = nativeWidget.text

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) {
            nativeWidget.metadata["icon"] = icon
        } else {
            nativeWidget.metadata.remove("icon")
        }
    }

    override fun getIcon(): String? = iconName

    /**
     * Renders the empty-text native iOS `UIButton` into an offscreen ARGB pixel buffer and tints
     * the pixels using [BootstrapColorPalette.resolveColor] from the active `ThemeConfig`.
     */
    fun renderOffscreenButtonPixels(
        width: Int = nativeWidget.frame.width.takeIf { it > 0 } ?: 120,
        height: Int = nativeWidget.frame.height.takeIf { it > 0 } ?: 38
    ): IntArray {
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val bType = styleSnapshot.type ?: BootstrapType.NONE
        val pixels = IosControlTintRenderUtil.renderEmptyTextTintedButtonPixels(
            width = width,
            height = height,
            type = bType,
            darkMode = isDark,
            outline = styleSnapshot.outline,
            themeConfig = BootstrapColorPalette.activeThemeConfig
        )
        lastOffscreenTintedPixels = pixels
        return pixels
    }

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val activeTheme = BootstrapColorPalette.activeThemeConfig
        val btnType = styleSnapshot.type?.takeIf { it != BootstrapType.NONE }
        val allowAesthetic = allowsSystemAestheticStyles()
        val tintColor = (if (allowAesthetic) customStyleBackgroundColor else null)
            ?: BootstrapColorPalette.resolveTintOrNull(btnType, isDark, activeTheme)
        if (tintColor == null) {
            nativeWidget.metadata["uiButtonType"] = "UIButtonTypeSystem"
            nativeWidget.metadata["uiButtonConfiguration"] = "systemDefault"
            nativeWidget.metadata["useSystemControlAppearance"] = "true"
            nativeWidget.metadata.remove("offscreenSpriteTinted")
            nativeWidget.metadata.remove("baseBackgroundColor")
            nativeWidget.metadata.remove("baseForegroundColor")
            if (!allowAesthetic) {
                nativeWidget.backgroundColor = null
                nativeWidget.tintColor = null
                lastOffscreenTintedPixels = null
            }
        } else {
            val fgColor = (if (allowAesthetic) customStyleTextColor else null)
                ?: if (btnType != null) {
                    IosControlTintRenderUtil.resolveContrastingForeground(
                        type = btnType,
                        darkMode = isDark,
                        outline = styleSnapshot.outline,
                        themeConfig = activeTheme
                    )
                } else {
                    BootstrapColorPalette.resolveForegroundOn(BootstrapType.PRIMARY, isDark, activeTheme)
                }
            if (!allowAesthetic && btnType != null) {
                lastOffscreenTintedPixels = IosControlTintRenderUtil.renderEmptyTextTintedButtonPixels(
                    width = nativeWidget.frame.width.takeIf { it > 0 } ?: 120,
                    height = nativeWidget.frame.height.takeIf { it > 0 } ?: 38,
                    type = btnType,
                    darkMode = isDark,
                    outline = styleSnapshot.outline,
                    themeConfig = activeTheme
                )
                nativeWidget.metadata["offscreenSpriteTinted"] = "true"
            }
            nativeWidget.metadata["uiButtonConfiguration"] = if (styleSnapshot.outline) "bordered" else "filled"
            nativeWidget.metadata["baseBackgroundColor"] = tintColor.toHex()
            nativeWidget.metadata["baseForegroundColor"] = fgColor.toHex()
            nativeWidget.metadata["overlayTitle"] = nativeWidget.text
            nativeWidget.backgroundColor = if (styleSnapshot.outline) null else tintColor
            nativeWidget.tintColor = fgColor
        }

        when (styleSnapshot.size) {
            BootstrapSize.EXTRA_SMALL, BootstrapSize.SMALL -> nativeWidget.metadata["uiButtonControlSize"] = "small"
            BootstrapSize.LARGE, BootstrapSize.EXTRA_LARGE, BootstrapSize.EXTRA_EXTRA_LARGE ->
                nativeWidget.metadata["uiButtonControlSize"] = "large"
            BootstrapSize.MEDIUM, null -> nativeWidget.metadata["uiButtonControlSize"] = "medium"
        }
    }
}
