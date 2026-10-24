package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.navigation.BottomNavigationBarNativeImpl

/**
 * iOS `UITabBar` bottom navigation bar implementation of [BottomNavigationBarNativeImpl].
 */
class IosBottomNavigationBarImpl : IosBaseNativeImpl("UITabBar", "navbar-bottom"), BottomNavigationBarNativeImpl<IosNativeView> {
    private var itemsList: List<Triple<String, String, String?>> = emptyList()
    private var selectedId: String? = null
    private var onItemSelectedListener: ((String) -> Unit)? = null

    override fun setItems(items: List<Triple<String, String, String?>>) {
        itemsList = items
        if (selectedId == null && items.isNotEmpty()) {
            selectedId = items.first().first
        }
    }

    override fun setSelectedItemId(itemId: String) {
        selectedId = itemId
        onItemSelectedListener?.invoke(itemId)
    }

    override fun getSelectedItemId(): String? = selectedId

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }
}
