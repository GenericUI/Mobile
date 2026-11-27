@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.layout.LayoutBackendTarget
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.form.StyledTextInputNativeImpl
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.system.style.StyledWidgetVisualController
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.NSLineBreakByWordWrapping
import platform.UIKit.UIColor
import platform.UIKit.UIEdgeInsetsMake
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UILabel
import platform.UIKit.UITextBorderStyle
import platform.UIKit.UIView
import platform.UIKit.clearColor
import kotlin.math.max
import kotlin.math.min

/**
 * Full-fidelity CSS aesthetic UIKit text input (`StyledTextField` / `StyledTextArea`) extending
 * [IosTextInputImpl] with `:system`'s [StyledWidgetVisualController] (`allowsBootstrapTypeTint = false`),
 * `:apple`'s [com.nexustools.genericui.apple.style.AppleStyledSurfaceHelper] (via [IosStyledVisualUtil]),
 * normalized linear/radial gradients, pill corner radii, state visuals (`base`, `hover`, `focus`,
 * `active`, `disabled`), custom fonts, placeholder colors, focus rings, and [UikitBackdropBlurController].
 */
class IosStyledTextInputImpl(
    multiline: Boolean = false,
    classPrefix: String = if (multiline) "styled-textarea" else "styled-textfield",
    val layoutTarget: LayoutBackendTarget = LayoutBackendTarget.IOS
) : IosTextInputImpl(
    initialMultiline = multiline,
    classPrefix = classPrefix
), StyledTextInputNativeImpl<UikitViewHandle> {

    override val allowsAestheticStyleSkinning: Boolean = true
    override val allowsTypographyStyleOverrides: Boolean = true

    private val visualController = StyledWidgetVisualController(
        allowsBootstrapTypeTint = false
    ).apply {
        componentClassPrefix = classPrefix
    }
    private val chromeState = IosStyledVisualUtil.ChromeLayersState()
    private val labeledSurfaceView = UikitFlexContainerView(
        CGRectMake(0.0, 0.0, 240.0, if (multiline) 96.0 else 40.0)
    ).apply {
        flexDirection = FlexDirection.COLUMN
        alignItems = AlignItems.STRETCH
    }
    private var hostBackdropController: UikitBackdropBlurController? = null
    private var labeledBackdropController: UikitBackdropBlurController? = null
    private var hoverRecognizerAttached: Boolean = false
    private var styledInitialized: Boolean = false
    private var isSyncingLayoutChrome: Boolean = false
    private var lastLayoutWidthPt: Double = -1.0
    private var lastLayoutHeightPt: Double = -1.0

    private val multilinePlaceholderLabel = UILabel(frame = CGRectMake(0.0, 0.0, 220.0, 22.0)).apply {
        userInteractionEnabled = false
        numberOfLines = 0L
        lineBreakMode = NSLineBreakByWordWrapping
        hidden = true
    }

    init {
        textField.borderStyle = UITextBorderStyle.UITextBorderStyleNone
        textField.backgroundColor = UIColor.clearColor
        textField.clipsToBounds = false

        textView.backgroundColor = UIColor.clearColor
        textView.layer.borderWidth = 0.0
        textView.layer.borderColor = null
        textView.layer.cornerRadius = 0.0
        textView.textContainerInset = UIEdgeInsetsMake(0.0, 0.0, 0.0, 0.0)
        textView.textContainer.lineFragmentPadding = 0.0
        textView.addSubview(multilinePlaceholderLabel)

        actionTarget.onHoveredChanged = { hovered ->
            setHoveredState(hovered)
        }
        if (!hoverRecognizerAttached) {
            hoverRecognizerAttached = true
            val hover = UIHoverGestureRecognizer(
                target = actionTarget,
                action = UikitControlActionTarget.SEL_HOVER
            )
            hostContainer.addGestureRecognizer(hover)
        }

        val layoutRefresh: () -> Unit = {
            if (!isSyncingLayoutChrome) {
                val activeSurf = activeSurfaceView()
                val dims = activeSurf.bounds.useContents { size.width to size.height }
                updateMultilinePlaceholderLayout()
                if (dims.first != lastLayoutWidthPt || dims.second != lastLayoutHeightPt) {
                    lastLayoutWidthPt = dims.first
                    lastLayoutHeightPt = dims.second
                    applyResolvedVisuals()
                }
            }
        }
        hostContainer.onLayoutSubviewsCallback = layoutRefresh
        labeledSurfaceView.onLayoutSubviewsCallback = layoutRefresh

        styledInitialized = true
        rebuildInputSubviews()
        applyResolvedVisuals()
    }

    private fun activeSurfaceView(): UikitFlexContainerView =
        if (captionLabel != null) labeledSurfaceView else hostContainer

    private fun backdropControllerFor(surface: UikitFlexContainerView): UikitBackdropBlurController {
        if (surface === labeledSurfaceView) {
            labeledBackdropController?.let { return it }
            return UikitBackdropBlurController(labeledSurfaceView).also { labeledBackdropController = it }
        }
        hostBackdropController?.let { return it }
        return UikitBackdropBlurController(hostContainer).also { hostBackdropController = it }
    }

    private fun clearChromeFromView(view: UikitFlexContainerView, controller: UikitBackdropBlurController?) {
        view.backgroundColor = null
        view.paddingInsets = EdgeInsets.ZERO
        view.layer.cornerRadius = 0.0
        view.layer.borderWidth = 0.0
        view.layer.borderColor = null
        view.layer.shadowOpacity = 0f
        controller?.clear()
    }

    private fun updateMultilinePlaceholderLayout() {
        val dims = textView.bounds.useContents { size.width to size.height }
        val w = max(0.0, dims.first)
        val h = if (dims.second > 0.0) min(max(20.0, dims.second), 64.0) else 22.0
        multilinePlaceholderLabel.setFrame(CGRectMake(0.0, 0.0, w, h))
        multilinePlaceholderLabel.hidden = !multilineMode || currentValue.isNotEmpty() || placeholderValue.isEmpty()
    }

    override fun rebuildInputSubviews() {
        if (!styledInitialized) {
            super.rebuildInputSubviews()
            return
        }
        for (sub in hostContainer.subviews.filterIsInstance<UIView>()) {
            if (sub !== hostContainer.backdropChromeView) {
                sub.removeFromSuperview()
            }
        }
        for (sub in labeledSurfaceView.subviews.filterIsInstance<UIView>()) {
            if (sub !== labeledSurfaceView.backdropChromeView) {
                sub.removeFromSuperview()
            }
        }
        hostContainer.removeChildMetadata(textField)
        hostContainer.removeChildMetadata(textView)
        hostContainer.removeChildMetadata(labeledSurfaceView)
        labeledSurfaceView.removeChildMetadata(textField)
        labeledSurfaceView.removeChildMetadata(textView)

        val current = resolveCurrentVisuals()
        val inputView = if (multilineMode) textView else textField
        val defaultInputH = if (multilineMode) 96.0 else 40.0
        val explicitW = current.widthPx?.toDouble()
        val explicitH = current.heightPx?.toDouble()
        val minInputH = current.minHeightPx?.toDouble() ?: defaultInputH

        val caption = captionLabel
        if (caption != null) {
            clearChromeFromView(hostContainer, hostBackdropController)
            hostContainer.addSubview(caption)
            hostContainer.setChildPreferredSize(caption, explicitW ?: 240.0, 18.0)

            labeledSurfaceView.addSubview(inputView)
            val surfH = explicitH ?: max(minInputH, defaultInputH)
            if (explicitW != null) {
                hostContainer.setChildPreferredSize(labeledSurfaceView, explicitW, surfH)
            }
            hostContainer.addSubview(labeledSurfaceView)
            val outerH = surfH + 22.0
            hostContainer.frame.useContents {
                val w = explicitW ?: size.width.coerceAtLeast(240.0)
                hostContainer.setFrame(CGRectMake(origin.x, origin.y, w, outerH))
            }
        } else {
            clearChromeFromView(labeledSurfaceView, labeledBackdropController)
            hostContainer.addSubview(inputView)
            val surfH = explicitH ?: max(minInputH, defaultInputH)
            hostContainer.frame.useContents {
                val w = explicitW ?: size.width.coerceAtLeast(240.0)
                hostContainer.setFrame(CGRectMake(origin.x, origin.y, w, surfH))
            }
        }
        updateMultilinePlaceholderLayout()
        hostContainer.setNeedsLayout()
        labeledSurfaceView.setNeedsLayout()
        applyResolvedVisuals()
    }

    override fun setValue(value: String) {
        super.setValue(value)
        if (styledInitialized) {
            updateMultilinePlaceholderLayout()
        }
    }

    override fun setPlaceholder(placeholder: String) {
        super.setPlaceholder(placeholder)
        if (styledInitialized) {
            applyResolvedVisuals()
        }
    }

    override fun setPadding(padding: EdgeInsets) {
        super.setPadding(padding)
        if (styledInitialized) {
            applyResolvedVisuals()
        }
    }

    override fun setBaseVisuals(visuals: StyledVisualSpec) {
        syncControllerMetadata()
        visualController.baseVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getBaseVisuals(): StyledVisualSpec = visualController.baseVisuals

    override fun setHoverVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.hoverVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getHoverVisuals(): StyledVisualSpec? = visualController.hoverVisuals

    override fun setFocusVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.focusVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getFocusVisuals(): StyledVisualSpec? = visualController.focusVisuals

    override fun setActiveVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.activeVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getActiveVisuals(): StyledVisualSpec? = visualController.activeVisuals

    override fun setDisabledVisuals(visuals: StyledVisualSpec?) {
        syncControllerMetadata()
        visualController.disabledVisuals = visuals
        applyResolvedVisuals()
    }

    override fun getDisabledVisuals(): StyledVisualSpec? = visualController.disabledVisuals

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        visualController.enabled = isEnabled()
        if (!enabled) {
            super<IosTextInputImpl>.setHoveredState(false)
            super<IosTextInputImpl>.setFocusedState(false)
            super<IosTextInputImpl>.setActivePressedState(false)
        }
        applyResolvedVisuals()
    }

    override fun setHoveredState(hovered: Boolean) {
        super<IosTextInputImpl>.setHoveredState(hovered && isEnabled())
        visualController.hovered = isHoveredState()
        applyResolvedVisuals()
    }

    override fun setFocusedState(focused: Boolean) {
        val coerced = focused && isEnabled() && isFocusable()
        val changed = isFocusedState() != coerced
        super<IosTextInputImpl>.setFocusedState(coerced)
        visualController.focused = isFocusedState()
        if (changed) {
            applyResolvedVisuals()
        }
    }

    override fun setActivePressedState(active: Boolean) {
        super<IosTextInputImpl>.setActivePressedState(active && isEnabled())
        visualController.activePressed = isActivePressedState()
        applyResolvedVisuals()
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        super<IosTextInputImpl>.applyComponentBootstrap(bootstrap)
        syncControllerMetadata()
        applyResolvedVisuals()
    }

    override fun onBootstrapAppearanceChanged() {
        syncControllerMetadata()
        applyResolvedVisuals()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        super<IosTextInputImpl>.applyComputedStyle(style)
        visualController.lastComputedStyle = style
        applyResolvedVisuals()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        super<IosTextInputImpl>.applyComponentStyle(style)
        visualController.lastComponentStyle = style
        visualController.invalidate()
        applyResolvedVisuals()
    }

    override fun setBackgroundGradient(gradient: Gradient?) {
        super<IosTextInputImpl>.setBackgroundGradient(gradient)
        val surf = activeSurfaceView()
        chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
            surf,
            chromeState.gradientLayer,
            gradient ?: resolveCurrentVisuals().backgroundGradient
        )
    }

    override fun resolveCurrentVisuals(): StyledVisualSpec {
        syncControllerMetadata()
        return visualController.resolveCurrentVisuals(
            isDarkTheme = UikitGeometryAndColorUtil.isDarkActive(),
            backendTarget = layoutTarget,
            animatedOpacity = activeAnimatedOpacityOrNull()
        )
    }

    override fun applyTypographyFromStyle(style: ComputedStyle) {
        if (styledInitialized) {
            applyResolvedVisuals()
        }
    }

    private fun syncControllerMetadata() {
        visualController.isCustomStyleEngine = isInCustomStyleEngineSubtree()
        visualController.bootstrapSnapshot = snapshotForStyledVisuals()
        visualController.enabled = isEnabled()
        visualController.hovered = isHoveredState()
        visualController.focused = isFocusedState()
        visualController.activePressed = isActivePressedState()
        visualController.lastComponentStyle = rawLayoutComponentStyle
        visualController.lastComputedStyle = rawLayoutComputedStyle
    }

    private fun applyResolvedVisuals() {
        if (!styledInitialized || isSyncingLayoutChrome) return
        isSyncingLayoutChrome = true
        try {
            val v = resolveCurrentVisuals()
            val surf = activeSurfaceView()
            val pad = v.padding ?: getPadding().takeIf { it != EdgeInsets.ZERO } ?: EdgeInsets(8, 12, 8, 12)
            surf.paddingInsets = pad

            surf.backgroundColor = v.backgroundColor?.toUiColor()
            IosStyledVisualUtil.applyLayerChrome(
                view = surf,
                spec = v,
                isFocused = isFocusedState(),
                componentStyle = visualController.lastComponentStyle,
                chromeState = chromeState
            )
            chromeState.gradientLayer = IosStyledVisualUtil.applyGradientLayer(
                surf,
                chromeState.gradientLayer,
                v.backgroundGradient ?: getBackgroundGradient()
            )
            backdropControllerFor(surf).apply(
                visuals = v,
                cornerRadiusPt = surf.layer.cornerRadius,
                clipsToBounds = surf.clipsToBounds,
                effectiveFilter = visualController.resolveEffectiveBackdropFilter(v)
            )
            if (activeAnimatedOpacityOrNull() == null) {
                v.opacity?.let { setOpacity(it) }
            }

            val isDark = UikitGeometryAndColorUtil.isDarkActive()
            val resolvedFont = UikitGeometryAndColorUtil.resolveFont(
                sizePt = v.fontSizePx?.toDouble() ?: 15.0,
                weight = getFontWeight(),
                weightNumeric = v.fontWeightNumeric,
                fontFamily = v.fontFamily,
                italic = UikitGeometryAndColorUtil.fontWeightToCssItalic(visualController.lastComputedStyle?.fontStyle)
            )
            val fgColor = (v.textColor ?: if (isDark) RgbaColor(0xEC, 0xEF, 0xF4) else RgbaColor(0x1C, 0x1B, 0x1F)).toUiColor()
            val phColor = (v.placeholderColor ?: if (isDark) RgbaColor(0x7B, 0x84, 0x98) else RgbaColor(0x77, 0x7E, 0x8E)).toUiColor()

            textField.font = resolvedFont
            textField.textColor = fgColor
            textField.tintColor = fgColor
            textView.font = resolvedFont
            textView.textColor = fgColor
            textView.tintColor = fgColor

            if (placeholderValue.isNotEmpty()) {
                textField.attributedPlaceholder = UikitGeometryAndColorUtil.buildAttributedText(
                    text = placeholderValue,
                    font = resolvedFont,
                    color = phColor
                )
                multilinePlaceholderLabel.font = resolvedFont
                multilinePlaceholderLabel.textColor = phColor
                multilinePlaceholderLabel.text = placeholderValue
            } else {
                textField.attributedPlaceholder = null
                textField.placeholder = null
                multilinePlaceholderLabel.text = null
            }
            updateMultilinePlaceholderLayout()
        } finally {
            isSyncingLayoutChrome = false
        }
    }

    override fun dispose() {
        hostBackdropController?.clear()
        labeledBackdropController?.clear()
        super.dispose()
    }
}
