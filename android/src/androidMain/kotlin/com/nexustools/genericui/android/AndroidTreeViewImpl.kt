package com.nexustools.genericui.android

import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl

/**
 * Android `androidx.recyclerview.widget.RecyclerView` implementation of [TreeViewNativeImpl]
 * that flattens expanded [TreeNode] hierarchies and mounts/unmounts visible rows lazily via [MobileLazyViewportPool].
 */
class AndroidTreeViewImpl : AndroidBaseNativeImpl("androidx.recyclerview.widget.RecyclerView", "tree-view"), TreeViewNativeImpl<AndroidViewHandle> {

    private data class FlattenedTreeRow(
        val node: TreeNode<*>,
        val depth: Int
    )

    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()

    private var rootNodes: List<TreeNode<*>> = emptyList()
    private val flattenedVisibleRows = mutableListOf<FlattenedTreeRow>()
    private var selectedNodeId: String? = null

    private var onNodeSelectedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeDoubleClickedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeContextMenuListener: ((TreeNode<*>, Float, Float) -> Unit)? = null

    var rowHeightPx: Int = 36

    val firstVisibleIndex: Int
        get() = viewportPool.firstVisibleIndex

    val lastVisibleIndex: Int
        get() = viewportPool.lastVisibleIndex

    val mountedRowIndices: Set<Int>
        get() = viewportPool.mountedIndices

    override fun setRootNodes(roots: List<TreeNode<*>>) {
        rootNodes = roots
        rebuildFlattenedRows()
        viewportPool.clearAll { _, impl ->
            (impl.nativeWidget as? AndroidViewHandle)?.removeFromParent()
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
                    setText(entry.node.label)
                    nativeWidget.attributes["data-tree-node-id"] = entry.node.id
                    nativeWidget.attributes["data-tree-depth"] = entry.depth.toString()
                    if (entry.node.id == selectedNodeId) {
                        nativeWidget.classes.add("active")
                    }
                }
            },
            onMountItem = { _, impl ->
                (impl.nativeWidget as? AndroidViewHandle)?.let { handle ->
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
