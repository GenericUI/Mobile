package com.nexustools.genericui.android

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.PathInterpolator
import android.widget.TextView
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.*
import com.nexustools.genericui.enums.css.CssDisplay
import com.nexustools.genericui.enums.css.CssPointerEvents
import com.nexustools.genericui.enums.css.CssPosition
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
import com.nexustools.genericui.style.css.CssAspectRatio
import com.nexustools.genericui.style.css.CssCursorValue
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.css.StyledVisualSpec
import com.nexustools.genericui.style.mapper.BootstrapClassMapper
import com.nexustools.genericui.style.mapper.BootstrapStyleSnapshot
import com.nexustools.genericui.style.policy.AestheticStylePolicy
import com.nexustools.genericui.style.theme.SystemThemeDetector
import com.nexustools.genericui.style.transition.CssTransform2D
import com.nexustools.genericui.style.transition.CssTransitionSpec
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
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
    initialView: View? = null,
    initialTagName: String = ""
) : NativeImpl<AndroidViewHandle> {

    override val nativeWidget: AndroidViewHandle = AndroidViewHandle(viewClassName, initialView).also {
        if (initialTagName.isNotBlank()) {
            it.attributes["data-genericui-tag"] = initialTagName
            it.attributes["tagName"] = initialTagName
        }
    }

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
    protected var rawLayoutComponentStyle: ComponentStyle? = null
    protected var rawLayoutComputedStyle: ComputedStyle? = null
    private var lastAppliedBootstrapWidth: BootstrapWidth? = null
    private var lastAppliedBootstrapHeight: BootstrapHeight? = null

    open val componentTagName: String
        get() = nativeWidget.attributes["data-genericui-tag"]
            ?: nativeWidget.attributes["tagName"]
            ?: ""

    internal fun isInCustomStyleEngineSubtree(): Boolean {
        if (nativeWidget.attributes["data-style-engine"] == "custom") return true
        var cur: AndroidViewHandle? = nativeWidget.parentHandle
        while (cur != null) {
            if (cur.attributes["data-style-engine"] == "custom") return true
            cur = cur.parentHandle
        }
        return false
    }

    protected open fun allowsSystemAestheticStyles(): Boolean =
        this is AndroidStyledContainerImpl ||
            this is AndroidStyledComponentImpl ||
            this is AndroidStyledButtonImpl ||
            AestheticStylePolicy.isExplicitlyStyledTagOrPrefix(componentTagName, componentClassPrefix) ||
            isCardComponent() ||
            isToastComponent() ||
            (this is AndroidTextImpl && nativeWidget.classes.contains("badge")) ||
            isInCustomStyleEngineSubtree()

    protected open fun allowsBackdropBlur(): Boolean =
        this is AndroidStyledContainerImpl ||
            this is AndroidStyledComponentImpl ||
            this is AndroidStyledButtonImpl

    protected open fun allowsBootstrapButtonTinting(): Boolean =
        this is AndroidButtonImpl && this !is AndroidStyledButtonImpl

    internal fun allowsBootstrapTintingForControl(): Boolean =
        allowsBootstrapButtonTinting() || allowsBootstrapColorForStyledSpec()

    protected open fun allowsBootstrapNonControlColor(): Boolean =
        AestheticStylePolicy.isBootstrapSpecificNonControlTagOrPrefix(componentTagName, componentClassPrefix)

    protected open fun allowsBootstrapColorForStyledSpec(): Boolean =
        this is AndroidStyledButtonImpl || allowsBootstrapNonControlColor() || isInCustomStyleEngineSubtree()

    internal fun snapshotForStyledVisuals(): BootstrapStyleSnapshot =
        if (allowsBootstrapColorForStyledSpec()) {
            styleSnapshot
        } else {
            styleSnapshot.copy(
                type = null,
                backgroundType = null,
                textColorType = null,
                borderType = null
            )
        }

    internal fun sanitizeIncomingStyledVisuals(spec: StyledVisualSpec): StyledVisualSpec {
        if (allowsBootstrapColorForStyledSpec()) return spec
        val semType = styleSnapshot.type?.takeIf { it.isSemantic }
            ?: styleSnapshot.backgroundType?.takeIf { it.isSemantic }
        val textType = styleSnapshot.textColorType?.takeIf { it.isSemantic }
        val borderType = styleSnapshot.borderType?.takeIf { it.isSemantic }
        if (semType == null && textType == null && borderType == null) return spec

        val isDark = SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK
        val explicitBg = rawLayoutComponentStyle?.getBackgroundColor() ?: baseComputedStyle?.backgroundColor
        val explicitFg = rawLayoutComponentStyle?.getTextColor() ?: baseComputedStyle?.textColor
        val explicitBorder = rawLayoutComponentStyle?.getBorderColor() ?: baseComputedStyle?.borderColor

        var sanitized = spec
        if (explicitBg == null && semType != null && !styleSnapshot.outline) {
            val bsBg = BootstrapColorPalette.resolveColor(semType, isDark)
            if (sanitized.backgroundColor == bsBg) {
                sanitized = sanitized.copy(backgroundColor = null)
            }
        }
        if (explicitFg == null && (semType != null || textType != null)) {
            val bsFg = textType?.let { BootstrapColorPalette.resolveColor(it, isDark) }
                ?: semType?.let {
                    if (styleSnapshot.outline) BootstrapColorPalette.resolveColor(it, isDark)
                    else BootstrapColorPalette.resolveForegroundOn(it)
                }
            if (bsFg != null && sanitized.textColor == bsFg) {
                sanitized = sanitized.copy(textColor = null)
            }
        }
        if (explicitBorder == null && (borderType != null || (semType != null && styleSnapshot.outline))) {
            val bsBorder = (borderType ?: semType)?.let { BootstrapColorPalette.resolveColor(it, isDark) }
            if (bsBorder != null && sanitized.borderColor == bsBorder) {
                val clearWidth = rawLayoutComponentStyle?.borderWidthPx == null &&
                    rawLayoutComputedStyle?.borderWidthPx == null &&
                    styleSnapshot.borderWidth == null &&
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

    internal fun resolveEffectiveCssPosition(): CssPosition {
        rawLayoutComponentStyle?.getPosition()?.let { return it }
        val rawPos = rawLayoutComputedStyle?.position?.trim()?.lowercase()
        when (rawPos) {
            "relative" -> return CssPosition.RELATIVE
            "absolute" -> return CssPosition.ABSOLUTE
            "fixed" -> return CssPosition.FIXED
            "sticky" -> return CssPosition.STICKY
            "static" -> return CssPosition.STATIC
        }
        return when (styleSnapshot.position) {
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

    protected open fun allowsSystemTypographyStyles(): Boolean =
        allowsSystemAestheticStyles() || this is AndroidTextImpl || this is AndroidMarkdownViewImpl

    private var tooltipTextValue: String? = null
    protected var preferredSizeValue: Size? = null
    private var focusableState: Boolean = true
    private var focusedState: Boolean = false
    private var hasSavedFocusableDefaults: Boolean = false
    private var savedFocusable: Boolean = false
    private var savedFocusableInTouchMode: Boolean = false
    private var savedApi26Focusable: Int = View.FOCUSABLE_AUTO
    private var savedDescendantFocusability: Int = ViewGroup.FOCUS_BEFORE_DESCENDANTS
    private val transitionLock = Any()
    private val activePropertyCancelHooks = linkedMapOf<String, () -> Unit>()
    @Volatile
    private var activeOpacityTransitionTarget: Float? = null

    private var lastTouchX: Float = 0f
    private var lastTouchY: Float = 0f
    private var touchStartX: Float = 0f
    private var touchStartY: Float = 0f
    private var touchDownTimestampMs: Long = 0L
    private var longPressFiredForTouch: Boolean = false
    private var touchListenerAttachedView: View? = null
    private var hoverListenerAttachedView: View? = null

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
                if (AndroidViewHandle.isOnMainThread()) {
                    nativeWidget.androidView = runCatching { createDefaultAndroidView(ctx) }.getOrNull()
                } else {
                    AndroidViewHandle.runOnMainThread {
                        ensureOwnAndroidView(ctx)
                    }
                }
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

    protected fun ensureOwnAndroidView(context: Context? = AndroidViewHandle.activeContext): View? {
        if (nativeWidget.androidView == null && context != null && !nativeWidget.disposed) {
            if (AndroidViewHandle.isOnMainThread()) {
                if (nativeWidget.androidView == null && !nativeWidget.disposed) {
                    nativeWidget.androidView = runCatching { createDefaultAndroidView(context) }.getOrNull()
                    if (nativeWidget.androidView != null) {
                        syncNativeBootstrapAppearance()
                        syncAndroidView()
                        nativeWidget.parentHandle?.attachChildHandleOnMainThread(nativeWidget)
                    }
                }
            } else {
                AndroidViewHandle.runOnMainThread {
                    if (nativeWidget.androidView == null && !nativeWidget.disposed) {
                        nativeWidget.androidView = runCatching { createDefaultAndroidView(context) }.getOrNull()
                        if (nativeWidget.androidView != null) {
                            syncNativeBootstrapAppearance()
                            syncAndroidView()
                            nativeWidget.parentHandle?.attachChildHandleOnMainThread(nativeWidget)
                        }
                    }
                }
            }
        }
        return nativeWidget.androidView
    }

    internal open fun ensureAndroidView(context: Context? = AndroidViewHandle.activeContext): View? =
        ensureOwnAndroidView(context)

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
        val allowButtonTint = allowsBootstrapButtonTinting()
        val allowTypography = allowsSystemTypographyStyles()
        val allowBootstrapColor = allowButtonTint || allowsBootstrapColorForStyledSpec()
        val tintColor = when {
            allowAesthetic && customStyleBackgroundColor != null -> customStyleBackgroundColor
            allowBootstrapColor -> BootstrapColorPalette.resolveTintOrNull(effectiveType, isDark, activeTheme)
            else -> null
        }
        val fgColor = (if (allowTypography) customStyleTextColor else null)
            ?: (if (allowBootstrapColor) {
                styleSnapshot.textColorType?.takeIf { it != BootstrapType.NONE }?.let {
                    BootstrapColorPalette.resolveColor(it, isDark, activeTheme)
                }
            } else {
                null
            })
            ?: (if (allowBootstrapColor) {
                styleSnapshot.type?.takeIf { it != BootstrapType.NONE && !styleSnapshot.outline }?.let {
                    BootstrapColorPalette.resolveForegroundOn(it, isDark, activeTheme)
                }
            } else {
                null
            })

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
                lastAppliedBootstrapWidth = w
            }
            BootstrapWidth.W_25, BootstrapWidth.W_50, BootstrapWidth.W_75 -> {
                nativeWidget.attributes["layoutWidth"] = "${((w.fraction ?: 1f) * 100f).roundToInt()}%"
                lastAppliedBootstrapWidth = w
            }
            BootstrapWidth.W_AUTO, BootstrapWidth.MW_100 -> {
                nativeWidget.attributes["layoutWidth"] = "WRAP_CONTENT"
                lastAppliedBootstrapWidth = w
            }
            null -> {
                if (lastAppliedBootstrapWidth != null) {
                    nativeWidget.attributes.remove("layoutWidth")
                    lastAppliedBootstrapWidth = null
                }
            }
        }

        when (val h = styleSnapshot.height) {
            BootstrapHeight.H_100, BootstrapHeight.VH_100, BootstrapHeight.MIN_VH_100 -> {
                nativeWidget.attributes["layoutHeight"] = "MATCH_PARENT"
                nativeWidget.fillMaxHeight = true
                lastAppliedBootstrapHeight = h
            }
            BootstrapHeight.H_25, BootstrapHeight.H_50, BootstrapHeight.H_75 -> {
                nativeWidget.attributes["layoutHeight"] = "${((h.fraction ?: 1f) * 100f).roundToInt()}%"
                lastAppliedBootstrapHeight = h
            }
            BootstrapHeight.H_AUTO, BootstrapHeight.MH_100 -> {
                nativeWidget.attributes["layoutHeight"] = "WRAP_CONTENT"
                lastAppliedBootstrapHeight = h
            }
            null -> {
                if (lastAppliedBootstrapHeight != null) {
                    nativeWidget.attributes.remove("layoutHeight")
                    lastAppliedBootstrapHeight = null
                }
            }
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

    protected fun isAlertComponent(): Boolean =
        componentClassPrefix == "alert" ||
            componentClassPrefix.startsWith("alert ") ||
            nativeWidget.classes.contains("alert")

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
            val explicitWeight = baseComponentStyle?.getFlexGrow() ?: baseComputedStyle?.flexGrow
            nativeWidget.layoutWeight = weightAttr?.toFloatOrNull()
                ?: explicitWeight
                ?: if (nativeWidget.classes.contains("flex-grow-1") || styleSnapshot.flexGrow == BootstrapFlexGrow.GROW_1) 1f else 0f

            val wCss = baseComponentStyle?.getWidth() ?: CssLength.parse(baseComputedStyle?.width)
            val hCss = baseComponentStyle?.getHeight() ?: CssLength.parse(baseComputedStyle?.height)

            nativeWidget.fillMaxWidth =
                nativeWidget.attributes["layoutWidth"] == "MATCH_PARENT" ||
                    nativeWidget.classes.contains("w-100") ||
                    styleSnapshot.width == BootstrapWidth.W_100 ||
                    styleSnapshot.width == BootstrapWidth.VW_100 ||
                    styleSnapshot.width == BootstrapWidth.MIN_VW_100 ||
                    (wCss?.unit == CssUnit.PERCENT && wCss.value >= 100f)

            nativeWidget.fillMaxHeight =
                nativeWidget.attributes["layoutHeight"] == "MATCH_PARENT" ||
                    nativeWidget.classes.contains("h-100") ||
                    styleSnapshot.height == BootstrapHeight.H_100 ||
                    styleSnapshot.height == BootstrapHeight.VH_100 ||
                    styleSnapshot.height == BootstrapHeight.MIN_VH_100 ||
                    (hCss?.unit == CssUnit.PERCENT && hCss.value >= 100f)

            nativeWidget.widthPercent = when {
                wCss?.unit == CssUnit.PERCENT && wCss.value > 0f && wCss.value < 100f -> wCss.value
                styleSnapshot.width?.fraction != null && (styleSnapshot.width?.fraction ?: 1f) < 1f ->
                    styleSnapshot.width!!.fraction!! * 100f
                else -> nativeWidget.attributes["layoutWidth"]?.takeIf { it.endsWith("%") }
                    ?.removeSuffix("%")?.toFloatOrNull()?.takeIf { it > 0f && it < 100f }
            }
            nativeWidget.heightPercent = when {
                hCss?.unit == CssUnit.PERCENT && hCss.value > 0f && hCss.value < 100f -> hCss.value
                styleSnapshot.height?.fraction != null && (styleSnapshot.height?.fraction ?: 1f) < 1f ->
                    styleSnapshot.height!!.fraction!! * 100f
                else -> nativeWidget.attributes["layoutHeight"]?.takeIf { it.endsWith("%") }
                    ?.removeSuffix("%")?.toFloatOrNull()?.takeIf { it > 0f && it < 100f }
            }

            val attrAspect = CssAspectRatio.parse(nativeWidget.attributes["aspectRatio"])?.numericRatio
            val bsAspect = nativeWidget.attributes["data-bs-aspect-ratio"]?.trim()?.let { raw ->
                when {
                    raw.endsWith("%") -> raw.removeSuffix("%").toFloatOrNull()?.takeIf { it > 0f }?.let { 100f / it }
                    raw.contains("/") -> CssAspectRatio.parse(raw)?.numericRatio
                    raw.contains(":") -> {
                        val parts = raw.split(":")
                        if (parts.size == 2) {
                            val w = parts[0].trim().toFloatOrNull()
                            val h = parts[1].trim().toFloatOrNull()
                            if (w != null && h != null && h > 0f) w / h else null
                        } else null
                    }
                    else -> raw.toFloatOrNull()?.takeIf { it > 0f }
                }
            }
            val classAspect = when {
                nativeWidget.classes.contains("ratio-16x9") -> 16f / 9f
                nativeWidget.classes.contains("ratio-4x3") -> 4f / 3f
                nativeWidget.classes.contains("ratio-1x1") -> 1f
                nativeWidget.classes.contains("ratio-21x9") -> 21f / 9f
                else -> null
            }
            val tagAttr = nativeWidget.attributes["data-genericui-tag"]
            val isRatioCls = componentClassPrefix == "ratio" ||
                nativeWidget.classes.contains("ratio") ||
                tagAttr == "genericui:aspectratiocontainer"
            nativeWidget.isRatioContainer = isRatioCls
            nativeWidget.aspectRatio = baseComponentStyle?.getAspectRatio()?.numericRatio
                ?: baseComputedStyle?.aspectRatio
                ?: styleSnapshot.aspectRatio?.numericRatio
                ?: attrAspect
                ?: bsAspect
                ?: classAspect
                ?: if (isRatioCls) 1f else 0f

            nativeWidget.isLayeredContainer = isRatioCls ||
                componentClassPrefix == "gui-layered-container" ||
                componentClassPrefix == "gui-overlay-host" ||
                componentClassPrefix == "gui-overlay-host-root" ||
                componentClassPrefix == "gui-overlay" ||
                nativeWidget.classes.contains("gui-layered-container") ||
                nativeWidget.classes.contains("gui-overlay-host") ||
                nativeWidget.classes.contains("gui-overlay-host-root") ||
                nativeWidget.classes.contains("gui-overlay") ||
                tagAttr == "genericui:layered-container" ||
                tagAttr == "genericui:overlay-host" ||
                tagAttr == "genericui:overlay-host-root" ||
                tagAttr == "genericui:overlay"

            val sizingAttr = nativeWidget.attributes["layerSizingMode"]?.trim()?.uppercase()
            val hasLayerFillClass = nativeWidget.classes.contains("gui-layer-fill")
            val hasLayerFloatClass = nativeWidget.classes.contains("gui-layer-float")
            nativeWidget.isLayerItem = nativeWidget.classes.contains("gui-layer-item") || sizingAttr != null
            nativeWidget.layerFloat = hasLayerFloatClass || sizingAttr == "FLOAT"
            nativeWidget.layerFill = !nativeWidget.layerFloat && (hasLayerFillClass || sizingAttr == "FILL")

            val alignAttr = nativeWidget.attributes["layerAlignment"]?.trim()?.uppercase()
            nativeWidget.layerAlignment = alignAttr?.let { raw ->
                LayerAlignment.entries.firstOrNull { it.name == raw }
            } ?: LayerAlignment.TOP_START
            nativeWidget.layerOffsetXPx = nativeWidget.attributes["layerOffsetXPx"]?.toIntOrNull() ?: 0
            nativeWidget.layerOffsetYPx = nativeWidget.attributes["layerOffsetYPx"]?.toIntOrNull() ?: 0
            nativeWidget.layerLeftPercent = nativeWidget.attributes["layerLeftPercent"]?.takeIf { it.isNotBlank() }?.toFloatOrNull()
            nativeWidget.layerTopPercent = nativeWidget.attributes["layerTopPercent"]?.takeIf { it.isNotBlank() }?.toFloatOrNull()
            nativeWidget.layerRightPx = nativeWidget.attributes["layerRightPx"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
            nativeWidget.layerBottomPx = nativeWidget.attributes["layerBottomPx"]?.takeIf { it.isNotBlank() }?.toIntOrNull()
            nativeWidget.layerZIndex = rawLayoutComponentStyle?.getZIndex()
                ?: baseComponentStyle?.getZIndex()
                ?: rawLayoutComputedStyle?.zIndex
                ?: baseComputedStyle?.zIndex
                ?: nativeWidget.attributes["layerZIndex"]?.toIntOrNull()
                ?: nativeWidget.attributes["zIndex"]?.toIntOrNull()
                ?: 0
            val hasLayerPassThroughFlag =
                nativeWidget.attributes["layerPassThrough"]?.equals("true", ignoreCase = true) == true ||
                    nativeWidget.classes.contains("gui-layer-pass-through")
            val hasExplicitCssPointerNone =
                nativeWidget.attributes["pointer-events"]?.equals("none", ignoreCase = true) == true ||
                    styleSnapshot.pointerEvents == BootstrapPointerEvents.NONE ||
                    rawLayoutComponentStyle?.getPointerEvents() == CssPointerEvents.NONE ||
                    rawLayoutComputedStyle?.pointerEventsEnabled == false ||
                    baseComponentStyle?.getPointerEvents() == CssPointerEvents.NONE ||
                    baseComputedStyle?.pointerEventsEnabled == false
            nativeWidget.cssPointerEventsNone = !hasLayerPassThroughFlag && hasExplicitCssPointerNone
            nativeWidget.layerPassThrough = hasLayerPassThroughFlag || hasExplicitCssPointerNone
            nativeWidget.cssPosition = resolveEffectiveCssPosition()
            nativeWidget.cssTop = rawLayoutComponentStyle?.getTop()
                ?: rawLayoutComputedStyle?.top?.let { CssLength.parse(it) }
            nativeWidget.cssRight = rawLayoutComponentStyle?.getRight()
                ?: rawLayoutComputedStyle?.right?.let { CssLength.parse(it) }
            nativeWidget.cssBottom = rawLayoutComponentStyle?.getBottom()
                ?: rawLayoutComputedStyle?.bottom?.let { CssLength.parse(it) }
            nativeWidget.cssLeft = rawLayoutComponentStyle?.getLeft()
                ?: rawLayoutComputedStyle?.left?.let { CssLength.parse(it) }
            nativeWidget.cssTransform2D = resolveEffectiveTransform2D()
            nativeWidget.overflowBehavior = styleSnapshot.overflow
                ?: rawLayoutComponentStyle?.getOverflowX()
                ?: rawLayoutComponentStyle?.getOverflowY()
                ?: rawLayoutComputedStyle?.overflowX
                ?: rawLayoutComputedStyle?.overflowY

            val resolvedFlexDir = baseComponentStyle?.getFlexDirection()
                ?: styleSnapshot.flexDirection
                ?: baseComputedStyle?.flexDirection
                ?: when {
                    nativeWidget.classes.contains("flex-row") || componentClassPrefix == "hstack" -> FlexDirection.ROW
                    else -> FlexDirection.COLUMN
                }
            nativeWidget.flexDirection = resolvedFlexDir
            nativeWidget.flexWrap = baseComponentStyle?.getFlexWrap()
                ?: styleSnapshot.flexWrap
                ?: baseComputedStyle?.flexWrap
                ?: if (nativeWidget.classes.contains("flex-wrap")) FlexWrap.WRAP else FlexWrap.NOWRAP
            nativeWidget.alignItems = baseComponentStyle?.getAlignItems()
                ?: styleSnapshot.alignItems
                ?: baseComputedStyle?.alignItems
                ?: AlignItems.STRETCH
            nativeWidget.justifyContent = baseComponentStyle?.getJustifyContent()
                ?: styleSnapshot.justifyContent
                ?: baseComputedStyle?.justifyContent
                ?: JustifyContent.START

            val gapScaleDp = spacingScaleToPx(styleSnapshot.gap)
            val gapDp = explicitGapDp
                ?: baseComponentStyle?.getGap()?.resolvePx()?.roundToInt()
                ?: baseComputedStyle?.gap?.resolvePx()?.roundToInt()
                ?: gapScaleDp
                ?: 0
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
                    if (activeOpacityTransitionTarget == null) {
                        view.alpha = nativeWidget.alpha
                    }

                    (view.layoutParams as? AndroidFlexContainerView.LayoutParams)?.let { lp ->
                        lp.layerPassThrough = nativeWidget.layerPassThrough
                        lp.cssPointerEventsNone = nativeWidget.cssPointerEventsNone
                    }
                    val selfFlex = (nativeWidget.childHostViewGroup ?: view) as? AndroidFlexContainerView
                    if (selfFlex != null) {
                        selfFlex.aspectRatio = nativeWidget.aspectRatio
                        selfFlex.isLayered = nativeWidget.isLayeredContainer
                        selfFlex.isRatioContainer = nativeWidget.isRatioContainer
                        selfFlex.pointerEventsNone = nativeWidget.layerPassThrough
                        selfFlex.cssPointerEventsNone = nativeWidget.cssPointerEventsNone
                        selfFlex.cssPosition = nativeWidget.cssPosition
                        selfFlex.isWindowRootHost = nativeWidget.isWindowRootHost
                        val ov = nativeWidget.overflowBehavior
                        val clips = ov == OverflowBehavior.HIDDEN || ov == OverflowBehavior.SCROLL || ov == OverflowBehavior.AUTO
                        selfFlex.clipChildren = clips
                        selfFlex.clipToPadding = clips
                    }

                    val effPad = resolveDefaultPaddingPx()
                    val padTarget = nativeWidget.childHostViewGroup ?: view
                    padTarget.setPadding(effPad.left, effPad.top, effPad.right, effPad.bottom)

                    if (shouldApplyBaseBackgroundDrawable()) {
                        applyBaseSurfaceDrawable(view)
                    }

                    if (nativeWidget.layerPassThrough) {
                        view.setOnClickListener(null)
                        view.isClickable = false
                        view.isLongClickable = false
                        view.isFocusable = false
                        view.isFocusableInTouchMode = false
                        if (android.os.Build.VERSION.SDK_INT >= 23) {
                            view.isContextClickable = false
                        }
                    } else if (onClickListener != null) {
                        view.isClickable = true
                        view.setOnClickListener { dispatchClick() }
                    } else if (this !is AndroidButtonImpl &&
                        this !is AndroidCheckboxImpl &&
                        this !is AndroidSelectImpl &&
                        this !is AndroidRangeSliderImpl &&
                        this !is AndroidTextInputImpl &&
                        this !is AndroidDropdownImpl
                    ) {
                        view.setOnClickListener(null)
                        view.isClickable = false
                    }
                    if (!focusableState) {
                        applyFocusableStateToView(view, false)
                    }

                    if (onPointerDownListener != null ||
                        onPointerMoveListener != null ||
                        onPointerUpListener != null ||
                        onContextMenuListener != null
                    ) {
                        ensureTouchListenerAttached(view)
                    }
                    if (onPointerEnterListener != null || onPointerLeaveListener != null) {
                        ensureHoverListenerAttached(view)
                    }

                    if (nativeWidget.isLayerItem || nativeWidget.layerZIndex != 0 || view.translationZ != 0f) {
                        view.translationZ = nativeWidget.layerZIndex.toFloat()
                    }
                    if (activePropertyCancelHooks["transform"] == null) {
                        val tf = nativeWidget.cssTransform2D
                        val refW = view.width.takeIf { it > 0 } ?: AndroidViewHandle.dpToPx(nativeWidget.bounds.width)
                        val refH = view.height.takeIf { it > 0 } ?: AndroidViewHandle.dpToPx(nativeWidget.bounds.height)
                        val density = view.resources?.displayMetrics?.density?.takeIf { it > 0f } ?: AndroidViewHandle.density()
                        val (rawDx, rawDy) = tf.resolveTranslationPx(refW, refH)
                        view.translationX = if (tf.translateXIsPercent) rawDx else rawDx * density
                        view.translationY = if (tf.translateYIsPercent) rawDy else rawDy * density
                        view.scaleX = tf.scaleX
                        view.scaleY = tf.scaleY
                        view.rotation = tf.rotateDeg
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
        if (!allowsSystemAestheticStyles()) return
        val isDark = AndroidViewHandle.isDarkTheme()
        val isCard = isCardComponent()
        val isToast = isToastComponent()
        val isAlert = isAlertComponent()
        val allowBsColor = allowsBootstrapNonControlColor() || isInCustomStyleEngineSubtree()

        val bgType = if (allowBsColor) (styleSnapshot.backgroundType ?: if (isToast || isAlert) styleSnapshot.type else null) else null
        val explicitBg = customStyleBackgroundColor
            ?: bgType?.takeIf { it != BootstrapType.NONE }?.let { BootstrapColorPalette.resolveColor(it, isDark) }

        val borderTypeColor = if (allowBsColor) {
            styleSnapshot.borderType?.takeIf { it != BootstrapType.NONE }?.let {
                BootstrapColorPalette.resolveColor(it, isDark)
            }
        } else {
            null
        }
        val explicitBorderColor = customStyleBorderColor ?: borderTypeColor

        val radiusDp = when {
            customStyleBorderRadiusPx != null -> customStyleBorderRadiusPx!!
            styleSnapshot.borderRadius != null -> styleSnapshot.borderRadius!!.radiusPx.toFloat()
            isCard || isToast -> 10f
            else -> 0f
        }

        val borderWidthDp = when {
            customStyleBorderWidthPx != null -> customStyleBorderWidthPx!!
            styleSnapshot.borderWidth != null -> styleSnapshot.borderWidth!!.widthPx.toFloat()
            isCard || isToast -> 1f
            else -> 0f
        }

        if (explicitBg == null && !isCard && !isToast && borderWidthDp <= 0f && radiusDp <= 0f) {
            return
        }

        val fillArgb: Int = when {
            explicitBg != null -> explicitBg.toArgbInt()
            isCard -> if (isDark) 0xFF1E222A.toInt() else 0xFFFFFFFF.toInt()
            else -> 0x00000000
        }

        val strokeArgb: Int = when {
            explicitBorderColor != null -> explicitBorderColor.toArgbInt()
            isCard -> if (isDark) 0xFF2E3440.toInt() else 0xFFE0E4EC.toInt()
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
        rawLayoutComponentStyle = style
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
            if (wLen?.unit == CssUnit.PERCENT && wLen.value >= 100f) {
                nativeWidget.fillMaxWidth = true
            } else if (wLen?.isAuto == true) {
                nativeWidget.hasExplicitPreferredWidth = preferredSizeValue?.let { it.width > 0 } == true
            }
            if (hLen?.unit == CssUnit.PERCENT && hLen.value >= 100f) {
                nativeWidget.fillMaxHeight = true
            } else if (hLen?.isAuto == true) {
                nativeWidget.hasExplicitPreferredHeight = preferredSizeValue?.let { it.height > 0 } == true
                if (styleSnapshot.height == null) {
                    nativeWidget.fillMaxHeight = false
                }
            }
            val explicitWPx = wLen?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
            val explicitHPx = hLen?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
            if (explicitWPx != null && explicitWPx > 0) {
                nativeWidget.hasExplicitPreferredWidth = true
            }
            if (explicitHPx != null && explicitHPx > 0) {
                nativeWidget.hasExplicitPreferredHeight = true
            }
            val nextW = explicitWPx ?: preferredSizeValue?.width
            val nextH = explicitHPx ?: preferredSizeValue?.height
            if (nextW != null || nextH != null) {
                nativeWidget.bounds = Rect(
                    nativeWidget.bounds.x,
                    nativeWidget.bounds.y,
                    nextW ?: nativeWidget.bounds.width,
                    nextH ?: nativeWidget.bounds.height
                )
            }
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
        filtered.getFlexShrink()?.let { nativeWidget.attributes["flexShrink"] = it.toString() }
            ?: nativeWidget.attributes.remove("flexShrink")
        filtered.getFlexBasis()?.let { nativeWidget.attributes["flexBasis"] = it.toCssString() }
            ?: nativeWidget.attributes.remove("flexBasis")
        filtered.getAspectRatio()?.let { nativeWidget.attributes["aspectRatio"] = it.toCssString() }
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

    protected fun applyFocusableStateToView(view: View, focusable: Boolean) {
        if (!focusable) {
            if (!hasSavedFocusableDefaults) {
                hasSavedFocusableDefaults = true
                savedFocusable = view.isFocusable
                savedFocusableInTouchMode = view.isFocusableInTouchMode
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    savedApi26Focusable = view.focusable
                }
                if (view is ViewGroup) {
                    savedDescendantFocusability = view.descendantFocusability
                }
            }
            view.isFocusable = false
            view.isFocusableInTouchMode = false
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                view.focusable = View.NOT_FOCUSABLE
            }
            if (view is ViewGroup) {
                view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            }
            if (view.hasFocus() || view.findFocus() != null) {
                view.clearFocus()
            }
        } else if (hasSavedFocusableDefaults) {
            view.isFocusable = savedFocusable
            view.isFocusableInTouchMode = savedFocusableInTouchMode
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                view.focusable = savedApi26Focusable
            }
            if (view is ViewGroup) {
                view.descendantFocusability = savedDescendantFocusability
            }
            hasSavedFocusableDefaults = false
        }
    }

    override fun setFocusable(focusable: Boolean) {
        focusableState = focusable
        if (!focusable) {
            setFocusedState(false)
        }
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val view = nativeWidget.androidView ?: return@runCatching
                applyFocusableStateToView(view, focusable)
            }
        }
    }

    override fun isFocusable(): Boolean = focusableState

    override fun setFocusedState(focused: Boolean) {
        focusedState = if (!isEnabled() || !focusableState) false else focused
    }

    override fun isFocusedState(): Boolean = if (!isEnabled() || !focusableState) false else focusedState

    override fun setOpacity(opacity: Float) {
        val clamped = opacity.coerceIn(0f, 1f)
        nativeWidget.alpha = clamped
        val pendingTarget = activeOpacityTransitionTarget
        if (pendingTarget != null) {
            if (abs(clamped - pendingTarget) < 1e-4f) {
                return
            }
            cancelNativePropertyTransition("opacity")
        }
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
        rawLayoutComputedStyle = style
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

        filtered.flexDirection?.let { styleSnapshot = styleSnapshot.copy(flexDirection = it) }
        filtered.flexWrap?.let { styleSnapshot = styleSnapshot.copy(flexWrap = it) }
        filtered.alignItems?.let { styleSnapshot = styleSnapshot.copy(alignItems = it) }
        filtered.justifyContent?.let { styleSnapshot = styleSnapshot.copy(justifyContent = it) }
        filtered.gap?.resolvePx()?.roundToInt()?.let { explicitGapDp = it }

        val wLen = CssLength.parse(filtered.width)
        val hLen = CssLength.parse(filtered.height)
        if (wLen != null || hLen != null) {
            if (wLen?.unit == CssUnit.PERCENT && wLen.value >= 100f) {
                nativeWidget.fillMaxWidth = true
            } else if (wLen?.isAuto == true) {
                nativeWidget.hasExplicitPreferredWidth = preferredSizeValue?.let { it.width > 0 } == true
            }
            if (hLen?.unit == CssUnit.PERCENT && hLen.value >= 100f) {
                nativeWidget.fillMaxHeight = true
            } else if (hLen?.isAuto == true) {
                nativeWidget.hasExplicitPreferredHeight = preferredSizeValue?.let { it.height > 0 } == true
                if (styleSnapshot.height == null && baseComponentStyle?.getHeight() == null) {
                    nativeWidget.fillMaxHeight = false
                }
            }
            val explicitWPx = wLen?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
            val explicitHPx = hLen?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }?.resolvePx()?.roundToInt()
            if (explicitWPx != null && explicitWPx > 0) {
                nativeWidget.hasExplicitPreferredWidth = true
            }
            if (explicitHPx != null && explicitHPx > 0) {
                nativeWidget.hasExplicitPreferredHeight = true
            }
            val nextW = explicitWPx ?: preferredSizeValue?.width
            val nextH = explicitHPx ?: preferredSizeValue?.height
            if (nextW != null || nextH != null) {
                nativeWidget.bounds = Rect(
                    nativeWidget.bounds.x,
                    nativeWidget.bounds.y,
                    nextW ?: nativeWidget.bounds.width,
                    nextH ?: nativeWidget.bounds.height
                )
            }
        }

        filtered.padding?.let { setPadding(it.toEdgeInsets()) }
        filtered.margin?.let { setMargin(it.toEdgeInsets()) }
        if (filtered.display.equals("none", ignoreCase = true)) {
            setVisible(false)
        }
        filtered.flexGrow?.let { nativeWidget.attributes["layoutWeight"] = it.toString() }
        filtered.flexShrink?.let { nativeWidget.attributes["flexShrink"] = it.toString() }
        filtered.flexBasis?.let { nativeWidget.attributes["flexBasis"] = it.toCssString() }
        filtered.aspectRatio?.let { nativeWidget.attributes["aspectRatio"] = it.toString() }

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
        if (nativeWidget.disposed) return false
        val view = nativeWidget.androidView ?: return false
        val rawProp = property.trim().lowercase()
        val canonicalKey = when (rawProp) {
            "opacity", "alpha" -> "opacity"
            "transform", "scale" -> "transform"
            else -> return false
        }

        val isScaleOnly = rawProp == "scale"
        val refW = view.width.takeIf { it > 0 } ?: nativeWidget.bounds.width
        val refH = view.height.takeIf { it > 0 } ?: nativeWidget.bounds.height

        var targetAlpha: Float? = null
        var fromAlpha: Float? = null
        var targetTransform: CssTransform2D? = null
        var fromTransform: CssTransform2D? = null

        when (canonicalKey) {
            "opacity" -> {
                targetAlpha = toValue.trim().toFloatOrNull()?.coerceIn(0f, 1f) ?: return false
                fromAlpha = fromValue?.trim()?.toFloatOrNull()?.coerceIn(0f, 1f)
            }
            "transform" -> {
                targetTransform = parseAndroidTransformSpec(toValue, isScaleOnly) ?: return false
                fromTransform = fromValue?.let { parseAndroidTransformSpec(it, isScaleOnly) }
            }
        }

        nativeWidget.attributes["lastTransitionProperty"] = property
        nativeWidget.attributes["viewPropertyAnimator:$property"] = "$fromValue->$toValue@${spec.durationMs}ms"

        val finished = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        val dispatchCompletionOnce: () -> Unit = {
            if (finished.compareAndSet(false, true)) {
                AndroidViewHandle.dispatchOnLogicThread(onComplete)
            }
        }

        var animatorRef: Animator? = null
        lateinit var cancelHook: () -> Unit
        cancelHook = {
            if (cancelled.compareAndSet(false, true)) {
                synchronized(transitionLock) {
                    if (activePropertyCancelHooks[canonicalKey] === cancelHook) {
                        activePropertyCancelHooks.remove(canonicalKey)
                        if (canonicalKey == "opacity") {
                            activeOpacityTransitionTarget = null
                        }
                    }
                }
                AndroidViewHandle.runOnMainThread {
                    runCatching { animatorRef?.cancel() }
                    dispatchCompletionOnce()
                }
            }
        }

        val previousCancel: (() -> Unit)?
        val hadActiveForProp: Boolean
        synchronized(transitionLock) {
            previousCancel = activePropertyCancelHooks.remove(canonicalKey)
            hadActiveForProp = previousCancel != null
            activePropertyCancelHooks[canonicalKey] = cancelHook
            if (canonicalKey == "opacity") {
                activeOpacityTransitionTarget = targetAlpha
                targetAlpha?.let { nativeWidget.alpha = it }
            }
        }
        previousCancel?.invoke()

        val durationMs = spec.durationMs.toLong().coerceAtLeast(0L)
        val delayMs = spec.delayMs.toLong().coerceAtLeast(0L)

        AndroidViewHandle.runOnMainThread {
            if (cancelled.get() || nativeWidget.disposed) {
                synchronized(transitionLock) {
                    if (activePropertyCancelHooks[canonicalKey] === cancelHook) {
                        activePropertyCancelHooks.remove(canonicalKey)
                        if (canonicalKey == "opacity") activeOpacityTransitionTarget = null
                    }
                }
                dispatchCompletionOnce()
                return@runOnMainThread
            }

            val applyTarget: () -> Unit = {
                when (canonicalKey) {
                    "opacity" -> targetAlpha?.let { view.alpha = it }
                    "transform" -> targetTransform?.let { tf ->
                        val w = view.width.takeIf { it > 0 } ?: refW
                        val h = view.height.takeIf { it > 0 } ?: refH
                        val (dx, dy) = tf.resolveTranslationPx(w, h)
                        view.translationX = dx
                        view.translationY = dy
                        view.scaleX = tf.scaleX
                        view.scaleY = tf.scaleY
                        view.rotation = tf.rotateDeg
                    }
                }
            }

            if (durationMs == 0L && delayMs == 0L) {
                runCatching { applyTarget() }
                synchronized(transitionLock) {
                    if (activePropertyCancelHooks[canonicalKey] === cancelHook) {
                        activePropertyCancelHooks.remove(canonicalKey)
                        if (canonicalKey == "opacity") activeOpacityTransitionTarget = null
                    }
                }
                dispatchCompletionOnce()
                return@runOnMainThread
            }

            runCatching {
                val createdAnimator: ObjectAnimator = when (canonicalKey) {
                    "opacity" -> {
                        val startA = if (!hadActiveForProp && fromAlpha != null) fromAlpha else view.alpha
                        view.alpha = startA
                        ObjectAnimator.ofFloat(view, View.ALPHA, startA, targetAlpha!!)
                    }
                    else -> {
                        val w = view.width.takeIf { it > 0 } ?: refW
                        val h = view.height.takeIf { it > 0 } ?: refH
                        val startDx: Float
                        val startDy: Float
                        val startSx: Float
                        val startSy: Float
                        val startRot: Float
                        if (!hadActiveForProp && fromTransform != null) {
                            val (fx, fy) = fromTransform.resolveTranslationPx(w, h)
                            startDx = fx
                            startDy = fy
                            startSx = fromTransform.scaleX
                            startSy = fromTransform.scaleY
                            startRot = fromTransform.rotateDeg
                        } else {
                            startDx = view.translationX
                            startDy = view.translationY
                            startSx = view.scaleX
                            startSy = view.scaleY
                            startRot = view.rotation
                        }
                        view.translationX = startDx
                        view.translationY = startDy
                        view.scaleX = startSx
                        view.scaleY = startSy
                        view.rotation = startRot
                        val endTf = targetTransform!!
                        val (endDx, endDy) = endTf.resolveTranslationPx(w, h)
                        ObjectAnimator.ofPropertyValuesHolder(
                            view,
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_X, startDx, endDx),
                            PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, startDy, endDy),
                            PropertyValuesHolder.ofFloat(View.SCALE_X, startSx, endTf.scaleX),
                            PropertyValuesHolder.ofFloat(View.SCALE_Y, startSy, endTf.scaleY),
                            PropertyValuesHolder.ofFloat(View.ROTATION, startRot, endTf.rotateDeg)
                        )
                    }
                }
                createdAnimator.duration = durationMs
                createdAnimator.startDelay = delayMs
                createdAnimator.interpolator = if (spec.timingFunction.isLinear) {
                    LinearInterpolator()
                } else {
                    PathInterpolator(
                        spec.timingFunction.x1.coerceIn(0f, 1f),
                        spec.timingFunction.y1,
                        spec.timingFunction.x2.coerceIn(0f, 1f),
                        spec.timingFunction.y2
                    )
                }
                createdAnimator.addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        synchronized(transitionLock) {
                            if (activePropertyCancelHooks[canonicalKey] === cancelHook) {
                                activePropertyCancelHooks.remove(canonicalKey)
                                if (canonicalKey == "opacity") {
                                    activeOpacityTransitionTarget = null
                                }
                            }
                        }
                        dispatchCompletionOnce()
                    }
                })
                animatorRef = createdAnimator
                createdAnimator.start()
            }.onFailure {
                runCatching { applyTarget() }
                synchronized(transitionLock) {
                    if (activePropertyCancelHooks[canonicalKey] === cancelHook) {
                        activePropertyCancelHooks.remove(canonicalKey)
                        if (canonicalKey == "opacity") activeOpacityTransitionTarget = null
                    }
                }
                dispatchCompletionOnce()
            }
        }
        return true
    }

    private fun parseAndroidTransformSpec(raw: String, isScaleOnly: Boolean): CssTransform2D? {
        val trimmed = raw.trim()
        if (isScaleOnly) {
            val s = trimmed.toFloatOrNull() ?: return null
            return CssTransform2D(scaleX = s, scaleY = s)
        }
        val cleaned = trimmed.replace("!important", "", ignoreCase = true).trim()
        if (cleaned.isEmpty() || cleaned.equals("none", ignoreCase = true)) {
            return CssTransform2D.IDENTITY
        }
        val fnRegex = Regex("""([a-zA-Z0-9]+)\(([^)]*)\)""")
        val matches = fnRegex.findAll(cleaned).toList()
        if (matches.isEmpty()) return null
        val supported = setOf(
            "translate", "translate3d", "translatex", "translatey",
            "scale", "scale3d", "scalex", "scaley",
            "rotate", "rotatez"
        )
        if (matches.any { it.groupValues[1].lowercase() !in supported }) return null
        if (cleaned.replace(fnRegex, "").trim().isNotEmpty()) return null
        return CssTransform2D.parse(cleaned)
    }

    private fun cancelNativePropertyTransition(canonicalKey: String) {
        val hook = synchronized(transitionLock) {
            if (canonicalKey == "opacity") {
                activeOpacityTransitionTarget = null
            }
            activePropertyCancelHooks[canonicalKey]
        }
        hook?.invoke()
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
                val view = nativeWidget.androidView ?: return@runCatching
                if (nativeWidget.layerPassThrough) {
                    view.setOnClickListener(null)
                    view.isClickable = false
                    view.isLongClickable = false
                } else if (listener != null) {
                    view.isClickable = true
                    view.setOnClickListener { dispatchClick() }
                } else {
                    view.setOnClickListener(null)
                    if (this !is AndroidButtonImpl &&
                        this !is AndroidCheckboxImpl &&
                        this !is AndroidSelectImpl &&
                        this !is AndroidRangeSliderImpl &&
                        this !is AndroidTextInputImpl &&
                        this !is AndroidDropdownImpl
                    ) {
                        view.isClickable = false
                    }
                }
                if (!focusableState) {
                    applyFocusableStateToView(view, false)
                }
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
        if (hoverListenerAttachedView === view) return
        hoverListenerAttachedView = view
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
        if (touchListenerAttachedView === view) return
        touchListenerAttachedView = view
        runCatching {
            view.setOnTouchListener { _, event ->
                if (nativeWidget.layerPassThrough) return@setOnTouchListener false
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
        val hooks = synchronized(transitionLock) {
            activeOpacityTransitionTarget = null
            activePropertyCancelHooks.values.toList()
        }
        for (hook in hooks) {
            hook.invoke()
        }
        nativeWidget.dispose()
    }
}
