package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl

/**
 * Android `androidx.recyclerview.widget.RecyclerView` (`GridLayoutManager`) implementation of [LazyGridNativeImpl]
 * backed by [MobileLazyViewportPool] for true lazy cell loading/unloading.
 */
class AndroidLazyGridImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-grid"), LazyGridNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()

    private var columnsCount: Int = 3
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedRowHeightPx: Int = 120

    val firstVisibleIndex: Int
        get() = viewportPool.firstVisibleIndex

    val lastVisibleIndex: Int
        get() = viewportPool.lastVisibleIndex

    val mountedItemIndices: Set<Int>
        get() = viewportPool.mountedIndices

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
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

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val targetRow = clamped / columnsCount
        nativeWidget.scrollY = targetRow * estimatedRowHeightPx
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
        val viewportHeight = nativeWidget.bounds.height.coerceAtLeast(estimatedRowHeightPx).toFloat()
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = nativeWidget.scrollY.coerceAtLeast(0).toFloat(),
            viewportSizePx = viewportHeight,
            itemExtentPx = estimatedRowHeightPx.toFloat(),
            totalItemCount = totalItemCount,
            columns = columnsCount
        )
        viewportPool.updateViewportWindow(
            visibleRange = visibleRange,
            itemFactory = factory,
            onMountItem = { idx, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.let { handle ->
                    handle.attributes["data-lazy-grid-index"] = idx.toString()
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
