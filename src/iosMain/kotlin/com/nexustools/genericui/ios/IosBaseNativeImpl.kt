@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapSize
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.DisplayMode
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.bootstrap.OverflowBehavior
import com.nexustools.genericui.enums.bootstrap.PositionMode
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.bootstrap.TextOverflow
import com.nexustools.genericui.enums.bootstrap.TextTransform
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.setValue
import platform.UIKit.UIControl
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UIPointerInteraction
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationOptionCurveEaseInOut
import platform.UIKit.UIViewAnimationOptionCurveEaseOut
import platform.UIKit.UIViewAnimationOptionCurveLinear
import platform.UIKit.accessibilityLabel
import platform.UIKit.addInteraction
import platform.objc.sel_registerName
import platform.darwin.dispatch_after
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC

/**
 * Base real UIKit [NativeImpl] wrapping a genuine `platform.UIKit.UIView` inside [UikitViewHandle].
 *
 * Standard subclasses leave [allowsAestheticStyleSkinning] = `false` and [allowsTypographyStyleOverrides] = `false`
 * so `ComponentStyle` aesthetic skinning and non-text typography overrides are ignored on native iOS controls,
 * while styled containers/buttons and typography components opt in explicitly.
 */
open class IosBaseNativeImpl(
    override val nativeWidget: UikitViewHandle,
    override val componentClassPrefix: String
) : NativeImpl<UikitViewHandle> {

    protected val actionTarget: UikitControlActionTarget = UikitControlActionTarget(nativeWidget.uiView)
    private var pointerDelegate: UikitPointerInteractionDelegate? = null
    private var pointerInteraction: UIPointerInteraction? = null
    private var tapRecognizer: UITapGestureRecognizer? = null
    private var doubleTapRecognizer: UITapGestureRecognizer? = null
    private var longPressRecognizer: UILongPressGestureRecognizer? = null
    private var hoverRecognizer: UIHoverGestureRecognizer? = null

    protected open val allowsTypographyStyleOverrides: Boolean get() = false
    protected open val allowsAestheticStyleSkinning: Boolean get() = false

    override var isDisposed: Boolean = false

    private val classTokens = mutableListOf<String>()
    private var bootstrapType: BootstrapType? = null
    private var outlineMode: Boolean = false
    private var bootstrapSize: BootstrapSize? = null
    private var bgType: BootstrapType? = null
    private var textType: BootstrapType? = null
    private var borderType: BootstrapType? = null
    private var textAlignment: TextAlignment? = null
    private var fontWeight: FontWeight? = null
    private var textTransform: TextTransform? = null
    private var textOverflow: TextOverflow? = null
    private var borderRadius: BorderRadiusStyle? = null
    private var shadowElevation: ShadowElevation? = null
    private var displayMode: DisplayMode? = null
    private var flexDirection: FlexDirection? = null
    private var flexWrap: FlexWrap? = null
    private var justifyContent: JustifyContent? = null
    private var alignItems: AlignItems? = null
    private var alignSelf: AlignSelf? = null
    private var overflowBehavior: OverflowBehavior? = null
    private var positionMode: PositionMode? = null
    private var marginAll: SpacingScale? = null
    private var paddingAll: SpacingScale? = null
    private var tooltipText: String? = null
    private var preferredSize: Size? = null
    private var padding: EdgeInsets = EdgeInsets(0, 0, 0, 0)
    private var margin: EdgeInsets = EdgeInsets(0, 0, 0, 0)
    private var backgroundGradient: Gradient? = null
    private var hovered: Boolean = false
    private var focused: Boolean = false
    private var activePressed: Boolean = false
    private var customCursor: CssCursorValue? = null
    private var configuredTransitions: List<CssTransitionSpec> = emptyList()

    var onKeyDownListener: ((KeyEvent) -> Unit)? = null
        protected set
    var onKeyUpListener: ((KeyEvent) -> Unit)? = null
        protected set
    var onPointerDownListener: ((PointerEvent) -> Unit)? = null
        protected set
    var onPointerMoveListener: ((PointerEvent) -> Unit)? = null
        protected set
    var onPointerUpListener: ((PointerEvent) -> Unit)? = null
        protected set
    var onWheelListener: ((WheelEvent) -> Unit)? = null
        protected set
    var onBeforeFrameListener: ((Long, Double?) -> Unit)? = null
        protected set

    protected val uiView: UIView get() = nativeWidget.uiView

    override fun setClass(className: String) {
        classTokens.clear()
        for (token in className.trim().split(Regex("\\s+"))) {
            if (token.isNotEmpty() && !classTokens.contains(token)) {
                classTokens += token
            }
        }
    }

    override fun getClass(): String = classTokens.joinToString(" ")

    override fun getClasses(): Set<String> = classTokens.toSet()

    override fun addClass(className: String) {
        for (token in className.trim().split(Regex("\\s+"))) {
            if (token.isNotEmpty() && !classTokens.contains(token)) {
                classTokens += token
            }
        }
    }

    override fun removeClass(className: String) {
        for (token in className.trim().split(Regex("\\s+"))) {
            classTokens.remove(token)
        }
    }

    override fun hasClass(className: String): Boolean = classTokens.contains(className.trim())

    override fun setType(type: BootstrapType?) {
        bootstrapType = type
        onBootstrapAppearanceChanged()
    }

    override fun getType(): BootstrapType? = bootstrapType

    override fun setOutline(outline: Boolean) {
        outlineMode = outline
        onBootstrapAppearanceChanged()
    }

    override fun isOutline(): Boolean = outlineMode

    override fun setSize(size: BootstrapSize?) {
        bootstrapSize = size
        onBootstrapAppearanceChanged()
    }

    override fun getSize(): BootstrapSize? = bootstrapSize

    override fun setBackgroundType(type: BootstrapType?) {
        bgType = type
        onBootstrapAppearanceChanged()
    }

    override fun getBackgroundType(): BootstrapType? = bgType

    override fun setTextColorType(type: BootstrapType?) {
        textType = type
        onBootstrapAppearanceChanged()
    }

    override fun getTextColorType(): BootstrapType? = textType

    override fun setBorderType(type: BootstrapType?) {
        borderType = type
        onBootstrapAppearanceChanged()
    }

    override fun getBorderType(): BootstrapType? = borderType

    protected open fun onBootstrapAppearanceChanged() {
        val bg = UikitGeometryAndColorUtil.resolveBootstrapColor(bgType)
        if (bg != null) {
            uiView.backgroundColor = bg.toUiColor()
        }
        val border = UikitGeometryAndColorUtil.resolveBootstrapColor(borderType)
        if (border != null) {
            uiView.layer.borderColor = border.toCgColor()
            if (uiView.layer.borderWidth <= 0.0) {
                uiView.layer.borderWidth = 1.0
            }
        }
    }

    override fun setTextAlignment(alignment: TextAlignment?) {
        textAlignment = alignment
    }

    override fun getTextAlignment(): TextAlignment? = textAlignment

    override fun setFontWeight(weight: FontWeight?) {
        fontWeight = weight
    }

    override fun getFontWeight(): FontWeight? = fontWeight

    override fun setTextTransform(transform: TextTransform?) {
        textTransform = transform
    }

    override fun getTextTransform(): TextTransform? = textTransform

    override fun setTextOverflow(overflow: TextOverflow?) {
        textOverflow = overflow
    }

    override fun getTextOverflow(): TextOverflow? = textOverflow

    override fun setBorderRadius(radius: BorderRadiusStyle?) {
        borderRadius = radius
        if (allowsAestheticStyleSkinning && radius != null) {
            uiView.layer.cornerRadius = UikitGeometryAndColorUtil.resolveBorderRadiusPt(radius)
            uiView.clipsToBounds = radius != BorderRadiusStyle.NONE
        }
    }

    override fun getBorderRadius(): BorderRadiusStyle? = borderRadius

    override fun setShadow(shadow: ShadowElevation?) {
        shadowElevation = shadow
        if (allowsAestheticStyleSkinning) {
            val (opacity, radius, offsetY) = UikitGeometryAndColorUtil.resolveShadow(shadow)
            uiView.layer.shadowOpacity = opacity
            uiView.layer.shadowRadius = radius
            uiView.layer.shadowOffset = CGSizeMake(0.0, offsetY)
        }
    }

    override fun getShadow(): ShadowElevation? = shadowElevation

    override fun setDisplay(display: DisplayMode?) {
        displayMode = display
        if (display == DisplayMode.NONE) {
            uiView.hidden = true
        }
        (uiView.superview as? UikitFlexContainerView)?.setNeedsLayout()
    }

    override fun getDisplay(): DisplayMode? = displayMode

    override fun setFlexDirection(direction: FlexDirection?) {
        flexDirection = direction
        (uiView as? UikitFlexContainerView)?.let {
            it.flexDirection = direction ?: FlexDirection.COLUMN
            it.setNeedsLayout()
        }
    }

    override fun getFlexDirection(): FlexDirection? = flexDirection

    override fun setFlexWrap(wrap: FlexWrap?) {
        flexWrap = wrap
        (uiView as? UikitFlexContainerView)?.let {
            it.wrapLines = wrap != null && wrap != FlexWrap.NOWRAP
            it.setNeedsLayout()
        }
    }

    override fun getFlexWrap(): FlexWrap? = flexWrap

    override fun setJustifyContent(justify: JustifyContent?) {
        justifyContent = justify
        (uiView as? UikitFlexContainerView)?.let {
            it.justifyContent = justify ?: JustifyContent.START
            it.setNeedsLayout()
        }
    }

    override fun getJustifyContent(): JustifyContent? = justifyContent

    override fun setAlignItems(align: AlignItems?) {
        alignItems = align
        (uiView as? UikitFlexContainerView)?.let {
            it.alignItems = align ?: AlignItems.STRETCH
            it.setNeedsLayout()
        }
    }

    override fun getAlignItems(): AlignItems? = alignItems

    override fun setAlignSelf(align: AlignSelf?) {
        alignSelf = align
        uiView.superview?.setNeedsLayout()
    }

    override fun getAlignSelf(): AlignSelf? = alignSelf

    override fun setOverflow(overflow: OverflowBehavior?) {
        overflowBehavior = overflow
        uiView.clipsToBounds = overflow == OverflowBehavior.HIDDEN ||
            overflow == OverflowBehavior.SCROLL ||
            overflow == OverflowBehavior.AUTO
    }

    override fun getOverflow(): OverflowBehavior? = overflowBehavior

    override fun setPosition(position: PositionMode?) {
        positionMode = position
    }

    override fun getPosition(): PositionMode? = positionMode

    override fun setMarginAll(scale: SpacingScale?) {
        marginAll = scale
        val pt = UikitGeometryAndColorUtil.resolveSpacingPt(scale).toInt()
        setMargin(EdgeInsets(pt, pt, pt, pt))
    }

    override fun getMarginAll(): SpacingScale? = marginAll

    override fun setPaddingAll(scale: SpacingScale?) {
        paddingAll = scale
        val pt = UikitGeometryAndColorUtil.resolveSpacingPt(scale).toInt()
        setPadding(EdgeInsets(pt, pt, pt, pt))
    }

    override fun getPaddingAll(): SpacingScale? = paddingAll

    override fun setVisible(visible: Boolean) {
        uiView.hidden = !visible
        uiView.superview?.setNeedsLayout()
    }

    override fun isVisible(): Boolean = !uiView.hidden

    override fun setEnabled(enabled: Boolean) {
        uiView.userInteractionEnabled = enabled
        (uiView as? UIControl)?.enabled = enabled
        uiView.alpha = if (enabled) 1.0 else 0.55
    }

    override fun isEnabled(): Boolean =
        (uiView as? UIControl)?.enabled ?: uiView.userInteractionEnabled

    override fun setOpacity(opacity: Float) {
        uiView.alpha = opacity.coerceIn(0f, 1f).toDouble()
    }

    override fun getOpacity(): Float = uiView.alpha.toFloat()

    override fun setTooltipText(tooltip: String?) {
        tooltipText = tooltip
    }

    override fun getTooltipText(): String? = tooltipText

    override fun setAttribute(name: String, value: String?) {
        when (name.lowercase()) {
            "id", "accessibilityidentifier" -> {
                if (uiView.respondsToSelector(sel_registerName("setAccessibilityIdentifier:"))) {
                    uiView.setValue(value, forKey = "accessibilityIdentifier")
                }
            }
            "accessibilitylabel", "aria-label" -> uiView.accessibilityLabel = value
        }
    }

    override fun setPreferredSize(size: Size?) {
        preferredSize = size
        if (size != null) {
            uiView.frame.useContents {
                uiView.setFrame(CGRectMake(origin.x, origin.y, size.width.toDouble(), size.height.toDouble()))
            }
            (uiView.superview as? UikitFlexContainerView)?.setChildPreferredSize(
                uiView,
                size.width.toDouble(),
                size.height.toDouble()
            )
        }
    }

    override fun getBounds(): Rect = uiView.frame.useContents {
        Rect(
            x = origin.x.toInt(),
            y = origin.y.toInt(),
            width = size.width.toInt(),
            height = size.height.toInt()
        )
    }

    override fun setPadding(padding: EdgeInsets) {
        this.padding = padding
        (uiView as? UikitFlexContainerView)?.let {
            it.paddingInsets = padding
            it.setNeedsLayout()
        }
    }

    override fun setMargin(margin: EdgeInsets) {
        this.margin = margin
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        val filtered = style.copyFilteredForSystemComponent(
            allowTypography = allowsTypographyStyleOverrides,
            allowAestheticSkin = allowsAestheticStyleSkinning
        )
        filtered.visibility?.let { vis ->
            uiView.hidden = vis == CssVisibility.HIDDEN || vis == CssVisibility.COLLAPSE
        }
        filtered.pointerEventsEnabled.let { pe ->
            uiView.userInteractionEnabled = pe
        }
        if (allowsAestheticStyleSkinning) {
            uiView.alpha = filtered.opacity.coerceIn(0f, 1f).toDouble()
            filtered.backgroundColor?.let { uiView.backgroundColor = it.toUiColor() }
            filtered.borderColor?.let { uiView.layer.borderColor = it.toCgColor() }
            filtered.borderWidthPx?.let { uiView.layer.borderWidth = it.toDouble() }
            val radiusPt = filtered.borderRadiusPx ?: filtered.borderRadius?.topLeft?.value
            if (radiusPt != null) {
                uiView.layer.cornerRadius = radiusPt.toDouble()
                uiView.clipsToBounds = radiusPt > 0f
            }
            filtered.backgroundGradient?.let { setBackgroundGradient(it) }
            filtered.customCursor?.let { setCustomCursor(it) }
            filtered.boxShadowList?.shadows?.firstOrNull()?.let { shadow ->
                uiView.layer.shadowColor = shadow.color.toCgColor()
                uiView.layer.shadowOpacity = 0.25f
                uiView.layer.shadowRadius = shadow.blurRadius.value.toDouble()
                uiView.layer.shadowOffset = CGSizeMake(shadow.offsetX.value.toDouble(), shadow.offsetY.value.toDouble())
            }
        }
        applyTypographyFromStyle(filtered)
    }

    protected open fun applyTypographyFromStyle(style: ComputedStyle) {}

    override fun applyComponentStyle(style: ComponentStyle) {
        applyComputedStyle(
            style.toComputedStyle().resolveForState(
                hovered = hovered,
                focused = focused,
                active = activePressed,
                enabled = isEnabled()
            )
        )
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        setType(bootstrap.getType())
        setOutline(bootstrap.isOutline())
        setSize(bootstrap.getSize())
        setBackgroundType(bootstrap.getBackgroundType())
        setTextColorType(bootstrap.getTextColorType())
        setBorderType(bootstrap.getBorderType())
        setTextAlignment(bootstrap.getTextAlignment())
        setFontWeight(bootstrap.getFontWeight())
        setTextTransform(bootstrap.getTextTransform())
        setTextOverflow(bootstrap.getTextOverflow())
        setBorderRadius(bootstrap.getBorderRadius())
        setShadow(bootstrap.getShadow())
        setDisplay(bootstrap.getDisplay())
        setFlexDirection(bootstrap.getFlexDirection())
        setFlexWrap(bootstrap.getFlexWrap())
        setJustifyContent(bootstrap.getJustifyContent())
        setAlignItems(bootstrap.getAlignItems())
        setAlignSelf(bootstrap.getAlignSelf())
        setOverflow(bootstrap.getOverflow())
        setPosition(bootstrap.getPosition())
        setMarginAll(bootstrap.getMargin())
        setPaddingAll(bootstrap.getPadding())
    }

    override fun setCustomCursor(cursor: CssCursorValue?) {
        customCursor = cursor
        if (pointerDelegate == null) {
            val del = UikitPointerInteractionDelegate(uiView, cursor)
            val interaction = UIPointerInteraction(delegate = del).takeIf { (it as Any?) != null }
            if (interaction != null) {
                uiView.addInteraction(interaction)
                pointerInteraction = interaction
            }
            pointerDelegate = del
        } else {
            pointerDelegate?.cursorValue = cursor
            pointerInteraction?.invalidate()
        }
    }

    override fun setBackgroundGradient(gradient: Gradient?) {
        backgroundGradient = gradient
    }

    override fun getBackgroundGradient(): Gradient? = backgroundGradient

    override fun setHoveredState(hovered: Boolean) {
        this.hovered = hovered
    }

    override fun isHoveredState(): Boolean = hovered

    override fun setFocusedState(focused: Boolean) {
        this.focused = focused
        if (focused) {
            uiView.becomeFirstResponder()
        } else if (uiView.isFirstResponder) {
            uiView.resignFirstResponder()
        }
    }

    override fun isFocusedState(): Boolean = focused

    override fun setActivePressedState(active: Boolean) {
        this.activePressed = active
    }

    override fun isActivePressedState(): Boolean = activePressed

    override fun supportsNativeTransitions(): Boolean = true

    override fun configureNativeTransitions(specs: List<CssTransitionSpec>) {
        configuredTransitions = specs
    }

    override fun runNativeTransition(
        property: String,
        fromValue: String?,
        toValue: String,
        spec: CssTransitionSpec,
        onComplete: () -> Unit
    ): Boolean {
        val durationSec = spec.durationMs.toDouble().coerceAtLeast(1.0) / 1000.0
        val timing = spec.timingFunction.toCssString().lowercase()
        val options = when {
            spec.timingFunction.isLinear || timing.contains("linear") -> UIViewAnimationOptionCurveLinear
            timing.contains("ease-out") -> UIViewAnimationOptionCurveEaseOut
            else -> UIViewAnimationOptionCurveEaseInOut
        }
        val targetVal = toValue.trim().toDoubleOrNull()
        UIView.animateWithDuration(
            duration = durationSec,
            delay = spec.delayMs.toDouble().coerceAtLeast(0.0) / 1000.0,
            options = options,
            animations = {
                when (property.lowercase()) {
                    "opacity" -> if (targetVal != null) uiView.alpha = targetVal.coerceIn(0.0, 1.0)
                    "transform", "scale" -> if (targetVal != null) {
                        uiView.setTransform(CGAffineTransformMakeScale(targetVal, targetVal))
                    }
                    "background-color" -> {
                        if (toValue.startsWith("#")) {
                            parseHexColor(toValue)?.let { uiView.backgroundColor = it.toUiColor() }
                        }
                    }
                }
            },
            completion = { _ -> onComplete() }
        )
        return true
    }

    override fun scheduleDelayed(delayMs: Int, action: () -> Unit): Boolean {
        val ns = delayMs.coerceAtLeast(0).toLong() * NSEC_PER_MSEC.toLong()
        dispatch_after(dispatch_time(0uL, ns), dispatch_get_main_queue()) {
            if (!isDisposed) action()
        }
        return true
    }

    override fun setOnBeforeTargetFramePresent(listener: ((frameSwapCount: Long, timestampMs: Double?) -> Unit)?) {
        onBeforeFrameListener = listener
    }

    override fun setOnClick(listener: (() -> Unit)?) {
        actionTarget.onClick = listener
        if (listener != null && tapRecognizer == null && uiView !is UIControl) {
            val tap = UITapGestureRecognizer(target = actionTarget, action = UikitControlActionTarget.SEL_SINGLE_TAP)
            tap.numberOfTapsRequired = 1uL
            uiView.addGestureRecognizer(tap)
            tapRecognizer = tap
        } else if (uiView is UIControl) {
            actionTarget.onPrimaryAction = listener
        }
    }

    override fun setOnDoubleClick(listener: (() -> Unit)?) {
        actionTarget.onDoubleClick = listener
        if (listener != null && doubleTapRecognizer == null) {
            val tap = UITapGestureRecognizer(target = actionTarget, action = UikitControlActionTarget.SEL_DOUBLE_TAP)
            tap.numberOfTapsRequired = 2uL
            uiView.addGestureRecognizer(tap)
            doubleTapRecognizer = tap
        }
    }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        actionTarget.onContextMenu = listener
        if (listener != null && longPressRecognizer == null) {
            val press = UILongPressGestureRecognizer(target = actionTarget, action = UikitControlActionTarget.SEL_LONG_PRESS)
            uiView.addGestureRecognizer(press)
            longPressRecognizer = press
        }
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        onPointerDownListener = listener
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        onPointerMoveListener = listener
        actionTarget.onPointerMove = listener
        ensureHoverRecognizerIfNeeded()
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        onPointerUpListener = listener
    }

    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) {
        actionTarget.onPointerEnter = listener
        ensureHoverRecognizerIfNeeded()
    }

    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) {
        actionTarget.onPointerLeave = listener
        ensureHoverRecognizerIfNeeded()
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) {
        onWheelListener = listener
    }

    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) {
        onKeyDownListener = listener
    }

    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) {
        onKeyUpListener = listener
    }

    private fun ensureHoverRecognizerIfNeeded() {
        if (hoverRecognizer == null) {
            val hover = UIHoverGestureRecognizer(target = actionTarget, action = UikitControlActionTarget.SEL_HOVER)
            uiView.addGestureRecognizer(hover)
            hoverRecognizer = hover
        }
    }

    override fun dispose() {
        if (isDisposed) return
        isDisposed = true
        uiView.removeFromSuperview()
    }

    private fun parseHexColor(hex: String): com.nexustools.genericui.style.color.RgbaColor? {
        val clean = hex.trim().removePrefix("#")
        if (clean.length != 6 && clean.length != 8) return null
        val r = clean.substring(0, 2).toIntOrNull(16) ?: return null
        val g = clean.substring(2, 4).toIntOrNull(16) ?: return null
        val b = clean.substring(4, 6).toIntOrNull(16) ?: return null
        val a = if (clean.length == 8) clean.substring(6, 8).toIntOrNull(16) ?: 255 else 255
        return com.nexustools.genericui.style.color.RgbaColor(r, g, b, a)
    }
}
