package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl

/**
 * Android `androidx.recyclerview.widget.RecyclerView` (`LinearLayoutManager`) implementation of [LazyListNativeImpl]
 * backed by [MobileLazyViewportPool] for true lazy loading/unloading (`onBindViewHolder` / `onViewRecycled`).
 */
class AndroidLazyListImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-list"), LazyListNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()

    private var orientation: Orientation = Orientation.VERTICAL
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedItemExtentPx: Int = 48

    val firstVisibleIndex: Int
        get() = viewportPool.firstVisibleIndex

    val lastVisibleIndex: Int
        get() = viewportPool.lastVisibleIndex

    val mountedItemIndices: Set<Int>
        get() = viewportPool.mountedIndices

    override fun setOrientation(orientation: Orientation) {
        this.orientation = orientation
        recomputeVisibleWindow()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        totalItemCount = count.coerceAtLeast(0)
        this.itemFactory = itemFactory
        viewportPool.clearAll { _, impl ->
            (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
        }
        recomputeVisibleWindow()
    }

    override fun notifyItemRangeChanged(startIndex: Int, count: Int) {
        val endIndex = startIndex + count - 1
        for (idx in startIndex..endIndex) {
            viewportPool.recycleExplicitIndex(idx) { impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        }
        recomputeVisibleWindow()
    }

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val targetOffset = clamped * estimatedItemExtentPx
        if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.scrollX = targetOffset
        } else {
            nativeWidget.scrollY = targetOffset
        }
        recomputeVisibleWindow()
    }

    fun recomputeVisibleWindow() {
        val factory = itemFactory
        if (factory == null || totalItemCount <= 0) {
            viewportPool.clearAll { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
            return
        }
        val scrollOffset = if (orientation == Orientation.HORIZONTAL) nativeWidget.scrollX else nativeWidget.scrollY
        val viewportExtent = if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.bounds.width.coerceAtLeast(estimatedItemExtentPx)
        } else {
            nativeWidget.bounds.height.coerceAtLeast(estimatedItemExtentPx)
        }
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = scrollOffset.coerceAtLeast(0).toFloat(),
            viewportSizePx = viewportExtent.toFloat(),
            itemExtentPx = estimatedItemExtentPx.toFloat(),
            totalItemCount = totalItemCount,
            columns = 1
        )
        viewportPool.updateViewportWindow(
            visibleRange = visibleRange,
            itemFactory = factory,
            onMountItem = { idx, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.let { handle ->
                    handle.attributes["data-lazy-index"] = idx.toString()
                    nativeWidget.addChild(handle)
                }
            },
            onUnmountItem = { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        )
    }

    override fun dispose() {
        viewportPool.clearAll { _, impl ->
            (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
        }
        super.dispose()
    }
}
