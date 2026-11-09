package com.nexustools.genericui.android

import android.widget.TextView
import com.nexustools.genericui.component.tabs.TabIconPlacement
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
    var iconVal: String? = null
        private set
    var iconSizePxVal: Int = 20
        private set
    var iconPlacementVal: TabIconPlacement = TabIconPlacement.START
        private set
    var tintImageToThemeVal: Boolean = true
        private set
    var closableVal: Boolean = initialClosable
        private set
    var customHeaderImpl: NativeImpl<*>? = null
        private set

    internal var onTabUpdated: (() -> Unit)? = null
    private var onCloseClickedListener: (() -> Unit)? = null
    private var onTitleEditedListener: ((String) -> Unit)? = null

    init {
        nativeWidget.attributes["closable"] = initialClosable.toString()
        nativeWidget.attributes["iconSizePx"] = iconSizePxVal.toString()
        nativeWidget.attributes["iconPlacement"] = iconPlacementVal.name
        nativeWidget.attributes["tintImageToTheme"] = tintImageToThemeVal.toString()
    }

    override fun setTabIdNative(id: String) {
        tabIdVal = id
        nativeWidget.attributes["tabId"] = id
    }

    override fun setTitleNative(title: String) {
        titleVal = title
        nativeWidget.text = title
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconNative(icon: String?) {
        iconVal = icon
        if (icon != null) {
            nativeWidget.attributes["icon"] = icon
        } else {
            nativeWidget.attributes.remove("icon")
        }
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconSizePxNative(sizePx: Int) {
        iconSizePxVal = sizePx.coerceAtLeast(1)
        nativeWidget.attributes["iconSizePx"] = iconSizePxVal.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconPlacementNative(placement: TabIconPlacement) {
        iconPlacementVal = placement
        nativeWidget.attributes["iconPlacement"] = placement.name
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setTintImageToThemeNative(tintToTheme: Boolean) {
        tintImageToThemeVal = tintToTheme
        nativeWidget.attributes["tintImageToTheme"] = tintToTheme.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setTooltipNative(tooltip: String?) {
        nativeWidget.tooltip = tooltip
    }

    override fun setClosableNative(closable: Boolean) {
        closableVal = closable
        nativeWidget.attributes["closable"] = closable.toString()
        onTabUpdated?.invoke()
    }

    override fun setDirtyNative(dirty: Boolean) {
        nativeWidget.attributes["dirty"] = dirty.toString()
        onTabUpdated?.invoke()
    }

    override fun setPinnedNative(pinned: Boolean) {
        nativeWidget.attributes["pinned"] = pinned.toString()
        onTabUpdated?.invoke()
    }

    override fun setEditableNative(editable: Boolean) {
        nativeWidget.attributes["editable"] = editable.toString()
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        if (badgeText != null) {
            nativeWidget.attributes["badgeText"] = badgeText
            nativeWidget.attributes["badgeVariant"] = variantCss ?: "primary"
        } else {
            nativeWidget.attributes.remove("badgeText")
            nativeWidget.attributes.remove("badgeVariant")
        }
        onTabUpdated?.invoke()
    }

    override fun setSelectedNative(selected: Boolean) {
        nativeWidget.attributes["selected"] = selected.toString()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        customHeaderImpl?.let { removeChildNative(it) }
        customHeaderImpl = customImpl
        if (customImpl != null) {
            addChildNative(customImpl)
            nativeWidget.attributes["hasCustomView"] = "true"
        } else {
            nativeWidget.attributes["hasCustomView"] = "false"
        }
        onTabUpdated?.invoke()
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseClickedListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        val view = nativeWidget.androidView as? TextView ?: return
        AndroidViewHandle.runOnMainThread {
            runCatching {
                view.text = titleVal
                val icon = iconVal
                val ctx = view.context ?: AndroidViewHandle.activeContext
                if (!icon.isNullOrBlank() && ctx != null) {
                    val isDark = AndroidViewHandle.isDarkTheme()
                    val tintArgb = if (tintImageToThemeVal) {
                        if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                    } else {
                        null
                    }
                    val drawable = AndroidImageImpl.resolveDrawable(ctx, icon, tintArgb)
                    if (drawable != null) {
                        val px = AndroidViewHandle.dpToPx(iconSizePxVal).coerceAtLeast(1)
                        drawable.setBounds(0, 0, px, px)
                        if (iconPlacementVal == TabIconPlacement.TOP) {
                            view.setCompoundDrawables(null, drawable, null, null)
                        } else {
                            view.setCompoundDrawables(drawable, null, null, null)
                        }
                        view.compoundDrawablePadding = AndroidViewHandle.dpToPx(6)
                    } else {
                        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                    }
                } else {
                    view.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
                }
            }
        }
    }
}
