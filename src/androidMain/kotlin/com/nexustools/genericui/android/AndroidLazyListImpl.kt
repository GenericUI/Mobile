package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import kotlin.math.roundToInt

/**
 * Android scrollable lazy list implementation of [LazyListNativeImpl] backed by
 * [MobileLazyViewportPool], wrapping an [AndroidFlexContainerView] + leading/trailing spacers
 * inside a native [ScrollView] / [HorizontalScrollView] with live scroll listener recycling.
 */
class AndroidLazyListImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-list"), LazyListNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()

    private var orientation: Orientation = Orientation.VERTICAL
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    private var onViewportScrollChangedListener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)? = null

    private var leadingSpacerView: View? = null
    private var trailingSpacerView: View? = null
    private var itemsHostView: AndroidFlexContainerView? = null
    private var isUpdatingFromMetrics: Boolean = false

    var estimatedItemExtentPx: Int = 48

    val firstVisibleIndex: Int
        get() = viewportPool.firstVisibleIndex

    val lastVisibleIndex: Int
        get() = viewportPool.lastVisibleIndex

    val mountedItemIndices: Set<Int>
        get() = viewportPool.mountedIndices

    override fun createDefaultAndroidView(context: Context): View? {
        val horizontal = orientation == Orientation.HORIZONTAL
        val leadingSpacer = View(context).also { leadingSpacerView = it }
        val trailingSpacer = View(context).also { trailingSpacerView = it }
        val itemsContainer = AndroidFlexContainerView(context).apply {
            flexDirection = if (horizontal) FlexDirection.ROW else FlexDirection.COLUMN
            alignItems = AlignItems.STRETCH
        }.also {
            itemsHostView = it
            nativeWidget.childHostViewGroup = it
        }

        val linearWrapper = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            addView(leadingSpacer, spacerLayoutParams(horizontal, 0))
            addView(
                itemsContainer,
                LinearLayout.LayoutParams(
                    if (horizontal) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                    if (horizontal) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(trailingSpacer, spacerLayoutParams(horizontal, 0))
        }

        val scroller: ViewGroup = if (horizontal) {
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = true
                isFillViewport = true
                setOnScrollChangeListener { v, scrollX, _, _, _ ->
                    onNativeScrolled(scrollX, v.width)
                }
                setTag(GenericUiFragmentActivity.TAG_VIEW_SCROLL_CALLBACK) {
                    onNativeScrolled(this.scrollX, this.width)
                }
            }
        } else {
            ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                isFillViewport = true
                setOnScrollChangeListener { v, _, scrollY, _, _ ->
                    onNativeScrolled(scrollY, v.height)
                }
                setTag(GenericUiFragmentActivity.TAG_VIEW_SCROLL_CALLBACK) {
                    onNativeScrolled(this.scrollY, this.height)
                }
            }
        }
        scroller.addOnLayoutChangeListener { v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val w = right - left
            val h = bottom - top
            val oldW = oldRight - oldLeft
            val oldH = oldBottom - oldTop
            if (w != oldW || h != oldH) {
                val density = v.resources.displayMetrics.density.coerceAtLeast(1f)
                val dpW = (w / density).roundToInt()
                val dpH = (h / density).roundToInt()
                nativeWidget.bounds = nativeWidget.bounds.copy(width = dpW, height = dpH)
                recomputeVisibleWindow()
                val rawScroll = if (orientation == Orientation.HORIZONTAL) v.scrollX else v.scrollY
                val rawExtent = if (orientation == Orientation.HORIZONTAL) w else h
                onViewportScrollChangedListener?.invoke(rawScroll / density, rawExtent / density)
            }
        }
        scroller.addView(
            linearWrapper,
            ViewGroup.LayoutParams(
                if (horizontal) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                if (horizontal) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scroller
    }

    private fun spacerLayoutParams(horizontal: Boolean, extentPx: Int): LinearLayout.LayoutParams =
        if (horizontal) {
            LinearLayout.LayoutParams(extentPx.coerceAtLeast(0), ViewGroup.LayoutParams.MATCH_PARENT)
        } else {
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, extentPx.coerceAtLeast(0))
        }

    private fun onNativeScrolled(rawScrollPx: Int, rawViewportPx: Int) {
        if (isUpdatingFromMetrics) return
        val density = runCatching {
            nativeWidget.androidView?.resources?.displayMetrics?.density?.takeIf { it > 0f }
        }.getOrNull() ?: 1f
        val logicalScroll = (rawScrollPx / density).coerceAtLeast(0f)
        val logicalExtent = (rawViewportPx / density).coerceAtLeast(estimatedItemExtentPx.toFloat())
        if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.scrollX = logicalScroll.roundToInt()
        } else {
            nativeWidget.scrollY = logicalScroll.roundToInt()
        }
        recomputeVisibleWindow()
        onViewportScrollChangedListener?.invoke(logicalScroll, logicalExtent)
    }

    override fun setOrientation(orientation: Orientation) {
        val changed = this.orientation != orientation
        this.orientation = orientation
        setFlexDirection(if (orientation == Orientation.HORIZONTAL) FlexDirection.ROW else FlexDirection.COLUMN)
        if (changed) {
            val ctx = nativeWidget.androidView?.context ?: AndroidViewHandle.activeContext
            if (ctx != null) {
                val rebuilt = createDefaultAndroidView(ctx)
                if (rebuilt != null) {
                    val parent = nativeWidget.androidView?.parent as? ViewGroup
                    val idx = parent?.indexOfChild(nativeWidget.androidView) ?: -1
                    val oldLp = nativeWidget.androidView?.layoutParams
                    parent?.removeView(nativeWidget.androidView)
                    nativeWidget.androidView = rebuilt
                    if (parent != null) {
                        if (idx >= 0) parent.addView(rebuilt, idx, oldLp) else parent.addView(rebuilt, oldLp)
                    }
                    ensureAndroidView(ctx)
                }
            }
        }
        recomputeVisibleWindow()
    }

    override fun setEstimatedItemExtent(extentPx: Float, overscanItems: Int) {
        estimatedItemExtentPx = extentPx.roundToInt().coerceAtLeast(1)
        viewportPool.overscanItems = overscanItems.coerceAtLeast(0)
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
        val horizontal = orientation == Orientation.HORIZONTAL
        if (horizontal) {
            nativeWidget.scrollX = scrollOffsetPx.roundToInt().coerceAtLeast(0)
        } else {
            nativeWidget.scrollY = scrollOffsetPx.roundToInt().coerceAtLeast(0)
        }
        AndroidViewHandle.runOnMainThread {
            runCatching {
                isUpdatingFromMetrics = true
                try {
                    val leadPx = AndroidViewHandle.dpToPxFloat(leadingSpacerPx).roundToInt().coerceAtLeast(0)
                    val trailPx = AndroidViewHandle.dpToPxFloat(trailingSpacerPx).roundToInt().coerceAtLeast(0)
                    leadingSpacerView?.let { v ->
                        v.layoutParams = spacerLayoutParams(horizontal, leadPx)
                        v.requestLayout()
                    }
                    trailingSpacerView?.let { v ->
                        v.layoutParams = spacerLayoutParams(horizontal, trailPx)
                        v.requestLayout()
                    }
                } finally {
                    isUpdatingFromMetrics = false
                }
            }
        }
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
            (nativeWidget.androidView as? HorizontalScrollView)?.post {
                runCatching {
                    isUpdatingFromMetrics = true
                    (nativeWidget.androidView as? HorizontalScrollView)?.scrollTo(AndroidViewHandle.dpToPx(targetOffset), 0)
                }.also { isUpdatingFromMetrics = false }
            }
        } else {
            nativeWidget.scrollY = targetOffset
            (nativeWidget.androidView as? ScrollView)?.post {
                runCatching {
                    isUpdatingFromMetrics = true
                    (nativeWidget.androidView as? ScrollView)?.scrollTo(0, AndroidViewHandle.dpToPx(targetOffset))
                }.also { isUpdatingFromMetrics = false }
            }
        }
        recomputeVisibleWindow()
        val extent = if (orientation == Orientation.HORIZONTAL) {
            nativeWidget.bounds.width.coerceAtLeast(estimatedItemExtentPx).toFloat()
        } else {
            nativeWidget.bounds.height.coerceAtLeast(estimatedItemExtentPx).toFloat()
        }
        onViewportScrollChangedListener?.invoke(targetOffset.toFloat(), extent)
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
                (impl as? AndroidBaseNativeImpl)?.ensureAndroidView()
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
        onViewportScrollChangedListener = null
        super.dispose()
    }
}
