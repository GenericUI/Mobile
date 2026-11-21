package com.nexustools.genericui.android

import android.content.Context
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.core.GenericUI
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import com.nexustools.genericui.threading.GenericUiThreading
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

/**
 * Android tree view implementation of [TreeViewNativeImpl] flattening expanded [TreeNode] hierarchies,
 * lazily mounting/unmounting visible rows via [MobileLazyViewportPool] inside a scrollable container,
 * and wiring single-tap selection, double-tap, and long-press context menu gesture listeners on row views.
 */
class AndroidTreeViewImpl : AndroidBaseNativeImpl("androidx.recyclerview.widget.RecyclerView", "tree-view"), TreeViewNativeImpl<AndroidViewHandle> {

    private data class FlattenedTreeRow(
        val node: TreeNode<*>,
        val depth: Int
    )

    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
    private val viewportLock = Any()
    private val pendingLogicViewportSync = AtomicBoolean(false)

    private var rootNodes: List<TreeNode<*>> = emptyList()
    private val flattenedVisibleRows = mutableListOf<FlattenedTreeRow>()
    private var selectedNodeId: String? = null

    private var onNodeSelectedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeDoubleClickedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeContextMenuListener: ((TreeNode<*>, Float, Float) -> Unit)? = null
    private var onViewportScrollChangedListener: ((scrollOffsetPx: Float, viewportHeightPx: Float) -> Unit)? = null

    private var leadingSpacerView: View? = null
    private var trailingSpacerView: View? = null
    private var rowsContainerView: AndroidFlexContainerView? = null
    private var isUpdatingFromMetrics: Boolean = false

    var rowHeightPx: Int = 36

    val firstVisibleIndex: Int
        get() = synchronized(viewportLock) { viewportPool.firstVisibleIndex }

    val lastVisibleIndex: Int
        get() = synchronized(viewportLock) { viewportPool.lastVisibleIndex }

    val mountedRowIndices: Set<Int>
        get() = synchronized(viewportLock) { viewportPool.mountedIndices }

    override fun createDefaultAndroidView(context: Context): View {
        val leadingSpacer = View(context).also { leadingSpacerView = it }
        val trailingSpacer = View(context).also { trailingSpacerView = it }
        val rowsHost = AndroidFlexContainerView(context).apply {
            flexDirection = FlexDirection.COLUMN
            alignItems = AlignItems.STRETCH
        }.also {
            rowsContainerView = it
            nativeWidget.childHostViewGroup = it
        }

        val wrapper = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(leadingSpacer, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0))
            addView(
                rowsHost,
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
                    val latestExtent = nativeWidget.bounds.height.coerceAtLeast(rowHeightPx).toFloat()
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
        val logicalExtent = (rawViewportH / density).coerceAtLeast(rowHeightPx.toFloat())
        nativeWidget.scrollY = logicalScroll.roundToInt()
        dispatchViewportSync(logicalScroll, logicalExtent)
    }

