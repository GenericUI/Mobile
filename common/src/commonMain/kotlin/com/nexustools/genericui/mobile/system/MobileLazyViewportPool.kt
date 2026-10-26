package com.nexustools.genericui.mobile.system

import com.nexustools.genericui.core.NativeImpl
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Memory-pressure-aware lazy viewport window and item recycling manager shared by `:android` and `:ios`
 * for `LazyList`, `LazyGrid`, and `TreeView`.
 *
 * Ensures that only items inside the visible viewport window (plus a small overscan buffer) are mounted/loaded,
 * while items scrolling out of view are immediately detached and recycled or disposed.
 */
class MobileLazyViewportPool(
    var overscanItems: Int = 2,
    var maxRecycledScrapCount: Int = 16
) {
    private val activeMountedItems = linkedMapOf<Int, NativeImpl<*>>()
    private val scrapPool = ArrayDeque<NativeImpl<*>>()

    var firstVisibleIndex: Int = 0
        private set

    var lastVisibleIndex: Int = -1
        private set

    val mountedIndices: Set<Int>
        get() = activeMountedItems.keys.toSet()

    val mountedCount: Int
        get() = activeMountedItems.size

    val scrapCount: Int
        get() = scrapPool.size

    fun computeVisibleRange(
        scrollOffsetPx: Float,
        viewportSizePx: Float,
        itemExtentPx: Float,
        totalItemCount: Int,
        columns: Int = 1
    ): IntRange {
        if (totalItemCount <= 0 || viewportSizePx <= 0f) {
            firstVisibleIndex = 0
            lastVisibleIndex = -1
            return IntRange.EMPTY
        }
        val safeExtent = max(1f, itemExtentPx)
        val safeCols = max(1, columns)
        val firstRow = max(0, floor(scrollOffsetPx / safeExtent).toInt() - overscanItems)
        val lastRow = ceil((scrollOffsetPx + viewportSizePx) / safeExtent).toInt() + overscanItems
        val startIdx = (firstRow * safeCols).coerceIn(0, totalItemCount - 1)
        val endIdx = min(totalItemCount - 1, ((lastRow + 1) * safeCols) - 1)
        firstVisibleIndex = startIdx
        lastVisibleIndex = endIdx
        return if (startIdx <= endIdx) startIdx..endIdx else IntRange.EMPTY
    }

    /**
     * Synchronizes mounted items for [visibleRange], unloading any items outside the range and loading
     * newly visible items via [itemFactory].
     */
    fun updateViewportWindow(
        visibleRange: IntRange,
        itemFactory: (index: Int) -> NativeImpl<*>,
        onMountItem: (index: Int, impl: NativeImpl<*>) -> Unit,
        onUnmountItem: (index: Int, impl: NativeImpl<*>) -> Unit
    ) {
        val toRemove = activeMountedItems.keys.filter { it !in visibleRange }
        for (idx in toRemove) {
            val removed = activeMountedItems.remove(idx) ?: continue
            onUnmountItem(idx, removed)
            recycleOrDispose(removed)
        }

        for (idx in visibleRange) {
            if (!activeMountedItems.containsKey(idx)) {
                val created = itemFactory(idx)
                activeMountedItems[idx] = created
                onMountItem(idx, created)
            }
        }
    }

    fun getMountedItem(index: Int): NativeImpl<*>? = activeMountedItems[index]

    fun bindExplicitIndex(index: Int, itemFactory: (index: Int) -> NativeImpl<*>): NativeImpl<*> {
        val existing = activeMountedItems[index]
        if (existing != null) return existing
        val created = itemFactory(index)
        activeMountedItems[index] = created
        return created
    }

    fun recycleExplicitIndex(index: Int, onUnmountItem: ((NativeImpl<*>) -> Unit)? = null) {
        val removed = activeMountedItems.remove(index) ?: return
        onUnmountItem?.invoke(removed)
        recycleOrDispose(removed)
    }

    private fun recycleOrDispose(impl: NativeImpl<*>) {
        if (scrapPool.size < maxRecycledScrapCount) {
            scrapPool.addLast(impl)
        } else {
            impl.dispose()
        }
    }

    fun onLowMemoryTrim(trimLevel: Int = 80) {
        while (scrapPool.isNotEmpty()) {
            scrapPool.removeFirst().dispose()
        }
        if (trimLevel >= 60) {
            overscanItems = 1
            maxRecycledScrapCount = 4
        }
    }

    fun clearAll(onUnmountItem: ((Int, NativeImpl<*>) -> Unit)? = null) {
        for ((idx, impl) in activeMountedItems) {
            onUnmountItem?.invoke(idx, impl)
            impl.dispose()
        }
        activeMountedItems.clear()
        while (scrapPool.isNotEmpty()) {
            scrapPool.removeFirst().dispose()
        }
        firstVisibleIndex = 0
        lastVisibleIndex = -1
    }
}
