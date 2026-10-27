package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.PointerEvent
import com.nexustools.genericui.event.WheelEvent
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.mobile.gesture.MobileGestureRecognizer
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.ComponentStyle
import com.nexustools.genericui.style.css.ComputedStyle
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.transition.CssTransitionSpec
import kotlin.math.roundToInt

/**
 * Base [NativeImpl] for the `:ios` UIKit backend.
 *
 * Isolates user custom CSS classes from Bootstrap Enum state (`styleSnapshot`), maps semantic
 * [BootstrapType] values (`DANGER`, `PRIMARY`, `SUCCESS`, etc.) to native `UIColor` tints while
 * keeping [BootstrapType.NONE] / `null` in the default iOS system control appearance, and translates
 * [ComponentBootstrap] and [ComponentStyle] properties to `UIView` / `CALayer` state.
 */
open class IosBaseNativeImpl(
    uiViewClassName: String = "UIView",
    override val componentClassPrefix: String = ""
) : NativeImpl<IosNativeView> {

    override val nativeWidget: IosNativeView = IosNativeView(uiViewClassName)

    val gestureRecognizer: MobileGestureRecognizer = MobileGestureRecognizer()

    protected var styleSnapshot: BootstrapStyleSnapshot =
        BootstrapStyleSnapshot(componentPrefix = componentClassPrefix)
    protected var bootstrapState: ComponentBootstrap? = null
    protected var customStyleBackgroundColor: RgbaColor? = null
    protected var customStyleTextColor: RgbaColor? = null

    private var tooltipTextValue: String? = null
    private var preferredSizeValue: Size? = null

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
            nativeWidget.metadata["lastContextMenuEvent"] = "$x,$y"
            onContextMenuListener?.invoke(PointerEvent(x = x, y = y, button = 2))
        }
    }

    override fun setClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        styleSnapshot = styleSnapshot.copy(customClasses = tokens)
        nativeWidget.metadata["userClasses"] = tokens.joinToString(" ")
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
        nativeWidget.metadata["userClasses"] = updated.joinToString(" ")
    }

    override fun removeClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.removeAll(tokens.toSet())
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        nativeWidget.metadata["userClasses"] = updated.joinToString(" ")
    }

    override fun hasClass(className: String): Boolean {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return false
        return styleSnapshot.customClasses.containsAll(tokens)
    }

    protected open fun syncNativeBootstrapAppearance() {
        val normalizedSnapshot = styleSnapshot.copy(type = styleSnapshot.type?.takeIf { it != BootstrapType.NONE })
        val enumClasses = BootstrapClassMapper.toEnumClassSet(normalizedSnapshot).filterNot { it.endsWith("-") }
        nativeWidget.metadata["enumClasses"] = enumClasses.joinToString(" ")

        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val effectiveType = styleSnapshot.type?.takeIf { it != BootstrapType.NONE } ?: styleSnapshot.backgroundType
        val tintColor = customStyleBackgroundColor ?: BootstrapColorPalette.resolveTintOrNull(effectiveType, isDark)
        val fgColor = customStyleTextColor
            ?: styleSnapshot.textColorType?.takeIf { it != BootstrapType.NONE }?.let { BootstrapColorPalette.resolveColor(it, isDark) }
            ?: styleSnapshot.type?.takeIf { it != BootstrapType.NONE && !styleSnapshot.outline }?.let {
                BootstrapColorPalette.resolveForegroundOn(it)
            }

        if (tintColor == null) {
            nativeWidget.metadata["useSystemControlAppearance"] = "true"
            nativeWidget.metadata.remove("bootstrapTintColor")
            nativeWidget.backgroundColor = null
        } else {
            nativeWidget.metadata["useSystemControlAppearance"] = "false"
            nativeWidget.metadata["bootstrapTintColor"] = tintColor.toCssHex()
            nativeWidget.backgroundColor = tintColor
        }

        if (fgColor != null) {
            nativeWidget.metadata["bootstrapForegroundColor"] = fgColor.toCssHex()
            nativeWidget.tintColor = fgColor
        } else {
            nativeWidget.metadata.remove("bootstrapForegroundColor")
            nativeWidget.tintColor = null
        }

        when (val w = styleSnapshot.width) {
            BootstrapWidth.W_100 -> nativeWidget.metadata["layoutWidth"] = "100%"
            BootstrapWidth.W_AUTO -> nativeWidget.metadata["layoutWidth"] = "auto"
            null -> nativeWidget.metadata.remove("layoutWidth")
            else -> nativeWidget.metadata["layoutWidth"] = "${w.percentage}%"
        }

        when (val h = styleSnapshot.height) {
            BootstrapHeight.H_100 -> nativeWidget.metadata["layoutHeight"] = "100%"
            BootstrapHeight.H_AUTO -> nativeWidget.metadata["layoutHeight"] = "auto"
            null -> nativeWidget.metadata.remove("layoutHeight")
            else -> nativeWidget.metadata["layoutHeight"] = "${h.percentage}%"
        }

        styleSnapshot.opacity?.let { op ->
            setOpacity(op.alpha)
        }

        styleSnapshot.visibility?.let { vis ->
            nativeWidget.isHidden = !vis.visible
        }
        if (styleSnapshot.display == DisplayMode.NONE) {
            nativeWidget.isHidden = true
        }

        styleSnapshot.borderRadius?.let { br ->
            val radiusPt = when (br) {
                BorderRadiusStyle.NONE -> 0f
                BorderRadiusStyle.SMALL -> 4f
                BorderRadiusStyle.DEFAULT -> 6f
                BorderRadiusStyle.LARGE -> 8f
                BorderRadiusStyle.XL -> 12f
                BorderRadiusStyle.XXL -> 16f
                BorderRadiusStyle.PILL -> 999f
                BorderRadiusStyle.CIRCLE -> 999f
            }
            nativeWidget.cornerRadius = radiusPt
            nativeWidget.metadata["cornerRadiusPt"] = radiusPt.toString()
        }

        styleSnapshot.shadow?.let { sh ->
            val radiusPt = when (sh) {
                ShadowElevation.NONE -> 0f
                ShadowElevation.SMALL -> 2f
                ShadowElevation.DEFAULT -> 6f
                ShadowElevation.LARGE -> 16f
            }
            nativeWidget.metadata["shadowRadiusPt"] = radiusPt.toString()
        }

        styleSnapshot.borderWidth?.let { bw ->
            nativeWidget.borderWidth = bw.scale.toFloat()
            nativeWidget.metadata["borderWidthPt"] = bw.scale.toString()
        }

        val padAll = spacingScaleToPt(styleSnapshot.paddingAll)
        val padX = spacingScaleToPt(styleSnapshot.paddingX)
        val padY = spacingScaleToPt(styleSnapshot.paddingY)
        val padTop = spacingScaleToPt(styleSnapshot.paddingTop) ?: padY ?: padAll
        val padBottom = spacingScaleToPt(styleSnapshot.paddingBottom) ?: padY ?: padAll
        val padStart = spacingScaleToPt(styleSnapshot.paddingStart) ?: padX ?: padAll
        val padEnd = spacingScaleToPt(styleSnapshot.paddingEnd) ?: padX ?: padAll
        if (padTop != null || padBottom != null || padStart != null || padEnd != null) {
            setPadding(EdgeInsets(padTop ?: 0, padEnd ?: 0, padBottom ?: 0, padStart ?: 0))
        }

        val marAll = spacingScaleToPt(styleSnapshot.marginAll)
        val marX = spacingScaleToPt(styleSnapshot.marginX)
        val marY = spacingScaleToPt(styleSnapshot.marginY)
        val marTop = spacingScaleToPt(styleSnapshot.marginTop) ?: marY ?: marAll
        val marBottom = spacingScaleToPt(styleSnapshot.marginBottom) ?: marY ?: marAll
        val marStart = spacingScaleToPt(styleSnapshot.marginStart) ?: marX ?: marAll
        val marEnd = spacingScaleToPt(styleSnapshot.marginEnd) ?: marX ?: marAll
        if (marTop != null || marBottom != null || marStart != null || marEnd != null) {
            setMargin(EdgeInsets(marTop ?: 0, marEnd ?: 0, marBottom ?: 0, marStart ?: 0))
        }
    }

    private fun spacingScaleToPt(scale: SpacingScale?): Int? = when (scale) {
        SpacingScale.S0 -> 0
        SpacingScale.S1 -> 4
        SpacingScale.S2 -> 8
        SpacingScale.S3 -> 16
        SpacingScale.S4 -> 24
        SpacingScale.S5 -> 48
        SpacingScale.AUTO -> 0
        null -> null
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        bootstrapState = bootstrap
        styleSnapshot = bootstrap.applyToSnapshot(styleSnapshot)
        syncNativeBootstrapAppearance()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        customStyleBackgroundColor = style.backgroundColor
        customStyleTextColor = style.color

        syncNativeBootstrapAppearance()

        val radiusPt = cssLengthToFloatPt(style.borderRadius)
        if (radiusPt != null) {
            nativeWidget.cornerRadius = radiusPt
            nativeWidget.metadata["cornerRadiusPt"] = radiusPt.toString()
        } else if (styleSnapshot.borderRadius == null) {
            nativeWidget.cornerRadius = 0f
            nativeWidget.metadata.remove("cornerRadiusPt")
        }

        val borderWidthPt = cssLengthToFloatPt(style.borderWidth)
        if (borderWidthPt != null) {
            nativeWidget.borderWidth = borderWidthPt
            nativeWidget.metadata["borderWidthPt"] = borderWidthPt.toString()
        } else if (styleSnapshot.borderWidth == null) {
            nativeWidget.borderWidth = 0f
            nativeWidget.metadata.remove("borderWidthPt")
        }

        style.borderColor?.let {
            nativeWidget.metadata["borderColor"] = it.toCssHex()
        } ?: nativeWidget.metadata.remove("borderColor")

        if (style.opacity != null) {
            setOpacity(style.opacity!!)
        } else if (styleSnapshot.opacity == null) {
            setOpacity(1.0f)
        }

        val wPt = cssLengthToIntPt(style.width)
        val hPt = cssLengthToIntPt(style.height)
        if (wPt != null || hPt != null) {
            val cur = nativeWidget.frame
            nativeWidget.frame = Rect(cur.x, cur.y, wPt ?: cur.width, hPt ?: cur.height)
        }

        val padAll = cssLengthToIntPt(style.padding)
        val padTop = cssLengthToIntPt(style.paddingTop) ?: padAll
        val padRight = cssLengthToIntPt(style.paddingRight) ?: padAll
        val padBottom = cssLengthToIntPt(style.paddingBottom) ?: padAll
        val padLeft = cssLengthToIntPt(style.paddingLeft) ?: padAll
        if (padTop != null || padRight != null || padBottom != null || padLeft != null) {
            setPadding(EdgeInsets(padTop ?: 0, padRight ?: 0, padBottom ?: 0, padLeft ?: 0))
        }

        val marAll = cssLengthToIntPt(style.margin)
        val marTop = cssLengthToIntPt(style.marginTop) ?: marAll
        val marRight = cssLengthToIntPt(style.marginRight) ?: marAll
        val marBottom = cssLengthToIntPt(style.marginBottom) ?: marAll
        val marLeft = cssLengthToIntPt(style.marginLeft) ?: marAll
        if (marTop != null || marRight != null || marBottom != null || marLeft != null) {
            setMargin(EdgeInsets(marTop ?: 0, marRight ?: 0, marBottom ?: 0, marLeft ?: 0))
        }

        when {
            style.display == DisplayMode.NONE || style.visibility == BootstrapVisibility.INVISIBLE -> {
                nativeWidget.isHidden = true
            }
            style.display != null || style.visibility == BootstrapVisibility.VISIBLE -> {
                nativeWidget.isHidden = false
            }
        }

        for ((k, pair) in style.toCssPropertyMap()) {
            if (k == "cursor") continue
            nativeWidget.metadata["style:$k"] = pair.first
        }
    }

    override fun setCustomCursor(cursor: CssCursorValue?) {
        // Touch-first mobile OS: cursor styling is a no-op on iOS.
    }

    private fun cssLengthToFloatPt(length: CssLength?): Float? = when (length) {
        is CssLength.Px -> length.value
        is CssLength.Rem -> length.value * 16f
        is CssLength.Em -> length.value * 16f
        is CssLength.Percent -> length.value
        is CssLength.Vw -> length.value
        is CssLength.Vh -> length.value
        CssLength.Auto, null -> null
    }

    private fun cssLengthToIntPt(length: CssLength?): Int? =
        cssLengthToFloatPt(length)?.roundToInt()

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
        style.opacity.let { setOpacity(it) }
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
    override fun setOnPointerEnter(listener: ((PointerEvent) -> Unit)?) { onPointerEnterListener = listener }
    override fun setOnPointerLeave(listener: ((PointerEvent) -> Unit)?) { onPointerLeaveListener = listener }
    override fun setOnWheel(listener: ((WheelEvent) -> Unit)?) { onWheelListener = listener }
    override fun setOnKeyDown(listener: ((KeyEvent) -> Unit)?) { onKeyDownListener = listener }
    override fun setOnKeyUp(listener: ((KeyEvent) -> Unit)?) { onKeyUpListener = listener }

    fun dispatchPointerEnter(event: PointerEvent = PointerEvent()) {
        onPointerEnterListener?.invoke(event)
    }

    fun dispatchPointerLeave(event: PointerEvent = PointerEvent()) {
        onPointerLeaveListener?.invoke(event)
    }

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
