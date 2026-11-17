package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
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
import com.nexustools.genericui.style.bootstrap.ComponentBootstrap
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
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
    protected var customStyleBorderColor: RgbaColor? = null
    protected var customStyleBorderWidthPx: Float? = null
    protected var customStyleBorderRadiusPx: Float? = null
    protected var customStyleFontSizePx: Float? = null
    protected var customStyleFontWeightNumeric: Int? = null
    protected var explicitGapDp: Int? = null
    protected var baseComponentStyle: ComponentStyle? = null
    protected var baseComputedStyle: ComputedStyle? = null

    protected open fun allowsSystemAestheticStyles(): Boolean =
        this is AndroidStyledContainerImpl || this is AndroidStyledButtonImpl

    protected open fun allowsSystemTypographyStyles(): Boolean =
        allowsSystemAestheticStyles() || this is AndroidTextImpl || this is AndroidMarkdownViewImpl

    private var tooltipTextValue: String? = null
    protected var preferredSizeValue: Size? = null

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

    protected open fun createDefaultAndroidView(context: Context): View? = View(context)

    protected open fun shouldApplyBaseBackgroundDrawable(): Boolean = true

    init {
        if (initialView == null) {
            AndroidViewHandle.activeContext?.let { ctx ->
                nativeWidget.androidView = runCatching { createDefaultAndroidView(ctx) }.getOrNull()
            }
        }
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

    internal open fun ensureAndroidView(context: Context? = AndroidViewHandle.activeContext): View? {
        if (nativeWidget.androidView == null && context != null) {
            nativeWidget.androidView = runCatching { createDefaultAndroidView(context) }.getOrNull()
            if (nativeWidget.androidView != null) {
                syncNativeBootstrapAppearance()
                syncAndroidView()
            }
        }
        return nativeWidget.androidView
    }

    override fun setClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.toSet()
        styleSnapshot = styleSnapshot.copy(customClasses = tokens)
        nativeWidget.classes.clear()
        nativeWidget.classes.addAll(tokens)
        syncAndroidView()
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
        syncAndroidView()
    }

    override fun removeClass(className: String) {
        val tokens = className.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return
        val updated = LinkedHashSet(styleSnapshot.customClasses)
        updated.removeAll(tokens.toSet())
        styleSnapshot = styleSnapshot.copy(customClasses = updated)
        nativeWidget.classes.clear()
        nativeWidget.classes.addAll(updated)
        syncAndroidView()
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
        val activeTheme = BootstrapColorPalette.activeThemeConfig
        nativeWidget.attributes["systemThemeMode"] = if (isDark) "dark" else "light"
        val effectiveType = styleSnapshot.type ?: styleSnapshot.backgroundType
        val allowAesthetic = allowsSystemAestheticStyles()
        val allowTypography = allowsSystemTypographyStyles()
        val tintColor = (if (allowAesthetic) customStyleBackgroundColor else null)
            ?: BootstrapColorPalette.resolveTintOrNull(effectiveType, isDark, activeTheme)
        val fgColor = (if (allowTypography) customStyleTextColor else null)
            ?: styleSnapshot.textColorType?.takeIf { it != BootstrapType.NONE }?.let {
                BootstrapColorPalette.resolveColor(it, isDark, activeTheme)
            }
            ?: styleSnapshot.type?.takeIf { it != BootstrapType.NONE && !styleSnapshot.outline }?.let {
                BootstrapColorPalette.resolveForegroundOn(it, isDark, activeTheme)
            }

        if (tintColor == null) {
            nativeWidget.attributes["useSystemControlAppearance"] = "true"
            nativeWidget.attributes.remove("bootstrapTintColor")
            AndroidViewHandle.runOnMainThread {
                runCatching { nativeWidget.androidView?.backgroundTintList = null }
            }
        } else {
            nativeWidget.attributes["useSystemControlAppearance"] = "false"
            nativeWidget.attributes["bootstrapTintColor"] = tintColor.toHex()
            AndroidViewHandle.runOnMainThread {
                nativeWidget.androidView?.let { view ->
                    runCatching {
                        view.backgroundTintList = ColorStateList.valueOf(tintColor.toArgbInt())
                    }.onFailure {
                        runCatching { view.setBackgroundColor(tintColor.toArgbInt()) }
                    }
                }
            }
        }

        if (fgColor != null) {
            nativeWidget.attributes["bootstrapForegroundColor"] = fgColor.toHex()
            AndroidViewHandle.runOnMainThread {
                runCatching { (nativeWidget.androidView as? TextView)?.setTextColor(fgColor.toArgbInt()) }
            }
        } else {
            nativeWidget.attributes.remove("bootstrapForegroundColor")
        }

        when (val w = styleSnapshot.width) {
            BootstrapWidth.W_100, BootstrapWidth.VW_100, BootstrapWidth.MIN_VW_100 -> {
                nativeWidget.attributes["layoutWidth"] = "MATCH_PARENT"
                nativeWidget.fillMaxWidth = true
            }
            BootstrapWidth.W_25, BootstrapWidth.W_50, BootstrapWidth.W_75 -> {
                nativeWidget.attributes["layoutWidth"] = "${((w.fraction ?: 1f) * 100f).roundToInt()}%"
            }
            BootstrapWidth.W_AUTO, BootstrapWidth.MW_100 -> {
                nativeWidget.attributes["layoutWidth"] = "WRAP_CONTENT"
            }
            null -> nativeWidget.attributes.remove("layoutWidth")
        }

        when (val h = styleSnapshot.height) {
            BootstrapHeight.H_100, BootstrapHeight.VH_100, BootstrapHeight.MIN_VH_100 -> {
                nativeWidget.attributes["layoutHeight"] = "MATCH_PARENT"
                nativeWidget.fillMaxHeight = true
            }
            BootstrapHeight.H_25, BootstrapHeight.H_50, BootstrapHeight.H_75 -> {
                nativeWidget.attributes["layoutHeight"] = "${((h.fraction ?: 1f) * 100f).roundToInt()}%"
            }
            BootstrapHeight.H_AUTO, BootstrapHeight.MH_100 -> {
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
                AndroidViewHandle.runOnMainThread {
                    runCatching { nativeWidget.androidView?.visibility = View.INVISIBLE }
                }
            }
            BootstrapVisibility.VISIBLE -> {
                nativeWidget.attributes["visibility"] = "VISIBLE"
                if (nativeWidget.visible) {
                    AndroidViewHandle.runOnMainThread {
                        runCatching { nativeWidget.androidView?.visibility = View.VISIBLE }
                    }
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
                ShadowElevation.SMALL -> 2f
                ShadowElevation.DEFAULT -> 6f
                ShadowElevation.LARGE -> 12f
            }
            nativeWidget.attributes["elevation"] = elev.toString()
            AndroidViewHandle.runOnMainThread {
                runCatching { nativeWidget.androidView?.elevation = elev }
            }
        }

        syncAndroidView()
    }

    protected fun isCardComponent(): Boolean =
        componentClassPrefix == "card" ||
            nativeWidget.classes.contains("card") ||
            nativeWidget.classes.any { it.endsWith("-card") }

    protected fun isToastComponent(): Boolean =
        componentClassPrefix == "toast" || nativeWidget.classes.contains("toast")

    protected open fun resolveDefaultPaddingPx(): EdgeInsets {
        val raw = nativeWidget.padding
        if (raw != EdgeInsets.ZERO) {
            return EdgeInsets(
                top = AndroidViewHandle.dpToPx(raw.top),
                right = AndroidViewHandle.dpToPx(raw.right),
                bottom = AndroidViewHandle.dpToPx(raw.bottom),
                left = AndroidViewHandle.dpToPx(raw.left)
            )
        }
        if (isCardComponent()) {
            val p = AndroidViewHandle.dpToPx(12)
            return EdgeInsets(p, p, p, p)
        }
        if (isToastComponent()) {
            val pxH = AndroidViewHandle.dpToPx(12)
            val pxV = AndroidViewHandle.dpToPx(8)
            return EdgeInsets(pxV, pxH, pxV, pxH)
        }
        return EdgeInsets.ZERO
    }

    protected open fun syncAndroidView() {
        runCatching {
            val weightAttr = nativeWidget.attributes["data-stack-weight"]
                ?: nativeWidget.attributes["layoutWeight"]
            nativeWidget.layoutWeight = weightAttr?.toFloatOrNull()
                ?: if (nativeWidget.classes.contains("flex-grow-1") || styleSnapshot.flexGrow == BootstrapFlexGrow.GROW_1) 1f else 0f

            nativeWidget.fillMaxWidth =
                nativeWidget.attributes["layoutWidth"] == "MATCH_PARENT" ||
                    nativeWidget.classes.contains("w-100") ||
                    styleSnapshot.width == BootstrapWidth.W_100 ||
                    styleSnapshot.width == BootstrapWidth.VW_100 ||
                    styleSnapshot.width == BootstrapWidth.MIN_VW_100

            nativeWidget.fillMaxHeight =
                nativeWidget.attributes["layoutHeight"] == "MATCH_PARENT" ||
                    nativeWidget.classes.contains("h-100") ||
                    styleSnapshot.height == BootstrapHeight.H_100 ||
                    styleSnapshot.height == BootstrapHeight.VH_100 ||
                    styleSnapshot.height == BootstrapHeight.MIN_VH_100

            val resolvedFlexDir = styleSnapshot.flexDirection ?: when {
                nativeWidget.classes.contains("flex-row") || componentClassPrefix == "hstack" -> FlexDirection.ROW
                else -> FlexDirection.COLUMN
            }
            nativeWidget.flexDirection = resolvedFlexDir
            nativeWidget.flexWrap = styleSnapshot.flexWrap ?: if (nativeWidget.classes.contains("flex-wrap")) FlexWrap.WRAP else FlexWrap.NOWRAP
            nativeWidget.alignItems = styleSnapshot.alignItems ?: AlignItems.STRETCH
            nativeWidget.justifyContent = styleSnapshot.justifyContent ?: JustifyContent.START

            val gapScaleDp = spacingScaleToPx(styleSnapshot.gap)
            val gapDp = explicitGapDp ?: gapScaleDp ?: 0
            nativeWidget.gapPx = AndroidViewHandle.dpToPx(gapDp)

            AndroidViewHandle.runOnMainThread {
                runCatching {
                    val view = nativeWidget.androidView ?: return@runCatching

                    val targetVisibility = when {
                        !nativeWidget.visible || styleSnapshot.display == DisplayMode.NONE -> View.GONE
                        styleSnapshot.visibility == BootstrapVisibility.INVISIBLE ||
                            nativeWidget.attributes["visibility"] == "INVISIBLE" -> View.INVISIBLE
                        else -> View.VISIBLE
                    }
                    if (view.visibility != targetVisibility) {
                        view.visibility = targetVisibility
                    }
                    view.isEnabled = nativeWidget.enabled
                    view.alpha = nativeWidget.alpha

                    val effPad = resolveDefaultPaddingPx()
                    val padTarget = nativeWidget.childHostViewGroup ?: view
                    padTarget.setPadding(effPad.left, effPad.top, effPad.right, effPad.bottom)

                    if (shouldApplyBaseBackgroundDrawable()) {
                        applyBaseSurfaceDrawable(view)
                    }

                    if (onClickListener != null) {
                        view.isClickable = true
                        view.setOnClickListener { dispatchClick() }
                    }

                    val parentVg = view.parent
                    if (parentVg is AndroidFlexContainerView) {
                        val lp = (view.layoutParams as? AndroidFlexContainerView.LayoutParams)
                            ?: AndroidFlexContainerView.buildLayoutParamsFor(nativeWidget)
                        AndroidFlexContainerView.applyHandleToLayoutParams(nativeWidget, lp)
                        view.layoutParams = lp
                        parentVg.requestLayout()
                    }
                }
            }
        }
    }

    protected open fun applyBaseSurfaceDrawable(view: View) {
        val isDark = AndroidViewHandle.isDarkTheme()
        val isCard = isCardComponent()
        val isToast = isToastComponent()
        val isViewAsBanner = nativeWidget.classes.contains("view-as-banner")
        val isQueueRow = nativeWidget.classes.contains("queue-row") || nativeWidget.classes.contains("handler-queue-row")

        val bgType = styleSnapshot.backgroundType ?: if (isToast) styleSnapshot.type else null
        val explicitBg = customStyleBackgroundColor
            ?: bgType?.takeIf { it != BootstrapType.NONE }?.let { BootstrapColorPalette.resolveColor(it, isDark) }

        val borderTypeColor = styleSnapshot.borderType?.takeIf { it != BootstrapType.NONE }?.let {
            BootstrapColorPalette.resolveColor(it, isDark)
        }
        val explicitBorderColor = customStyleBorderColor ?: borderTypeColor

        val radiusDp = when {
            customStyleBorderRadiusPx != null -> customStyleBorderRadiusPx!!
            styleSnapshot.borderRadius != null -> styleSnapshot.borderRadius!!.radiusPx.toFloat()
            isCard || isToast -> 10f
            isViewAsBanner || isQueueRow -> 8f
            else -> 0f
        }

        val borderWidthDp = when {
            customStyleBorderWidthPx != null -> customStyleBorderWidthPx!!
            styleSnapshot.borderWidth != null -> styleSnapshot.borderWidth!!.widthPx.toFloat()
            isCard || isToast || isViewAsBanner || isQueueRow -> 1f
            else -> 0f
        }

        if (explicitBg == null && !isCard && !isToast && !isViewAsBanner && !isQueueRow && borderWidthDp <= 0f && radiusDp <= 0f) {
            return
        }

        val fillArgb: Int = when {
            explicitBg != null -> explicitBg.toArgbInt()
            isViewAsBanner -> if (isDark) 0xFF3A2E05.toInt() else 0xFFFFF3CD.toInt()
            isCard -> if (isDark) 0xFF1E222A.toInt() else 0xFFFFFFFF.toInt()
            isQueueRow -> when {
                nativeWidget.classes.contains("queue-row-unavailable") ||
                    nativeWidget.classes.contains("handler-queue-row-trouble") ->
                    if (isDark) 0xFF3B1A1E.toInt() else 0xFFFDE8E8.toInt()
                nativeWidget.classes.contains("queue-row-trouble") ->
                    if (isDark) 0xFF3A2E05.toInt() else 0xFFFFF8E1.toInt()
                nativeWidget.classes.contains("queue-row-handled") ||
                    nativeWidget.classes.contains("handler-queue-row-handled") ->
                    if (isDark) 0xFF142E24.toInt() else 0xFFE8F5E9.toInt()
                nativeWidget.classes.contains("queue-row-hot") ->
                    if (isDark) 0xFF331B22.toInt() else 0xFFFFF0F0.toInt()
                else -> if (isDark) 0xFF252A34.toInt() else 0xFFF8F9FC.toInt()
            }
            else -> 0x00000000
        }

        val strokeArgb: Int = when {
            explicitBorderColor != null -> explicitBorderColor.toArgbInt()
            isViewAsBanner -> if (isDark) 0xFF66520E.toInt() else 0xFFFFECB5.toInt()
            isCard -> if (isDark) 0xFF2E3440.toInt() else 0xFFE0E4EC.toInt()
            isQueueRow -> if (isDark) 0xFF323846.toInt() else 0xFFE2E8F0.toInt()
            else -> if (isDark) 0xFF3B4252.toInt() else 0xFFCBD5E1.toInt()
        }

        val drawable = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fillArgb)
            if (radiusDp > 0f) {
                cornerRadius = AndroidViewHandle.dpToPxFloat(radiusDp)
            }
            if (borderWidthDp > 0f) {
                setStroke(AndroidViewHandle.dpToPx(borderWidthDp).coerceAtLeast(1), strokeArgb)
            }
        }
        view.backgroundTintList = null
        view.background = drawable
    }

    protected fun spacingScaleToPx(scale: SpacingScale?): Int? = when (scale) {
        SpacingScale.ZERO -> 0
        SpacingScale.ONE -> 4
        SpacingScale.TWO -> 8
        SpacingScale.THREE -> 16
        SpacingScale.FOUR -> 24
        SpacingScale.FIVE -> 48
        SpacingScale.AUTO, null -> null
    }

    override fun applyComponentBootstrap(bootstrap: ComponentBootstrap) {
        styleSnapshot = bootstrap.applyToSnapshot(styleSnapshot)
        syncNativeBootstrapAppearance()
    }

    override fun applyComponentStyle(style: ComponentStyle) {
        val allowAesthetic = allowsSystemAestheticStyles()
        val allowTypography = allowsSystemTypographyStyles()
        val filtered = style.copyFilteredForSystemComponent(
            allowTypography = allowTypography,
            allowAestheticSkin = allowAesthetic
        )
        baseComponentStyle = filtered

        if (allowAesthetic) {
            customStyleBackgroundColor = filtered.getBackgroundColor() ?: customStyleBackgroundColor
            customStyleBorderColor = filtered.getBorderColor() ?: customStyleBorderColor
            filtered.borderWidthPx?.let { customStyleBorderWidthPx = it }
            filtered.borderRadiusPx?.let { customStyleBorderRadiusPx = it }
        } else {
            customStyleBackgroundColor = null
            customStyleBorderColor = null
            customStyleBorderWidthPx = null
            customStyleBorderRadiusPx = null
            nativeWidget.attributes.remove("backgroundColor")
            nativeWidget.attributes.remove("backgroundGradient")
            nativeWidget.attributes.remove("borderColor")
            nativeWidget.attributes.remove("borderWidthPx")
            nativeWidget.attributes.remove("borderRadiusPx")
            nativeWidget.attributes.remove("boxShadowBlurPx")
            nativeWidget.attributes.remove("boxShadowColor")
        }

        if (allowTypography) {
            customStyleTextColor = filtered.getTextColor() ?: customStyleTextColor
            filtered.fontSizePx?.let { customStyleFontSizePx = it }
            filtered.fontWeightNumeric?.let { customStyleFontWeightNumeric = it }
            filtered.getFontFamily()?.resolvedPrimaryName()?.let { nativeWidget.attributes["fontFamily"] = it }
            filtered.fontSizePx?.let { nativeWidget.attributes["fontSizePx"] = it.toString() }
            filtered.getFontWeight()?.let { nativeWidget.attributes["fontWeight"] = it.cssClass }
            filtered.getFontStyle()?.let { nativeWidget.attributes["fontStyle"] = it.cssValue }
            filtered.getLineHeight()?.let { nativeWidget.attributes["lineHeight"] = it.toCssString() }
            filtered.getLetterSpacing()?.let { nativeWidget.attributes["letterSpacing"] = it.toCssString() }
            filtered.getTextAlign()?.let { nativeWidget.attributes["textAlign"] = when (it) {
                TextAlignment.START -> "left"
                TextAlignment.CENTER -> "center"
                TextAlignment.END -> "right"
                TextAlignment.JUSTIFY -> "justify"
            } }
            filtered.getTextDecoration()?.let { nativeWidget.attributes["textDecoration"] = it.cssValue }
            filtered.getTextTransform()?.let { nativeWidget.attributes["textTransform"] = it.cssClass }
            filtered.getTextOverflow()?.let { nativeWidget.attributes["textOverflow"] = it.cssClass }
            filtered.getWhiteSpace()?.let { nativeWidget.attributes["whiteSpace"] = it.cssValue }
            filtered.getWordBreak()?.let { nativeWidget.attributes["wordBreak"] = it.cssValue }
            filtered.getTextColor()?.let { nativeWidget.attributes["textColor"] = it.toCssRgbaString() }
        } else {
            customStyleTextColor = null
            customStyleFontSizePx = null
            customStyleFontWeightNumeric = null
            nativeWidget.attributes.remove("textColor")
            nativeWidget.attributes.remove("fontFamily")
            nativeWidget.attributes.remove("fontSizePx")
            nativeWidget.attributes.remove("fontWeight")
            nativeWidget.attributes.remove("fontStyle")
            nativeWidget.attributes.remove("lineHeight")
            nativeWidget.attributes.remove("letterSpacing")
            nativeWidget.attributes.remove("textAlign")
            nativeWidget.attributes.remove("textDecoration")
            nativeWidget.attributes.remove("textTransform")
            nativeWidget.attributes.remove("textOverflow")
            nativeWidget.attributes.remove("whiteSpace")
            nativeWidget.attributes.remove("wordBreak")
        }

        filtered.getOpacity()?.let { setOpacity(it) }

        filtered.getFlexDirection()?.let { styleSnapshot = styleSnapshot.copy(flexDirection = it) }
        filtered.getFlexWrap()?.let { styleSnapshot = styleSnapshot.copy(flexWrap = it) }
        filtered.getAlignItems()?.let { styleSnapshot = styleSnapshot.copy(alignItems = it) }
        filtered.getJustifyContent()?.let { styleSnapshot = styleSnapshot.copy(justifyContent = it) }
        filtered.getGap()?.resolvePx()?.roundToInt()?.let { explicitGapDp = it }

        val wLen = filtered.getWidth()
        val hLen = filtered.getHeight()
        if (wLen != null || hLen != null) {
            if (wLen?.unit == CssUnit.PERCENT && wLen.value == 100f) {
                nativeWidget.attributes["layoutWidth"] = "MATCH_PARENT"
                nativeWidget.fillMaxWidth = true
            }
            if (hLen?.unit == CssUnit.PERCENT && hLen.value == 100f) {
                nativeWidget.attributes["layoutHeight"] = "MATCH_PARENT"
                nativeWidget.fillMaxHeight = true
            }
            val wPx = wLen?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
                ?: preferredSizeValue?.width
                ?: nativeWidget.bounds.width
            val hPx = hLen?.takeIf { it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
                ?: preferredSizeValue?.height
                ?: nativeWidget.bounds.height
            if (wLen != null && wLen.unit != CssUnit.PERCENT && wPx > 0) {
                nativeWidget.hasExplicitPreferredWidth = true
            }
            if (hLen != null && hLen.unit != CssUnit.PERCENT && hPx > 0) {
                nativeWidget.hasExplicitPreferredHeight = true
            }
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, wPx, hPx)
        }

        filtered.getPadding()?.let { setPadding(it.toEdgeInsets()) }
        filtered.getMargin()?.let { setMargin(it.toEdgeInsets()) }

        when (filtered.getVisibility()) {
            CssVisibility.HIDDEN, CssVisibility.COLLAPSE -> {
                nativeWidget.attributes["visibility"] = "INVISIBLE"
                AndroidViewHandle.runOnMainThread {
                    runCatching { nativeWidget.androidView?.visibility = View.INVISIBLE }
                }
            }
            CssVisibility.VISIBLE -> {
                nativeWidget.attributes["visibility"] = "VISIBLE"
                if (nativeWidget.visible) {
                    AndroidViewHandle.runOnMainThread {
                        runCatching { nativeWidget.androidView?.visibility = View.VISIBLE }
                    }
                }
            }
            null -> {}
        }

        if (filtered.getDisplay() == CssDisplay.NONE) {
            setVisible(false)
        }
        filtered.getFlexGrow()?.let { nativeWidget.attributes["layoutWeight"] = it.toString() }
            ?: nativeWidget.attributes.remove("layoutWeight")
        filtered.getFlexShrink()?.let { nativeWidget.attributes["flexShrink"] = it.toString() }
            ?: nativeWidget.attributes.remove("flexShrink")
        filtered.getFlexBasis()?.let { nativeWidget.attributes["flexBasis"] = it.toCssString() }
            ?: nativeWidget.attributes.remove("flexBasis")
        filtered.getAspectRatio()?.let { nativeWidget.attributes["aspectRatio"] = it.toCssString() }
            ?: nativeWidget.attributes.remove("aspectRatio")
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
        syncAndroidView()
    }

    override fun isVisible(): Boolean = nativeWidget.visible

    override fun setEnabled(enabled: Boolean) {
        nativeWidget.enabled = enabled
        syncAndroidView()
    }

    override fun isEnabled(): Boolean = nativeWidget.enabled

    override fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        nativeWidget.alpha = clamped
        AndroidViewHandle.runOnMainThread {
            runCatching { nativeWidget.androidView?.alpha = clamped }
        }
    }

    override fun getOpacity(): Float = nativeWidget.alpha

    override fun setTooltipText(tooltip: String?) {
        tooltipTextValue = tooltip
    }

    override fun getTooltipText(): String? = tooltipTextValue

    override fun setPreferredSize(size: Size?) {
        preferredSizeValue = size
        if (size != null) {
            nativeWidget.hasExplicitPreferredWidth = size.width > 0
            nativeWidget.hasExplicitPreferredHeight = size.height > 0
            nativeWidget.bounds = Rect(nativeWidget.bounds.x, nativeWidget.bounds.y, size.width, size.height)
        } else {
            nativeWidget.hasExplicitPreferredWidth = false
            nativeWidget.hasExplicitPreferredHeight = false
        }
        syncAndroidView()
    }

    override fun getBounds(): Rect = nativeWidget.bounds

    override fun setPadding(padding: EdgeInsets) {
        nativeWidget.padding = padding
        syncAndroidView()
    }

    fun getPadding(): EdgeInsets = nativeWidget.padding

    fun getPaddingScale(): SpacingScale? = styleSnapshot.paddingAll

    override fun setMargin(margin: EdgeInsets) {
        nativeWidget.margin = margin
        syncAndroidView()
    }

    fun getMargin(): EdgeInsets = nativeWidget.margin

    protected fun resolveEffectiveBorderRadiusPx(defaultDp: Float = 6f): Float {
        val dp = when {
            customStyleBorderRadiusPx != null -> customStyleBorderRadiusPx!!
            styleSnapshot.borderRadius != null -> styleSnapshot.borderRadius!!.radiusPx.toFloat()
            else -> defaultDp
        }
        return AndroidViewHandle.dpToPxFloat(dp)
    }

    override fun applyComputedStyle(style: ComputedStyle) {
        val allowAesthetic = allowsSystemAestheticStyles()
        val allowTypography = allowsSystemTypographyStyles()
        val filtered = style.copyFilteredForSystemComponent(
            allowTypography = allowTypography,
            allowAestheticSkin = allowAesthetic
        )
        baseComputedStyle = filtered
        customStyleBackgroundColor = if (allowAesthetic) filtered.backgroundColor else null
        customStyleTextColor = if (allowTypography) filtered.textColor else null
        customStyleBorderColor = if (allowAesthetic) filtered.borderColor else null
        customStyleBorderWidthPx = if (allowAesthetic) filtered.borderWidthPx else null
        customStyleBorderRadiusPx = if (allowAesthetic) filtered.borderRadiusPx else null
        customStyleFontSizePx = if (allowTypography) filtered.fontSizePx else null
        customStyleFontWeightNumeric = if (allowTypography) filtered.fontWeightNumeric else null
        filtered.opacity.let { setOpacity(it) }
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
        nativeWidget.attributes["lastTransitionProperty"] = property
        nativeWidget.attributes["viewPropertyAnimator:$property"] = "$fromValue->$toValue@${spec.durationMs}ms"
        val view = nativeWidget.androidView
        if (view == null) {
            if (property == "opacity") {
                toValue.toFloatOrNull()?.let { setOpacity(it) }
            }
            onComplete()
            return true
        }
        runCatching {
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
        }.onFailure {
            onComplete()
        }
        return true
    }

    override fun setAttribute(name: String, value: String?) {
        if (value == null) {
            nativeWidget.attributes.remove(name)
        } else {
            nativeWidget.attributes[name] = value
        }
        syncAndroidView()
    }

    override fun setOnClick(listener: (() -> Unit)?) {
        onClickListener = listener
        AndroidViewHandle.runOnMainThread {
            runCatching {
                nativeWidget.androidView?.setOnClickListener(
                    if (listener != null) View.OnClickListener { dispatchClick() } else null
                )
            }
        }
    }

    override fun setOnDoubleClick(listener: (() -> Unit)?) { onDoubleClickListener = listener }

    override fun setOnContextMenu(listener: ((PointerEvent) -> Unit)?) {
        onContextMenuListener = listener
        nativeWidget.attributes["longPressContextMenuEnabled"] = (listener != null).toString()
        val view = nativeWidget.androidView
        if (view != null) {
            AndroidViewHandle.runOnMainThread {
                runCatching {
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
        runCatching {
            view.setOnHoverListener { _, event ->
                val pe = PointerEvent(x = event.x, y = event.y)
                when (event.actionMasked) {
                    MotionEvent.ACTION_HOVER_ENTER -> onPointerEnterListener?.invoke(pe)
                    MotionEvent.ACTION_HOVER_EXIT -> onPointerLeaveListener?.invoke(pe)
                }
                false
            }
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
        if (com.nexustools.genericui.event.PopupMouseCaptureManager.interceptClick(lastTouchX, lastTouchY, this)) return false
        val listener = onClickListener ?: return false
        listener()
        return true
    }

    fun dispatchClick() {
        if (!nativeWidget.enabled) return
        if (com.nexustools.genericui.event.PopupMouseCaptureManager.interceptClick(lastTouchX, lastTouchY, this)) return
        onClickListener?.invoke()
    }

    fun dispatchContextMenu(event: PointerEvent) {
        onContextMenuListener?.invoke(event)
    }

    private fun ensureTouchListenerAttached(view: View) {
        if (touchListenerAttached) return
        touchListenerAttached = true
        runCatching {
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
                        if (com.nexustools.genericui.event.PopupMouseCaptureManager.interceptPointerDown(x, y, this)) {
                            return@setOnTouchListener true
                        }
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
                        if (com.nexustools.genericui.event.PopupMouseCaptureManager.interceptPointerUp(x, y, this)) {
                            com.nexustools.genericui.event.PopupMouseCaptureManager.interceptClick(x, y, this)
                            return@setOnTouchListener true
                        }
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
    }

    override fun dispose() {
        nativeWidget.removeFromParent()
    }
}