    override fun setRootNodes(roots: List<TreeNode<*>>) {
        synchronized(viewportLock) {
            rootNodes = roots
            rebuildFlattenedRows()
            viewportPool.clearAll { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        }
        recomputeVisibleWindow()
    }

    override fun setSelectedNodeId(nodeId: String?) {
        selectedNodeId = nodeId
        val matched = flattenedVisibleRows.firstOrNull { it.node.id == nodeId }?.node
        if (matched != null) {
            onNodeSelectedListener?.invoke(matched)
        }
    }

    override fun setOnNodeSelected(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeSelectedListener = listener
    }

    override fun setOnNodeDoubleClicked(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeDoubleClickedListener = listener
    }

    override fun setOnNodeContextMenu(listener: ((TreeNode<*>, Float, Float) -> Unit)?) {
        onNodeContextMenuListener = listener
    }

    override fun setEstimatedRowHeight(rowHeightPx: Float, overscanRows: Int) {
        this.rowHeightPx = rowHeightPx.roundToInt().coerceAtLeast(1)
        synchronized(viewportLock) {
            viewportPool.overscanItems = overscanRows.coerceAtLeast(0)
        }
    }

    override fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportHeightPx: Float) -> Unit)?) {
        onViewportScrollChangedListener = listener
    }

    override fun updateViewportMetrics(
        scrollOffsetPx: Float,
        viewportHeightPx: Float,
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

    private fun rebuildFlattenedRows() {
        flattenedVisibleRows.clear()
        fun walk(node: TreeNode<*>, depth: Int) {
            flattenedVisibleRows.add(FlattenedTreeRow(node, depth))
            if (node.isExpanded) {
                node.ensureChildrenLoaded()
                for (child in node.children) {
                    walk(child, depth + 1)
                }
            }
        }
        for (root in rootNodes) {
            walk(root, 0)
        }
    }

    fun recomputeVisibleWindow() {
        synchronized(viewportLock) {
            val totalRows = flattenedVisibleRows.size
            if (totalRows <= 0) {
                viewportPool.clearAll { _, impl ->
                    (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                }
                return
            }
            val viewportHeight = nativeWidget.bounds.height.coerceAtLeast(rowHeightPx).toFloat()
            val visibleRange = viewportPool.computeVisibleRange(
                scrollOffsetPx = nativeWidget.scrollY.coerceAtLeast(0).toFloat(),
                viewportSizePx = viewportHeight,
                itemExtentPx = rowHeightPx.toFloat(),
                totalItemCount = totalRows,
                columns = 1
            )
            viewportPool.updateViewportWindow(
                visibleRange = visibleRange,
                itemFactory = { idx ->
                    val entry = flattenedVisibleRows[idx]
                    AndroidTextImpl("div", "tree-view-row").apply {
                        setText("  ".repeat(entry.depth) + entry.node.label)
                        nativeWidget.attributes["data-tree-node-id"] = entry.node.id
                        nativeWidget.attributes["data-tree-depth"] = entry.depth.toString()
                        if (entry.node.id == selectedNodeId) {
                            nativeWidget.classes.add("active")
                        }
                        setOnClick {
                            setSelectedNodeId(entry.node.id)
                        }
                        setOnDoubleClick {
                            onNodeDoubleClickedListener?.invoke(entry.node)
                        }
                        setOnContextMenu { ev ->
                            onNodeContextMenuListener?.invoke(entry.node, ev.x, ev.y)
                        }
                    }
                },
                onMountItem = { idx, impl ->
                    val rowView = (impl as? AndroidBaseNativeImpl)?.ensureAndroidView()
                    val entry = flattenedVisibleRows.getOrNull(idx)
                    if (rowView != null && entry != null) {
                        attachRowGestureHandlers(rowView, entry.node)
                    }
                    (impl.nativeWidget as? AndroidViewHandle)?.let { handle ->
                        nativeWidget.addChild(handle)
                    }
                },
                onUnmountItem = { _, impl ->
                    (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
                }
            )
        }
    }

    private fun attachRowGestureHandlers(rowView: View, node: TreeNode<*>) {
        runCatching {
            val ctx = rowView.context ?: return@runCatching
            var lastX = 0f
            var lastY = 0f
            val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    setSelectedNodeId(node.id)
                    return true
                }

                override fun onDoubleTap(e: MotionEvent): Boolean {
                    onNodeDoubleClickedListener?.invoke(node)
                    return true
                }

                override fun onLongPress(e: MotionEvent) {
                    onNodeContextMenuListener?.invoke(node, e.rawX, e.rawY)
                }
            })
            rowView.isClickable = true
            rowView.isLongClickable = true
            rowView.setOnTouchListener { _, event ->
                lastX = event.rawX
                lastY = event.rawY
                detector.onTouchEvent(event)
                false
            }
            rowView.setOnLongClickListener {
                onNodeContextMenuListener?.invoke(node, lastX, lastY)
                true
            }
        }
    }

    override fun dispose() {
        synchronized(viewportLock) {
            viewportPool.clearAll { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
            }
        }
        onNodeSelectedListener = null
        onNodeDoubleClickedListener = null
        onNodeContextMenuListener = null
        onViewportScrollChangedListener = null
        super.dispose()
    }
}
