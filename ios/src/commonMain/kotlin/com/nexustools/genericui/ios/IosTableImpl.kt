package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * iOS `UICollectionView` multi-column table implementation of [TableNativeImpl].
 */
class IosTableImpl : IosBaseContainerNativeImpl("UICollectionView", "table"), TableNativeImpl<IosNativeView> {
    private var columnWeights: FloatArray = FloatArray(0)

    override fun setHeaders(headers: List<String>) {
        nativeWidget.metadata["headers"] = headers.joinToString("|")
    }

    override fun setRows(rows: List<List<String>>) {
        nativeWidget.metadata["rowCount"] = rows.size.toString()
    }

    override fun setStriped(striped: Boolean) {
        nativeWidget.metadata["striped"] = striped.toString()
    }

    override fun setHoverable(hoverable: Boolean) {
        nativeWidget.metadata["hoverable"] = hoverable.toString()
    }

    override fun setColumnWeights(weights: FloatArray) {
        columnWeights = weights.copyOf()
        nativeWidget.metadata["columnWeights"] = columnWeights.joinToString(",")
    }

    fun getColumnWeights(): FloatArray = columnWeights.copyOf()
}
