@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.JustifyContent
import com.nexustools.genericui.enums.css.CssPosition
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.geometry.EdgeInsets
import com.nexustools.genericui.style.css.CssLength
import com.nexustools.genericui.style.transition.CssTransform2D
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGAffineTransform
import platform.CoreGraphics.CGAffineTransformMakeTranslation
import platform.CoreGraphics.CGAffineTransformRotate
import platform.CoreGraphics.CGAffineTransformScale
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIEvent
import platform.UIKit.UIFocusUpdateContext
import platform.UIKit.UIView
import platform.UIKit.nextFocusedView
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.roundToInt

internal data class UikitNodeLayoutMetadata(
    val position: CssPosition = CssPosition.STATIC,
    val top: CssLength? = null,
    val right: CssLength? = null,
    val bottom: CssLength? = null,
    val left: CssLength? = null,
    val width: CssLength? = null,
    val height: CssLength? = null,
    val margin: EdgeInsets = EdgeInsets.ZERO,
    val zIndex: Int = 0,
    val transform2D: CssTransform2D = CssTransform2D.IDENTITY,
    val pointerEventsNone: Boolean = false,
    val customStyleEngine: Boolean = false
) {
    fun isOutOfFlow(): Boolean =
        position == CssPosition.ABSOLUTE || position == CssPosition.FIXED
}

internal object UikitViewLayoutRegistry {
    private val map = LinkedHashMap<UIView, UikitNodeLayoutMetadata>()

    fun get(view: UIView): UikitNodeLayoutMetadata = map[view] ?: UikitNodeLayoutMetadata()

    fun update(view: UIView, transform: (UikitNodeLayoutMetadata) -> UikitNodeLayoutMetadata): UikitNodeLayoutMetadata {
        val next = transform(get(view))
        map[view] = next
        return next
    }

    fun remove(view: UIView) {
        map.remove(view)
    }

    fun buildCgAffineTransform(tf: CssTransform2D, widthPt: Double, heightPt: Double): CValue<CGAffineTransform> {
        val dx = if (tf.translateXIsPercent) (tf.translateX / 100.0) * widthPt else tf.translateX.toDouble()
        val dy = if (tf.translateYIsPercent) (tf.translateY / 100.0) * heightPt else tf.translateY.toDouble()
        var result = CGAffineTransformMakeTranslation(dx, dy)
        if (tf.rotateDeg != 0f) {
            result = CGAffineTransformRotate(result, tf.rotateDeg.toDouble() * PI / 180.0)
        }
        if (tf.scaleX != 1f || tf.scaleY != 1f) {
            result = CGAffineTransformScale(result, tf.scaleX.toDouble(), tf.scaleY.toDouble())
        }
        return result
    }
}

