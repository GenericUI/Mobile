package com.nexustools.genericui.android

import android.view.MotionEvent
import android.view.View
import android.view.animation.PathInterpolator
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
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.hypot

/**
 * Base [NativeImpl] for the `:android` backend.
 *
 * Stores and executes Bootstrap 5 style Enums directly via [styleSnapshot] and synthesizes
 * `getClass()` / parses `setClass()` via [BootstrapClassMapper].
 */
open class AndroidBaseNativeImpl(
    viewClassName: String = "android.view.View",
    override val componentClassPrefix: String = "",
    initialView: View? = null
) : NativeImpl<AndroidViewHandle> {

    override val nativeWidget: AndroidViewHandle = AndroidViewHandle(viewClassName, initialView)

    val gestureRecognizer: MobileGestureRecognizer = MobileGestureRecognizer()

    protected var styleSnapshot: BootstrapStyleSnapshot =
        BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)

    private var tooltipTextValue: String? = null
    private var preferredSizeValue: Size? = null

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var touchDownTimestampMs: Long = 0L
    private var longPressFiredForTouch: Boolean = false
    private var touchListenerAttached: Boolean = false
    private var hoverListenerAttached: Boolean = false

    protected var onClickListener: (() -> Unit)? = null
    protected var onDoubleClickListener: (() -> Unit)? = null
    protected var onContextMenuListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerDownListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerMoveListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerUpListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerEnterListener: ((PointerEvent) -> Unit)? = null
    protected var onPointerLeaveListener: ((PointerEvent) -> Unit)? = null
    protected var onWheelListener: ((WheelEvent) -> Unit)? = null
    protected var onKeyDownListener: ((KeyEvent) -> Unit)? = null
    protected var onKeyUpListener: ((KeyEvent) -> Unit)? = null

    init {
        gestureRecognizer.onLongPress = { x, y ->
            if (!longPressFiredForTouch) {
                longPressFiredForTouch = true
                lastTouchX = x
                lastTouchY = y
                nativeWidget.attributes["lastContextMenuEvent"] = "$x,$y"
                onContextMenuListener?.invoke(PointerEvent(x = x, y = y, button = 2))
            }
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
        nativeWidget.visible = visible
        nativeWidget.androidView?.visibility = if (visible) View.VISIBLE else View.GONE
    }

    override fun isVisible(): Boolean = nativeWidget.visible

    override fun setEnabled(enabled: Boolean) {
        nativeWidget.enabled = enabled
        nativeWidget.androidView?.isEnabled = enabled
    }

    override fun isEnabled(): Boolean = nativeWidget.enabled

    override fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        nativeWidget.alpha = clamped
        nativeWidget.androidView?.alpha = clamped
    }

    override fun getOpacity(): Float = nativeWidget.alpha

    override fun setTooltipText(tooltip: String?) {
        tooltipTextValue = tooltip
    }

    override fun getTooltipText(): String? = tooltipTextValue

    override fun setPreferredSize(size: Size?) {
        preferredSizeValue = size
        if (size != null) {
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, size.width, size.height)
        }
    }

    override fun getBounds(): Rect = nativeWidget.bounds

    override fun setPadding(padding: EdgeInsets) {
        nativeWidget.padding = padding
        nativeWidget.androidView?.setPadding(padding.left, padding.top, padding.right, padding.bottom)
    }

    override fun setMargin(margin: EdgeInsets) {
        nativeWidget.margin = margin
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        style.backgroundColor?.let {
            nativeWidget.androidView?.setBackgroundColor(it.toArgbInt())
        }
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
        val view = nativeWidget.androidView ?: return true
        val anim = view.animate()
            .setDuration(spec.durationMs.toLong())
            .setStartDelay(spec.delayMs.toLong())
            .setInterpolator(
                PathInterpolator(
                    spec.timingFunction.x1,
                    spec.timingFunction.y1,
                    spec.timingFunction.x2,
                    spec.timingFunction.y2
                )
            )
            .withEndAction { onComplete() }
        when (property) {
            "opacity" -> toValue.toFloatOrNull()?.let { anim.alpha(it) }
            "transform" -> {
                val tf = CssTransform2D.parse(toValue)
                val (dx, dy) = tf.resolveTranslationPx(view.width, view.height)
                anim.translationX(dx).translationY(dy).scaleX(tf.scaleX).scaleY(tf.scaleY).rotation(tf.rotateDeg)
            }
        }
        anim.start()
        return true
    }

    override fun setAttribute(name: String, value: String?) {
        if (value == null) {
            nativeWidget.attributes.remove(name)
        } else {
            nativeWidget.attributes[name] = value
        }
    }

    override fun setOnClick(listener: (() -> Unit)?) {
        onClickListener = listener
        nativeWidget.androidView?.setOnClickListener(if (listener != null) View.OnClickListener { listener() } else null)
    }

    override fun setOnDoubleClick(listener: (() -> Unit)?) { onDoubleClickListener = listener }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        onContextMenuListener = listener
        nativeWidget.attributes["longPressContextMenuEnabled"] = (listener != null).toString()
        val view = nativeWidget.androidView
        if (view != null) {
            view.isLongClickable = listener != null
            if (listener != null) {
                view.setOnLongClickListener {
                    if (!longPressFiredForTouch) {
                        longPressFiredForTouch = true
                        nativeWidget.attributes["lastContextMenuEvent"] = "$lastTouchX,$lastTouchY"
                        listener(PointerEvent(x = lastTouchX, y = lastTouchY, button = 2))
                    }
                    true
                }
                ensureTouchListenerAttached(view)
            } else {
                view.setOnLongClickListener(null)
            }
        }
    }

    override fun setOnPointerDown(listener: ((PointerEvent) -> Unit)?) {
        onPointerDownListener = listener
        nativeWidget.androidView?.let { ensureTouchListenerAttached(it) }
    }

    override fun setOnPointerMove(listener: ((PointerEvent) -> Unit)?) {
        onPointerMoveListener = listener
        nativeWidget.androidView?.let { ensureTouchListenerAttached(it) }
    }

    override fun setOnPointerUp(listener: ((PointerEvent) -> Unit)?) {
        onPointerUpListener = listener
        nativeWidget.androidView?.let { ensureTouchListenerAttached(it) }
    }

    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) {
        onPointerEnterListener = listener
        nativeWidget.androidView?.let { ensureHoverListenerAttached(it) }
    }

    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) {
        onPointerLeaveListener = listener
        nativeWidget.androidView?.let { ensureHoverListenerAttached(it) }
    }

    fun dispatchPointerEnter(event: PointerEvent = PointerEvent()) {
        onPointerEnterListener?.invoke(event)
    }

    fun dispatchPointerLeave(event: PointerEvent = PointerEvent()) {
        onPointerLeaveListener?.invoke(event)
    }

    private fun ensureHoverListenerAttached(view: View) {
        if (hoverListenerAttached) return
        hoverListenerAttached = true
        view.setOnHoverListener { _, event ->
            val pe = PointerEvent(x = event.x, y = event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_HOVER_ENTER -> onPointerEnterListener?.invoke(pe)
                MotionEvent.ACTION_HOVER_EXIT -> onPointerLeaveListener?.invoke(pe)
            }
            false
        }
    }

    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) { onWheelListener = listener }
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) { onKeyDownListener = listener }
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) { onKeyUpListener = listener }

    fun performLongPress(x: Float = 0f, y: Float = 0f): Boolean {
        lastTouchX = x
        lastTouchY = y
        longPressFiredForTouch = true
        val listener = onContextMenuListener ?: return false
        nativeWidget.attributes["lastContextMenuEvent"] = "$x,$y"
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

    private fun ensureTouchListenerAttached(view: View) {
        if (touchListenerAttached) return
        touchListenerAttached = true
        view.setOnTouchListener { _, event ->
            val x = event.x
            val y = event.y
            lastTouchX = x
            lastTouchY = y
            val pointerId = if (event.pointerCount > 0) event.getPointerId(0) else 0
            val eventTime = event.eventTime
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartX = x
                    touchStartY = y
                    touchDownTimestampMs = eventTime
                    longPressFiredForTouch = false
                    gestureRecognizer.onPointerDown(pointerId, x, y, eventTime)
                    onPointerDownListener?.invoke(PointerEvent(x = x, y = y, button = 0, pointerId = pointerId))
                }
                MotionEvent.ACTION_MOVE -> {
                    gestureRecognizer.onPointerMove(pointerId, x, y, eventTime)
                    val dist = hypot(x - touchStartX, y - touchStartY)
                    if (!longPressFiredForTouch && dist < gestureRecognizer.touchSlopPx &&
                        (eventTime - touchDownTimestampMs) >= 500L
                    ) {
                        longPressFiredForTouch = true
                        nativeWidget.attributes["lastContextMenuEvent"] = "$x,$y"
                        onContextMenuListener?.invoke(PointerEvent(x = x, y = y, button = 2, pointerId = pointerId))
                    }
                    onPointerMoveListener?.invoke(PointerEvent(x = x, y = y, button = 0, pointerId = pointerId))
                }
                MotionEvent.ACTION_UP -> {
                    val dist = hypot(x - touchStartX, y - touchStartY)
                    if (!longPressFiredForTouch && dist < gestureRecognizer.touchSlopPx &&
                        (eventTime - touchDownTimestampMs) >= 500L
                    ) {
                        longPressFiredForTouch = true
                        nativeWidget.attributes["lastContextMenuEvent"] = "$x,$y"
                        onContextMenuListener?.invoke(PointerEvent(x = x, y = y, button = 2, pointerId = pointerId))
                    }
                    gestureRecognizer.onPointerUp(pointerId, x, y, eventTime)
                    onPointerUpListener?.invoke(PointerEvent(x = x, y = y, button = 0, pointerId = pointerId))
                }
                MotionEvent.ACTION_CANCEL -> {
                    gestureRecognizer.reset()
                }
            }
            false
        }
    }

    override fun dispose() {
        nativeWidget.removeFromParent()
    }
}
