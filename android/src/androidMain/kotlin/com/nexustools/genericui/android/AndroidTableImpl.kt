package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.content.TableNativeImpl

/**
 * Android `android.widget.TableLayout` implementation of [TableNativeImpl].
 */
class AndroidTableImpl : AndroidBaseContainerNativeImpl("android.widget.TableLayout", "table"), TableNativeImpl<AndroidViewHandle> {
    override fun setHeaders(headers: List<String>) {
        nativeWidget.metadata["headers"] = headers.joinToString("|")
    }

    override fun setRows(rows: List<List<String>>) {
        nativeWidget.metadata["rowCount"] = rows.size.toString()
    }

    override fun setStriped(striped: Boolean) {
        if (striped) nativeWidget.classes.add("table-striped") else nativeWidget.classes.remove("table-striped")
    }

    override fun setHoverable(hoverable: Boolean) {
        if (hoverable) nativeWidget.classes.add("table-hover") else nativeWidget.classes.remove("table-hover")
    }
}
