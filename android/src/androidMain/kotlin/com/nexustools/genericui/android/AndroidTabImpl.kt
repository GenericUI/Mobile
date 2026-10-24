package com.nexustools.genericui.android

import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl

/**
 * Android Material `TabLayout.Tab` implementation of [TabNativeImpl], translating `Tab`, `CloseableTab`,
 * `BadgeTab`, `PinnedTab`, `EditableTab`, and custom `AbstractComponent` tab headers into native Android views.
 */
class AndroidTabImpl(
    initialClosable: Boolean = false
) : AndroidBaseContainerNativeImpl("com.google.android.material.tabs.TabLayout.TabView", "nav-item"), TabNativeImpl<AndroidViewHandle> {

    var tabIdVal: String = ""
        private set
    var titleVal: String = ""
        private set
    var closableVal: Boolean = initialClosable
        private set
    var customHeaderImpl: NativeImpl<*>? = null
        private set

    private var onCloseClickedListener: (() -> Unit)? = null
    private var onTitleEditedListener: ((String) -> Unit)? = null

    init {
        nativeWidget.metadata["closable"] = initialClosable.toString()
    }

    override fun setTabIdNative(id: String) {
        tabIdVal = id
        nativeWidget.metadata["tabId"] = id
    }

    override fun setTitleNative(title: String) {
        titleVal = title
        nativeWidget.text = title
    }

    override fun setIconNative(icon: String?) {
        if (icon != null) nativeWidget.metadata["icon"] = icon else nativeWidget.metadata.remove("icon")
    }

    override fun setTooltipNative(tooltip: String?) {
        nativeWidget.tooltip = tooltip
    }

    override fun setClosableNative(closable: Boolean) {
        closableVal = closable
        nativeWidget.metadata["closable"] = closable.toString()
    }

    override fun setDirtyNative(dirty: Boolean) {
        nativeWidget.metadata["dirty"] = dirty.toString()
    }

    override fun setPinnedNative(pinned: Boolean) {
        nativeWidget.metadata["pinned"] = pinned.toString()
    }

    override fun setEditableNative(editable: Boolean) {
        nativeWidget.metadata["editable"] = editable.toString()
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        if (badgeText != null) {
            nativeWidget.metadata["badgeText"] = badgeText
            nativeWidget.metadata["badgeVariant"] = variantCss ?: "primary"
        } else {
            nativeWidget.metadata.remove("badgeText")
            nativeWidget.metadata.remove("badgeVariant")
        }
    }

    override fun setSelectedNative(selected: Boolean) {
        nativeWidget.metadata["selected"] = selected.toString()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        customHeaderImpl?.let { removeChildNative(it) }
        customHeaderImpl = customImpl
        if (customImpl != null) {
            addChildNative(customImpl)
            nativeWidget.metadata["hasCustomView"] = "true"
        } else {
            nativeWidget.metadata["hasCustomView"] = "false"
        }
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseClickedListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }
}
