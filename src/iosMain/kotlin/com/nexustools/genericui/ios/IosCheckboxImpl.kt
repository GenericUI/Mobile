@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.form.CheckboxNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlEventValueChanged
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIImage
import platform.UIKit.UILabel
import platform.UIKit.UISwitch

/**
 * Native UIKit checkbox / switch / radio implementation of [CheckboxNativeImpl] wrapping a
 * genuine `UISwitch` (when `switchStyle = true`) or SF-Symbol-backed `UIButton` + `UILabel`.
 */
class IosCheckboxImpl(
    val switchStyle: Boolean = false,
    val radioStyle: Boolean = false
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 200.0, 36.0)).apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            itemGapPt = 8.0
        }
    ),
    componentClassPrefix = if (radioStyle) "radio" else if (switchStyle) "switch" else "checkbox"
), CheckboxNativeImpl<UikitViewHandle> {

    private val switchControl: UISwitch? = if (switchStyle) UISwitch(frame = CGRectMake(0.0, 0.0, 51.0, 31.0)) else null
    private val toggleButton: UIButton? = if (!switchStyle) UIButton.buttonWithType(UIButtonTypeSystem) else null
    private val defaultToggleTintColor = toggleButton?.tintColor
    private val titleLabel: UILabel = UILabel(frame = CGRectMake(0.0, 0.0, 140.0, 24.0))

    private var labelText: String = ""
    private var checkedState: Boolean = false
    private var listener: ((Boolean) -> Unit)? = null

    init {
        val host = uiView as UikitFlexContainerView
        if (switchControl != null) {
            host.addSubview(switchControl)
            host.setChildPreferredSize(switchControl, 51.0, 31.0)
            host.addSubview(titleLabel)
            switchControl.addTarget(
                target = actionTarget,
                action = UikitControlActionTarget.SEL_VALUE_CHANGED,
                forControlEvents = UIControlEventValueChanged
            )
            actionTarget.onValueChanged = {
                val next = switchControl.on
                if (next != checkedState) {
                    checkedState = next
                    listener?.invoke(next)
                }
            }
        } else if (toggleButton != null) {
            toggleButton.setFrame(CGRectMake(0.0, 0.0, 200.0, 36.0))
            host.addSubview(toggleButton)
            toggleButton.addTarget(
                target = actionTarget,
                action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
                forControlEvents = UIControlEventTouchUpInside
            )
            actionTarget.onPrimaryAction = {
                setChecked(!checkedState)
            }
        }
        refreshVisualState()
    }

    override fun setLabel(label: String) {
        labelText = label
        titleLabel.text = label
        toggleButton?.setTitle(if (label.isEmpty()) null else " $label", forState = UIControlStateNormal)
    }

    override fun getLabel(): String = labelText

    override fun setChecked(checked: Boolean) {
        if (checkedState == checked) {
            refreshVisualState()
            return
        }
        checkedState = checked
        refreshVisualState()
        listener?.invoke(checked)
    }

    override fun isChecked(): Boolean = checkedState

    override fun resolveFocusTargetUiView(): platform.UIKit.UIView =
        switchControl ?: toggleButton ?: uiView

    override fun setFocusable(focusable: Boolean) {
        switchControl?.let { UikitFocusRegistry.setFocusable(it, focusable) }
        toggleButton?.let { UikitFocusRegistry.setFocusable(it, focusable) }
        super<IosBaseNativeImpl>.setFocusable(focusable)
    }

    override fun setOnCheckedChanged(listener: ((Boolean) -> Unit)?) {
        this.listener = listener
    }

    override fun onBootstrapAppearanceChanged() {
        switchControl?.onTintColor = null
        defaultToggleTintColor?.let { toggleButton?.tintColor = it }
    }

    private fun refreshVisualState() {
        switchControl?.setOn(checkedState, animated = false)
        toggleButton?.let { btn ->
            btn.selected = checkedState
            val symbolName = when {
                radioStyle && checkedState -> "largecircle.fill.circle"
                radioStyle -> "circle"
                checkedState -> "checkmark.square.fill"
                else -> "square"
            }
            btn.setImage(UIImage.systemImageNamed(symbolName), forState = UIControlStateNormal)
        }
    }
}
