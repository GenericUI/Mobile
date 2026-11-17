@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIMenu
import platform.UIKit.UIView

/**
 * Native UIKit dropdown menu button backed by `UIButton` with `showsMenuAsPrimaryAction = true`
 * and a live `UIMenu` / `UIAction` hierarchy.
 */
class IosDropdownImpl(
    val button: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 140.0, 44.0))
    }
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(button),
    componentClassPrefix = "dropdown"
), DropdownNativeImpl<UikitViewHandle> {

    private var labelText: String = ""
    private var itemsList: List<Pair<String, String>> = emptyList()
    private var selectionListener: ((String) -> Unit)? = null

    init {
        button.showsMenuAsPrimaryAction = true
        rebuildMenu()
    }

    override fun contentContainerView(): UIView = button

    override fun setLabel(label: String) {
        labelText = label
        button.setTitle(label, forState = UIControlStateNormal)
        rebuildMenu()
    }

    override fun setItems(items: List<Pair<String, String>>) {
        itemsList = items
        rebuildMenu()
    }

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        selectionListener = listener
        rebuildMenu()
    }

    private fun rebuildMenu() {
        val actions = itemsList.map { (id, title) ->
            UIAction.actionWithTitle(
                title = title,
                image = null,
                identifier = id,
                handler = { _ -> selectionListener?.invoke(id) }
            )
        }
        button.menu = UIMenu.menuWithTitle(title = labelText, children = actions)
    }
}
