package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * iOS `UIButton` (`showsMenuAsPrimaryAction = true`) implementation of [DropdownNativeImpl].
 */
class IosDropdownImpl : IosBaseContainerNativeImpl("UIButton", "dropdown"), DropdownNativeImpl<IosNativeView> {
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
