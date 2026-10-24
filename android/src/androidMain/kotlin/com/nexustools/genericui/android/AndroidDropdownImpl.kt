package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Android `androidx.appcompat.widget.PopupMenu` implementation of [DropdownNativeImpl].
 */
class AndroidDropdownImpl : AndroidBaseContainerNativeImpl("com.google.android.material.button.MaterialButton", "dropdown"), DropdownNativeImpl<AndroidViewHandle> {
    private var onItemSelectedListener: ((String) -> Unit)? = null

    override fun setLabel(label: String) {
        nativeWidget.text = label
    }

    override fun setItems(items: List<Pair<String, String>>) {
        nativeWidget.metadata["itemCount"] = items.size.toString()
    }

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }
}
