package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Android `androidx.appcompat.widget.PopupMenu` implementation of [DropdownNativeImpl].
 */
class AndroidDropdownImpl : AndroidBaseContainerNativeImpl("com.google.android.material.button.MaterialButton", "dropdown"), DropdownNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""
    private val dropdownItems = mutableListOf<Pair<String, String>>()
    private var onItemSelectedListener: ((String) -> Unit)? = null

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
    }

    fun getLabel(): String = labelText

    override fun setItems(items: List<Pair<String, String>>) {
        dropdownItems.clear()
        dropdownItems.addAll(items)
        nativeWidget.metadata["itemCount"] = items.size.toString()
        nativeWidget.metadata["itemLabels"] = items.joinToString("|") { it.second }
    }

    fun getItems(): List<Pair<String, String>> = dropdownItems

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }

    fun selectItem(id: String): Boolean {
        val listener = onItemSelectedListener ?: return false
        listener(id)
        return true
    }

    fun selectItemAt(index: Int): Boolean {
        val entry = dropdownItems.getOrNull(index) ?: return false
        return selectItem(entry.first)
    }
}
