@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIMenu
import platform.UIKit.UIView

/**
 * Native UIKit dropdown menu button backed by unmodified `UIButton(UIButtonTypeSystem)` with
 * `showsMenuAsPrimaryAction = true` and a live `UIMenu` / `UIAction` hierarchy.
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
    private var primaryActionListener: (() -> Unit)? = null
    private val defaultTintColor = button.tintColor
    private val defaultTitleColor = button.titleColorForState(UIControlStateNormal)

    init {
        button.showsMenuAsPrimaryAction = true
        button.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        rebuildMenu()
        applyDropdownAppearance()
    }

    override fun contentContainerView(): UIView = button

    override fun onBootstrapAppearanceChanged() {
        applyDropdownAppearance()
    }

    private fun applyDropdownAppearance() {
        button.configuration = null
        button.setBackgroundImage(null, forState = UIControlStateNormal)
        button.setTitleColor(defaultTitleColor ?: defaultTintColor, forState = UIControlStateNormal)
        button.tintColor = defaultTintColor
        button.backgroundColor = null
    }

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

    override fun setOnPrimaryAction(listener: (() -> Unit)?) {
        primaryActionListener = listener
        actionTarget.onPrimaryAction = listener
        button.showsMenuAsPrimaryAction = (listener == null)
        rebuildMenu()
    }

    fun triggerPrimaryAction(): Boolean {
        val listener = primaryActionListener ?: return false
        listener()
        return true
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
