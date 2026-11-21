package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.bootstrap.FlexWrap
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyGridNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Android scrollable lazy grid implementation of [LazyGridNativeImpl] backed by
 * [MobileLazyViewportPool], wrapping an [AndroidFlexContainerView] (`FlexWrap.WRAP`) +
 * leading/trailing spacers inside a native [ScrollView] with live scroll listener recycling.
 */
class AndroidLazyGridImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-grid"), LazyGridNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
    private val viewportLock = Any()
    private val pendingLogicViewportSync = AtomicBoolean(false)

    private var columnsCount: Int = 3
    private var minColumnWidthPx: Int = 0
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    private var onViewportScrollChangedListener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)? = null

    private var leadingSpacerView: View? = null
    private var trailingSpacerView: View? = null
    private var gridHostView: AndroidFlexContainerView? = null
    private var isUpdatingFromMetrics: Boolean = false

    var estimatedRowHeightPx: Int = 120

    val firstVisibleIndex: Int
        get() = synchronized(viewportLock) { viewportPool.firstVisibleIndex }

    val lastVisibleIndex: Int
        get() = synchronized(viewportLock) { viewportPool.lastVisibleIndex }

    val mountedItemIndices: Set<Int>
        get() = synchronized(viewportLock) { viewportPool.mountedIndices }

    init {
        setFlexDirection(FlexDirection.ROW)
        setFlexWrap(FlexWrap.WRAP)
    }

    override fun createDefaultAndroidView(context: Context): View? {
        val leadingSpacer = View(context).also { leadingSpacerView = it }
        val trailingSpacer = View(context).also { trailingSpacerView = it }
        val gridContainer = AndroidFlexContainerView(context).apply {
            flexDirection = FlexDirection.ROW
            flexWrap = FlexWrap.WRAP
        }.also {
            gridHostView = it
            nativeWidget.childHostViewGroup = it
        }

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(leadingSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(
                gridContainer,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(trailingSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
        }

        return ScrollView(context).apply {
            isVerticalScrollBarEnabled = true
            isFillViewport = true
            setOnScrollChangeListener { v, _, scrollY, _, _ ->
                onNativeScrolled(scrollY, v.height)
            }
            setTag(GenericUiFragmentActivity.TAG_VIEW_SCROLL_CALLBACK) {
                onNativeScrolled(this.scrollY, this.height)
            }
            addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val w = right - left
                val h = bottom - top
                val oldW = oldRight - oldLeft
                val oldH = oldBottom - oldTop
                if (w != oldW || h != oldH) {
                    val density = v.resources.displayMetrics.density.coerceAtLeast(1f)
                    nativeWidget.bounds = nativeWidget.bounds.copy(
                        width = (w / density).roundToInt(),
                        height = (h / density).roundToInt()
                    )
                    dispatchViewportSync(v.scrollY / density, h / density)
                }
            }
            addView(
                wrapper,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun dispatchViewportSync(logicalScroll: Float, logicalExtent: Float) {
        if (GenericUI.activeFactory != null &&
            GenericUiThreading.isSplitThreadingEnabled &&
            !GenericUiThreading.isOnLogicThread()
        ) {
            if (pendingLogicViewportSync.compareAndSet(false, true)) {
                GenericUiThreading.runOnLogic {
                    pendingLogicViewportSync.set(false)
                    val latestScroll = nativeWidget.scrollY.coerceAtLeast(0).toFloat()
                    val latestExtent = nativeWidget.bounds.height.coerceAtLeast(estimatedRowHeightPx).toFloat()
                    recomputeVisibleWindow()
                    onViewportScrollChangedListener?.invoke(latestScroll, latestExtent)
                }
            }
        } else {
            recomputeVisibleWindow()
            onViewportScrollChangedListener?.invoke(logicalScroll, logicalExtent)
        }
    }

    private fun onNativeScrolled(rawScrollY: Int, rawViewportH: Int) {
        if (isUpdatingFromMetrics) return
        val density = runCatching {
            nativeWidget.androidView?.resources?.displayMetrics?.density?.takeIf { it > 0f }
        }.getOrNull() ?: 1f
        val logicalScroll = (rawScrollY / density).coerceAtLeast(0f)
        val logicalExtent = (rawViewportH / density).coerceAtLeast(estimatedRowHeightPx.toFloat())
        nativeWidget.scrollY = logicalScroll.roundToInt()
        dispatchViewportSync(logicalScroll, logicalExtent)
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

    override fun setEstimatedRowExtent(rowHeightPx: Float, overscanRows: Int) {
        estimatedRowHeightPx = rowHeightPx.roundToInt().coerceAtLeast(1)
        synchronized(viewportLock) {
            viewportPool.overscanItems = overscanRows.coerceAtLeast(0)
        }
    }

    override fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)?) {
        onViewportScrollChangedListener = listener
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
        nativeWidget.attributes["leadingSpacerPx"] = leadingSpacerPx.toString()
        nativeWidget.attributes["trailingSpacerPx"] = trailingSpacerPx.toString()
        nativeWidget.attributes["totalContentExtentPx"] = totalContentExtentPx.toString()
        nativeWidget.scrollY = scrollOffsetPx.roundToInt().coerceAtLeast(0)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                isUpdatingFromMetrics = true
                try {
                    val leadPx = AndroidViewHandle.dpToPxFloat(leadingSpacerPx).roundToInt().coerceAtLeast(0)
                    val trailPx = AndroidViewHandle.dpToPxFloat(trailingSpacerPx).roundToInt().coerceAtLeast(0)
                    leadingSpacerView?.let { v ->
                        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, leadPx)
                        v.requestLayout()
                    }
                    trailingSpacerView?.let { v ->
                        v.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, trailPx)
                        v.requestLayout()
                    }
                } finally {
                    isUpdatingFromMetrics = false
                }
            }
        }
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        synchronized(viewportLock) {
            totalItemCount = count.coerceAtLeast(0)
            this.itemFactory = itemFactory
            viewportPool.clearAll { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        }
        recomputeVisibleWindow()
    }

    override fun scrollToIndex(index: Int) {
        if (totalItemCount <= 0) return
        val effectiveCols = resolveEffectiveColumns()
        val clamped = index.coerceIn(0, totalItemCount - 1)
        val targetRow = clamped / effectiveCols
        val targetOffset = targetRow * estimatedRowHeightPx
        nativeWidget.scrollY = targetOffset
        (nativeWidget.androidView as? ScrollView)?.post {
            runCatching {
                isUpdatingFromMetrics = true
                (nativeWidget.androidView as? ScrollView)?.scrollTo(0, AndroidViewHandle.dpToPx(targetOffset))
            }.also { isUpdatingFromMetrics = false }
        }
        recomputeVisibleWindow()
        val extent = nativeWidget.bounds.height.coerceAtLeast(estimatedRowHeightPx).toFloat()
        onViewportScrollChangedListener?.invoke(targetOffset.toFloat(), extent)
    }

    fun recomputeVisibleWindow() {
        synchronized(viewportLock) {
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
    }

    override fun dispose() {
        synchronized(viewportLock) {
            viewportPool.clearAll { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        }
        onViewportScrollChangedListener = null
        super.dispose()
    }
}
