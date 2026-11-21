package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.enums.ui.Orientation
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.LazyListNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Android scrollable lazy list implementation of [LazyListNativeImpl] backed by
 * [MobileLazyViewportPool], wrapping an [AndroidFlexContainerView] + leading/trailing spacers
 * inside a native [ScrollView] / [HorizontalScrollView] with live scroll listener recycling.
 */
class AndroidLazyListImpl : AndroidBaseContainerNativeImpl("androidx.recyclerview.widget.RecyclerView", "lazy-list"), LazyListNativeImpl<AndroidViewHandle> {
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
    private val viewportLock = Any()
    private val pendingLogicViewportSync = AtomicBoolean(false)

    private var orientation: Orientation = Orientation.VERTICAL
    private var totalItemCount: Int = 0
    private var itemFactory: ((Int) -> NativeImpl<*>)? = null
    private var onViewportScrollChangedListener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)? = null
    @Volatile
    private var explicitViewportWindowProvided: Boolean = false
    @Volatile
    private var explicitFirstActiveIndex: Int = 0
    @Volatile
    private var explicitLastActiveIndex: Int = -1
    @Volatile
    private var lastLeadingSpacerPx: Float = 0f
    @Volatile
    private var lastTrailingSpacerPx: Float = 0f

    private var leadingSpacerView: View? = null
    private var trailingSpacerView: View? = null
    private var itemsHostView: AndroidFlexContainerView? = null
    private var isUpdatingFromMetrics: Boolean = false

    var estimatedItemExtentPx: Int = 48

    val firstVisibleIndex: Int
        get() = synchronized(viewportLock) {
            if (explicitViewportWindowProvided) explicitFirstActiveIndex else viewportPool.firstVisibleIndex
        }

    val lastVisibleIndex: Int
        get() = synchronized(viewportLock) {
            if (explicitViewportWindowProvided) explicitLastActiveIndex else viewportPool.lastVisibleIndex
        }

    val mountedItemIndices: Set<Int>
        get() = synchronized(viewportLock) {
            if (explicitViewportWindowProvided) {
                if (totalItemCount <= 0 || explicitLastActiveIndex < explicitFirstActiveIndex) {
                    emptySet()
                } else {
                    (explicitFirstActiveIndex.coerceAtLeast(0)..explicitLastActiveIndex.coerceAtMost(totalItemCount - 1)).toSet()
                }
            } else {
                viewportPool.mountedIndices
            }
        }

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

        val initialLeadPx = AndroidViewHandle.dpToPxFloat(lastLeadingSpacerPx).roundToInt().coerceAtLeast(0)
        val initialTrailPx = AndroidViewHandle.dpToPxFloat(lastTrailingSpacerPx).roundToInt().coerceAtLeast(0)
        val linearWrapper = LinearLayout(context).apply {
            orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            addView(leadingSpacer, spacerLayoutParams(horizontal, initialLeadPx))
            addView(
                itemsContainer,
                LinearLayout.LayoutParams(
                    if (horizontal) ViewGroup.LayoutParams.WRAP_CONTENT else ViewGroup.LayoutParams.MATCH_PARENT,
                    if (horizontal) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
            addView(trailingSpacer, spacerLayoutParams(horizontal, initialTrailPx))
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
                val rawScroll = if (orientation == Orientation.HORIZONTAL) v.scrollX else v.scrollY
                val rawExtent = if (orientation == Orientation.HORIZONTAL) w else h
                dispatchViewportSync(rawScroll / density, rawExtent / density)
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

    private fun dispatchViewportSync(logicalScroll: Float, logicalExtent: Float) {
        if (GenericUI.activeFactory != null &&
            GenericUiThreading.isSplitThreadingEnabled &&
            !GenericUiThreading.isOnLogicThread()
        ) {
            if (pendingLogicViewportSync.compareAndSet(false, true)) {
                GenericUiThreading.runOnLogic {
                    pendingLogicViewportSync.set(false)
                    val latestScroll = if (orientation == Orientation.HORIZONTAL) {
                        nativeWidget.scrollX.coerceAtLeast(0).toFloat()
                    } else {
                        nativeWidget.scrollY.coerceAtLeast(0).toFloat()
                    }
                    val latestExtent = if (orientation == Orientation.HORIZONTAL) {
                        nativeWidget.bounds.width.coerceAtLeast(estimatedItemExtentPx).toFloat()
                    } else {
                        nativeWidget.bounds.height.coerceAtLeast(estimatedItemExtentPx).toFloat()
                    }
                    val listener = onViewportScrollChangedListener
                    if (listener != null && explicitViewportWindowProvided) {
                        listener(latestScroll, latestExtent)
                    } else {
                        recomputeVisibleWindow()
                        listener?.invoke(latestScroll, latestExtent)
                    }
                }
            }
        } else {
            val listener = onViewportScrollChangedListener
            if (listener != null && explicitViewportWindowProvided) {
                listener(logicalScroll, logicalExtent)
            } else {
                recomputeVisibleWindow()
                listener?.invoke(logicalScroll, logicalExtent)
            }
        }
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
        dispatchViewportSync(logicalScroll, logicalExtent)
    }

    override fun setOrientation(orientation: Orientation) {
        val changed = this.orientation != orientation
        this.orientation = orientation
        setFlexDirection(if (orientation == Orientation.HORIZONTAL) FlexDirection.ROW else FlexDirection.COLUMN)
        if (changed) {
            val ctx = nativeWidget.androidView?.context ?: AndroidViewHandle.activeContext
            if (ctx != null) {
                AndroidViewHandle.runOnMainThread {
                    runCatching {
                        if (nativeWidget.disposed) return@runCatching
                        if (nativeWidget.androidView == null) {
                            ensureOwnAndroidView(ctx)
                            return@runCatching
                        }
                        val rebuilt = createDefaultAndroidView(ctx)
                        if (rebuilt != null) {
                            val oldView = nativeWidget.androidView
                            val parent = oldView?.parent as? ViewGroup
                            val idx = parent?.indexOfChild(oldView) ?: -1
                            val oldLp = oldView?.layoutParams
                            parent?.removeView(oldView)
                            nativeWidget.androidView = rebuilt
                            if (parent != null) {
                                if (idx >= 0) parent.addView(rebuilt, idx, oldLp) else parent.addView(rebuilt, oldLp)
                            }
                            ensureAndroidView(ctx)
                        }
                    }
                }
            }
        }
        recomputeVisibleWindow()
    }

    override fun setEstimatedItemExtent(extentPx: Float, overscanItems: Int) {
        estimatedItemExtentPx = extentPx.roundToInt().coerceAtLeast(1)
        synchronized(viewportLock) {
            viewportPool.overscanItems = overscanItems.coerceAtLeast(0)
        }
    }

    override fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportExtentPx: Float) -> Unit)?) {
        onViewportScrollChangedListener = listener
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        super.removeChildNative(childImpl)
        syncAndSweepItemsHostOnMainThread()
    }

    override fun clearChildrenNative() {
        super.clearChildrenNative()
        syncAndSweepItemsHostOnMainThread()
    }

    private fun syncAndSweepItemsHostOnMainThread() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                if (nativeWidget.disposed) return@runCatching
                val host = itemsHostView ?: return@runCatching
                val liveChildren = snapshotChildNativeImpls()
                val liveHandles = nativeWidget.children
                val useExternalList = explicitViewportWindowProvided || liveChildren.isNotEmpty()
                val orderedLiveViews = ArrayList<Pair<View, AndroidViewHandle>>()
                if (useExternalList) {
                    val handleSet = liveHandles.toHashSet()
                    for (childImpl in liveChildren) {
                        val childHandle = childImpl.nativeWidget as? AndroidViewHandle ?: continue
                        val childView = childHandle.androidView ?: continue
                        if (!childHandle.disposed &&
                            childHandle.parentHandle === nativeWidget &&
                            childHandle in handleSet
                        ) {
                            orderedLiveViews.add(childView to childHandle)
                        }
                    }
                } else {
                    for (childHandle in liveHandles) {
                        val childView = childHandle.androidView ?: continue
                        if (!childHandle.disposed && childHandle.parentHandle === nativeWidget) {
                            orderedLiveViews.add(childView to childHandle)
                        }
                    }
                }
                val liveViewSet = HashSet<View>(orderedLiveViews.size)
                for ((v, _) in orderedLiveViews) {
                    liveViewSet.add(v)
                }
                for (i in host.childCount - 1 downTo 0) {
                    val existing = host.getChildAt(i)
                    if (existing != null && existing !in liveViewSet) {
                        host.removeViewAt(i)
                    }
                }
                for ((targetIndex, pair) in orderedLiveViews.withIndex()) {
                    val (childView, childHandle) = pair
                    val lp = AndroidFlexContainerView.buildLayoutParamsFor(childHandle)
                    val currentIndex = if (childView.parent === host) host.indexOfChild(childView) else -1
                    if (currentIndex != targetIndex) {
                        (childView.parent as? ViewGroup)?.removeView(childView)
                        host.addView(childView, targetIndex.coerceIn(0, host.childCount), lp)
                    } else {
                        childView.layoutParams = lp
                    }
                }
                host.requestLayout()
                host.invalidate()
            }
        }
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
        synchronized(viewportLock) {
            explicitViewportWindowProvided = true
            explicitFirstActiveIndex = firstActiveIndex
            explicitLastActiveIndex = lastActiveIndex
            lastLeadingSpacerPx = leadingSpacerPx
            lastTrailingSpacerPx = trailingSpacerPx
        }
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
        syncAndSweepItemsHostOnMainThread()
    }

    override fun setItemCount(count: Int, itemFactory: (index: Int) -> NativeImpl<*>) {
        synchronized(viewportLock) {
            totalItemCount = count.coerceAtLeast(0)
            this.itemFactory = itemFactory
            if (!explicitViewportWindowProvided) {
                val externallyManaged = snapshotChildNativeImpls().toHashSet()
                viewportPool.clearAll { _, impl ->
                    if (impl !in externallyManaged) {
                        (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                    }
                }
            }
        }
        recomputeVisibleWindow()
    }

    override fun notifyItemRangeChanged(startIndex: Int, count: Int) {
        synchronized(viewportLock) {
            if (!explicitViewportWindowProvided) {
                val externallyManaged = snapshotChildNativeImpls().toHashSet()
                val endIndex = startIndex + count - 1
                for (idx in startIndex..endIndex) {
                    viewportPool.recycleExplicitIndex(idx) { impl ->
                        if (impl !in externallyManaged) {
                            (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                        }
                    }
                }
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
        synchronized(viewportLock) {
            val factory = itemFactory
            if (explicitViewportWindowProvided) {
                if (totalItemCount > 0) {
                    val liveChildren = snapshotChildNativeImpls()
                    val ctx = AndroidViewHandle.activeContext
                    for (childImpl in liveChildren) {
                        (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(ctx)
                    }
                }
                syncAndSweepItemsHostOnMainThread()
                return
            }
            if (factory == null || totalItemCount <= 0) {
                val externallyManaged = snapshotChildNativeImpls().toHashSet()
                viewportPool.clearAll { _, impl ->
                    if (impl !in externallyManaged) {
                        (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                    }
                }
                syncAndSweepItemsHostOnMainThread()
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
        syncAndSweepItemsHostOnMainThread()
    }

    override fun dispose() {
        synchronized(viewportLock) {
            val externallyManaged = snapshotChildNativeImpls().toHashSet()
            viewportPool.clearAll { _, impl ->
                if (impl !in externallyManaged) {
                    (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                }
            }
        }
        onViewportScrollChangedListener = null
        super.dispose()
    }
}
