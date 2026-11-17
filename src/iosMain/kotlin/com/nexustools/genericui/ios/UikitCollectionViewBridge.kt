@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSIndexPath
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewDataSourceProtocol
import platform.UIKit.UICollectionViewDelegateFlowLayoutProtocol
import platform.UIKit.UICollectionViewLayout
import platform.UIKit.UIView
import platform.UIKit.row
import platform.darwin.NSObject

/**
 * `UICollectionViewDataSource` + `UICollectionViewDelegateFlowLayout` bridge driving real recycled
 * `UICollectionViewCell`s while notifying [onViewportScrolled] so `MobileLazyViewportPool` tracks
 * active virtualized indices synchronously.
 */
class UikitCollectionViewBridge(
    private val reuseIdentifier: String = REUSE_IDENTIFIER
) : NSObject(), UICollectionViewDataSourceProtocol, UICollectionViewDelegateFlowLayoutProtocol {

    var itemCount: Int = 0
    var columnCount: Int = 1
    var estimatedItemExtentPt: Double = 48.0
    var horizontalOrientation: Boolean = false
    var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var onViewportScrolled: ((scrollOffsetPt: Float, viewportExtentPt: Float) -> Unit)? = null
    var onCellMounted: ((index: Int, impl: NativeImpl<*>) -> Unit)? = null

    private val cellBoundImpls = LinkedHashMap<UICollectionViewCell, Pair<Int, NativeImpl<*>>>()

    override fun numberOfSectionsInCollectionView(collectionView: UICollectionView): Long = 1L

    override fun collectionView(collectionView: UICollectionView, numberOfItemsInSection: Long): Long =
        itemCount.toLong()

    override fun collectionView(
        collectionView: UICollectionView,
        cellForItemAtIndexPath: NSIndexPath
    ): UICollectionViewCell {
        val cell = collectionView.dequeueReusableCellWithReuseIdentifier(
            reuseIdentifier,
            forIndexPath = cellForItemAtIndexPath
        )
        val index = cellForItemAtIndexPath.row.toInt()
        for (existing in cell.contentView.subviews.filterIsInstance<UIView>()) {
            existing.removeFromSuperview()
        }
        val factory = itemFactory ?: return cell
        if (index < 0 || index >= itemCount) return cell
        val impl = factory(index)
        val handle = impl.nativeWidget as? UikitViewHandle
        if (handle != null) {
            val contentBounds = cell.contentView.bounds
            handle.uiView.setFrame(contentBounds)
            cell.contentView.addSubview(handle.uiView)
            handle.uiView.setNeedsLayout()
        }
        cellBoundImpls[cell] = index to impl
        onCellMounted?.invoke(index, impl)
        return cell
    }

    override fun collectionView(
        collectionView: UICollectionView,
        layout: UICollectionViewLayout,
        sizeForItemAtIndexPath: NSIndexPath
    ): CValue<CGSize> {
        val (cw, ch) = collectionView.bounds.useContents { size.width to size.height }
        val cols = columnCount.coerceAtLeast(1)
        return if (cols > 1) {
            val cellW = ((cw.takeIf { it > 0.0 } ?: 360.0) / cols).coerceAtLeast(48.0)
            CGSizeMake(cellW, estimatedItemExtentPt)
        } else if (horizontalOrientation) {
            CGSizeMake(estimatedItemExtentPt, ch.takeIf { it > 0.0 } ?: 120.0)
        } else {
            CGSizeMake(cw.takeIf { it > 0.0 } ?: 360.0, estimatedItemExtentPt)
        }
    }

    override fun scrollViewDidScroll(scrollView: platform.UIKit.UIScrollView) {
        val (ox, oy) = scrollView.contentOffset.useContents { x.toFloat() to y.toFloat() }
        val (vw, vh) = scrollView.bounds.useContents { size.width.toFloat() to size.height.toFloat() }
        val offset = if (horizontalOrientation) ox else oy
        val viewport = if (horizontalOrientation) vw else vh
        onViewportScrolled?.invoke(offset, viewport)
    }

    fun clearBoundCells() {
        cellBoundImpls.clear()
    }

    companion object {
        val REUSE_IDENTIFIER: String get() = "GenericUiCollectionCell"
    }
}
