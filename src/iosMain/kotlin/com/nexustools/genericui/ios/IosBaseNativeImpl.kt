@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapPointerEvents
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
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.gradient.Gradient.Gradient
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.transition.CssTimingFunction
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import com.nexustools.genericui.threading.GenericUiThreading
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformMakeScale
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGAffineTransformRotate
import platform.CoreGraphics.CGAffineTransformScale
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSThread
import platform.Foundation.setValue
import platform.UIKit.UIControl
import platform.UIKit.UICubicTimingParameters
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UIPointerInteraction
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIViewAnimationCurve
import platform.UIKit.UIViewPropertyAnimator
import platform.UIKit.accessibilityLabel
import platform.UIKit.addInteraction
import platform.UIKit.endEditing
import platform.objc.sel_registerName
import platform.darwin.dispatch_after
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import platform.darwin.dispatch_time
import platform.darwin.NSEC_PER_MSEC
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Base real UIKit [NativeImpl] wrapping a genuine `platform.UIKit.UIView` inside [UikitViewHandle].
 *
 * Standard subclasses leave [allowsAestheticStyleSkinning] = `false` and [allowsTypographyStyleOverrides] = `false`
 * so `ComponentStyle` aesthetic skinning and non-text typography overrides are ignored on native iOS controls,
 * while styled containers/buttons and typography components opt in explicitly.
 */
