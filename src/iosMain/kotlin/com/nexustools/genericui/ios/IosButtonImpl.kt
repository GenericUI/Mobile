@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.action.ButtonNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal

/**
 * Native iOS `UIButton.buttonWithType(UIButtonTypeSystem)` implementation of [ButtonNativeImpl].
 *
 * - Leaves `UIButton` 100% unmodified when [BootstrapType] is `NONE` or `null`.
 * - When [BootstrapType] != `NONE`, renders an empty-title native UIKit `UIButton` offscreen
 *   into a `CGBitmapContext` via [IosControlTintRenderUtil], tints pixels with the active
 *   `ThemeConfig`, applies the tinted `UIImage` via `setBackgroundImage(..., UIControlStateNormal)`,
 *   and paints contrasting foreground text/icon colors on top.
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

    var lastOffscreenTintedPixels: IntArray? = null
        private set

    init {
        button.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        applyButtonAppearance()
    }

    override fun setText(text: String) {
        buttonText = text
        button.setTitle(text, forState = UIControlStateNormal)
    }

    override fun getText(): String = buttonText

    override fun setIcon(icon: String?) {
        buttonIcon = icon
        val image = UikitGeometryAndColorUtil.systemIconImage(icon)
        button.setImage(image, forState = UIControlStateNormal)
    }

    override fun getIcon(): String? = buttonIcon

    override fun isIconOnly(): Boolean = buttonText.isEmpty() && !buttonIcon.isNullOrEmpty()

    override fun onBootstrapAppearanceChanged() {
        applyButtonAppearance()
    }

    private fun applyButtonAppearance() {
        val type = getType() ?: BootstrapType.NONE
        if (type == BootstrapType.NONE) {
            button.setBackgroundImage(null, forState = UIControlStateNormal)
            button.setTitleColor(defaultTintColor, forState = UIControlStateNormal)
            button.tintColor = defaultTintColor
            button.backgroundColor = null
            lastOffscreenTintedPixels = null
            return
        }

        val (w, h) = button.bounds.useContents {
            val bw = size.width.toInt().coerceAtLeast(120)
            val bh = size.height.toInt().coerceAtLeast(44)
            bw to bh
        }
        val dark = UikitGeometryAndColorUtil.isDarkActive()
        val themeConfig = IosApplicationImpl.activeInstance?.getThemeConfig()
            ?: com.nexustools.genericui.style.color.BootstrapColorPalette.activeThemeConfig
        val (image, pixels) = IosControlTintRenderUtil.renderEmptyTextTintedButtonImage(
            width = w,
            height = h,
            type = type,
            darkMode = dark,
            outline = isOutline(),
            themeConfig = themeConfig
        )
        lastOffscreenTintedPixels = pixels
        button.setBackgroundImage(image, forState = UIControlStateNormal)
        val fg = IosControlTintRenderUtil.resolveContrastingForeground(
            type = type,
            darkMode = dark,
            outline = isOutline(),
            themeConfig = themeConfig
        )
        val fgColor = fg.toUiColor()
        button.setTitleColor(fgColor, forState = UIControlStateNormal)
        button.tintColor = fgColor
    }
}
