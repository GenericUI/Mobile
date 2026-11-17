@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIAction
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIMenu
import platform.UIKit.UIMenuElementState

/**
 * Native UIKit pull-down `UIButton` + `UIMenu` picker implementation of [SelectNativeImpl].
 */
class IosSelectImpl(
    val button: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 200.0, 44.0))
    }
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(button),
    componentClassPrefix = "select"
), SelectNativeImpl<UikitViewHandle> {

    private var options: List<Pair<String, String>> = emptyList()
    private var selectedValue: String? = null
    private var selectionListener: ((String) -> Unit)? = null

    init {
        button.showsMenuAsPrimaryAction = true
        rebuildSelectMenu()
    }

    override fun setOptions(options: List<Pair<String, String>>) {
        this.options = options
        if (selectedValue == null && options.isNotEmpty()) {
            selectedValue = options.first().first
        }
        rebuildSelectMenu()
    }

    override fun setSelectedValue(value: String?) {
        selectedValue = value
        rebuildSelectMenu()
    }

    override fun getSelectedValue(): String? = selectedValue

    override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {
        selectionListener = listener
    }

    private fun rebuildSelectMenu() {
        val currentTitle = options.firstOrNull { it.first == selectedValue }?.second
            ?: options.firstOrNull()?.second
            ?: "Select..."
        button.setTitle(currentTitle, forState = UIControlStateNormal)
        val actions = options.map { (optVal, optLabel) ->
            UIAction.actionWithTitle(
                title = optLabel,
                image = null,
                identifier = optVal,
                handler = { _ ->
                    selectedValue = optVal
                    rebuildSelectMenu()
                    selectionListener?.invoke(optVal)
                }
            ).apply {
                state = if (optVal == selectedValue) {
                    UIMenuElementState.UIMenuElementStateOn
                } else {
                    UIMenuElementState.UIMenuElementStateOff
                }
            }
        }
        button.menu = UIMenu.menuWithTitle(title = "", children = actions)
    }
}
