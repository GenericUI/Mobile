@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.shell.Application
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.theme.SystemThemeDetector
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonConfiguration
import platform.UIKit.UIButtonConfigurationCornerStyleCapsule
import platform.UIKit.UIButtonConfigurationCornerStyleDynamic
import platform.UIKit.UIButtonConfigurationSize
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal

/**
 * Native iOS `UIButton.buttonWithType(UIButtonTypeSystem)` implementation of [ButtonNativeImpl].
 *
 * - Leaves `UIButton` 100% unmodified (`configuration = null`, native `tintColor`) when
 *   [BootstrapType] is `NONE` (or `null`), `!isOutline()`, and no custom border radius is set.
 * - Uses genuine UIKit `UIButtonConfiguration` (`filledButtonConfiguration`, `plainButtonConfiguration`,
 *   `grayButtonConfiguration`) + `UIBackgroundConfiguration` stroke/corner settings and native
 *   `tintColor` for semantic [BootstrapType], outline, link, size, and pill/rounded variants.
 * - Standard control aesthetic skinning and typography CSS overrides are silently ignored.
 */
class IosButtonImpl(
    classPrefix: String = "btn",
    val button: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 120.0, 44.0))
    }
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(button),
    componentClassPrefix = classPrefix
), ButtonNativeImpl<UikitViewHandle> {

    private var buttonText: String = ""
    private var buttonIcon: String? = null
    private val defaultTintColor = button.tintColor
    private val defaultTitleColor = button.titleColorForState(UIControlStateNormal)
    private val systemThemeListener: (ThemeMode) -> Unit = {
        applyButtonAppearance()
    }

    init {
        button.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        SystemThemeDetector.addSystemThemeListener(systemThemeListener)
        applyButtonAppearance()
    }

    override fun setText(text: String) {
        buttonText = text
        applyButtonAppearance()
    }

    override fun getText(): String = buttonText

    override fun setIcon(icon: String?) {
        buttonIcon = icon
        applyButtonAppearance()
    }

    override fun getIcon(): String? = buttonIcon

    override fun isIconOnly(): Boolean = buttonText.isEmpty() && !buttonIcon.isNullOrEmpty()

    override fun setBorderRadius(radius: BorderRadiusStyle?) {
        super.setBorderRadius(radius)
        applyButtonAppearance()
    }

    override fun onBootstrapAppearanceChanged() {
        applyButtonAppearance()
    }

    override fun dispose() {
        SystemThemeDetector.removeSystemThemeListener(systemThemeListener)
        super.dispose()
    }

    private fun isDarkModeActive(): Boolean {
        Application.current?.getResolvedTheme()?.let { return it == ThemeMode.DARK }
        val iosMode = IosApplicationImpl.activeInstance?.getThemeMode()
        if (iosMode == ThemeMode.DARK) return true
        if (iosMode == ThemeMode.LIGHT) return false
        return UikitGeometryAndColorUtil.isDarkActive()
    }

    private fun applyButtonAppearance() {
        val type = getType() ?: BootstrapType.NONE
        val outline = isOutline()
        val borderRadius = getBorderRadius()
        val iconImage = UikitGeometryAndColorUtil.systemIconImage(buttonIcon)

        if (type == BootstrapType.NONE &&
            !outline &&
            (borderRadius == null || borderRadius == BorderRadiusStyle.NONE)
        ) {
            button.configuration = null
            button.tintColor = defaultTintColor
            button.backgroundColor = null
            button.setBackgroundImage(null, forState = UIControlStateNormal)
            button.setTitle(buttonText, forState = UIControlStateNormal)
            button.setImage(iconImage, forState = UIControlStateNormal)
            button.setTitleColor(defaultTitleColor ?: defaultTintColor, forState = UIControlStateNormal)
            return
        }

        val darkMode = isDarkModeActive()
        val themeConfig = IosApplicationImpl.activeInstance?.getThemeConfig()
            ?: BootstrapColorPalette.activeThemeConfig

        val config = when {
            type == BootstrapType.LINK -> {
                val linkColor = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, darkMode, themeConfig).toUiColor()
                button.tintColor = linkColor
                UIButtonConfiguration.plainButtonConfiguration().apply {
                    baseForegroundColor = linkColor
                }
            }
            outline -> {
                val accentUiColor = if (type == BootstrapType.NONE) {
                    BootstrapColorPalette.resolveForegroundOn(BootstrapType.NONE, darkMode, themeConfig).toUiColor()
                } else {
                    BootstrapColorPalette.resolveColor(type, darkMode, themeConfig).toUiColor()
                }
                button.tintColor = accentUiColor
                UIButtonConfiguration.plainButtonConfiguration().apply {
                    baseForegroundColor = accentUiColor
                    val bg = background
                    bg.strokeColor = accentUiColor
                    bg.strokeWidth = 1.5
                    bg.backgroundColor = UIColor.clearColor
                    background = bg
                }
            }
            type == BootstrapType.NONE -> {
                val fgUiColor = BootstrapColorPalette.resolveForegroundOn(BootstrapType.NONE, darkMode, themeConfig).toUiColor()
                button.tintColor = defaultTintColor
                UIButtonConfiguration.grayButtonConfiguration().apply {
                    baseForegroundColor = fgUiColor
                }
            }
            else -> {
                val bgUiColor = BootstrapColorPalette.resolveColor(type, darkMode, themeConfig).toUiColor()
                val fgUiColor = BootstrapColorPalette.resolveForegroundOn(type, darkMode, themeConfig).toUiColor()
                button.tintColor = bgUiColor
                UIButtonConfiguration.filledButtonConfiguration().apply {
                    baseBackgroundColor = bgUiColor
                    baseForegroundColor = fgUiColor
                }
            }
        }

        config.cornerStyle = if (borderRadius == BorderRadiusStyle.PILL || borderRadius == BorderRadiusStyle.CIRCLE) {
            UIButtonConfigurationCornerStyleCapsule
        } else {
            UIButtonConfigurationCornerStyleDynamic
        }
        if (borderRadius != null && borderRadius != BorderRadiusStyle.PILL && borderRadius != BorderRadiusStyle.CIRCLE) {
            val bg = config.background
            bg.cornerRadius = UikitGeometryAndColorUtil.resolveBorderRadiusPt(borderRadius)
            config.background = bg
        }
        config.buttonSize = when (getSize()) {
            BootstrapSize.EXTRA_SMALL -> UIButtonConfigurationSize.UIButtonConfigurationSizeMini
            BootstrapSize.SMALL -> UIButtonConfigurationSize.UIButtonConfigurationSizeSmall
            BootstrapSize.LARGE, BootstrapSize.EXTRA_LARGE, BootstrapSize.EXTRA_EXTRA_LARGE ->
                UIButtonConfigurationSize.UIButtonConfigurationSizeLarge
            BootstrapSize.MEDIUM, null -> UIButtonConfigurationSize.UIButtonConfigurationSizeMedium
        }
        config.title = if (buttonText.isEmpty() && iconImage != null) null else buttonText
        config.image = iconImage
        if (iconImage != null && buttonText.isNotEmpty()) {
            config.imagePadding = 6.0
        }

        button.setBackgroundImage(null, forState = UIControlStateNormal)
        button.backgroundColor = null
        button.configuration = config
    }
}
