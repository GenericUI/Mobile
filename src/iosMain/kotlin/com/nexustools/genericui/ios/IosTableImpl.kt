@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.content.TableNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIScrollView
import platform.UIKit.UIView
import platform.UIKit.labelColor
import platform.UIKit.secondarySystemBackgroundColor
import platform.UIKit.separatorColor
import platform.UIKit.tertiarySystemBackgroundColor

/**
 * Native UIKit `UIScrollView` multi-column table implementation of [TableNativeImpl],
 * arranging header and data cells cleanly with real `UIView` rows and `UILabel`s.
 */
class IosTableImpl(
    val scrollView: UIScrollView = UIScrollView(frame = CGRectMake(0.0, 0.0, 360.0, 240.0))
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(scrollView),
    componentClassPrefix = "table"
), TableNativeImpl<UikitViewHandle> {

    private var headers: List<String> = emptyList()
    private var rows: List<List<String>> = emptyList()
    private var striped: Boolean = false
    private var hoverable: Boolean = false
    private var columnWeights: FloatArray = FloatArray(0)
    private val renderedRows = mutableListOf<UIView>()

    override fun setHeaders(headers: List<String>) {
        this.headers = headers
        rebuildTableViews()
    }

    override fun setRows(rows: List<List<String>>) {
        this.rows = rows
        rebuildTableViews()
    }

    override fun setStriped(striped: Boolean) {
        this.striped = striped
        rebuildTableViews()
    }

    override fun setHoverable(hoverable: Boolean) {
        this.hoverable = hoverable
    }

    override fun setColumnWeights(weights: FloatArray) {
        this.columnWeights = weights
        rebuildTableViews()
    }

    fun getColumnWeights(): FloatArray = columnWeights.copyOf()

    private fun rebuildTableViews() {
        for (v in renderedRows) {
            v.removeFromSuperview()
        }
        renderedRows.clear()

        val totalWidth = scrollView.bounds.useContents { size.width }.takeIf { it > 0.0 } ?: 360.0
        val colCount = maxOf(headers.size, rows.maxOfOrNull { it.size } ?: 0, 1)
        val colWidths = resolveColumnWidths(totalWidth, colCount)
        var yCursor = 0.0
        val rowHeight = 36.0

        if (headers.isNotEmpty()) {
            val headerRow = buildRowView(
                cells = headers,
                colWidths = colWidths,
                y = yCursor,
                width = totalWidth,
                height = rowHeight,
                isHeader = true,
                rowIndex = -1
            )
            scrollView.addSubview(headerRow)
            renderedRows += headerRow
            yCursor += rowHeight
        }

        for ((rowIdx, row) in rows.withIndex()) {
            val rowView = buildRowView(
                cells = row,
                colWidths = colWidths,
                y = yCursor,
                width = totalWidth,
                height = rowHeight,
                isHeader = false,
                rowIndex = rowIdx
            )
            scrollView.addSubview(rowView)
            renderedRows += rowView
            yCursor += rowHeight
        }
        scrollView.setContentSize(CGSizeMake(totalWidth, yCursor))
    }

    private fun resolveColumnWidths(totalWidth: Double, colCount: Int): List<Double> {
        if (columnWeights.size >= colCount) {
            val sum = columnWeights.take(colCount).sum().toDouble().coerceAtLeast(0.01)
            return columnWeights.take(colCount).map { (it / sum) * totalWidth }
        }
        return List(colCount) { totalWidth / colCount }
    }

    private fun buildRowView(
        cells: List<String>,
        colWidths: List<Double>,
        y: Double,
        width: Double,
        height: Double,
        isHeader: Boolean,
        rowIndex: Int
    ): UIView {
        val rowView = UIView(frame = CGRectMake(0.0, y, width, height)).apply {
            backgroundColor = when {
                isHeader -> UIColor.secondarySystemBackgroundColor
                striped && rowIndex % 2 == 1 -> UIColor.tertiarySystemBackgroundColor
                else -> UIColor.clearColor
            }
        }
        var xCursor = 0.0
        for (i in colWidths.indices) {
            val colW = colWidths[i]
            val cellLabel = UILabel(frame = CGRectMake(xCursor + 8.0, 0.0, (colW - 16.0).coerceAtLeast(16.0), height)).apply {
                text = cells.getOrNull(i) ?: ""
                font = if (isHeader) UIFont.boldSystemFontOfSize(13.0) else UIFont.systemFontOfSize(13.0)
                textColor = UIColor.labelColor
            }
            rowView.addSubview(cellLabel)
            xCursor += colW
        }
        val sep = UIView(frame = CGRectMake(0.0, height - 0.5, width, 0.5)).apply {
            backgroundColor = UIColor.separatorColor
        }
        rowView.addSubview(sep)
        return rowView
    }
}
