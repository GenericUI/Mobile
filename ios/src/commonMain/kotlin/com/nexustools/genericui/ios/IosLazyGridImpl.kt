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
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedRowHeightPx: Float = 120f

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        recomputeVisibleViewport()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        this.itemFactory = itemFactory
        viewportPool.clearAll { _, impl -> removeChildNative(impl) }
        recomputeVisibleViewport()
    }

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val row = clamped / columnsCount
        nativeWidget.contentOffsetY = row * estimatedRowHeightPx
        recomputeVisibleViewport()
    }

    fun recomputeVisibleViewport() {
        val factory = itemFactory ?: return
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = nativeWidget.contentOffsetY,
            viewportSizePx = nativeWidget.frame.height.toFloat().coerceAtLeast(estimatedRowHeightPx),
            itemExtentPx = estimatedRowHeightPx,
            totalItemCount = totalItemCount,
            columns = columnsCount
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
