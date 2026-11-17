@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.lazy.TreeNode
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.mobile.system.MobileLazyViewportPool
import com.nexustools.genericui.nativeimpl.lazy.TreeViewNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UICollectionView
import platform.UIKit.UICollectionViewCell
import platform.UIKit.UICollectionViewFlowLayout
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.labelColor
import platform.UIKit.systemFillColor

/**
 * Virtualized UIKit hierarchical `UICollectionView` tree implementation of [TreeViewNativeImpl]
 * flattening expanded [TreeNode] items through [MobileLazyViewportPool] with native indent spacing,
 * SF Symbol disclosure chevrons, double-tap expansion/callback, and long-press context triggers.
 */
class IosTreeViewImpl : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UICollectionView(
            frame = CGRectMake(0.0, 0.0, 320.0, 400.0),
            collectionViewLayout = UICollectionViewFlowLayout().apply {
                minimumLineSpacing = 0.0
                minimumInteritemSpacing = 0.0
            }
        )
    ),
    componentClassPrefix = "tree-view"
), TreeViewNativeImpl<UikitViewHandle> {

    private data class FlattenedTreeRow(val node: TreeNode<*>, val depth: Int)

    val collectionView: UICollectionView get() = uiView as UICollectionView
    val viewportPool: MobileLazyViewportPool = MobileLazyViewportPool(overscanItems = 4)
    private val bridge = UikitCollectionViewBridge()

    private var rootNodes: List<TreeNode<*>> = emptyList()
    private val flattenedRows = mutableListOf<FlattenedTreeRow>()
    private var selectedNodeId: String? = null
    private var estimatedRowHeightPx: Float = 36f
    private var onNodeSelected: ((TreeNode<*>) -> Unit)? = null
    private var onNodeDoubleClicked: ((TreeNode<*>) -> Unit)? = null
    private var onNodeContextMenu: ((TreeNode<*>, Float, Float) -> Unit)? = null
    private var onViewportScrollChanged: ((Float, Float) -> Unit)? = null
    private val rowActionTargets = mutableListOf<UikitControlActionTarget>()

    init {
        bridge.estimatedItemExtentPt = estimatedRowHeightPx.toDouble()
        collectionView.registerClass(
            cellClass = UICollectionViewCell,
            forCellWithReuseIdentifier = UikitCollectionViewBridge.REUSE_IDENTIFIER
        )
        collectionView.dataSource = bridge
        collectionView.delegate = bridge
        bridge.itemFactory = { rowIndex -> createRowNativeImpl(rowIndex) }
        bridge.onViewportScrolled = { offsetPx, viewportPx ->
            recomputeViewport(offsetPx, viewportPx)
            onViewportScrollChanged?.invoke(offsetPx, viewportPx)
        }
    }

    override fun setRootNodes(roots: List<TreeNode<*>>) {
        rootNodes = roots
        rebuildFlattenedRows()
    }

    override fun setSelectedNodeId(nodeId: String?) {
        selectedNodeId = nodeId
        collectionView.reloadData()
    }

    override fun setOnNodeSelected(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeSelected = listener
    }

    override fun setOnNodeDoubleClicked(listener: ((TreeNode<*>) -> Unit)?) {
        onNodeDoubleClicked = listener
    }

    override fun setOnNodeContextMenu(listener: ((TreeNode<*>, Float, Float) -> Unit)?) {
        onNodeContextMenu = listener
    }

    override fun setEstimatedRowHeight(rowHeightPx: Float, overscanRows: Int) {
        estimatedRowHeightPx = rowHeightPx.coerceAtLeast(16f)
        bridge.estimatedItemExtentPt = estimatedRowHeightPx.toDouble()
        viewportPool.overscanItems = overscanRows.coerceAtLeast(0)
        collectionView.collectionViewLayout.invalidateLayout()
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
        recomputeViewport(scrollOffsetPx, viewportHeightPx)
    }

    override fun setOnViewportScrollChanged(listener: ((scrollOffsetPx: Float, viewportHeightPx: Float) -> Unit)?) {
        onViewportScrollChanged = listener
    }

    private fun rebuildFlattenedRows() {
        flattenedRows.clear()
        rowActionTargets.clear()
        fun append(node: TreeNode<*>, depth: Int) {
            flattenedRows += FlattenedTreeRow(node, depth)
            if (node.isExpanded) {
                node.ensureChildrenLoaded()
                for (child in node.children) {
                    append(child, depth + 1)
                }
            }
        }
        for (root in rootNodes) {
            append(root, 0)
        }
        bridge.itemCount = flattenedRows.size
        viewportPool.clearAll { _, _ -> }
        collectionView.reloadData()
        val vh = collectionView.bounds.useContents { size.height }.toFloat().takeIf { it > 0f } ?: 400f
        recomputeViewport(0f, vh)
    }

    private fun createRowNativeImpl(index: Int): NativeImpl<*> {
        val row = flattenedRows[index]
        val node = row.node
        val isSelected = node.id == selectedNodeId
        val indentPt = row.depth * 18.0
        val rowView = UIView(frame = CGRectMake(0.0, 0.0, 320.0, estimatedRowHeightPx.toDouble())).apply {
            backgroundColor = if (isSelected) UIColor.systemFillColor else UIColor.clearColor
        }
        val target = UikitControlActionTarget(rowView)
        rowActionTargets += target

        val hasChildren = node.children.isNotEmpty() || node.lazyChildrenLoader != null
        val chevronBtn = UIButton.buttonWithType(UIButtonTypeSystem).apply {
            setFrame(CGRectMake(indentPt + 4.0, 4.0, 24.0, estimatedRowHeightPx.toDouble() - 8.0))
            if (hasChildren) {
                val sym = if (node.isExpanded) "chevron.down" else "chevron.right"
                setImage(UIImage.systemImageNamed(sym), forState = UIControlStateNormal)
                addTarget(
                    target = target,
                    action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
                    forControlEvents = UIControlEventTouchUpInside
                )
            } else {
                enabled = false
                alpha = 0.0
            }
        }
        target.onPrimaryAction = {
            node.isExpanded = !node.isExpanded
            rebuildFlattenedRows()
        }
        rowView.addSubview(chevronBtn)

        val titleLabel = UILabel(
            frame = CGRectMake(indentPt + 32.0, 0.0, 260.0, estimatedRowHeightPx.toDouble())
        ).apply {
            text = node.label
            font = UIFont.systemFontOfSize(14.0)
            textColor = UIColor.labelColor
        }
        rowView.addSubview(titleLabel)

        target.onClick = {
            selectedNodeId = node.id
            onNodeSelected?.invoke(node)
            collectionView.reloadData()
        }
        target.onDoubleClick = {
            if (hasChildren) {
                node.isExpanded = !node.isExpanded
                rebuildFlattenedRows()
            }
            onNodeDoubleClicked?.invoke(node)
        }
        target.onContextMenu = { evt ->
            onNodeContextMenu?.invoke(node, evt.x, evt.y)
        }
        val tap = UITapGestureRecognizer(target = target, action = UikitControlActionTarget.SEL_SINGLE_TAP).apply {
            numberOfTapsRequired = 1uL
        }
        val dbl = UITapGestureRecognizer(target = target, action = UikitControlActionTarget.SEL_DOUBLE_TAP).apply {
            numberOfTapsRequired = 2uL
        }
        val press = UILongPressGestureRecognizer(target = target, action = UikitControlActionTarget.SEL_LONG_PRESS)
        rowView.addGestureRecognizer(tap)
        rowView.addGestureRecognizer(dbl)
        rowView.addGestureRecognizer(press)

        return IosContainerImpl(classPrefix = "tree-row").apply {
            nativeWidget.uiView.addSubview(rowView)
        }
    }

    private fun recomputeViewport(scrollOffsetPx: Float, viewportHeightPx: Float) {
        val range = viewportPool.computeVisibleRange(
            scrollOffsetPx = scrollOffsetPx,
            viewportSizePx = viewportHeightPx,
            itemExtentPx = estimatedRowHeightPx,
            totalItemCount = flattenedRows.size,
            columns = 1
        )
        viewportPool.updateViewportWindow(
            visibleRange = range,
            itemFactory = { idx -> createRowNativeImpl(idx) },
            onMountItem = { _, _ -> },
            onUnmountItem = { _, impl ->
                (impl.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
            }
        )
    }
}
