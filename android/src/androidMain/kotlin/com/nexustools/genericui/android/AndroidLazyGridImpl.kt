package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl

/**
 * Android lazy grid container implementation of [LazyGridNativeImpl]
 * backed by [MobileLazyViewportPool] for true lazy cell loading/unloading.
 */
class AndroidLazyGridImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-grid"), LazyGridNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()

    private var columnsCount: Int = 3
    private var minColumnWidthPx: Int = 0
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    var estimatedRowHeightPx: Int = 120

    val firstVisibleIndex: Int
        get() = viewportPool.firstVisibleIndex

    val lastVisibleIndex: Int
        get() = viewportPool.lastVisibleIndex

    val mountedItemIndices: Set<Int>
        get() = viewportPool.mountedIndices

    init {
        setFlexDirection(FlexDirection.ROW)
        setFlexWrap(FlexWrap.WRAP)
    }

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        nativeWidget.attributes["columns"] = columnsCount.toString()
        recomputeVisibleWindow()
    }

    override fun setMinColumnWidthPx(minWidthPx: Int) {
        minColumnWidthPx = minWidthPx.coerceAtLeast(0)
        nativeWidget.attributes["minColumnWidthPx"] = minColumnWidthPx.toString()
        recomputeVisibleWindow()
    }

    fun getMinColumnWidthPx(): Int = minColumnWidthPx

    fun resolveEffectiveColumns(availableWidthPx: Int = nativeWidget.bounds.width): Int {
        if (minColumnWidthPx <= 0 || availableWidthPx <= 0) {
            return columnsCount.coerceAtLeast(1)
        }
        val fitCols = (availableWidthPx / minColumnWidthPx).coerceAtLeast(1)
        return minOf(columnsCount.coerceAtLeast(1), fitCols)
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
        val effectiveCols = resolveEffectiveColumns()
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val targetRow = clamped / effectiveCols
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
        val effectiveCols = resolveEffectiveColumns()
        val viewportHeight = nativeWidget.bounds.height.coerceAtLeast(estimatedRowHeightPx).toFloat()
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = nativeWidget.scrollY.coerceAtLeast(0).toFloat(),
            viewportSizePx = viewportHeight,
            itemExtentPx = estimatedRowHeightPx.toFloat(),
            totalItemCount = totalItemCount,
            columns = effectiveCols
        )
        viewportPool.updateViewportWindow(
            visibleRange = visibleRange,
            itemFactory = factory,
            onMountItem = { idx, impl ->
                (impl as? AndroidBaseNativeImpl)?.ensureAndroidView()
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
