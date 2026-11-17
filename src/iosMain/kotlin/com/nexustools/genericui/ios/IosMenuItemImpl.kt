@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.nativeimpl.shell.MenuItemNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIMenuElement
import platform.UIKit.UIMenuElementState
import platform.UIKit.UIView

/**
 * Native UIKit `UIAction` / `UIMenuElement` item wrapper implementing [MenuItemNativeImpl].
 */
class IosMenuItemImpl(
    val checkable: Boolean = false,
    val radio: Boolean = false
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(UIView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0))),
    componentClassPrefix = "menu-item"
), MenuItemNativeImpl<UikitViewHandle> {

    private var labelText: String = ""
    private var iconName: String? = null
    private var shortcutValue: KeyShortcut? = null
    private var checkedState: Boolean = false
    private var clickListener: (() -> Unit)? = null

    override fun setLabel(label: String) {
        labelText = label
    }

    override fun getLabel(): String = labelText

    override fun setIcon(icon: String?) {
        iconName = icon
    }

    override fun getIcon(): String? = iconName

    override fun setShortcut(shortcut: KeyShortcut?) {
        shortcutValue = shortcut
    }

    override fun getShortcut(): KeyShortcut? = shortcutValue

    override fun setChecked(checked: Boolean) {
        checkedState = checked
    }

    override fun isChecked(): Boolean = checkedState

    override fun setOnClick(listener: (() -> Unit)?) {
        super.setOnClick(listener)
        clickListener = listener
    }

    fun triggerClick() {
        if (checkable) {
            checkedState = !checkedState
        } else if (radio) {
            checkedState = true
        }
        clickListener?.invoke()
    }

    fun toUiMenuElement(indexId: String = labelText): UIMenuElement {
        val action = UIAction.actionWithTitle(
            title = labelText.ifEmpty { "Item" },
            image = UikitGeometryAndColorUtil.systemIconImage(iconName),
            identifier = indexId,
            handler = { _ -> triggerClick() }
        )
        if (checkable || radio) {
            action.state = if (checkedState) {
                UIMenuElementState.UIMenuElementStateOn
            } else {
                UIMenuElementState.UIMenuElementStateOff
            }
        }
        shortcutValue?.let { sc ->
            action.discoverabilityTitle = sc.displayText ?: sc.toString()
        }
        return action
    }
}
