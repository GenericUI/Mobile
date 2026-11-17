package com.nexustools.genericui.android

import android.graphics.Typeface
import android.util.TypedValue
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatTextView
import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * Android table container implementation of [TableNativeImpl].
 */
class AndroidTableImpl : AndroidBaseContainerNativeImpl("android.widget.TableLayout", "table"), TableNativeImpl<AndroidViewHandle> {
    private var headersList: List<String> = emptyList()
    private var rowsList: List<List<String>> = emptyList()
    private var columnWeights: FloatArray = FloatArray(0)

    override fun setHeaders(headers: List<String>) {
        headersList = headers.toList()
        nativeWidget.attributes["headers"] = headers.joinToString("|")
        rebuildTableViews()
    }

    override fun setRows(rows: List<List<String>>) {
        rowsList = rows.map { it.toList() }
        nativeWidget.attributes["rowCount"] = rows.size.toString()
        rebuildTableViews()
    }

    override fun setStriped(striped: Boolean) {
        if (striped) nativeWidget.classes.add("table-striped") else nativeWidget.classes.remove("table-striped")
        rebuildTableViews()
    }

    override fun setHoverable(hoverable: Boolean) {
        if (hoverable) nativeWidget.classes.add("table-hover") else nativeWidget.classes.remove("table-hover")
    }

    override fun setColumnWeights(weights: FloatArray) {
        columnWeights = weights.copyOf()
        nativeWidget.attributes["columnWeights"] = columnWeights.joinToString(",")
        rebuildTableViews()
    }

    fun getColumnWeights(): FloatArray = columnWeights.copyOf()

    private fun resolveWeightForColumn(colIdx: Int): Float =
        columnWeights.getOrNull(colIdx)?.takeIf { it > 0f } ?: 1f

    private fun rebuildTableViews() {
        if (childNativeImpls.isNotEmpty()) return
        runCatching {
            val container = nativeWidget.androidView as? ViewGroup ?: return@runCatching
            val ctx = container.context ?: return@runCatching
            container.removeAllViews()
            val isDark = AndroidViewHandle.isDarkTheme()
            val textArgb = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
            val headerBgArgb = if (isDark) 0xFF232834.toInt() else 0xFFE9ECEF.toInt()
            val stripeBgArgb = if (isDark) 0xFF1A1E26.toInt() else 0xFFF8F9FA.toInt()
            val padH = AndroidViewHandle.dpToPx(8)
            val padV = AndroidViewHandle.dpToPx(6)

            if (headersList.isNotEmpty()) {
                val hRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setBackgroundColor(headerBgArgb)
                }
                headersList.forEachIndexed { colIdx, h ->
                    val tv = AppCompatTextView(ctx).apply {
                        text = h
                        setTypeface(null, Typeface.BOLD)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(textArgb)
                        setPadding(padH, padV, padH, padV)
                    }
                    hRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, resolveWeightForColumn(colIdx)))
                }
                container.addView(hRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }

            val striped = nativeWidget.classes.contains("table-striped")
            rowsList.forEachIndexed { rIdx, rowCells ->
                val dRow = LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    if (striped && rIdx % 2 == 1) {
                        setBackgroundColor(stripeBgArgb)
                    }
                }
                rowCells.forEachIndexed { colIdx, cell ->
                    val tv = AppCompatTextView(ctx).apply {
                        text = cell
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTextColor(textArgb)
                        setPadding(padH, padV, padH, padV)
                    }
                    dRow.addView(tv, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, resolveWeightForColumn(colIdx)))
                }
                container.addView(dRow, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
        }
    }
}
