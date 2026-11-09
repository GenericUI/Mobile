package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl

/**
 * iOS `UICollectionViewCompositionalLayout` true lazy virtualized grid implementation ([LazyGridNativeImpl])
 * backed by [MobileLazyViewportPool].
 */
class IosLazyGridImpl(
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
) : IosBaseContainerNativeImpl("UICollectionView", "lazy-grid"), LazyGridNativeImpl<IosNativeView> {

    private var columnsCount: Int = 3
    private var minColumnWidthPx: Int = 0
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedRowHeightPx: Float = 120f

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        nativeWidget.metadata["columns"] = columnsCount.toString()
        recomputeVisibleViewport()
    }

    override fun setMinColumnWidthPx(minWidthPx: Int) {
        minColumnWidthPx = minWidthPx.coerceAtLeast(0)
        nativeWidget.metadata["minColumnWidthPx"] = minColumnWidthPx.toString()
        recomputeVisibleViewport()
    }

    fun getMinColumnWidthPx(): Int = minColumnWidthPx

    fun resolveEffectiveColumns(availableWidthPx: Int = nativeWidget.frame.width): Int {
        if (minColumnWidthPx <= 0 || availableWidthPx <= 0) {
            return columnsCount.coerceAtLeast(1)
        }
        val fitCols = (availableWidthPx / minColumnWidthPx).coerceAtLeast(1)
        return minOf(columnsCount.coerceAtLeast(1), fitCols)
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        this.itemFactory = itemFactory
        viewportPool.clearAll { _, impl -> removeChildNative(impl) }
        recomputeVisibleViewport()
    }

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val effectiveCols = resolveEffectiveColumns()
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val row = clamped / effectiveCols
        nativeWidget.contentOffsetY = row * estimatedRowHeightPx
        recomputeVisibleViewport()
    }

    fun recomputeVisibleViewport() {
        val factory = itemFactory ?: return
        val effectiveCols = resolveEffectiveColumns()
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = nativeWidget.contentOffsetY,
            viewportSizePx = nativeWidget.frame.height.toFloat().coerceAtLeast(estimatedRowHeightPx),
            itemExtentPx = estimatedRowHeightPx,
            totalItemCount = totalItemCount,
            columns = effectiveCols
        )
        viewportPool.updateViewportWindow(
            visibleRange = visibleRange,
            itemFactory = factory,
            onMountItem = { _, impl -> addChildNative(impl) },
            onUnmountItem = { _, impl -> removeChildNative(impl) }
        )
    }

    override fun dispose() {
        viewportPool.clearAll { _, impl -> removeChildNative(impl) }
        super.dispose()
    }
}
