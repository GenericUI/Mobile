package com.nexustools.genericui.ios

import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl

/**
 * iOS `UICollectionView` outline true lazy tree view implementation ([TreeViewNativeImpl])
 * backed by [MobileLazyViewportPool].
 */
class IosTreeViewImpl(
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool()
) : IosBaseNativeImpl("UICollectionView", "tree-view"), TreeViewNativeImpl<IosNativeView> {

    private var rootNodes: List<TreeNode<*>> = emptyList()
    private val flattenedNodes = mutableListOf<Pair<TreeNode<*>, Int>>()
    private var selectedNodeId: String? = null
    private var onNodeSelectedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeDoubleClickedListener: ((TreeNode<*>) -> Unit)? = null
    private var onNodeContextMenuListener: ((TreeNode<*>, Float, Float) -> Unit)? = null
    var rowHeightPx: Float = 36f

    override fun setRootNodes(roots: List<TreeNode<*>>) {
        rootNodes = roots
        rebuildFlattenedNodes()
        viewportPool.clearAll { _, impl -> (impl.nativeWidget as? IosNativeView)?.removeFromSuperview() }
        recomputeVisibleViewport()
    }

    override fun setSelectedNodeId(nodeId: String?) {
        selectedNodeId = nodeId
        val matched = flattenedNodes.firstOrNull { it.first.id == nodeId }?.first
        if (matched != null) {
            onNodeSelectedListener?.invoke(matched)
        }
    }

    override fun setOnNodeSelected(listener: ((TreeNode<*>) -> Unit)?) { onNodeSelectedListener = listener }
    override fun setOnNodeDoubleClicked(listener: ((TreeNode<*>) -> Unit)?) { onNodeDoubleClickedListener = listener }
    override fun setOnNodeContextMenu(listener: ((TreeNode<*>, Float, Float) -> Unit)?) { onNodeContextMenuListener = listener }

    private fun rebuildFlattenedNodes() {
        flattenedNodes.clear()
        fun walk(node: TreeNode<*>, depth: Int) {
            flattenedNodes.add(node to depth)
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

    fun recomputeVisibleViewport() {
        val visibleRange = viewportPool.computeVisibleRange(
            scrollOffsetPx = nativeWidget.contentOffsetY,
            viewportSizePx = nativeWidget.frame.height.toFloat().coerceAtLeast(rowHeightPx),
            itemExtentPx = rowHeightPx,
            totalItemCount = flattenedNodes.size,
            columns = 1
        )
        viewportPool.updateViewportWindow(
            visibleRange = visibleRange,
            itemFactory = { idx ->
                val (node, depth) = flattenedNodes[idx]
                IosTextImpl("span", "tree-row").apply {
                    setText("${"  ".repeat(depth)}${node.label}")
                }
            },
            onMountItem = { _, impl ->
                (impl.nativeWidget as? IosNativeView)?.let { nativeWidget.addSubview(it) }
            },
            onUnmountItem = { _, impl ->
                (impl.nativeWidget as? IosNativeView)?.removeFromSuperview()
            }
        )
    }

    override fun dispose() {
        viewportPool.clearAll()
        super.dispose()
    }
}
