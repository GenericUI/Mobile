package com.nexustools.genericui.ios

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl

/**
 * iOS `UICollectionView` true lazy virtualized list implementation ([LazyListNativeImpl])
 * backed by [MobileLazyViewportPool] so items are dynamically loaded only inside the visible
 * viewport window and unloaded/recycled immediately when scrolling out of view.
 */
class IosLazyListImpl(
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
) : IosBaseContainerNativeImpl("UICollectionView", "lazy-list"), LazyListNativeImpl<IosNativeView> {

    private var orientation: Orientation = Orientation.VERTICAL
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedItemExtentPx: Float = 48f

    override fun setOrientation(orientation: Orientation) {
        this.orientation = orientation
        recomputeVisibleViewport()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        this.itemFactory = itemFactory
        viewportPool.clearAll { _, impl -> removeChildNative(impl) }
        recomputeVisibleViewport()
    }

    override fun notifyItemRangeChanged(startIndex: Int, count: Int) {
        for (idx in startIndex until (startIndex + count)) {
            viewportPool.recycleExplicitIndex(idx) { impl -> removeChildNative(impl) }
        }
        recomputeVisibleViewport()
    }

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val offset = clamped * estimatedItemExtentPx
        if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.contentOffsetX = offset
        } else {
            nativeWidget.contentOffsetY = offset
        }
        recomputeVisibleViewport()
    }

    fun onScrollOffsetChanged(offsetX: Float, offsetY: Float) {
        nativeWidget.contentOffsetX = offsetX
        nativeWidget.contentOffsetY = offsetY
        recomputeVisibleViewport()
    }

    fun recomputeVisibleViewport() {
        val factory = itemFactory ?: return
        val scrollOffset = if (orientation == Orientation.HORIZONTAL) nativeWidget.contentOffsetX else nativeWidget.contentOffsetY
        val viewportExtent = if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.frame.width.toFloat()
        } else {
            nativeWidget.frame.height.toFloat()
        }.coerceAtLeast(estimatedItemExtentPx)

        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = scrollOffset,
            viewportSizePx = viewportExtent,
            itemExtentPx = estimatedItemExtentPx,
            totalItemCount = totalItemCount,
            columns = 1
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