/**
 * Genuine `platform.UIKit.UIView` container subclass performing real `layoutSubviews()`
 * flexbox-aligned geometry distribution, proximal `position: relative` / window-root `position: absolute`
 * positioning, signed `CGAffineTransform` application, `zPosition` / subview z-order stacking,
 * and `sizeThatFits(...)` measurement for child `UIView`s.
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
    var isWindowRootHost: Boolean = false
    internal var backdropChromeView: UIView? = null
    internal var onLayoutSubviewsCallback: (() -> Unit)? = null

    private val preferredChildSizes = LinkedHashMap<UIView, Pair<Double, Double>>()

    fun setChildPreferredSize(child: UIView, widthPt: Double, heightPt: Double) {
        preferredChildSizes[child] = widthPt to heightPt
        setNeedsLayout()
    }

    fun removeChildMetadata(child: UIView) {
        preferredChildSizes.remove(child)
    }

    override fun canBecomeFocused(): Boolean =
        UikitFocusRegistry.isEffectivelyFocusable(this) && super.canBecomeFocused()

    override fun shouldUpdateFocusInContext(context: UIFocusUpdateContext): Boolean {
        val nextView = context.nextFocusedView
        if (nextView != null && !UikitFocusRegistry.isEffectivelyFocusable(nextView)) {
            return false
        }
        return super.shouldUpdateFocusInContext(context)
    }

    override fun sizeThatFits(size: CValue<CGSize>): CValue<CGSize> {
        val availW = size.useContents { width }
        val availH = size.useContents { height }
        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val visibleChildren = subviews.filterIsInstance<UIView>()
            .filter { !it.hidden && it !== backdropChromeView && !UikitViewLayoutRegistry.get(it).isOutOfFlow() }
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
            chrome.layer.maskedCorners = layer.maskedCorners
            chrome.clipsToBounds = clipsToBounds
        }
        onLayoutSubviewsCallback?.invoke()
        if (totalW <= 0.0 && totalH <= 0.0) return

        val contentX = paddingInsets.left.toDouble()
        val contentY = paddingInsets.top.toDouble()
        val contentW = max(0.0, totalW - paddingInsets.left - paddingInsets.right)
        val contentH = max(0.0, totalH - paddingInsets.top - paddingInsets.bottom)

        val visibleChildren = subviews.filterIsInstance<UIView>().filter { !it.hidden && it !== backdropChromeView }
        if (visibleChildren.isEmpty()) return

        // Temporarily reset transform to identity before assigning frames so UIKit frame geometry stays exact.
        for (child in visibleChildren) {
            child.transform = CGAffineTransformMakeTranslation(0.0, 0.0)
        }

        val flowChildren = visibleChildren.filterNot { UikitViewLayoutRegistry.get(it).isOutOfFlow() }
        val outOfFlowChildren = visibleChildren.filter { UikitViewLayoutRegistry.get(it).isOutOfFlow() }

        val isRow = flexDirection == FlexDirection.ROW || flexDirection == FlexDirection.ROW_REVERSE
        val isReverse = flexDirection == FlexDirection.ROW_REVERSE || flexDirection == FlexDirection.COLUMN_REVERSE
        val ordered = if (isReverse) flowChildren.asReversed() else flowChildren

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
            val childMeta = UikitViewLayoutRegistry.get(child)
            val m = childMeta.margin
            val childW = sizePair.first
            val childH = sizePair.second
            if (isRow) {
                val h = if (alignItems == AlignItems.STRETCH && preferredChildSizes[child] == null && childMeta.height == null) {
                    max(0.0, contentH - m.top - m.bottom)
                } else childH
                val yOff = when (alignItems) {
                    AlignItems.CENTER -> (contentH - h) / 2.0
                    AlignItems.END -> contentH - h - m.bottom
                    else -> m.top.toDouble()
                }
                val x = contentX + cursor + m.left
                child.setFrame(CGRectMake(x, contentY + yOff, childW, h))
                cursor += childW + m.left + m.right + itemGapPt + extraGap
            } else {
                val w = if (alignItems == AlignItems.STRETCH && preferredChildSizes[child] == null && childMeta.width == null) {
                    max(0.0, contentW - m.left - m.right)
                } else childW
                val xOff = when (alignItems) {
                    AlignItems.CENTER -> (contentW - w) / 2.0
                    AlignItems.END -> contentW - w - m.right
                    else -> m.left.toDouble()
                }
                val y = contentY + cursor + m.top
                child.setFrame(CGRectMake(contentX + xOff, y, w, childH))
                cursor += childH + m.top + m.bottom + itemGapPt + extraGap
            }
        }

        layoutOutOfFlowChildren(outOfFlowChildren, totalW, totalH)
        applyZOrderAndTransforms(visibleChildren)
    }

    private fun resolveContainingBlock(childPosition: CssPosition): Triple<UIView, Double, Double> {
        if (childPosition == CssPosition.FIXED) {
            if (isWindowRootHost || superview == null) return Triple(this, 0.0, 0.0)
        } else {
            val selfIsBlock = isWindowRootHost ||
                UikitViewLayoutRegistry.get(this).position != CssPosition.STATIC ||
                superview !is UIView
            if (selfIsBlock) return Triple(this, 0.0, 0.0)
        }

        var offX = frame.useContents { origin.x }
        var offY = frame.useContents { origin.y }
        var cur: UIView? = superview
        while (cur != null) {
            val isWinHost = (cur as? UikitFlexContainerView)?.isWindowRootHost == true
            val isPositioned = UikitViewLayoutRegistry.get(cur).position != CssPosition.STATIC
            val next = cur.superview
            if (isWinHost || (childPosition != CssPosition.FIXED && isPositioned) || next == null) {
                return Triple(cur, offX, offY)
            }
            cur.frame.useContents {
                offX += origin.x
                offY += origin.y
            }
            cur = next
        }
        return Triple(this, 0.0, 0.0)
    }

    private fun resolveCssLengthPt(len: CssLength, refPt: Double): Double =
        when (len.unit) {
            CssUnit.PERCENT -> (len.value.toDouble() / 100.0) * refPt
            else -> (len.resolvePx(refPt.toFloat()) ?: 0f).toDouble()
        }

    private fun layoutOutOfFlowChildren(outOfFlow: List<UIView>, fallbackW: Double, fallbackH: Double) {
        if (outOfFlow.isEmpty()) return
        for (child in outOfFlow) {
            val meta = UikitViewLayoutRegistry.get(child)
            val (cbView, offX, offY) = resolveContainingBlock(meta.position)
            val cbSize = cbView.bounds.useContents {
                (size.width.takeIf { it > 0.0 } ?: fallbackW) to (size.height.takeIf { it > 0.0 } ?: fallbackH)
            }
            val cbW = cbSize.first
            val cbH = cbSize.second

            val leftPt = meta.left?.let { resolveCssLengthPt(it, cbW) }
            val rightPt = meta.right?.let { resolveCssLengthPt(it, cbW) }
            val topPt = meta.top?.let { resolveCssLengthPt(it, cbH) }
            val bottomPt = meta.bottom?.let { resolveCssLengthPt(it, cbH) }

            val explicit = preferredChildSizes[child]
            val fit = measureChild(child, cbW, cbH)
            val targetW = when {
                explicit != null -> explicit.first
                meta.width != null -> resolveCssLengthPt(meta.width, cbW).coerceAtLeast(0.0)
                leftPt != null && rightPt != null -> (cbW - leftPt - rightPt - meta.margin.left - meta.margin.right).coerceAtLeast(0.0)
                else -> fit.first
            }
            val targetH = when {
                explicit != null -> explicit.second
                meta.height != null -> resolveCssLengthPt(meta.height, cbH).coerceAtLeast(0.0)
                topPt != null && bottomPt != null -> (cbH - topPt - bottomPt - meta.margin.top - meta.margin.bottom).coerceAtLeast(0.0)
                else -> fit.second
            }

            val ancX = when {
                leftPt != null -> leftPt + meta.margin.left
                rightPt != null -> cbW - targetW - rightPt - meta.margin.right
                else -> paddingInsets.left.toDouble() + meta.margin.left + offX
            }
            val ancY = when {
                topPt != null -> topPt + meta.margin.top
                bottomPt != null -> cbH - targetH - bottomPt - meta.margin.bottom
                else -> paddingInsets.top.toDouble() + meta.margin.top + offY
            }

            child.setFrame(CGRectMake(ancX - offX, ancY - offY, targetW, targetH))
        }
    }

    private fun applyZOrderAndTransforms(visibleChildren: List<UIView>) {
        // Stable z-order sort: lowest zPosition first, highest last (frontmost for CoreAnimation & UIView.hitTest)
        val allManaged = subviews.filterIsInstance<UIView>().filter { it !== backdropChromeView }
        if (allManaged.size > 1) {
            val desiredOrder = allManaged.withIndex()
                .sortedWith(compareBy({ UikitViewLayoutRegistry.get(it.value).zIndex }, { it.index }))
                .map { it.value }
            val currentManaged = allManaged
            val needsReorder = desiredOrder.indices.any { desiredOrder[it] !== currentManaged[it] }
            if (needsReorder) {
                for (view in desiredOrder) {
                    bringSubviewToFront(view)
                }
            }
        }
        for (child in visibleChildren) {
            val meta = UikitViewLayoutRegistry.get(child)
            child.layer.zPosition = meta.zIndex.toDouble()
            val tf = meta.transform2D
            if (tf != CssTransform2D.IDENTITY) {
                val dims = child.bounds.useContents { size.width to size.height }
                child.transform = UikitViewLayoutRegistry.buildCgAffineTransform(tf, dims.first, dims.second)
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
