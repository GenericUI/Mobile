package com.nexustools.genericui.android

import android.content.res.ColorStateList
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.enums.css.CssVisibility
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.gesture.MobileGestureRecognizer
import com.nexustools.genericui.style.RgbaColor
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Base [NativeImpl] for the `:android` backend.
 *
 * - User custom classes (`setClass`, `getClass`, `getClasses`, `addClass`, `removeClass`, `hasClass`)
 *   are strictly isolated from Bootstrap 5 enum classes (`styleSnapshot`).
 * - When `type == null` and no custom background color is set, native Android/Material system control
 *   appearance is preserved (`useSystemControlAppearance = true`). When a [BootstrapType] is set,
 *   tints the control using [BootstrapColorPalette].
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

    protected var customStyleBackgroundColor: RgbaColor? = null
    protected var customStyleTextColor: RgbaColor? = null

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
        syncNativeBootstrapAppearance()
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
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        styleSnapshot = styleSnapshot.copy(customClasses = tokens)
        nativeWidget.classes.clear()
        nativeWidget.classes.addAll(tokens)
    }

    override fun getClass(): String =
        styleSnapshot.customClasses.joinToString(" ")

    override fun getClasses(): Set<String> =
        styleSnapshot.customClasses.toSet()

    override fun addClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.addAll(tokens)
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        nativeWidget.classes.clear()
        nativeWidget.classes.addAll(updated)
    }

    override fun removeClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.removeAll(tokens.toSet())
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        nativeWidget.classes.clear()
        nativeWidget.classes.addAll(updated)
    }

    override fun hasClass(className: String): Boolean {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        return styleSnapshot.customClasses.containsAll(tokens)
    }

    protected open fun syncNativeBootstrapAppearance() {
        val normalizedSnapshot = styleSnapshot.copy(type = styleSnapshot.type?.takeIf { it != BootstrapType.NONE })
        val enumClasses = BootstrapClassMapper.toEnumClassSet(normalizedSnapshot).filterNot { it.endsWith("-") }
        nativeWidget.attributes["enumClasses"] = enumClasses.joinToString(" ")

        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val effectiveType = styleSnapshot.type ?: styleSnapshot.backgroundType
        val tintColor = customStyleBackgroundColor ?: BootstrapColorPalette.resolveTintOrNull(effectiveType, isDark)
        val fgColor = customStyleTextColor
            ?: styleSnapshot.textColorType?.let { BootstrapColorPalette.resolveColor(it, isDark) }
            ?: styleSnapshot.type?.takeIf { it != BootstrapType.NONE && !styleSnapshot.outline }?.let {
                BootstrapColorPalette.resolveForegroundOn(it)
            }

        if (tintColor == null) {
            nativeWidget.attributes["useSystemControlAppearance"] = "true"
            nativeWidget.attributes.remove("bootstrapTintColor")
            nativeWidget.androidView?.backgroundTintList = null
        } else {
            nativeWidget.attributes["useSystemControlAppearance"] = "false"
            nativeWidget.attributes["bootstrapTintColor"] = tintColor.toCssHex()
            nativeWidget.androidView?.let { view ->
                runCatching {
                    view.backgroundTintList = ColorStateList.valueOf(tintColor.toArgbInt())
                }.onFailure {
                    view.setBackgroundColor(tintColor.toArgbInt())
                }
            }
        }

        if (fgColor != null) {
            nativeWidget.attributes["bootstrapForegroundColor"] = fgColor.toCssHex()
            (nativeWidget.androidView as? TextView)?.setTextColor(fgColor.toArgbInt())
        } else {
            nativeWidget.attributes.remove("bootstrapForegroundColor")
        }

        when (val w = styleSnapshot.width) {
            BootstrapWidth.W_100 -> {
                nativeWidget.attributes["layoutWidth"] = "MATCH_PARENT"
                nativeWidget.androidView?.layoutParams?.let { lp ->
                    lp.width = ViewGroup.LayoutParams.MATCH_PARENT
                    nativeWidget.androidView?.layoutParams = lp
                }
            }
            BootstrapWidth.W_25, BootstrapWidth.W_50, BootstrapWidth.W_75 -> {
                nativeWidget.attributes["layoutWidth"] = "${w.percent ?: 100}%"
            }
            BootstrapWidth.W_AUTO -> {
                nativeWidget.attributes["layoutWidth"] = "WRAP_CONTENT"
            }
            null -> nativeWidget.attributes.remove("layoutWidth")
        }

        when (val h = styleSnapshot.height) {
            BootstrapHeight.H_100 -> {
                nativeWidget.attributes["layoutHeight"] = "MATCH_PARENT"
                nativeWidget.androidView?.layoutParams?.let { lp ->
                    lp.height = ViewGroup.LayoutParams.MATCH_PARENT
                    nativeWidget.androidView?.layoutParams = lp
                }
            }
            BootstrapHeight.H_25, BootstrapHeight.H_50, BootstrapHeight.H_75 -> {
                nativeWidget.attributes["layoutHeight"] = "${h.percent ?: 100}%"
            }
            BootstrapHeight.H_AUTO -> {
                nativeWidget.attributes["layoutHeight"] = "WRAP_CONTENT"
            }
            null -> nativeWidget.attributes.remove("layoutHeight")
        }

        styleSnapshot.opacity?.let { op ->
            val alpha = when (op) {
                BootstrapOpacity.OPACITY_0 -> 0f
                BootstrapOpacity.OPACITY_25 -> 0.25f
                BootstrapOpacity.OPACITY_50 -> 0.5f
                BootstrapOpacity.OPACITY_75 -> 0.75f
                BootstrapOpacity.OPACITY_100 -> 1f
            }
            setOpacity(alpha)
        }

        when (styleSnapshot.visibility) {
            BootstrapVisibility.INVISIBLE -> {
                nativeWidget.attributes["visibility"] = "INVISIBLE"
                nativeWidget.androidView?.visibility = View.INVISIBLE
            }
            BootstrapVisibility.VISIBLE -> {
                nativeWidget.attributes["visibility"] = "VISIBLE"
                if (nativeWidget.visible) {
                    nativeWidget.androidView?.visibility = View.VISIBLE
                }
            }
            null -> nativeWidget.attributes.remove("visibility")
        }

        if (styleSnapshot.display == DisplayMode.NONE) {
            setVisible(false)
        }

        val mAll = spacingScaleToPx(styleSnapshot.marginAll)
        val mX = spacingScaleToPx(styleSnapshot.marginX) ?: mAll
        val mY = spacingScaleToPx(styleSnapshot.marginY) ?: mAll
        val mTop = spacingScaleToPx(styleSnapshot.marginTop) ?: mY ?: 0
        val mBottom = spacingScaleToPx(styleSnapshot.marginBottom) ?: mY ?: 0
        val mLeft = spacingScaleToPx(styleSnapshot.marginStart) ?: mX ?: 0
        val mRight = spacingScaleToPx(styleSnapshot.marginEnd) ?: mX ?: 0
        if (mTop != 0 || mBottom != 0 || mLeft != 0 || mRight != 0) {
            setMargin(EdgeInsets(mTop, mRight, mBottom, mLeft))
        }

        val pAll = spacingScaleToPx(styleSnapshot.paddingAll)
        val pX = spacingScaleToPx(styleSnapshot.paddingX) ?: pAll
        val pY = spacingScaleToPx(styleSnapshot.paddingY) ?: pAll
        val pTop = spacingScaleToPx(styleSnapshot.paddingTop) ?: pY ?: 0
        val pBottom = spacingScaleToPx(styleSnapshot.paddingBottom) ?: pY ?: 0
        val pLeft = spacingScaleToPx(styleSnapshot.paddingStart) ?: pX ?: 0
        val pRight = spacingScaleToPx(styleSnapshot.paddingEnd) ?: pX ?: 0
        if (pTop != 0 || pBottom != 0 || pLeft != 0 || pRight != 0) {
            setPadding(EdgeInsets(pTop, pRight, pBottom, pLeft))
        }

        styleSnapshot.shadow?.let { sh ->
            val elev = when (sh) {
                ShadowElevation.NONE -> 0f
                ShadowElevation.SM -> 2f
                ShadowElevation.REGULAR -> 6f
                ShadowElevation.LG -> 12f
            }
            nativeWidget.attributes["elevation"] = elev.toString()
            nativeWidget.androidView?.elevation = elev
        }
    }

    private fun spacingScaleToPx(scale: SpacingScale?): Int? = when (scale) {
        SpacingScale.S_0 -> 0
        SpacingScale.S_1 -> 4
        SpacingScale.S_2 -> 8
        SpacingScale.S_3 -> 16
        SpacingScale.S_4 -> 24
        SpacingScale.S_5 -> 48
        SpacingScale.AUTO, null -> null
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        styleSnapshot = bootstrap.applyToSnapshot(styleSnapshot)
        syncNativeBootstrapAppearance()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        customStyleBackgroundColor = style.getBackgroundColor()
        customStyleTextColor = style.getTextColor()

        style.getOpacity()?.let { setOpacity(it) }

        val wLen = style.getWidth()
        val hLen = style.getHeight()
        if (wLen != null || hLen != null) {
            if (wLen?.unit == CssUnit.PERCENT && wLen.value == 100f) {
                nativeWidget.attributes["layoutWidth"] = "MATCH_PARENT"
            }
            if (hLen?.unit == CssUnit.PERCENT && hLen.value == 100f) {
                nativeWidget.attributes["layoutHeight"] = "MATCH_PARENT"
            }
            val wPx = wLen?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
                ?: preferredSizeValue?.width
                ?: nativeWidget.bounds.width
            val hPx = hLen?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
                ?: preferredSizeValue?.height
                ?: nativeWidget.bounds.height
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, wPx, hPx)
        }

        style.getPadding()?.let { setPadding(it.toEdgeInsets()) }
        style.getMargin()?.let { setMargin(it.toEdgeInsets()) }

        when (style.getVisibility()) {
            CssVisibility.HIDDEN, CssVisibility.COLLAPSE -> {
                nativeWidget.attributes["visibility"] = "INVISIBLE"
                nativeWidget.androidView?.visibility = View.INVISIBLE
            }
            CssVisibility.VISIBLE -> {
                nativeWidget.attributes["visibility"] = "VISIBLE"
                if (nativeWidget.visible) nativeWidget.androidView?.visibility = View.VISIBLE
            }
            null -> {}
        }

        if (style.getDisplay() == CssDisplay.NONE) {
            setVisible(false)
        }
        // Unsupported CSS properties on mobile (such as cursor) are silently ignored.
        syncNativeBootstrapAppearance()
    }

    override fun setCustomCursor(cursor: CssCursorValue?) {
        // Cursors are silently ignored on touch-first mobile platforms.
    }

    override fun setType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(type = type)
        syncNativeBootstrapAppearance()
    }
    override fun getType(): BootstrapType? = styleSnapshot.type

    override fun setOutline(outline: Boolean) {
        styleSnapshot = styleSnapshot.copy(outline = outline)
        syncNativeBootstrapAppearance()
    }
    override fun isOutline(): Boolean = styleSnapshot.outline

    override fun setSize(size: BootstrapSize?) {
        styleSnapshot = styleSnapshot.copy(size = size)
        syncNativeBootstrapAppearance()
    }
    override fun getSize(): BootstrapSize? = styleSnapshot.size

    override fun setBackgroundType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(backgroundType = type)
        syncNativeBootstrapAppearance()
    }
    override fun getBackgroundType(): BootstrapType? = styleSnapshot.backgroundType

    override fun setTextColorType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(textColorType = type)
        syncNativeBootstrapAppearance()
    }
    override fun getTextColorType(): BootstrapType? = styleSnapshot.textColorType

    override fun setBorderType(type: BootstrapType?) {
        styleSnapshot = styleSnapshot.copy(borderType = type)
        syncNativeBootstrapAppearance()
    }
    override fun getBorderType(): BootstrapType? = styleSnapshot.borderType

    override fun setTextAlignment(alignment: TextAlignment?) {
        styleSnapshot = styleSnapshot.copy(textAlignment = alignment)
        syncNativeBootstrapAppearance()
    }
    override fun getTextAlignment(): TextAlignment? = styleSnapshot.textAlignment

    override fun setFontWeight(weight: FontWeight?) {
        styleSnapshot = styleSnapshot.copy(fontWeight = weight)
        syncNativeBootstrapAppearance()
    }
    override fun getFontWeight(): FontWeight? = styleSnapshot.fontWeight

    override fun setTextTransform(transform: TextTransform?) {
        styleSnapshot = styleSnapshot.copy(textTransform = transform)
        syncNativeBootstrapAppearance()
    }
    override fun getTextTransform(): TextTransform? = styleSnapshot.textTransform

    override fun setTextOverflow(overflow: TextOverflow?) {
        styleSnapshot = styleSnapshot.copy(textOverflow = overflow)
        syncNativeBootstrapAppearance()
    }
    override fun getTextOverflow(): TextOverflow? = styleSnapshot.textOverflow

    override fun setBorderRadius(radius: BorderRadiusStyle?) {
        styleSnapshot = styleSnapshot.copy(borderRadius = radius)
        syncNativeBootstrapAppearance()
    }
    override fun getBorderRadius(): BorderRadiusStyle? = styleSnapshot.borderRadius

    override fun setShadow(shadow: ShadowElevation?) {
        styleSnapshot = styleSnapshot.copy(shadow = shadow)
        syncNativeBootstrapAppearance()
    }
    override fun getShadow(): ShadowElevation? = styleSnapshot.shadow

    override fun setDisplay(display: DisplayMode?) {
        styleSnapshot = styleSnapshot.copy(display = display)
        syncNativeBootstrapAppearance()
    }
    override fun getDisplay(): DisplayMode? = styleSnapshot.display

    override fun setFlexDirection(direction: FlexDirection?) {
        styleSnapshot = styleSnapshot.copy(flexDirection = direction)
        syncNativeBootstrapAppearance()
    }
    override fun getFlexDirection(): FlexDirection? = styleSnapshot.flexDirection

    override fun setFlexWrap(wrap: FlexWrap?) {
        styleSnapshot = styleSnapshot.copy(flexWrap = wrap)
        syncNativeBootstrapAppearance()
    }
    override fun getFlexWrap(): FlexWrap? = styleSnapshot.flexWrap

    override fun setJustifyContent(justify: JustifyContent?) {
        styleSnapshot = styleSnapshot.copy(justifyContent = justify)
        syncNativeBootstrapAppearance()
    }
    override fun getJustifyContent(): JustifyContent? = styleSnapshot.justifyContent

    override fun setAlignItems(align: AlignItems?) {
        styleSnapshot = styleSnapshot.copy(alignItems = align)
        syncNativeBootstrapAppearance()
    }
    override fun getAlignItems(): AlignItems? = styleSnapshot.alignItems

    override fun setAlignSelf(align: AlignSelf?) {
        styleSnapshot = styleSnapshot.copy(alignSelf = align)
        syncNativeBootstrapAppearance()
    }
    override fun getAlignSelf(): AlignSelf? = styleSnapshot.alignSelf

    override fun setOverflow(overflow: OverflowBehavior?) {
        styleSnapshot = styleSnapshot.copy(overflow = overflow)
        syncNativeBootstrapAppearance()
    }
    override fun getOverflow(): OverflowBehavior? = styleSnapshot.overflow

    override fun setPosition(position: PositionMode?) {
        styleSnapshot = styleSnapshot.copy(position = position)
        syncNativeBootstrapAppearance()
    }
    override fun getPosition(): PositionMode? = styleSnapshot.position

    override fun setMarginAll(scale: SpacingScale?) {
        styleSnapshot = styleSnapshot.copy(marginAll = scale)
        syncNativeBootstrapAppearance()
    }
    override fun getMarginAll(): SpacingScale? = styleSnapshot.marginAll

    override fun setPaddingAll(scale: SpacingScale?) {
        styleSnapshot = styleSnapshot.copy(paddingAll = scale)
        syncNativeBootstrapAppearance()
    }
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
        customStyleBackgroundColor = style.backgroundColor
        customStyleTextColor = style.textColor
        style.backgroundColor?.let {
            nativeWidget.androidView?.setBackgroundColor(it.toArgbInt())
        }
        style.opacity.let { setOpacity(it) }
        syncNativeBootstrapAppearance()
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
