package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * Android `com.google.android.material.bottomnavigation.BottomNavigationView` implementation of [BottomNavigationBarNativeImpl].
 */
class AndroidBottomNavigationBarImpl : AndroidBaseNativeImpl("com.google.android.material.bottomnavigation.BottomNavigationView", "navbar fixed-bottom"), BottomNavigationBarNativeImpl<AndroidViewHandle> {
    private var selectedId: String? = null
    private var onItemSelectedListener: ((String) -> Unit)? = null

    internal var itemsList: List<Triple<String, String, String?>> = emptyList()
        private set

    internal var onBarChanged: (() -> Unit)? = null

    override fun setItems(items: List<Triple<String, String, String?>>) {
        itemsList = items.toList()
        nativeWidget.attributes["itemCount"] = items.size.toString()
        if (selectedId == null && items.isNotEmpty()) {
            selectedId = items.first().first
        }
        onBarChanged?.invoke()
    }

    override fun setSelectedItemId(itemId: String) {
        selectedId = itemId
        onBarChanged?.invoke()
        onItemSelectedListener?.invoke(itemId)
    }

    override fun getSelectedItemId(): String? = selectedId

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }

    internal fun dispatchItemSelectedFromView(itemId: String) {
        if (selectedId != itemId) {
            selectedId = itemId
            onItemSelectedListener?.invoke(itemId)
        }
    }
}
