package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * iOS `UICollectionView` multi-column table implementation of [TableNativeImpl].
 */
class IosTableImpl : IosBaseContainerNativeImpl("UICollectionView", "table"), TableNativeImpl<IosNativeView> {
    override fun setHeaders(headers: List<String>) {
        nativeWidget.metadata["headers"] = headers.joinToString("|")
    }

    override fun setRows(rows: List<List<String>>) {
        nativeWidget.metadata["rowCount"] = rows.size.toString()
    }

    override fun setStriped(striped: Boolean) {}
    override fun setHoverable(hoverable: Boolean) {}
}
