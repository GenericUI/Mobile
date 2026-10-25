package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.gesture.MobileGestureRecognizer
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.transition.CssTransitionSpec

/**
 * Base [NativeImpl] for the `:ios` UIKit backend.
 *
 * Handles Bootstrap 5 style Enums directly in strongly-typed fields (`styleSnapshot`) for zero-string-parsing
 * efficiency, synthesizing `getClass()` and parsing `setClass()` via [BootstrapClassMapper].
 */
open class IosBaseNativeImpl(
    uiViewClassName: String = "UIView",
    override val componentClassPrefix: String = ""
) : NativeImpl<IosNativeView> {

    override val nativeWidget: IosNativeView = IosNativeView(uiViewClassName)

    val gestureRecognizer: MobileGestureRecognizer = MobileGestureRecognizer()

    protected var styleSnapshot: BootstrapStyleSnapshot =
        BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)

    private var tooltipTextValue: String? = null
    private var preferredSizeValue: Size? = null

    protected var onClickListener: (() -> Unit)? = null
    protected var onDoubleClickListener: (() -> Unit)? = null
    protected var onContextMenuListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerDownListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerMoveListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerUpListener: ((PointerEvent) -> Unit)? = null
    protected var onWheelListener: ((WheelEvent) -> Unit)? = null
    protected var onKeyDownListener: ((KeyEvent) -> Unit)? = null
    protected var onKeyUpListener: ((KeyEvent) -> Unit)? = null

    init {
        gestureRecognizer.onLongPress = { x, y ->
            nativeWidget.metadata["lastContextMenuEvent"] = "$x,$y"
            onContextMenuListener?.invoke(PointerEvent(x = x, y = y, button = 2))
        }
    }

    override fun setClass(className: String) {
        styleSnapshot = BootstrapClassMapper.fromClassString(className, componentClassPrefix)
    }

    override fun getClass(): String =
        BootstrapClassMapper.toClassString(styleSnapshot)

    override fun addClass(className: String) {
        val combined = (getClass() + " " + className).trim()
        styleSnapshot = BootstrapClassMapper.fromClassString(combined, componentClassPrefix)
    }

    override fun removeClass(className: String) {
        val remaining = getClass().split(Regex("\\s+")).filter { it.isNotEmpty() && it != className }
        styleSnapshot = BootstrapClassMapper.fromClassString(remaining.joinToString(" "), componentClassPrefix)
    }

    override fun hasClass(className: String): Boolean =
        BootstrapClassMapper.toClassTokens(styleSnapshot).contains(className.trim())

    override fun setType(type: BootstrapType?) { styleSnapshot = styleSnapshot.copy(type = type) }
    override fun getType(): BootstrapType? = styleSnapshot.type

    override fun setOutline(outline: Boolean) { styleSnapshot = styleSnapshot.copy(outline = outline) }
    override fun isOutline(): Boolean = styleSnapshot.outline

    override fun setSize(size: BootstrapSize?) { styleSnapshot = styleSnapshot.copy(size = size) }
    override fun getSize(): BootstrapSize? = styleSnapshot.size

    override fun setBackgroundType(type: BootstrapType?) { styleSnapshot = styleSnapshot.copy(backgroundType = type) }
    override fun getBackgroundType(): BootstrapType? = styleSnapshot.backgroundType

    override fun setTextColorType(type: BootstrapType?) { styleSnapshot = styleSnapshot.copy(textColorType = type) }
    override fun getTextColorType(): BootstrapType? = styleSnapshot.textColorType

    override fun setBorderType(type: BootstrapType?) { styleSnapshot = styleSnapshot.copy(borderType = type) }
    override fun getBorderType(): BootstrapType? = styleSnapshot.borderType

    override fun setTextAlignment(alignment: TextAlignment?) { styleSnapshot = styleSnapshot.copy(textAlignment = alignment) }
    override fun getTextAlignment(): TextAlignment? = styleSnapshot.textAlignment

    override fun setFontWeight(weight: FontWeight?) { styleSnapshot = styleSnapshot.copy(fontWeight = weight) }
    override fun getFontWeight(): FontWeight? = styleSnapshot.fontWeight

    override fun setTextTransform(transform: TextTransform?) { styleSnapshot = styleSnapshot.copy(textTransform = transform) }
    override fun getTextTransform(): TextTransform? = styleSnapshot.textTransform

    override fun setTextOverflow(overflow: TextOverflow?) { styleSnapshot = styleSnapshot.copy(textOverflow = overflow) }
    override fun getTextOverflow(): TextOverflow? = styleSnapshot.textOverflow

    override fun setBorderRadius(radius: BorderRadiusStyle?) { styleSnapshot = styleSnapshot.copy(borderRadius = radius) }
    override fun getBorderRadius(): BorderRadiusStyle? = styleSnapshot.borderRadius

    override fun setShadow(shadow: ShadowElevation?) { styleSnapshot = styleSnapshot.copy(shadow = shadow) }
    override fun getShadow(): ShadowElevation? = styleSnapshot.shadow

    override fun setDisplay(display: DisplayMode?) { styleSnapshot = styleSnapshot.copy(display = display) }
    override fun getDisplay(): DisplayMode? = styleSnapshot.display

    override fun setFlexDirection(direction: FlexDirection?) { styleSnapshot = styleSnapshot.copy(flexDirection = direction) }
    override fun getFlexDirection(): FlexDirection? = styleSnapshot.flexDirection

    override fun setFlexWrap(wrap: FlexWrap?) { styleSnapshot = styleSnapshot.copy(flexWrap = wrap) }
    override fun getFlexWrap(): FlexWrap? = styleSnapshot.flexWrap

    override fun setJustifyContent(justify: JustifyContent?) { styleSnapshot = styleSnapshot.copy(justifyContent = justify) }
    override fun getJustifyContent(): JustifyContent? = styleSnapshot.justifyContent

    override fun setAlignItems(align: AlignItems?) { styleSnapshot = styleSnapshot.copy(alignItems = align) }
    override fun getAlignItems(): AlignItems? = styleSnapshot.alignItems

    override fun setAlignSelf(align: AlignSelf?) { styleSnapshot = styleSnapshot.copy(alignSelf = align) }
    override fun getAlignSelf(): AlignSelf? = styleSnapshot.alignSelf

    override fun setOverflow(overflow: OverflowBehavior?) { styleSnapshot = styleSnapshot.copy(overflow = overflow) }
    override fun getOverflow(): OverflowBehavior? = styleSnapshot.overflow

    override fun setPosition(position: PositionMode?) { styleSnapshot = styleSnapshot.copy(position = position) }
    override fun getPosition(): PositionMode? = styleSnapshot.position

    override fun setMarginAll(scale: SpacingScale?) { styleSnapshot = styleSnapshot.copy(marginAll = scale) }
    override fun getMarginAll(): SpacingScale? = styleSnapshot.marginAll

    override fun setPaddingAll(scale: SpacingScale?) { styleSnapshot = styleSnapshot.copy(paddingAll = scale) }
    override fun getPaddingAll(): SpacingScale? = styleSnapshot.paddingAll

    override fun setVisible(visible: Boolean) {
        nativeWidget.isHidden = !visible
    }

    override fun isVisible(): Boolean = !nativeWidget.isHidden

    override fun setEnabled(enabled: Boolean) {
        nativeWidget.isUserInteractionEnabled = enabled
    }

    override fun isEnabled(): Boolean = nativeWidget.isUserInteractionEnabled

    override fun setOpacity(opacity: Float) {
        nativeWidget.alpha = opacity.coerceIn(0f, 1f)
    }

    override fun getOpacity(): Float = nativeWidget.alpha

    override fun setTooltipText(tooltip: String?) {
        tooltipTextValue = tooltip
    }

    override fun getTooltipText(): String? = tooltipTextValue

    override fun setPreferredSize(size: Size?) {
        preferredSizeValue = size
        if (size != null) {
            nativeWidget.frame = Rect(nativeWidget.frame.x, nativeWidget.frame.y, size.width, size.height)
        }
    }

    override fun getBounds(): Rect = nativeWidget.frame

    override fun setPadding(padding: EdgeInsets) {
        nativeWidget.layoutMargins = padding
    }

    override fun setMargin(margin: EdgeInsets) {
        nativeWidget.metadata["margin"] = "${margin.top},${margin.right},${margin.bottom},${margin.left}"
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        style.backgroundColor?.let { nativeWidget.backgroundColor = it }
        style.textColor?.let { nativeWidget.tintColor = it }
        style.borderRadiusPx?.let { nativeWidget.cornerRadius = it }
        style.borderWidthPx?.let { nativeWidget.borderWidth = it }
        style.opacity?.let { setOpacity(it) }
    }

    override fun supportsNativeTransitions(): Boolean = true

    override fun runNativeTransition(
        property: String,
        fromValue: String?,
        toValue: String,
        spec: CssTransitionSpec,
        onComplete: () -> Unit
    ): Boolean {
        nativeWidget.metadata["coreAnimation:$property"] = "$fromValue->$toValue@${spec.durationMs}ms"
        return true
    }

    override fun setAttribute(name: String, value: String?) {
        if (value == null) {
            nativeWidget.metadata.remove(name)
        } else {
            nativeWidget.metadata[name] = value
        }
    }

    override fun setOnClick(listener: (() -> Unit)?) { onClickListener = listener }
    override fun setOnDoubleClick(listener: (() -> Unit)?) { onDoubleClickListener = listener }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        onContextMenuListener = listener
        nativeWidget.metadata["UILongPressGestureRecognizer"] = (listener != null).toString()
        nativeWidget.metadata["longPressContextMenuEnabled"] = (listener != null).toString()
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) { onPointerDownListener = listener }
    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) { onPointerMoveListener = listener }
    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) { onPointerUpListener = listener }
    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) { onWheelListener = listener }
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) { onKeyDownListener = listener }
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) { onKeyUpListener = listener }

    fun performLongPress(x: Float = 0f, y: Float = 0f): Boolean {
        val listener = onContextMenuListener ?: return false
        nativeWidget.metadata["lastContextMenuEvent"] = "$x,$y"
        listener(PointerEvent(x = x, y = y, button = 2))
        return true
    }

    fun performClick(): Boolean {
        val listener = onClickListener ?: return false
        listener()
        return true
    }

    fun dispatchClick() {
        onClickListener?.invoke()
    }

    fun dispatchContextMenu(event: PointerEvent) {
        onContextMenuListener?.invoke(event)
    }

    override fun dispose() {
        nativeWidget.removeFromSuperview()
    }
}
