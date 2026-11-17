@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSIndexPath
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewFlowLayout
import platform.UIKit.UICollectionViewScrollDirection
import platform.UIKit.UICollectionViewScrollPositionNone
import platform.UIKit.indexPathForRow

/**
 * Virtualized UIKit `UICollectionView` + `UICollectionViewFlowLayout` list driven by
 * [MobileLazyViewportPool] with live `scrollViewDidScroll` viewport recomputation.
 */
class IosLazyListImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UICollectionView(
            frame = CGRectMake(0.0, 0.0, 360.0, 480.0),
            collectionViewLayout = UICollectionViewFlowLayout().apply {
                minimumLineSpacing = 0.0
                minimumInteritemSpacing = 0.0
            }
        )
    ),
    componentClassPrefix = "lazy-list"
), LazyListNativeImpl<UikitViewHandle> {

    val collectionView: UICollectionView get() = uiView as UICollectionView
    private val flowLayout: UICollectionViewFlowLayout
        get() = collectionView.collectionViewLayout as UICollectionViewFlowLayout
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool(overscanItems = 3)
    private val bridge = UikitCollectionViewBridge()

    private var orientation: Orientation = Orientation.VERTICAL
    private var totalItems: Int = 0
    private var estimatedItemExtentPx: Float = 48f
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    private var viewportListener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)? = null

    init {
        collectionView.registerClass(
            cellClass = UICollectionViewCell,
            forCellWithReuseIdentifier = UikitCollectionViewBridge.REUSE_IDENTIFIER
        )
        collectionView.dataSource = bridge
        collectionView.delegate = bridge
        bridge.onViewportScrolled = { offsetPx, viewportPx ->
            recomputeViewportWindow(offsetPx, viewportPx)
            viewportListener?.invoke(offsetPx, viewportPx)
        }
    }

    override fun setOrientation(orientation: Orientation) {
        this.orientation = orientation
        val horizontal = orientation == Orientation.HORIZONTAL
        bridge.horizontalOrientation = horizontal
        flowLayout.scrollDirection = if (horizontal) {
            UICollectionViewScrollDirection.UICollectionViewScrollDirectionHorizontal
        } else {
            UICollectionViewScrollDirection.UICollectionViewScrollDirectionVertical
        }
        flowLayout.invalidateLayout()
        collectionView.reloadData()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItems = count.coerceAtLeast(0)
        this.itemFactory = itemFactory
        bridge.itemCount = totalItems
        bridge.itemFactory = { index ->
            viewportPool.bindExplicitIndex(index, itemFactory)
        }
        viewportPool.clearAll { _, _ -> }
        collectionView.reloadData()
        val viewportPx = readCurrentViewportExtent()
        recomputeViewportWindow(scrollOffsetPx = 0f, viewportExtentPx = viewportPx)
    }

    override fun notifyItemRangeChanged(startIndex: Int, count: Int) {
        if (count <= 0 || totalItems <= 0) return
        val factory = itemFactory ?: return
        val end = (startIndex + count).coerceAtMost(totalItems)
        val indexPaths = mutableListOf<NSIndexPath>()
        for (idx in startIndex.coerceAtLeast(0) until end) {
            viewportPool.recycleExplicitIndex(idx)
            viewportPool.bindExplicitIndex(idx, factory)
            indexPaths += NSIndexPath.indexPathForRow(idx.toLong(), inSection = 0L)
        }
        if (indexPaths.isNotEmpty()) {
            collectionView.reloadItemsAtIndexPaths(indexPaths)
        }
    }

    override fun scrollToIndex(index: Int) {
        if (totalItems <= 0) return
        val clamped = index.coerceIn(0, totalItems - 1)
        val path = NSIndexPath.indexPathForRow(clamped.toLong(), inSection = 0L)
        collectionView.scrollToItemAtIndexPath(
            path,
            atScrollPosition = UICollectionViewScrollPositionNone,
            animated = true
        )
        val offsetPx = clamped * estimatedItemExtentPx
        recomputeViewportWindow(offsetPx, readCurrentViewportExtent())
    }

    override fun setEstimatedItemExtent(extentPx: Float, overscanItems: Int) {
        estimatedItemExtentPx = extentPx.coerceAtLeast(1f)
        bridge.estimatedItemExtentPt = estimatedItemExtentPx.toDouble()
        viewportPool.overscanItems = overscanItems.coerceAtLeast(0)
        flowLayout.invalidateLayout()
    }

    override fun updateViewportMetrics(
        scrollOffsetPx: Float,
        viewportExtentPx: Float,
        firstActiveIndex: Int,
        lastActiveIndex: Int,
        leadingSpacerPx: Float,
        trailingSpacerPx: Float,
        totalContentExtentPx: Float
    ) {
        recomputeViewportWindow(scrollOffsetPx, viewportExtentPx)
    }

    override fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)?) {
        viewportListener = listener
    }

    private fun recomputeViewportWindow(scrollOffsetPx: Float, viewportExtentPx: Float) {
        val factory = itemFactory ?: return
        val range = viewportPool.computeVisibleRange(
            scrollOffsetPx = scrollOffsetPx,
            viewportSizePx = viewportExtentPx,
            itemExtentPx = estimatedItemExtentPx,
            totalItemCount = totalItems,
            columns = 1
        )
        viewportPool.updateViewportWindow(
            visibleRange = range,
            itemFactory = factory,
            onMountItem = { _, _ -> },
            onUnmountItem = { _, impl ->
                (impl.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
            }
        )
    }

    private fun readCurrentViewportExtent(): Float = collectionView.bounds.useContents {
        val v = if (orientation == Orientation.HORIZONTAL) size.width else size.height
        v.toFloat().takeIf { it > 0f } ?: 480f
    }
}
