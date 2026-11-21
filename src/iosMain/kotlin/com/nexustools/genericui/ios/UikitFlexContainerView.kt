@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.geometry.EdgeInsets
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIView
import kotlin.math.max

/**
 * Genuine `platform.UIKit.UIView` container subclass performing real `layoutSubviews()`
 * flexbox-aligned geometry distribution and `sizeThatFits(...)` measurement for child `UIView`s.
 */
class UikitFlexContainerView(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0)
) : UIView(frame = frame) {

    var flexDirection: FlexDirection = FlexDirection.COLUMN
    var justifyContent: JustifyContent = JustifyContent.START
    var alignItems: AlignItems = AlignItems.STRETCH
    var paddingInsets: EdgeInsets = EdgeInsets(0, 0, 0, 0)
    var itemGapPt: Double = 0.0
    var wrapLines: Boolean = false
    var explicitBoundsOverride: CValue<CGRect>? = null
    internal var backdropChromeView: UIView? = null

    private val preferredChildSizes = LinkedHashMap<UIView, Pair<Double, Double>>()

    fun setChildPreferredSize(child: UIView, widthPt: Double, heightPt: Double) {
        preferredChildSizes[child] = widthPt to heightPt
        setNeedsLayout()
    }

    fun removeChildMetadata(child: UIView) {
        preferredChildSizes.remove(child)
    }

    override fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val availW = size.useContents { width }
        val availH = size.useContents { height }
        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val visibleChildren = subviews.filterIsInstance<UIView>().filter { !it.hidden && it !== backdropChromeView }
        if (visibleChildren.isEmpty()) {
            return CGSizeMake(
                (paddingInsets.left + paddingInsets.right).toDouble(),
                (paddingInsets.top + paddingInsets.bottom).toDouble()
            )
        }
        var mainSum = 0.0
        var crossMax = 0.0
        for ((index, child) in visibleChildren.withIndex()) {
            val childSize = measureChild(child, availW, availH)
            if (index > 0) mainSum += itemGapPt
            if (isRow) {
                mainSum += childSize.first
                crossMax = max(crossMax, childSize.second)
            } else {
                mainSum += childSize.second
                crossMax = max(crossMax, childSize.first)
            }
        }
        val padW = (paddingInsets.left + paddingInsets.right).toDouble()
        val padH = (paddingInsets.top + paddingInsets.bottom).toDouble()
        return if (isRow) {
            CGSizeMake(mainSum + padW, crossMax + padH)
        } else {
            CGSizeMake(crossMax + padW, mainSum + padH)
        }
    }

    override fun layoutSubviews() {
        super.layoutSubviews()
        val totalW = bounds.useContents { size.width }
        val totalH = bounds.useContents { size.height }
        backdropChromeView?.let { chrome ->
            chrome.setFrame(bounds)
            chrome.layer.cornerRadius = layer.cornerRadius
            chrome.clipsToBounds = clipsToBounds
        }
        if (totalW <= 0.0 && totalH <= 0.0) return

        val contentX = paddingInsets.left.toDouble()
        val contentY = paddingInsets.top.toDouble()
        val contentW = max(0.0, totalW - paddingInsets.left - paddingInsets.right)
        val contentH = max(0.0, totalH - paddingInsets.top - paddingInsets.bottom)

        val visibleChildren = subviews.filterIsInstance<UIView>().filter { !it.hidden && it !== backdropChromeView }
        if (visibleChildren.isEmpty()) return

        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isReverse = flexDirection == FlexDirection.ROW_REVERSE || flexDirection == FlexDirection.COLUMN_REVERSE
        val ordered = if (isReverse) visibleChildren.asReversed() else visibleChildren

        val measured = ordered.map { child ->
            val explicit = preferredChildSizes[child]
            val fit = measureChild(child, contentW, contentH)
            val w = explicit?.first ?: fit.first
            val h = explicit?.second ?: fit.second
            child to (w to h)
        }

        val totalMain = measured.sumOf { if (isRow) it.second.first else it.second.second } +
            itemGapPt * max(0, measured.size - 1)
        val availableMain = if (isRow) contentW else contentH
        val freeMain = max(0.0, availableMain - totalMain)

        var cursor = when (justifyContent) {
            JustifyContent.CENTER -> freeMain / 2.0
            JustifyContent.END -> freeMain
            JustifyContent.AROUND -> if (measured.isNotEmpty()) freeMain / (measured.size * 2.0) else 0.0
            JustifyContent.EVENLY -> freeMain / (measured.size + 1.0)
            else -> 0.0
        }
        val extraGap = when (justifyContent) {
            JustifyContent.BETWEEN -> if (measured.size > 1) freeMain / (measured.size - 1) else 0.0
            JustifyContent.AROUND -> if (measured.isNotEmpty()) freeMain / measured.size else 0.0
            JustifyContent.EVENLY -> freeMain / (measured.size + 1.0)
            else -> 0.0
        }

        for ((child, sizePair) in measured) {
            val childW = sizePair.first
            val childH = sizePair.second
            if (isRow) {
                val h = if (alignItems == AlignItems.STRETCH && preferredChildSizes[child] == null) contentH else childH
                val yOff = when (alignItems) {
                    AlignItems.CENTER -> (contentH - h) / 2.0
                    AlignItems.END -> contentH - h
                    else -> 0.0
                }
                child.setFrame(CGRectMake(contentX + cursor, contentY + yOff, childW, h))
                cursor += childW + itemGapPt + extraGap
            } else {
                val w = if (alignItems == AlignItems.STRETCH && preferredChildSizes[child] == null) contentW else childW
                val xOff = when (alignItems) {
                    AlignItems.CENTER -> (contentW - w) / 2.0
                    AlignItems.END -> contentW - w
                    else -> 0.0
                }
                child.setFrame(CGRectMake(contentX + xOff, contentY + cursor, w, childH))
                cursor += childH + itemGapPt + extraGap
            }
        }
    }

    private fun measureChild(child: UIView, maxWidth: Double, maxHeight: Double): Pair<Double, Double> {
        preferredChildSizes[child]?.let { return it }
        val existingW = child.frame.useContents { size.width }
        val existingH = child.frame.useContents { size.height }
        val fit = child.sizeThatFits(CGSizeMake(max(1.0, maxWidth), max(1.0, maxHeight)))
        return fit.useContents {
            val resolvedW = when {
                width > 0.0 -> width
                existingW > 0.0 -> existingW
                else -> max(44.0, maxWidth)
            }
            val resolvedH = when {
                height > 0.0 -> height
                existingH > 0.0 -> existingH
                else -> 44.0
            }
            resolvedW to resolvedH
        }
    }
}