open class IosBaseNativeImpl(
    override val nativeWidget: UikitViewHandle,
    override val componentClassPrefix: String,
    initialTagName: String = ""
) : NativeImpl<UikitViewHandle> {

    val componentTagName: String = initialTagName

    protected val actionTarget: UikitControlActionTarget = UikitControlActionTarget(nativeWidget.uiView)
    private var pointerDelegate: UikitPointerInteractionDelegate? = null
    private var pointerInteraction: UIPointerInteraction? = null
    private var tapRecognizer: UITapGestureRecognizer? = null
    private var doubleTapRecognizer: UITapGestureRecognizer? = null
    private var longPressRecognizer: UILongPressGestureRecognizer? = null
    private var hoverRecognizer: UIHoverGestureRecognizer? = null

    protected open val allowsTypographyStyleOverrides: Boolean get() = false
    protected open val allowsAestheticStyleSkinning: Boolean
        get() = AestheticStylePolicy.isExplicitlyStyledTagOrPrefix(componentTagName, componentClassPrefix) ||
            isInCustomStyleEngineSubtree()

    internal fun isInCustomStyleEngineSubtree(): Boolean {
        if (attributesMap["data-style-engine"] == "custom") return true
        var cur: UIView? = nativeWidget.uiView.superview
        while (cur != null) {
            if (UikitViewLayoutRegistry.get(cur).customStyleEngine) return true
            cur = cur.superview
        }
        return false
    }

    protected open fun allowsBootstrapNonControlColor(): Boolean =
        AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix(componentTagName, componentClassPrefix)

    protected open fun allowsBootstrapColorForStyledSpec(): Boolean =
        this is IosStyledButtonImpl || allowsBootstrapNonControlColor() || isInCustomStyleEngineSubtree()

    internal fun activeAnimatedOpacityOrNull(): Float? = activeOpacityTransitionTarget

    internal fun rawBootstrapStyleSnapshot(): BootstrapStyleSnapshot = BootstrapStyleSnapshot(
        componentPrefix = componentClassPrefix,
        type = bootstrapType,
        outline = outlineMode,
        backgroundType = bgType,
        textColorType = textType,
        borderType = borderType
    )

    internal fun snapshotForStyledVisuals(): BootstrapStyleSnapshot =
        if (allowsBootstrapColorForStyledSpec()) {
            BootstrapStyleSnapshot(
                componentPrefix = componentClassPrefix,
                type = bootstrapType,
                outline = outlineMode,
                backgroundType = bgType,
                textColorType = textType,
                borderType = borderType
            )
        } else {
            BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)
        }

    internal fun sanitizeIncomingStyledVisuals(spec: StyledVisualSpec): StyledVisualSpec {
        if (allowsBootstrapColorForStyledSpec()) return spec
        val semType = bootstrapType?.takeIf { it.isSemantic }
            ?: bgType?.takeIf { it.isSemantic }
        val txType = textType?.takeIf { it.isSemantic }
        val brType = borderType?.takeIf { it.isSemantic }
        if (semType == null && txType == null && brType == null) return spec

        val isDark = UikitGeometryAndColorUtil.isDarkActive()
        val explicitBg = rawLayoutComponentStyle?.getBackgroundColor() ?: rawLayoutComputedStyle?.backgroundColor
        val explicitFg = rawLayoutComponentStyle?.getTextColor() ?: rawLayoutComputedStyle?.textColor
        val explicitBorder = rawLayoutComponentStyle?.getBorderColor() ?: rawLayoutComputedStyle?.borderColor

        var sanitized = spec
        if (explicitBg == null && semType != null && !outlineMode) {
            val bsBg = BootstrapColorPalette.resolveColor(semType, isDark)
            if (sanitized.backgroundColor == bsBg) {
                sanitized = sanitized.copy(backgroundColor = null)
            }
        }
        if (explicitFg == null && (semType != null || txType != null)) {
            val bsFg = txType?.let { BootstrapColorPalette.resolveColor(it, isDark) }
                ?: semType?.let {
                    if (outlineMode) BootstrapColorPalette.resolveColor(it, isDark)
                    else BootstrapColorPalette.resolveForegroundOn(it)
                }
            if (bsFg != null && sanitized.textColor == bsFg) {
                sanitized = sanitized.copy(textColor = null)
            }
        }
        if (explicitBorder == null && (brType != null || (semType != null && outlineMode))) {
            val bsBorder = (brType ?: semType)?.let { BootstrapColorPalette.resolveColor(it, isDark) }
            if (bsBorder != null && sanitized.borderColor == bsBorder) {
                val clearWidth = rawLayoutComponentStyle?.borderWidthPx == null &&
                    rawLayoutComputedStyle?.borderWidthPx == null &&
                    sanitized.borderWidthPx == 1f
                sanitized = sanitized.copy(
                    borderColor = null,
                    borderWidthPx = if (clearWidth) null else sanitized.borderWidthPx,
                    borderStyle = if (clearWidth) null else sanitized.borderStyle
                )
            }
        }
        return sanitized
    }

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
    private var enabledState: Boolean = true
    private var focusableState: Boolean = true
    private var pointerEventsNoneState: Boolean = false
    private val attributesMap = linkedMapOf<String, String>()
    protected var rawLayoutComponentStyle: ComponentStyle? = null
    protected var rawLayoutComputedStyle: ComputedStyle? = null
    private var customCursor: CssCursorValue? = null
    private var configuredTransitions: List<CssTransitionSpec> = emptyList()
    private val activeAnimators = linkedMapOf<String, UIViewPropertyAnimator>()
    private val activeAnimatorCompletions = linkedMapOf<String, () -> Unit>()
    private var activeOpacityTransitionTarget: Float? = null
    private var activeOpacityTransitionFrom: Float? = null
    private var activeBackgroundColorTransitionTarget: RgbaColor? = null

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
        syncUserInteractionEnabled()
        syncLayoutMetadataToUikit()
    }

    override fun getClass(): String = classTokens.joinToString(" ")

    override fun getClasses(): Set<String> = classTokens.toSet()

    override fun addClass(className: String) {
        for (token in className.trim().split(Regex("\\s+"))) {
            if (token.isNotEmpty() && !classTokens.contains(token)) {
                classTokens += token
            }
        }
        syncUserInteractionEnabled()
        syncLayoutMetadataToUikit()
    }

    override fun removeClass(className: String) {
        for (token in className.trim().split(Regex("\\s+"))) {
            classTokens.remove(token)
        }
        syncUserInteractionEnabled()
        syncLayoutMetadataToUikit()
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
        val allowBsColor = allowsBootstrapNonControlColor() || isInCustomStyleEngineSubtree()
        if (!allowBsColor) return
        val bg = UikitGeometryAndColorUtil.resolveBootstrapColor(bgType ?: getType())
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
        syncLayoutMetadataToUikit()
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

    private fun isPointerEventsNoneFromAttributesOrClasses(): Boolean {
        if (attributesMap["pointer-events"]?.equals("none", ignoreCase = true) == true) return true
        if (attributesMap["layerPassThrough"]?.equals("true", ignoreCase = true) == true) return true
        if (classTokens.contains("gui-layer-pass-through")) return true
        if (rawLayoutComponentStyle?.getPointerEvents() == CssPointerEvents.NONE) return true
        if (rawLayoutComputedStyle?.pointerEventsEnabled == false) return true
        return false
    }

    protected fun syncUserInteractionEnabled() {
        pointerEventsNoneState = isPointerEventsNoneFromAttributesOrClasses()
        uiView.userInteractionEnabled = enabledState && !pointerEventsNoneState
    }

    override fun setEnabled(enabled: Boolean) {
        enabledState = enabled
        (uiView as? UIControl)?.enabled = enabled
        uiView.alpha = if (enabled) 1.0 else 0.55
        syncUserInteractionEnabled()
    }

    override fun isEnabled(): Boolean = enabledState

    protected open fun resolveFocusTargetUiView(): UIView = uiView

    override fun setFocusable(focusable: Boolean) {
        focusableState = focusable
        val target = resolveFocusTargetUiView()
        UikitFocusRegistry.setFocusable(uiView, focusable)
        if (target !== uiView) {
            UikitFocusRegistry.setFocusable(target, focusable)
        }
        if (!focusable) {
            setFocusedState(false)
            runOnMainThread {
                if (isDisposed) return@runOnMainThread
                uiView.endEditing(true)
                if (target.isFirstResponder) {
                    target.resignFirstResponder()
                }
                if (uiView.isFirstResponder) {
                    uiView.resignFirstResponder()
                }
                uiView.setNeedsFocusUpdate()
            }
        } else {
            runOnMainThread {
                if (isDisposed) return@runOnMainThread
                uiView.setNeedsFocusUpdate()
            }
        }
    }

    override fun isFocusable(): Boolean = focusableState

    override fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        val pendingTarget = activeOpacityTransitionTarget
        if (pendingTarget != null) {
            if (abs(clamped - pendingTarget) < 1e-4f) {
                return
            }
            cancelNativeAnimator("opacity")
        }
        uiView.alpha = clamped.toDouble()
    }

    override fun getOpacity(): Float {
        if (activeOpacityTransitionTarget != null) {
            val presentation = uiView.layer.presentationLayer()
            if (presentation != null) {
                return presentation.opacity
            }
            activeOpacityTransitionFrom?.let { return it }
        }
        return uiView.alpha.toFloat()
    }

    override fun setTooltipText(tooltip: String?) {
        tooltipText = tooltip
    }

    override fun getTooltipText(): String? = tooltipText

    override fun setAttribute(name: String, value: String?) {
        if (value == null) {
            attributesMap.remove(name)
        } else {
            attributesMap[name] = value
        }
        when (name.lowercase()) {
            "id", "accessibilityidentifier" -> {
                if (uiView.respondsToSelector(sel_registerName("setAccessibilityIdentifier:"))) {
                    uiView.setValue(value, forKey = "accessibilityIdentifier")
                }
            }
            "accessibilitylabel", "aria-label" -> uiView.accessibilityLabel = value
            "pointer-events", "layerpassthrough" -> {
                syncUserInteractionEnabled()
                syncLayoutMetadataToUikit()
            }
            "z-index", "zindex", "layerzindex", "data-style-engine" -> syncLayoutMetadataToUikit()
        }
    }

    override fun setMargin(margin: EdgeInsets) {
        this.margin = margin
        syncLayoutMetadataToUikit()
    }

    override fun setPreferredSize(size: Size?) {
        preferredSize = size
        if (size != null) {
            val savedTransform = uiView.transform
            uiView.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
            uiView.frame.useContents {
                uiView.setFrame(CGRectMake(origin.x, origin.y, size.width.toDouble(), size.height.toDouble()))
            }
            uiView.transform = savedTransform
            (uiView.superview as? UikitFlexContainerView)?.setChildPreferredSize(
                uiView,
                size.width.toDouble(),
                size.height.toDouble()
            )
        }
        syncLayoutMetadataToUikit()
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

    internal fun resolveEffectiveCssPosition(): CssPosition {
        rawLayoutComponentStyle?.getPosition()?.let { return it }
        when (rawLayoutComputedStyle?.position?.trim()?.lowercase()) {
            "relative" -> return CssPosition.RELATIVE
            "absolute" -> return CssPosition.ABSOLUTE
            "fixed" -> return CssPosition.FIXED
            "sticky" -> return CssPosition.STICKY
            "static" -> return CssPosition.STATIC
        }
        return when (positionMode) {
            PositionMode.RELATIVE -> CssPosition.RELATIVE
            PositionMode.ABSOLUTE -> CssPosition.ABSOLUTE
            PositionMode.FIXED -> CssPosition.FIXED
            PositionMode.STICKY -> CssPosition.STICKY
            PositionMode.STATIC, null -> CssPosition.STATIC
        }
    }

    internal fun resolveEffectiveTransform2D(): CssTransform2D {
        rawLayoutComponentStyle?.getTransform()?.let { return it }
        rawLayoutComputedStyle?.transform2D?.let { return it }
        return CssTransform2D.parse(rawLayoutComputedStyle?.transform)
    }

    internal fun syncLayoutMetadataToUikit() {
        val pos = resolveEffectiveCssPosition()
        val top = rawLayoutComponentStyle?.getTop() ?: rawLayoutComputedStyle?.top?.let { CssLength.parse(it) }
        val right = rawLayoutComponentStyle?.getRight() ?: rawLayoutComputedStyle?.right?.let { CssLength.parse(it) }
        val bottom = rawLayoutComponentStyle?.getBottom() ?: rawLayoutComputedStyle?.bottom?.let { CssLength.parse(it) }
        val left = rawLayoutComponentStyle?.getLeft() ?: rawLayoutComputedStyle?.left?.let { CssLength.parse(it) }
        val wLen = rawLayoutComponentStyle?.getWidth() ?: rawLayoutComputedStyle?.width?.let { CssLength.parse(it) }
        val hLen = rawLayoutComponentStyle?.getHeight() ?: rawLayoutComputedStyle?.height?.let { CssLength.parse(it) }
        val z = rawLayoutComponentStyle?.getZIndex()
            ?: rawLayoutComputedStyle?.zIndex
            ?: attributesMap["z-index"]?.toIntOrNull()
            ?: attributesMap["zIndex"]?.toIntOrNull()
            ?: attributesMap["layerZIndex"]?.toIntOrNull()
            ?: 0
        val tf = resolveEffectiveTransform2D()
        UikitViewLayoutRegistry.update(uiView) {
            UikitNodeLayoutMetadata(
                position = pos,
                top = top,
                right = right,
                bottom = bottom,
                left = left,
                width = wLen,
                height = hLen,
                margin = margin,
                zIndex = z,
                transform2D = tf,
                pointerEventsNone = pointerEventsNoneState,
                customStyleEngine = attributesMap["data-style-engine"] == "custom"
            )
        }
        uiView.layer.zPosition = z.toDouble()
        if (activeAnimators["transform"] == null) {
            val dims = uiView.bounds.useContents { size.width to size.height }
            uiView.transform = UikitViewLayoutRegistry.buildCgAffineTransform(tf, dims.first, dims.second)
        }
        (uiView.superview as? UikitFlexContainerView)?.setNeedsLayout()
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        rawLayoutComputedStyle = style
        val filtered = style.copyFilteredForSystemComponent(
            allowTypography = allowsTypographyStyleOverrides,
            allowAestheticSkin = allowsAestheticStyleSkinning
        )
        filtered.visibility?.let { vis ->
            uiView.hidden = vis == CssVisibility.HIDDEN || vis == CssVisibility.COLLAPSE
        }
        style.margin?.let { setMargin(it.toEdgeInsets()) }
        style.padding?.let { setPadding(it.toEdgeInsets()) }
        syncUserInteractionEnabled()
        syncLayoutMetadataToUikit()
        if (allowsAestheticStyleSkinning) {
            setOpacity(filtered.opacity)
            filtered.backgroundColor?.let { bg ->
                val pendingBg = activeBackgroundColorTransitionTarget
                if (pendingBg == null || bg != pendingBg) {
                    if (pendingBg != null) {
                        cancelNativeAnimator("background-color")
                    }
                    uiView.backgroundColor = bg.toUiColor()
                }
            }
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
        rawLayoutComponentStyle = style
        val resolvedComputed = style.toComputedStyle().resolveForState(
            hovered = hovered,
            focused = focused,
            active = activePressed,
            enabled = isEnabled()
        )
        applyComputedStyle(resolvedComputed)
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        bootstrap.getPointerEvents()?.let { pe ->
            if (pe == BootstrapPointerEvents.NONE) {
                attributesMap["pointer-events"] = "none"
            } else {
                attributesMap.remove("pointer-events")
            }
        }
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
        syncUserInteractionEnabled()
        syncLayoutMetadataToUikit()
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
        val coerced = if (!enabledState || !focusableState) false else focused
        this.focused = coerced
        val target = resolveFocusTargetUiView()
        if (coerced) {
            if (!target.isFirstResponder) {
                target.becomeFirstResponder()
            }
        } else if (target.isFirstResponder) {
            target.resignFirstResponder()
        }
    }

    override fun isFocusedState(): Boolean = if (!enabledState || !focusableState) false else focused

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
        if (isDisposed || (nativeWidget.uiView as Any?) == null) {
            return false
        }
        val rawProp = property.trim().lowercase()
        val canonicalKey = when (rawProp) {
            "opacity", "alpha" -> "opacity"
            "transform", "scale" -> "transform"
            "left", "top", "width", "height" -> rawProp
            "background-color" -> "background-color"
            else -> return false
        }

        val viewWidthPt = uiView.bounds.useContents { size.width }.roundToInt()
        val viewHeightPt = uiView.bounds.useContents { size.height }.roundToInt()

        var targetAlpha: Double? = null
        var fromAlpha: Double? = null
        var targetTransform: CValue<CGAffineTransform>? = null
        var fromTransform: CValue<CGAffineTransform>? = null
        var targetFrameScalar: Double? = null
        var fromFrameScalar: Double? = null
        var targetBgColor: RgbaColor? = null
        var fromBgColor: RgbaColor? = null

        when (canonicalKey) {
            "opacity" -> {
                targetAlpha = toValue.trim().toDoubleOrNull()?.coerceIn(0.0, 1.0) ?: return false
                fromAlpha = fromValue?.trim()?.toDoubleOrNull()?.coerceIn(0.0, 1.0)
            }
            "transform" -> {
                val isScaleOnly = rawProp == "scale"
                targetTransform = buildCgAffineTransform(toValue, isScaleOnly, viewWidthPt, viewHeightPt) ?: return false
                fromTransform = fromValue?.let { buildCgAffineTransform(it, isScaleOnly, viewWidthPt, viewHeightPt) }
            }
            "left", "top", "width", "height" -> {
                targetFrameScalar = parseCssPx(toValue) ?: return false
                fromFrameScalar = fromValue?.let { parseCssPx(it) }
            }
            "background-color" -> {
                if (!allowsAestheticStyleSkinning || backgroundGradient != null) return false
                targetBgColor = RgbaColor.fromCss(toValue) ?: return false
                fromBgColor = fromValue?.let { RgbaColor.fromCss(it) }
            }
        }

        var completionFired = false
        val fireCompletionOnce: () -> Unit = {
            if (!completionFired) {
                completionFired = true
                dispatchCompletionOnLogicThread(onComplete)
            }
        }

        if (canonicalKey == "opacity" && targetAlpha != null) {
            activeOpacityTransitionFrom = (fromAlpha ?: uiView.alpha).toFloat().coerceIn(0f, 1f)
            activeOpacityTransitionTarget = targetAlpha.toFloat()
        } else if (canonicalKey == "background-color" && targetBgColor != null) {
            activeBackgroundColorTransitionTarget = targetBgColor
        }

        val durationMs = spec.durationMs.toDouble().coerceAtLeast(0.0)
        val delayMs = spec.delayMs.toDouble().coerceAtLeast(0.0)

        runOnMainThread {
            if (isDisposed) {
                if (canonicalKey == "opacity") {
                    activeOpacityTransitionTarget = null
                    activeOpacityTransitionFrom = null
                }
                if (canonicalKey == "background-color") activeBackgroundColorTransitionTarget = null
                fireCompletionOnce()
                return@runOnMainThread
            }

            val hadActiveForProp = activeAnimators.containsKey(canonicalKey)
            if (hadActiveForProp) {
                val prevTargetOpacity = activeOpacityTransitionTarget
                val prevFromOpacity = activeOpacityTransitionFrom
                val prevTargetBg = activeBackgroundColorTransitionTarget
                cancelNativeAnimator(canonicalKey)
                if (canonicalKey == "opacity") {
                    activeOpacityTransitionTarget = prevTargetOpacity
                    activeOpacityTransitionFrom = prevFromOpacity
                }
                if (canonicalKey == "background-color") activeBackgroundColorTransitionTarget = prevTargetBg
            }

            val applyTargetValues: () -> Unit = {
                when (canonicalKey) {
                    "opacity" -> targetAlpha?.let { uiView.alpha = it }
                    "transform" -> targetTransform?.let { uiView.setTransform(it) }
                    "left" -> targetFrameScalar?.let { xPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(xPt, origin.y, size.width, size.height))
                        }
                    }
                    "top" -> targetFrameScalar?.let { yPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, yPt, size.width, size.height))
                        }
                    }
                    "width" -> targetFrameScalar?.let { wPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, origin.y, wPt.coerceAtLeast(0.0), size.height))
                        }
                    }
                    "height" -> targetFrameScalar?.let { hPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, origin.y, size.width, hPt.coerceAtLeast(0.0)))
                        }
                    }
                    "background-color" -> targetBgColor?.let { uiView.backgroundColor = it.toUiColor() }
                }
            }

            if (durationMs <= 0.0 && delayMs <= 0.0) {
                applyTargetValues()
                if (canonicalKey == "opacity") {
                    activeOpacityTransitionTarget = null
                    activeOpacityTransitionFrom = null
                }
                if (canonicalKey == "background-color") activeBackgroundColorTransitionTarget = null
                fireCompletionOnce()
                return@runOnMainThread
            }

            if (!hadActiveForProp) {
                when (canonicalKey) {
                    "opacity" -> fromAlpha?.let { uiView.alpha = it }
                    "transform" -> fromTransform?.let { uiView.setTransform(it) }
                    "left" -> fromFrameScalar?.let { xPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(xPt, origin.y, size.width, size.height))
                        }
                    }
                    "top" -> fromFrameScalar?.let { yPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, yPt, size.width, size.height))
                        }
                    }
                    "width" -> fromFrameScalar?.let { wPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, origin.y, wPt.coerceAtLeast(0.0), size.height))
                        }
                    }
                    "height" -> fromFrameScalar?.let { hPt ->
                        uiView.frame.useContents {
                            uiView.setFrame(CGRectMake(origin.x, origin.y, size.width, hPt.coerceAtLeast(0.0)))
                        }
                    }
                    "background-color" -> fromBgColor?.let { uiView.backgroundColor = it.toUiColor() }
                }
            }

            runCatching {
                val timingParams = createCubicTimingParameters(spec.timingFunction)
                val animator = UIViewPropertyAnimator(
                    duration = (durationMs / 1000.0).coerceAtLeast(0.001),
                    timingParameters = timingParams
                )
                activeAnimators[canonicalKey] = animator
                activeAnimatorCompletions[canonicalKey] = fireCompletionOnce
                animator.addAnimations {
                    applyTargetValues()
                }
                animator.addCompletion { _ ->
                    if (activeAnimators[canonicalKey] === animator) {
                        activeAnimators.remove(canonicalKey)
                        activeAnimatorCompletions.remove(canonicalKey)
                        if (canonicalKey == "opacity") {
                            activeOpacityTransitionTarget = null
                            activeOpacityTransitionFrom = null
                        } else if (canonicalKey == "background-color") {
                            activeBackgroundColorTransitionTarget = null
                        }
                    }
                    fireCompletionOnce()
                }
                val delaySec = delayMs / 1000.0
                if (delaySec > 0.0) {
                    animator.startAnimationAfterDelay(delaySec)
                } else {
                    animator.startAnimation()
                }
            }.onFailure {
                if (canonicalKey == "opacity") {
                    activeOpacityTransitionTarget = null
                    activeOpacityTransitionFrom = null
                }
                if (canonicalKey == "background-color") activeBackgroundColorTransitionTarget = null
                activeAnimators.remove(canonicalKey)
                activeAnimatorCompletions.remove(canonicalKey)
                applyTargetValues()
                fireCompletionOnce()
            }
        }
        return true
    }

    private fun createCubicTimingParameters(tf: CssTimingFunction): UICubicTimingParameters {
        if (tf.isLinear) {
            return UICubicTimingParameters(animationCurve = UIViewAnimationCurve.UIViewAnimationCurveLinear)
        }
        return UICubicTimingParameters(
            controlPoint1 = CGPointMake(tf.x1.toDouble().coerceIn(0.0, 1.0), tf.y1.toDouble()),
            controlPoint2 = CGPointMake(tf.x2.toDouble().coerceIn(0.0, 1.0), tf.y2.toDouble())
        )
    }

    private fun buildCgAffineTransform(
        raw: String,
        isScaleOnly: Boolean,
        widthPt: Int,
        heightPt: Int
    ): CValue<CGAffineTransform>? {
        val trimmed = raw.trim()
        if (isScaleOnly) {
            val s = trimmed.toDoubleOrNull() ?: return null
            return CGAffineTransformMakeScale(s, s)
        }
        val cleaned = trimmed.replace("!important", "", ignoreCase = true).trim()
        if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) {
            return CGAffineTransformMakeTranslation(0.0, 0.0)
        }
        if (!isSupportedCssTransformExpression(cleaned)) return null
        val tf = CssTransform2D.parse(cleaned)
        val (dx, dy) = tf.resolveTranslationPx(widthPt, heightPt)
        var result = CGAffineTransformMakeTranslation(dx.toDouble(), dy.toDouble())
        if (tf.rotateDeg != 0f) {
            result = CGAffineTransformRotate(result, tf.rotateDeg.toDouble() * PI / 180.0)
        }
        if (tf.scaleX != 1f || tf.scaleY != 1f) {
            result = CGAffineTransformScale(result, tf.scaleX.toDouble(), tf.scaleY.toDouble())
        }
        return result
    }

    private fun isSupportedCssTransformExpression(cleaned: String): Boolean {
        val fnRegex = Regex("""([a-zA-Z0-9]+)\(([^)]*)\)""")
        val matches = fnRegex.findAll(cleaned).toList()
        if (matches.isEmpty()) return false
        for (m in matches) {
            if (m.groupValues[1].lowercase() !in SUPPORTED_TRANSFORM_FUNCTIONS) return false
        }
        return cleaned.replace(fnRegex, "").trim().isEmpty()
    }

    private fun parseCssPx(raw: String): Double? {
        val cleaned = raw.replace("!important", "", ignoreCase = true).trim().lowercase()
        if (cleaned.isEmpty() || cleaned.endsWith("%") || cleaned == "auto" || cleaned == "none") return null
        val numPart = when {
            cleaned.endsWith("px") -> cleaned.removeSuffix("px").trim()
            cleaned.endsWith("pt") -> cleaned.removeSuffix("pt").trim()
            else -> cleaned
        }
        return numPart.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    private fun cancelNativeAnimator(canonicalKey: String) {
        val prevAnimator = activeAnimators.remove(canonicalKey)
        val prevCompletion = activeAnimatorCompletions.remove(canonicalKey)
        if (canonicalKey == "opacity") {
            activeOpacityTransitionTarget = null
            activeOpacityTransitionFrom = null
        } else if (canonicalKey == "background-color") {
            activeBackgroundColorTransitionTarget = null
        }
        runCatching { prevAnimator?.stopAnimation(true) }
        prevCompletion?.invoke()
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (NSThread.isMainThread) {
            block()
        } else {
            dispatch_async(dispatch_get_main_queue()) {
                block()
            }
        }
    }

    private fun dispatchCompletionOnLogicThread(onComplete: () -> Unit) {
        if (GenericUiThreading.isSplitThreadingEnabled && !GenericUiThreading.isOnLogicThread()) {
            GenericUiThreading.runOnLogic(onComplete)
        } else {
            onComplete()
        }
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
        if (uiView !is UIControl) {
            if (listener != null && tapRecognizer == null) {
                val tap = UITapGestureRecognizer(target = actionTarget, action = UikitControlActionTarget.SEL_SINGLE_TAP)
                tap.numberOfTapsRequired = 1uL
                uiView.addGestureRecognizer(tap)
                tapRecognizer = tap
            } else if (listener == null && tapRecognizer != null) {
                uiView.removeGestureRecognizer(tapRecognizer!!)
                tapRecognizer = null
            }
            syncUserInteractionEnabled()
        } else {
            if (this !is IosCheckboxImpl) {
                actionTarget.onPrimaryAction = listener
            }
            syncUserInteractionEnabled()
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
        for (key in activeAnimators.keys.toList()) {
            cancelNativeAnimator(key)
        }
        UikitViewLayoutRegistry.remove(uiView)
        UikitFocusRegistry.remove(uiView)
        uiView.removeFromSuperview()
    }

    private companion object {
        val SUPPORTED_TRANSFORM_FUNCTIONS = setOf(
            "translate", "translate3d", "translatex", "translatey",
            "scale", "scale3d", "scalex", "scaley",
            "rotate", "rotatez"
        )
    }
}
