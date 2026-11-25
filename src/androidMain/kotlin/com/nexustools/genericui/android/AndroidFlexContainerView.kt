package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.nexustools.genericui.component.layout.LayerAlignment
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Custom [ViewGroup] implementing CSS Flexbox layout (`VStack`, `HStack`, `FlexBox`, `FlowRow`, `Card`, `Container`, `Navbar`)
 * for the `:mobile:android` backend.
 */
class AndroidFlexContainerView(context: Context) : ViewGroup(context) {

    var flexDirection: FlexDirection = FlexDirection.COLUMN
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var flexWrap: FlexWrap = FlexWrap.NOWRAP
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var alignItems: AlignItems = AlignItems.STRETCH
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var justifyContent: JustifyContent = JustifyContent.START
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var gapPx: Int = 0
        set(value) {
            val coerced = value.coerceAtLeast(0)
            if (field != coerced) {
                field = coerced
                requestLayout()
            }
        }

    var aspectRatio: Float = 0f
        set(value) {
            val sanitized = if (value > 0f && value.isFinite()) value else 0f
            if (field != sanitized) {
                field = sanitized
                requestLayout()
            }
        }

    var isLayered: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                isChildrenDrawingOrderEnabled = true
                requestLayout()
            }
        }

    var isRatioContainer: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                isChildrenDrawingOrderEnabled = true
                requestLayout()
            }
        }

    var cssPosition: CssPosition = CssPosition.STATIC
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    var isWindowRootHost: Boolean = false

    var pointerEventsNone: Boolean = false
    var cssPointerEventsNone: Boolean = false

    init {
        isChildrenDrawingOrderEnabled = true
    }

    internal val backdropController = AndroidHardwareBackdropBlurController(this)

    var backdropFilter: CssFilter?
        get() = backdropController.backdropFilter
        set(value) {
            backdropController.backdropFilter = value
        }

    var backdropBlurPx: Float
        get() = backdropController.backdropBlurPx
        set(value) {
            backdropController.backdropBlurPx = value
        }

    var backdropCornerRadiusPx: Float
        get() = backdropController.backdropCornerRadiusPx
        set(value) {
            backdropController.backdropCornerRadiusPx = value
        }

    var backdropOverlayDrawable: Drawable?
        get() = backdropController.backdropOverlayDrawable
        set(value) {
            backdropController.backdropOverlayDrawable = value
        }

    class LayoutParams : MarginLayoutParams {
        var weight: Float = 0f
        var fillWidth: Boolean = false
        var fillHeight: Boolean = false
        var alignSelf: AlignSelf? = null
        var widthPercent: Float? = null
        var heightPercent: Float? = null
        var isLayerItem: Boolean = false
        var layerFill: Boolean = false
        var layerFloat: Boolean = false
        var layerAlignment: LayerAlignment = LayerAlignment.TOP_START
        var layerOffsetXPx: Int = 0
        var layerOffsetYPx: Int = 0
        var layerLeftPercent: Float? = null
        var layerTopPercent: Float? = null
        var layerRightPx: Int? = null
        var layerBottomPx: Int? = null
        var layerZIndex: Int = 0
        var layerPassThrough: Boolean = false
        var cssPointerEventsNone: Boolean = false
        var cssPosition: CssPosition = CssPosition.STATIC
        var cssTop: CssLength? = null
        var cssRight: CssLength? = null
        var cssBottom: CssLength? = null
        var cssLeft: CssLength? = null
        var cssTransform2D: CssTransform2D = CssTransform2D.IDENTITY
        internal var lastAppliedTransform: CssTransform2D? = null
        internal var lastTransformWidth: Int = -1
        internal var lastTransformHeight: Int = -1

        internal fun isCssOutOfFlow(): Boolean =
            cssPosition == CssPosition.ABSOLUTE || cssPosition == CssPosition.FIXED

        constructor(width: Int, height: Int) : super(width, height)
        constructor(source: ViewGroup.LayoutParams?) : super(source ?: MarginLayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        constructor(source: MarginLayoutParams?) : super(source ?: MarginLayoutParams(WRAP_CONTENT, WRAP_CONTENT))
    }

    private data class WrappedLine(
        val children: MutableList<View> = mutableListOf(),
        var mainWidth: Int = 0,
        var crossHeight: Int = 0
    )

    private val wrappedLines = mutableListOf<WrappedLine>()

    override fun generateDefaultLayoutParams(): LayoutParams =
        LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: ViewGroup.LayoutParams?): LayoutParams =
        when (p) {
            is LayoutParams -> LayoutParams(p).also {
                it.weight = p.weight
                it.fillWidth = p.fillWidth
                it.fillHeight = p.fillHeight
                it.alignSelf = p.alignSelf
                it.widthPercent = p.widthPercent
                it.heightPercent = p.heightPercent
                it.isLayerItem = p.isLayerItem
                it.layerFill = p.layerFill
                it.layerFloat = p.layerFloat
                it.layerAlignment = p.layerAlignment
                it.layerOffsetXPx = p.layerOffsetXPx
                it.layerOffsetYPx = p.layerOffsetYPx
                it.layerLeftPercent = p.layerLeftPercent
                it.layerTopPercent = p.layerTopPercent
                it.layerRightPx = p.layerRightPx
                it.layerBottomPx = p.layerBottomPx
                it.layerZIndex = p.layerZIndex
                it.layerPassThrough = p.layerPassThrough
                it.cssPointerEventsNone = p.cssPointerEventsNone
                it.cssPosition = p.cssPosition
                it.cssTop = p.cssTop
                it.cssRight = p.cssRight
                it.cssBottom = p.cssBottom
                it.cssLeft = p.cssLeft
                it.cssTransform2D = p.cssTransform2D
            }
            is MarginLayoutParams -> LayoutParams(p)
            else -> LayoutParams(p)
        }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    private fun lpOf(child: View): LayoutParams =
        (child.layoutParams as? LayoutParams) ?: generateLayoutParams(child.layoutParams).also {
            child.layoutParams = it
        }

    private fun isFillLayerChild(lp: LayoutParams): Boolean =
        lp.layerFill || (!lp.layerFloat && (isRatioContainer || !lp.isLayerItem))

    override fun getChildDrawingOrder(childCount: Int, drawingPosition: Int): Int {
        if (childCount <= 1) {
            return super.getChildDrawingOrder(childCount, drawingPosition)
        }
        val ordered = (0 until childCount).sortedWith(
            compareBy<Int>(
                { getChildAt(it)?.let { child -> lpOf(child).layerZIndex } ?: 0 },
                { it }
            )
        )
        return ordered.getOrElse(drawingPosition) { drawingPosition }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        if (pointerEventsNone) return false
        return super.onInterceptTouchEvent(ev)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        backdropController.release()
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (pointerEventsNone) return false
        return super.onTouchEvent(event)
    }

    override fun dispatchDraw(canvas: Canvas) {
        backdropController.drawBackdropIfActive(canvas)
        backdropController.drawOverlayDrawable(canvas)
        super.dispatchDraw(canvas)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev == null || cssPointerEventsNone) return false
        if (ev.actionMasked == MotionEvent.ACTION_DOWN) {
            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                val lp = lpOf(child)
                if (child is AndroidFlexContainerView) {
                    child.pointerEventsNone = lp.layerPassThrough
                    child.cssPointerEventsNone = lp.cssPointerEventsNone
                } else if (lp.layerPassThrough && child !is ViewGroup) {
                    child.isClickable = false
                    child.isLongClickable = false
                    child.isFocusable = false
                    child.isFocusableInTouchMode = false
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val effectiveHeightSpec = if (aspectRatio > 0f && widthMode != MeasureSpec.UNSPECIFIED && heightMode != MeasureSpec.EXACTLY) {
            val padH = paddingLeft + paddingRight
            val padV = paddingTop + paddingBottom
            val innerW = (widthSize - padH).coerceAtLeast(0)
            val rawHeight = (innerW / aspectRatio).roundToInt().coerceAtLeast(0) + padV
            val resolvedHeight = if (heightMode == MeasureSpec.AT_MOST) {
                rawHeight.coerceAtMost(MeasureSpec.getSize(heightMeasureSpec))
            } else {
                rawHeight
            }
            MeasureSpec.makeMeasureSpec(resolvedHeight, MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }

        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isWrapping = isRow && (flexWrap == FlexWrap.WRAP || flexWrap == FlexWrap.WRAP_REVERSE)
        when {
            isLayered || isRatioContainer -> measureLayered(widthMeasureSpec, effectiveHeightSpec)
            isWrapping -> measureWrappedRow(widthMeasureSpec, effectiveHeightSpec)
            isRow -> measureSingleRow(widthMeasureSpec, effectiveHeightSpec)
            else -> measureColumn(widthMeasureSpec, effectiveHeightSpec)
        }
    }

    private fun measureLayered(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val padH = paddingLeft + paddingRight
        val padV = paddingTop + paddingBottom
        val availW = (widthSize - padH).coerceAtLeast(0)
        val availH = (heightSize - padV).coerceAtLeast(0)

        val selfLp = layoutParams as? LayoutParams
        val selfFillsWidth = widthMode == MeasureSpec.EXACTLY ||
            (widthMode == MeasureSpec.AT_MOST && (
                aspectRatio > 0f ||
                    isRatioContainer ||
                    selfLp?.fillWidth == true ||
                    layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT
                ))
        val selfFillsHeight = heightMode == MeasureSpec.EXACTLY ||
            (heightMode == MeasureSpec.AT_MOST && (
                aspectRatio > 0f ||
                    isRatioContainer ||
                    selfLp?.fillHeight == true ||
                    layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT
                ))

        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
                visibleChildren.add(c)
            }
        }

        if (visibleChildren.isEmpty()) {
            val desiredW = if (selfFillsWidth && widthMode != MeasureSpec.UNSPECIFIED) widthSize else padH
            val desiredH = if (selfFillsHeight && heightMode != MeasureSpec.UNSPECIFIED) heightSize else padV
            setMeasuredDimension(
                resolveSize(desiredW, widthMeasureSpec),
                resolveSize(desiredH, heightMeasureSpec)
            )
            return
        }

        var maxChildW = 0
        var maxChildH = 0
        val fillChildren = ArrayList<View>()

        for (child in visibleChildren) {
            val lp = lpOf(child)
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin

            if (isFillLayerChild(lp)) {
                fillChildren.add(child)
                if (!selfFillsWidth || !selfFillsHeight) {
                    val probeWSpec = when {
                        selfFillsWidth && widthMode != MeasureSpec.UNSPECIFIED ->
                            MeasureSpec.makeMeasureSpec((availW - cMarginH).coerceAtLeast(0), MeasureSpec.EXACTLY)
                        lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                        widthMode != MeasureSpec.UNSPECIFIED ->
                            MeasureSpec.makeMeasureSpec((availW - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                        else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    }
                    val probeHSpec = when {
                        selfFillsHeight && heightMode != MeasureSpec.UNSPECIFIED ->
                            MeasureSpec.makeMeasureSpec((availH - cMarginV).coerceAtLeast(0), MeasureSpec.EXACTLY)
                        lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                        heightMode != MeasureSpec.UNSPECIFIED ->
                            MeasureSpec.makeMeasureSpec((availH - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                        else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                    }
                    child.measure(probeWSpec, probeHSpec)
                    maxChildW = max(maxChildW, child.measuredWidth + cMarginH)
                    maxChildH = max(maxChildH, child.measuredHeight + cMarginV)
                }
            } else {
                val childWSpec = when {
                    lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                    lp.widthPercent != null && widthMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec(
                            ((availW * (lp.widthPercent!! / 100f)).roundToInt() - cMarginH).coerceAtLeast(0),
                            MeasureSpec.EXACTLY
                        )
                    (lp.fillWidth || lp.width == ViewGroup.LayoutParams.MATCH_PARENT) && widthMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availW - cMarginH).coerceAtLeast(0), MeasureSpec.EXACTLY)
                    widthMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availW - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                val childHSpec = when {
                    lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    lp.heightPercent != null && heightMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec(
                            ((availH * (lp.heightPercent!! / 100f)).roundToInt() - cMarginV).coerceAtLeast(0),
                            MeasureSpec.EXACTLY
                        )
                    (lp.fillHeight || lp.height == ViewGroup.LayoutParams.MATCH_PARENT) && heightMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availH - cMarginV).coerceAtLeast(0), MeasureSpec.EXACTLY)
                    heightMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availH - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                child.measure(childWSpec, childHSpec)
                maxChildW = max(maxChildW, child.measuredWidth + cMarginH)
                maxChildH = max(maxChildH, child.measuredHeight + cMarginV)
            }
        }

        val targetInnerW = if (selfFillsWidth && widthMode != MeasureSpec.UNSPECIFIED) availW else maxChildW
        val targetInnerH = if (selfFillsHeight && heightMode != MeasureSpec.UNSPECIFIED) availH else maxChildH

        for (child in fillChildren) {
            val lp = lpOf(child)
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin
            val fillW = (targetInnerW - cMarginH).coerceAtLeast(0)
            val fillH = (targetInnerH - cMarginV).coerceAtLeast(0)
            child.measure(
                MeasureSpec.makeMeasureSpec(fillW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(fillH, MeasureSpec.EXACTLY)
            )
        }

        val desiredW = if (selfFillsWidth && widthMode != MeasureSpec.UNSPECIFIED) widthSize else targetInnerW + padH
        val desiredH = if (selfFillsHeight && heightMode != MeasureSpec.UNSPECIFIED) heightSize else targetInnerH + padV

        setMeasuredDimension(
            resolveSize(desiredW, widthMeasureSpec),
            resolveSize(desiredH, heightMeasureSpec)
        )
    }

    private fun measureColumn(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val padH = paddingLeft + paddingRight
        val padV = paddingTop + paddingBottom
        val availWidth = (widthSize - padH).coerceAtLeast(0)
        val availHeight = (heightSize - padV).coerceAtLeast(0)

        val allVisible = ArrayList<View>(childCount)
        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
                allVisible.add(c)
                if (!lpOf(c).isCssOutOfFlow()) {
                    visibleChildren.add(c)
                }
            }
        }
        measureOutOfFlowChildren(allVisible, widthMode, availWidth, heightMode, availHeight)

        if (visibleChildren.isEmpty()) {
            setMeasuredDimension(
                resolveSize(padH, widthMeasureSpec),
                resolveSize(padV, heightMeasureSpec)
            )
            return
        }

        val totalGaps = if (visibleChildren.size > 1) gapPx * (visibleChildren.size - 1) else 0
        var totalFixedHeight = 0
        var totalWeight = 0f
        var maxChildWidth = 0

        val selfLp = layoutParams as? LayoutParams
        val parentIsRow = (parent as? AndroidFlexContainerView)?.let {
            it.flexDirection == FlexDirection.ROW || it.flexDirection == FlexDirection.ROW_REVERSE
        } ?: false
        val parentFillsWidth = widthMode == MeasureSpec.EXACTLY ||
            (widthMode == MeasureSpec.AT_MOST && (
                selfLp?.fillWidth == true ||
                    layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                    (!parentIsRow && alignItems == AlignItems.STRETCH)
                ))
        val selfFillsHeight = heightMode == MeasureSpec.EXACTLY ||
            (heightMode == MeasureSpec.AT_MOST && !parentIsRow && (
                selfLp?.fillHeight == true ||
                    layoutParams?.height == ViewGroup.LayoutParams.MATCH_PARENT
                ))

        for (child in visibleChildren) {
            val lp = lpOf(child)
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin

            val stretchH = lp.fillWidth ||
                lp.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                lp.alignSelf == AlignSelf.STRETCH ||
                (lp.alignSelf == null && alignItems == AlignItems.STRETCH)

            val childWidthSpec = when {
                lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                stretchH && widthMode != MeasureSpec.UNSPECIFIED && parentFillsWidth ->
                    MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.EXACTLY)
                widthMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }

            if (lp.weight > 0f && heightMode != MeasureSpec.UNSPECIFIED) {
                totalWeight += lp.weight
            } else {
                val childFillsHeight = lp.fillHeight || lp.height == ViewGroup.LayoutParams.MATCH_PARENT
                val childHeightSpec = when {
                    lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    childFillsHeight && selfFillsHeight ->
                        MeasureSpec.makeMeasureSpec((availHeight - totalFixedHeight - cMarginV).coerceAtLeast(0), MeasureSpec.EXACTLY)
                    heightMode != MeasureSpec.UNSPECIFIED && lp.weight <= 0f ->
                        MeasureSpec.makeMeasureSpec((availHeight - totalFixedHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                child.measure(childWidthSpec, childHeightSpec)
                totalFixedHeight += child.measuredHeight + cMarginV
                maxChildWidth = max(maxChildWidth, child.measuredWidth + cMarginH)
            }
        }

        if (totalWeight > 0f && heightMode != MeasureSpec.UNSPECIFIED) {
            val remainingHeight = (availHeight - totalFixedHeight - totalGaps).coerceAtLeast(0)
            for (child in visibleChildren) {
                val lp = lpOf(child)
                if (lp.weight <= 0f) continue
                val cMarginH = lp.leftMargin + lp.rightMargin
                val cMarginV = lp.topMargin + lp.bottomMargin
                val stretchH = lp.fillWidth ||
                    lp.width == ViewGroup.LayoutParams.MATCH_PARENT ||
                    lp.alignSelf == AlignSelf.STRETCH ||
                    (lp.alignSelf == null && alignItems == AlignItems.STRETCH)

                val childWidthSpec = when {
                    lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                    stretchH && widthMode != MeasureSpec.UNSPECIFIED && parentFillsWidth ->
                        MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.EXACTLY)
                    widthMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                val share = ((remainingHeight * (lp.weight / totalWeight)).roundToInt() - cMarginV).coerceAtLeast(0)
                val specMode = if (selfFillsHeight || lp.fillHeight || lp.height == ViewGroup.LayoutParams.MATCH_PARENT || child is android.widget.ScrollView) {
                    MeasureSpec.EXACTLY
                } else {
                    MeasureSpec.AT_MOST
                }
                val childHeightSpec = MeasureSpec.makeMeasureSpec(share, specMode)
                child.measure(childWidthSpec, childHeightSpec)
                totalFixedHeight += child.measuredHeight + cMarginV
                maxChildWidth = max(maxChildWidth, child.measuredWidth + cMarginH)
            }
        }

        val desiredWidth = if (parentFillsWidth && widthMode != MeasureSpec.UNSPECIFIED) {
            widthSize
        } else {
            maxChildWidth + padH
        }
        val desiredHeight = if (selfFillsHeight) {
            heightSize
        } else {
            val used = totalFixedHeight + totalGaps + padV
            if (heightMode == MeasureSpec.AT_MOST) used.coerceAtMost(heightSize) else used
        }

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    private fun measureSingleRow(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val padH = paddingLeft + paddingRight
        val padV = paddingTop + paddingBottom
        val availWidth = (widthSize - padH).coerceAtLeast(0)
        val availHeight = (heightSize - padV).coerceAtLeast(0)

        val allVisible = ArrayList<View>(childCount)
        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
                allVisible.add(c)
                if (!lpOf(c).isCssOutOfFlow()) {
                    visibleChildren.add(c)
                }
            }
        }
        measureOutOfFlowChildren(allVisible, widthMode, availWidth, heightMode, availHeight)

        if (visibleChildren.isEmpty()) {
            setMeasuredDimension(
                resolveSize(padH, widthMeasureSpec),
                resolveSize(padV, heightMeasureSpec)
            )
            return
        }

        val totalGaps = if (visibleChildren.size > 1) gapPx * (visibleChildren.size - 1) else 0
        var usedNonWeightedWidth = 0
        var totalWeight = 0f
        var maxChildHeight = 0

        for (child in visibleChildren) {
            val lp = lpOf(child)
            if (lp.weight > 0f && widthMode != MeasureSpec.UNSPECIFIED) {
                totalWeight += lp.weight
                continue
            }
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin
            val childWidthSpec = when {
                lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                widthMode != MeasureSpec.UNSPECIFIED && lp.weight <= 0f ->
                    MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            val childHeightSpec = when {
                lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                heightMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            child.measure(childWidthSpec, childHeightSpec)
            usedNonWeightedWidth += child.measuredWidth + cMarginH
            maxChildHeight = max(maxChildHeight, child.measuredHeight + cMarginV)
        }

        // Prevent horizontal overflow in non-weighted rows (so trailing badges/buttons are never pushed off-screen)
        if (widthMode != MeasureSpec.UNSPECIFIED &&
            usedNonWeightedWidth + totalGaps > availWidth &&
            visibleChildren.size > 1
        ) {
            val overflow = (usedNonWeightedWidth + totalGaps) - availWidth
            val shrinkCandidate = visibleChildren
                .filter { lpOf(it).weight <= 0f && (it is TextView || it is ViewGroup) }
                .maxByOrNull { it.measuredWidth }
            if (shrinkCandidate != null && shrinkCandidate.measuredWidth > overflow + AndroidViewHandle.dpToPx(36)) {
                val lp = lpOf(shrinkCandidate)
                val cMarginV = lp.topMargin + lp.bottomMargin
                val constrainedW = (shrinkCandidate.measuredWidth - overflow).coerceAtLeast(AndroidViewHandle.dpToPx(36))
                val childHeightSpec = when {
                    lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    heightMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                usedNonWeightedWidth -= shrinkCandidate.measuredWidth
                shrinkCandidate.measure(
                    MeasureSpec.makeMeasureSpec(constrainedW, MeasureSpec.EXACTLY),
                    childHeightSpec
                )
                usedNonWeightedWidth += shrinkCandidate.measuredWidth
                maxChildHeight = max(maxChildHeight, shrinkCandidate.measuredHeight + cMarginV)
            }
        }

        var usedWeightedWidth = 0
        if (totalWeight > 0f && widthMode != MeasureSpec.UNSPECIFIED) {
            val remainingWidth = (availWidth - usedNonWeightedWidth - totalGaps).coerceAtLeast(0)
            for (child in visibleChildren) {
                val lp = lpOf(child)
                if (lp.weight <= 0f) continue
                val cMarginH = lp.leftMargin + lp.rightMargin
                val cMarginV = lp.topMargin + lp.bottomMargin
                val share = ((remainingWidth * (lp.weight / totalWeight)).roundToInt() - cMarginH).coerceAtLeast(0)
                val specMode = if (widthMode == MeasureSpec.EXACTLY || lp.fillWidth || lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                    MeasureSpec.EXACTLY
                } else {
                    MeasureSpec.EXACTLY
                }
                val childWidthSpec = MeasureSpec.makeMeasureSpec(share, specMode)
                val childHeightSpec = when {
                    lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                    heightMode != MeasureSpec.UNSPECIFIED ->
                        MeasureSpec.makeMeasureSpec((availHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                    else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
                }
                child.measure(childWidthSpec, childHeightSpec)
                usedWeightedWidth += child.measuredWidth + cMarginH
                maxChildHeight = max(maxChildHeight, child.measuredHeight + cMarginV)
            }
        }

        val fillsRowWidth = widthMode == MeasureSpec.EXACTLY ||
            (widthMode == MeasureSpec.AT_MOST && (
                totalWeight > 0f ||
                    justifyContent == JustifyContent.BETWEEN ||
                    justifyContent == JustifyContent.AROUND ||
                    justifyContent == JustifyContent.EVENLY ||
                    (layoutParams as? LayoutParams)?.fillWidth == true ||
                    layoutParams?.width == ViewGroup.LayoutParams.MATCH_PARENT
                ))

        val desiredWidth = if (fillsRowWidth && widthMode != MeasureSpec.UNSPECIFIED) {
            widthSize
        } else {
            usedNonWeightedWidth + usedWeightedWidth + totalGaps + padH
        }
        val desiredHeight = maxChildHeight + padV

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    private fun measureWrappedRow(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val widthSize = MeasureSpec.getSize(widthMeasureSpec)
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = MeasureSpec.getSize(heightMeasureSpec)

        val padH = paddingLeft + paddingRight
        val padV = paddingTop + paddingBottom
        val availWidth = if (widthMode == MeasureSpec.UNSPECIFIED) Int.MAX_VALUE else (widthSize - padH).coerceAtLeast(0)
        val availHeight = (heightSize - padV).coerceAtLeast(0)

        wrappedLines.clear()
        var currentLine = WrappedLine()

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            val lp = lpOf(child)
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin

            val childWidthSpec = when {
                lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                widthMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            val childHeightSpec = when {
                lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                heightMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            child.measure(childWidthSpec, childHeightSpec)
            if (lp.isCssOutOfFlow()) continue

            val childTotalW = child.measuredWidth + cMarginH
            val childTotalH = child.measuredHeight + cMarginV
            val gapBefore = if (currentLine.children.isEmpty()) 0 else gapPx

            if (currentLine.children.isNotEmpty() && currentLine.mainWidth + gapBefore + childTotalW > availWidth) {
                wrappedLines.add(currentLine)
                currentLine = WrappedLine()
            }
            if (currentLine.children.isNotEmpty()) {
                currentLine.mainWidth += gapPx
            }
            currentLine.children.add(child)
            currentLine.mainWidth += childTotalW
            currentLine.crossHeight = max(currentLine.crossHeight, childTotalH)
        }
        if (currentLine.children.isNotEmpty()) {
            wrappedLines.add(currentLine)
        }

        val maxLineWidth = wrappedLines.maxOfOrNull { it.mainWidth } ?: 0
        val totalLinesHeight = wrappedLines.sumOf { it.crossHeight } +
            if (wrappedLines.size > 1) gapPx * (wrappedLines.size - 1) else 0

        val desiredWidth = if (widthMode == MeasureSpec.EXACTLY) widthSize else (maxLineWidth + padH)
        val desiredHeight = totalLinesHeight + padV

        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = r - l
        val ch = b - t
        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isWrapping = isRow && (flexWrap == FlexWrap.WRAP || flexWrap == FlexWrap.WRAP_REVERSE)
        when {
            isLayered || isRatioContainer -> layoutLayered(cw, ch)
            isWrapping -> layoutWrappedRow(cw)
            isRow -> layoutSingleRow(cw, ch)
            else -> layoutColumn(cw, ch)
        }
        if (!isLayered && !isRatioContainer) {
            layoutOutOfFlowPositionedChildren(cw, ch)
        }
        applyZOrderAndTransformsAfterLayout()
    }

    private fun measureOutOfFlowChildren(
        allVisible: List<View>,
        widthMode: Int,
        availWidth: Int,
        heightMode: Int,
        availHeight: Int
    ) {
        for (child in allVisible) {
            val lp = lpOf(child)
            if (!lp.isCssOutOfFlow()) continue
            val cMarginH = lp.leftMargin + lp.rightMargin
            val cMarginV = lp.topMargin + lp.bottomMargin
            val childWSpec = when {
                lp.width > 0 -> MeasureSpec.makeMeasureSpec(lp.width, MeasureSpec.EXACTLY)
                widthMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availWidth - cMarginH).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            val childHSpec = when {
                lp.height > 0 -> MeasureSpec.makeMeasureSpec(lp.height, MeasureSpec.EXACTLY)
                heightMode != MeasureSpec.UNSPECIFIED ->
                    MeasureSpec.makeMeasureSpec((availHeight - cMarginV).coerceAtLeast(0), MeasureSpec.AT_MOST)
                else -> MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
            }
            child.measure(childWSpec, childHSpec)
        }
    }

    private fun resolveContainingBlock(childPosition: CssPosition): Triple<ViewGroup, Int, Int> {
        if (childPosition == CssPosition.FIXED) {
            if (isWindowRootHost || parent !is ViewGroup) {
                return Triple(this, 0, 0)
            }
        } else {
            val selfIsBlock = isWindowRootHost || isLayered || isRatioContainer ||
                cssPosition != CssPosition.STATIC || parent !is ViewGroup
            if (selfIsBlock) {
                return Triple(this, 0, 0)
            }
        }
        var offsetX = left
        var offsetY = top
        var cur: ViewGroup? = parent as? ViewGroup
        while (cur != null) {
            if (cur is AndroidFlexContainerView) {
                val curIsBlock = cur.isWindowRootHost ||
                    (childPosition != CssPosition.FIXED && (cur.isLayered || cur.isRatioContainer || cur.cssPosition != CssPosition.STATIC))
                if (curIsBlock) {
                    return Triple(cur, offsetX, offsetY)
                }
            }
            val nextParent = cur.parent as? ViewGroup
            if (nextParent == null || nextParent.id == android.R.id.content) {
                return Triple(cur, offsetX, offsetY)
            }
            offsetX += cur.left
            offsetY += cur.top
            cur = nextParent
        }
        return Triple(this, 0, 0)
    }

    private fun resolveCssLengthPx(length: CssLength, refPx: Int): Int =
        when (length.unit) {
            CssUnit.PERCENT -> ((length.value / 100f) * refPx).roundToInt()
            else -> AndroidViewHandle.dpToPx(length.resolvePx(refPx.toFloat()) ?: 0f)
        }

    private fun layoutOutOfFlowPositionedChildren(containerW: Int, containerH: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            val lp = lpOf(child)
            if (!lp.isCssOutOfFlow()) continue

            val (cbView, offsetX, offsetY) = resolveContainingBlock(lp.cssPosition)
            val cbW = cbView.width.takeIf { it > 0 } ?: containerW
            val cbH = cbView.height.takeIf { it > 0 } ?: containerH

            val leftPx = lp.cssLeft?.let { resolveCssLengthPx(it, cbW) }
            val rightPx = lp.cssRight?.let { resolveCssLengthPx(it, cbW) }
            val topPx = lp.cssTop?.let { resolveCssLengthPx(it, cbH) }
            val bottomPx = lp.cssBottom?.let { resolveCssLengthPx(it, cbH) }

            val targetW = when {
                lp.width > 0 -> lp.width
                lp.widthPercent != null -> ((cbW * lp.widthPercent!!) / 100f).roundToInt().coerceAtLeast(0)
                leftPx != null && rightPx != null -> (cbW - leftPx - rightPx - lp.leftMargin - lp.rightMargin).coerceAtLeast(0)
                lp.fillWidth && leftPx == null && rightPx == null -> cbW
                else -> child.measuredWidth
            }
            val targetH = when {
                lp.height > 0 -> lp.height
                lp.heightPercent != null -> ((cbH * lp.heightPercent!!) / 100f).roundToInt().coerceAtLeast(0)
                topPx != null && bottomPx != null -> (cbH - topPx - bottomPx - lp.topMargin - lp.bottomMargin).coerceAtLeast(0)
                lp.fillHeight && topPx == null && bottomPx == null -> cbH
                else -> child.measuredHeight
            }

            if (child.measuredWidth != targetW || child.measuredHeight != targetH) {
                child.measure(
                    MeasureSpec.makeMeasureSpec(targetW, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(targetH, MeasureSpec.EXACTLY)
                )
            }

            val ancX = when {
                leftPx != null -> leftPx + lp.leftMargin
                rightPx != null -> cbW - targetW - rightPx - lp.rightMargin
                else -> paddingLeft + lp.leftMargin + offsetX
            }
            val ancY = when {
                topPx != null -> topPx + lp.topMargin
                bottomPx != null -> cbH - targetH - bottomPx - lp.bottomMargin
                else -> paddingTop + lp.topMargin + offsetY
            }

            val localX = ancX - offsetX
            val localY = ancY - offsetY
            child.layout(localX, localY, localX + targetW, localY + targetH)
        }
    }

    private fun applyZOrderAndTransformsAfterLayout() {
        val density = resources?.displayMetrics?.density?.takeIf { it > 0f } ?: AndroidViewHandle.density()
        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            val lp = lpOf(child)
            if (lp.layerZIndex != 0 || child.translationZ != 0f) {
                child.translationZ = lp.layerZIndex.toFloat()
            }
            val tf = lp.cssTransform2D
            val w = child.width
            val h = child.height
            if (tf != lp.lastAppliedTransform || w != lp.lastTransformWidth || h != lp.lastTransformHeight) {
                val (rawDx, rawDy) = tf.resolveTranslationPx(w, h)
                child.translationX = if (tf.translateXIsPercent) rawDx else rawDx * density
                child.translationY = if (tf.translateYIsPercent) rawDy else rawDy * density
                child.scaleX = tf.scaleX
                child.scaleY = tf.scaleY
                child.rotation = tf.rotateDeg
                lp.lastAppliedTransform = tf
                lp.lastTransformWidth = w
                lp.lastTransformHeight = h
            }
        }
    }

    private fun layoutLayered(containerW: Int, containerH: Int) {
        val innerW = (containerW - paddingLeft - paddingRight).coerceAtLeast(0)
        val innerH = (containerH - paddingTop - paddingBottom).coerceAtLeast(0)

        for (i in 0 until childCount) {
            val child = getChildAt(i) ?: continue
            if (child.visibility == View.GONE) continue
            val lp = lpOf(child)
            child.translationZ = lp.layerZIndex.toFloat()

            if (isFillLayerChild(lp)) {
                val left = paddingLeft + lp.leftMargin + lp.layerOffsetXPx
                val top = paddingTop + lp.topMargin + lp.layerOffsetYPx
                val right = (left + innerW - lp.leftMargin - lp.rightMargin).coerceAtLeast(left)
                val bottom = (top + innerH - lp.topMargin - lp.bottomMargin).coerceAtLeast(top)
                child.layout(left, top, right, bottom)
            } else {
                val cw = child.measuredWidth
                val ch = child.measuredHeight

                val leftPct = lp.layerLeftPercent
                val rightPx = lp.layerRightPx
                val x = when {
                    leftPct != null -> {
                        val baseX = paddingLeft + (innerW * (leftPct.coerceIn(0f, 100f) / 100f)).roundToInt() + lp.layerOffsetXPx
                        when (lp.layerAlignment) {
                            LayerAlignment.TOP_CENTER, LayerAlignment.CENTER, LayerAlignment.BOTTOM_CENTER -> baseX - (cw / 2)
                            LayerAlignment.TOP_END, LayerAlignment.CENTER_END, LayerAlignment.BOTTOM_END -> baseX - cw
                            else -> baseX
                        }
                    }
                    rightPx != null -> paddingLeft + innerW - cw - rightPx + lp.layerOffsetXPx - lp.rightMargin
                    else -> when (lp.layerAlignment) {
                        LayerAlignment.TOP_START, LayerAlignment.CENTER_START, LayerAlignment.BOTTOM_START ->
                            paddingLeft + lp.leftMargin + lp.layerOffsetXPx
                        LayerAlignment.TOP_CENTER, LayerAlignment.CENTER, LayerAlignment.BOTTOM_CENTER ->
                            paddingLeft + ((innerW - cw) / 2) + lp.layerOffsetXPx
                        LayerAlignment.TOP_END, LayerAlignment.CENTER_END, LayerAlignment.BOTTOM_END ->
                            paddingLeft + innerW - cw - lp.rightMargin + lp.layerOffsetXPx
                    }
                }

                val topPct = lp.layerTopPercent
                val bottomPx = lp.layerBottomPx
                val y = when {
                    topPct != null -> {
                        val baseY = paddingTop + (innerH * (topPct.coerceIn(0f, 100f) / 100f)).roundToInt() + lp.layerOffsetYPx
                        when (lp.layerAlignment) {
                            LayerAlignment.CENTER_START, LayerAlignment.CENTER, LayerAlignment.CENTER_END -> baseY - (ch / 2)
                            LayerAlignment.BOTTOM_START, LayerAlignment.BOTTOM_CENTER, LayerAlignment.BOTTOM_END -> baseY - ch
                            else -> baseY
                        }
                    }
                    bottomPx != null -> paddingTop + innerH - ch - bottomPx + lp.layerOffsetYPx - lp.bottomMargin
                    else -> when (lp.layerAlignment) {
                        LayerAlignment.TOP_START, LayerAlignment.TOP_CENTER, LayerAlignment.TOP_END ->
                            paddingTop + lp.topMargin + lp.layerOffsetYPx
                        LayerAlignment.CENTER_START, LayerAlignment.CENTER, LayerAlignment.CENTER_END ->
                            paddingTop + ((innerH - ch) / 2) + lp.layerOffsetYPx
                        LayerAlignment.BOTTOM_START, LayerAlignment.BOTTOM_CENTER, LayerAlignment.BOTTOM_END ->
                            paddingTop + innerH - ch - lp.bottomMargin + lp.layerOffsetYPx
                    }
                }

                child.layout(x, y, x + cw, y + ch)
            }
        }
    }

    private fun layoutColumn(containerW: Int, containerH: Int) {
        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE && !lpOf(c).isCssOutOfFlow()) {
                visibleChildren.add(c)
            }
        }
        if (visibleChildren.isEmpty()) return

        val innerW = (containerW - paddingLeft - paddingRight).coerceAtLeast(0)
        val innerH = (containerH - paddingTop - paddingBottom).coerceAtLeast(0)
        val count = visibleChildren.size
        val totalGaps = if (count > 1) gapPx * (count - 1) else 0
        val contentH = visibleChildren.sumOf {
            val lp = lpOf(it)
            it.measuredHeight + lp.topMargin + lp.bottomMargin
        } + totalGaps
        val freeH = (innerH - contentH).coerceAtLeast(0)

        var y = paddingTop
        var extraGap = 0f
        when (justifyContent) {
            JustifyContent.START -> {}
            JustifyContent.CENTER -> y += freeH / 2
            JustifyContent.END -> y += freeH
            JustifyContent.BETWEEN -> if (count > 1) extraGap = freeH.toFloat() / (count - 1)
            JustifyContent.AROUND -> {
                extraGap = freeH.toFloat() / count
                y += (extraGap / 2f).roundToInt()
            }
            JustifyContent.EVENLY -> {
                extraGap = freeH.toFloat() / (count + 1)
                y += extraGap.roundToInt()
            }
        }

        var accumulatedExtra = 0f
        for ((index, child) in visibleChildren.withIndex()) {
            val lp = lpOf(child)
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            val effectiveAlign = when (lp.alignSelf) {
                AlignSelf.START -> AlignItems.START
                AlignSelf.END -> AlignItems.END
                AlignSelf.CENTER -> AlignItems.CENTER
                AlignSelf.BASELINE -> AlignItems.BASELINE
                AlignSelf.STRETCH -> AlignItems.STRETCH
                AlignSelf.AUTO, null -> alignItems
            }

            val x = when (effectiveAlign) {
                AlignItems.CENTER -> paddingLeft + ((innerW - cw) / 2).coerceAtLeast(0)
                AlignItems.END -> paddingLeft + (innerW - cw - lp.rightMargin).coerceAtLeast(0)
                else -> paddingLeft + lp.leftMargin
            }

            val rightEdge = if (effectiveAlign == AlignItems.STRETCH || lp.fillWidth || lp.width == ViewGroup.LayoutParams.MATCH_PARENT) {
                (paddingLeft + innerW - lp.rightMargin).coerceAtLeast(x + cw)
            } else {
                x + cw
            }

            val topEdge = y + lp.topMargin
            child.layout(x, topEdge, rightEdge, topEdge + ch)

            y = topEdge + ch + lp.bottomMargin + gapPx
            if (index < count - 1 && extraGap > 0f) {
                accumulatedExtra += extraGap
                val step = accumulatedExtra.roundToInt()
                y += step
                accumulatedExtra -= step
            }
        }
    }

    private fun layoutSingleRow(containerW: Int, containerH: Int) {
        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE && !lpOf(c).isCssOutOfFlow()) {
                visibleChildren.add(c)
            }
        }
        if (visibleChildren.isEmpty()) return

        val innerW = (containerW - paddingLeft - paddingRight).coerceAtLeast(0)
        val innerH = (containerH - paddingTop - paddingBottom).coerceAtLeast(0)
        val count = visibleChildren.size
        val totalGaps = if (count > 1) gapPx * (count - 1) else 0
        val contentW = visibleChildren.sumOf {
            val lp = lpOf(it)
            it.measuredWidth + lp.leftMargin + lp.rightMargin
        } + totalGaps
        val freeW = (innerW - contentW).coerceAtLeast(0)

        var x = paddingLeft
        var extraGap = 0f
        when (justifyContent) {
            JustifyContent.START -> {}
            JustifyContent.CENTER -> x += freeW / 2
            JustifyContent.END -> x += freeW
            JustifyContent.BETWEEN -> if (count > 1) extraGap = freeW.toFloat() / (count - 1)
            JustifyContent.AROUND -> {
                extraGap = freeW.toFloat() / count
                x += (extraGap / 2f).roundToInt()
            }
            JustifyContent.EVENLY -> {
                extraGap = freeW.toFloat() / (count + 1)
                x += extraGap.roundToInt()
            }
        }

        var accumulatedExtra = 0f
        for ((index, child) in visibleChildren.withIndex()) {
            val lp = lpOf(child)
            val cw = child.measuredWidth
            val ch = child.measuredHeight

            val effectiveAlign = when (lp.alignSelf) {
                AlignSelf.START -> AlignItems.START
                AlignSelf.END -> AlignItems.END
                AlignSelf.CENTER -> AlignItems.CENTER
                AlignSelf.BASELINE -> AlignItems.CENTER
                AlignSelf.STRETCH -> AlignItems.STRETCH
                AlignSelf.AUTO, null -> alignItems
            }

            val y = when (effectiveAlign) {
                AlignItems.CENTER, AlignItems.BASELINE -> paddingTop + ((innerH - ch) / 2).coerceAtLeast(0)
                AlignItems.END -> paddingTop + (innerH - ch - lp.bottomMargin).coerceAtLeast(0)
                else -> paddingTop + lp.topMargin
            }

            val bottomEdge = if (effectiveAlign == AlignItems.STRETCH && (lp.fillHeight || lp.height == ViewGroup.LayoutParams.MATCH_PARENT)) {
                (paddingTop + innerH - lp.bottomMargin).coerceAtLeast(y + ch)
            } else {
                y + ch
            }

            val leftEdge = x + lp.leftMargin
            child.layout(leftEdge, y, leftEdge + cw, bottomEdge)

            x = leftEdge + cw + lp.rightMargin + gapPx
            if (index < count - 1 && extraGap > 0f) {
                accumulatedExtra += extraGap
                val step = accumulatedExtra.roundToInt()
                x += step
                accumulatedExtra -= step
            }
        }
    }

    private fun layoutWrappedRow(containerW: Int) {
        val innerW = (containerW - paddingLeft - paddingRight).coerceAtLeast(0)
        var y = paddingTop
        for (line in wrappedLines) {
            val freeW = (innerW - line.mainWidth).coerceAtLeast(0)
            var x = paddingLeft + when (justifyContent) {
                JustifyContent.CENTER -> freeW / 2
                JustifyContent.END -> freeW
                else -> 0
            }
            for (child in line.children) {
                val lp = lpOf(child)
                val cw = child.measuredWidth
                val ch = child.measuredHeight
                val childTop = when (alignItems) {
                    AlignItems.CENTER, AlignItems.BASELINE -> y + ((line.crossHeight - ch) / 2).coerceAtLeast(0)
                    AlignItems.END -> y + (line.crossHeight - ch - lp.bottomMargin).coerceAtLeast(0)
                    else -> y + lp.topMargin
                }
                val leftEdge = x + lp.leftMargin
                child.layout(leftEdge, childTop, leftEdge + cw, childTop + ch)
                x = leftEdge + cw + lp.rightMargin + gapPx
            }
            y += line.crossHeight + gapPx
        }
    }

    companion object {
        fun buildLayoutParamsFor(handle: AndroidViewHandle): LayoutParams {
            val w = when {
                handle.fillMaxWidth || handle.attributes["layoutWidth"] == "MATCH_PARENT" ->
                    ViewGroup.LayoutParams.MATCH_PARENT
                handle.hasExplicitPreferredWidth && handle.bounds.width > 0 ->
                    AndroidViewHandle.dpToPx(handle.bounds.width)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            val h = when {
                handle.fillMaxHeight || handle.attributes["layoutHeight"] == "MATCH_PARENT" ->
                    ViewGroup.LayoutParams.MATCH_PARENT
                handle.hasExplicitPreferredHeight && handle.bounds.height > 0 ->
                    AndroidViewHandle.dpToPx(handle.bounds.height)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            return LayoutParams(w, h).apply {
                applyHandleToLayoutParams(handle, this)
            }
        }

        fun applyHandleToLayoutParams(handle: AndroidViewHandle, lp: LayoutParams) {
            lp.weight = handle.layoutWeight
            lp.fillWidth = handle.fillMaxWidth || handle.attributes["layoutWidth"] == "MATCH_PARENT"
            lp.fillHeight = handle.fillMaxHeight || handle.attributes["layoutHeight"] == "MATCH_PARENT"
            lp.widthPercent = handle.widthPercent
            lp.heightPercent = handle.heightPercent
            lp.isLayerItem = handle.isLayerItem
            lp.layerFill = handle.layerFill
            lp.layerFloat = handle.layerFloat
            lp.layerAlignment = handle.layerAlignment
            lp.layerOffsetXPx = AndroidViewHandle.dpToPx(handle.layerOffsetXPx)
            lp.layerOffsetYPx = AndroidViewHandle.dpToPx(handle.layerOffsetYPx)
            lp.layerLeftPercent = handle.layerLeftPercent
            lp.layerTopPercent = handle.layerTopPercent
            lp.layerRightPx = handle.layerRightPx?.let { AndroidViewHandle.dpToPx(it) }
            lp.layerBottomPx = handle.layerBottomPx?.let { AndroidViewHandle.dpToPx(it) }
            lp.layerZIndex = handle.layerZIndex
            lp.layerPassThrough = handle.layerPassThrough
            lp.cssPointerEventsNone = handle.cssPointerEventsNone
            lp.cssPosition = handle.cssPosition
            lp.cssTop = handle.cssTop
            lp.cssRight = handle.cssRight
            lp.cssBottom = handle.cssBottom
            lp.cssLeft = handle.cssLeft
            lp.cssTransform2D = handle.cssTransform2D
            lp.width = when {
                lp.fillWidth -> ViewGroup.LayoutParams.MATCH_PARENT
                handle.hasExplicitPreferredWidth && handle.bounds.width > 0 ->
                    AndroidViewHandle.dpToPx(handle.bounds.width)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            lp.height = when {
                lp.fillHeight -> ViewGroup.LayoutParams.MATCH_PARENT
                handle.hasExplicitPreferredHeight && handle.bounds.height > 0 ->
                    AndroidViewHandle.dpToPx(handle.bounds.height)
                else -> ViewGroup.LayoutParams.WRAP_CONTENT
            }
            lp.setMargins(
                AndroidViewHandle.dpToPx(handle.margin.left),
                AndroidViewHandle.dpToPx(handle.margin.top),
                AndroidViewHandle.dpToPx(handle.margin.right),
                AndroidViewHandle.dpToPx(handle.margin.bottom)
            )
        }
    }
}
