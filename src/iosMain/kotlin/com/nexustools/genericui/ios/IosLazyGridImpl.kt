@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSIndexPath
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewFlowLayout
import platform.UIKit.UICollectionViewScrollPositionNone
import platform.UIKit.indexPathForRow
import kotlin.math.max

/**
 * Virtualized UIKit `UICollectionView` multi-column grid driven by [MobileLazyViewportPool].
 */
class IosLazyGridImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UICollectionView(
            frame = CGRectMake(0.0, 0.0, 360.0, 480.0),
            collectionViewLayout = UICollectionViewFlowLayout().apply {
                minimumLineSpacing = 4.0
                minimumInteritemSpacing = 4.0
            }
        )
    ),
    componentClassPrefix = "lazy-grid"
), LazyGridNativeImpl<UikitViewHandle> {

    val collectionView: UICollectionView get() = uiView as UICollectionView
    private val flowLayout: UICollectionViewFlowLayout
        get() = collectionView.collectionViewLayout as UICollectionViewFlowLayout
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool(overscanItems = 6)
    private val bridge = UikitCollectionViewBridge()

    private var columns: Int = 2
    private var minColumnWidthPx: Int = 0
    private var totalItems: Int = 0
    private var estimatedRowHeightPx: Float = 120f
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    private var viewportListener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)? = null

    init {
        bridge.columnCount = columns
        bridge.estimatedItemExtentPt = estimatedRowHeightPx.toDouble()
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

    override fun setColumns(columns: Int) {
        this.columns = columns.coerceAtLeast(1)
        syncEffectiveColumns()
    }

    override fun setMinColumnWidthPx(minWidthPx: Int) {
        this.minColumnWidthPx = minWidthPx.coerceAtLeast(0)
        syncEffectiveColumns()
    }

    fun getMinColumnWidthPx(): Int = minColumnWidthPx

    fun resolveEffectiveColumns(availableWidthPx: Int): Int {
        if (minColumnWidthPx > 0 && availableWidthPx > 0) {
            return max(1, availableWidthPx / minColumnWidthPx).coerceAtMost(columns.coerceAtLeast(1))
        }
        return columns.coerceAtLeast(1)
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
        recomputeViewportWindow(0f, readViewportHeight())
    }

    override fun scrollToIndex(index: Int) {
        if (totalItems <= 0) return
        val clamped = index.coerceIn(0, totalItems - 1)
        collectionView.scrollToItemAtIndexPath(
            NSIndexPath.indexPathForRow(clamped.toLong(), inSection = 0L),
            atScrollPosition = UICollectionViewScrollPositionNone,
            animated = true
        )
        val effCols = effectiveColumnCount()
        val rowIdx = clamped / effCols
        recomputeViewportWindow(rowIdx * estimatedRowHeightPx, readViewportHeight())
    }

    override fun setEstimatedRowExtent(rowHeightPx: Float, overscanRows: Int) {
        estimatedRowHeightPx = rowHeightPx.coerceAtLeast(1f)
        bridge.estimatedItemExtentPt = estimatedRowHeightPx.toDouble()
        viewportPool.overscanItems = overscanRows.coerceAtLeast(0) * effectiveColumnCount()
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

    private fun effectiveColumnCount(): Int {
        val w = collectionView.bounds.useContents { size.width }.toInt().takeIf { it > 0 } ?: 360
        return resolveEffectiveColumns(w)
    }

    private fun syncEffectiveColumns() {
        bridge.columnCount = effectiveColumnCount()
        flowLayout.invalidateLayout()
        collectionView.reloadData()
    }

    private fun recomputeViewportWindow(scrollOffsetPx: Float, viewportExtentPx: Float) {
        val factory = itemFactory ?: return
        val range = viewportPool.computeVisibleRange(
            scrollOffsetPx = scrollOffsetPx,
            viewportSizePx = viewportExtentPx,
            itemExtentPx = estimatedRowHeightPx,
            totalItemCount = totalItems,
            columns = effectiveColumnCount()
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

    private fun readViewportHeight(): Float =
        collectionView.bounds.useContents { size.height }.toFloat().takeIf { it > 0f } ?: 480f
}
