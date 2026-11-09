package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.AlignSelf
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import kotlin.math.max
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

    class LayoutParams : MarginLayoutParams {
        var weight: Float = 0f
        var fillWidth: Boolean = false
        var fillHeight: Boolean = false
        var alignSelf: AlignSelf? = null

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
            }
            is MarginLayoutParams -> LayoutParams(p)
            else -> LayoutParams(p)
        }

    override fun checkLayoutParams(p: ViewGroup.LayoutParams?): Boolean = p is LayoutParams

    private fun lpOf(child: View): LayoutParams =
        (child.layoutParams as? LayoutParams) ?: generateLayoutParams(child.layoutParams).also {
            child.layoutParams = it
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isWrapping = isRow && (flexWrap == FlexWrap.WRAP || flexWrap == FlexWrap.WRAP_REVERSE)
        when {
            isWrapping -> measureWrappedRow(widthMeasureSpec, heightMeasureSpec)
            isRow -> measureSingleRow(widthMeasureSpec, heightMeasureSpec)
            else -> measureColumn(widthMeasureSpec, heightMeasureSpec)
        }
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

        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
                visibleChildren.add(c)
            }
        }

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
            (heightMode == MeasureSpec.AT_MOST && (
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

        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
                visibleChildren.add(c)
            }
        }

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
        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isWrapping = isRow && (flexWrap == FlexWrap.WRAP || flexWrap == FlexWrap.WRAP_REVERSE)
        when {
            isWrapping -> layoutWrappedRow(r - l)
            isRow -> layoutSingleRow(r - l, b - t)
            else -> layoutColumn(r - l, b - t)
        }
    }

    private fun layoutColumn(containerW: Int, containerH: Int) {
        val visibleChildren = ArrayList<View>(childCount)
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            if (c != null && c.visibility != View.GONE) {
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
            if (c != null && c.visibility != View.GONE) {
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
