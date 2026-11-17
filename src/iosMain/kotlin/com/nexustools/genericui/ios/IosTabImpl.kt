@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UITextField
import platform.UIKit.labelColor
import platform.UIKit.secondarySystemBackgroundColor
import platform.UIKit.systemBlueColor
import platform.UIKit.systemGreenColor
import platform.UIKit.systemOrangeColor
import platform.UIKit.systemRedColor

/**
 * Native UIKit tab button chip implementation of [TabNativeImpl], hosting a title/icon `UIButton`,
 * badge `UILabel`, dirty indicator, inline rename `UITextField`, and optional close `UIButton`.
 */
class IosTabImpl(
    val closable: Boolean = false
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 128.0, 38.0)).apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            itemGapPt = 4.0
        }
    ),
    componentClassPrefix = "tab"
), TabNativeImpl<UikitViewHandle> {

    val selectButton: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 84.0, 34.0))
    }
    private val closeButton: UIButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 22.0, 22.0))
        setTitle("×", forState = UIControlStateNormal)
    }
    private val badgeLabel: UILabel = UILabel(frame = CGRectMake(0.0, 0.0, 28.0, 18.0)).apply {
        font = UIFont.boldSystemFontOfSize(11.0)
        textAlignment = platform.UIKit.NSTextAlignmentCenter
        layer.cornerRadius = 9.0
        clipsToBounds = true
        hidden = true
    }
    private val inlineEditor: UITextField = UITextField(frame = CGRectMake(0.0, 0.0, 84.0, 30.0)).apply {
        hidden = true
    }
    private val closeTarget = UikitControlActionTarget()
    private val editorDelegate = UikitTextInputDelegate()

    private var tabId: String = ""
    private var tabTitle: String = ""
    private var tabIcon: String? = null
    private var dirtyState: Boolean = false
    private var closableState: Boolean = closable
    private var pinnedState: Boolean = false
    private var editableState: Boolean = false
    private var selectedState: Boolean = false
    private var customHeaderImpl: NativeImpl<*>? = null
    private var onCloseListener: (() -> Unit)? = null
    private var onTitleEditedListener: ((String) -> Unit)? = null
    var onSelectTabRequested: (() -> Unit)? = null

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(selectButton)
        host.addSubview(inlineEditor)
        host.addSubview(badgeLabel)
        host.addSubview(closeButton)

        selectButton.addTarget(
            target = actionTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        actionTarget.onPrimaryAction = {
            if (editableState && selectedState) {
                beginInlineEdit()
            } else {
                onSelectTabRequested?.invoke()
            }
        }
        closeButton.addTarget(
            target = closeTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        closeTarget.onPrimaryAction = {
            onCloseListener?.invoke()
        }
        inlineEditor.delegate = editorDelegate
        editorDelegate.onSubmit = { committed ->
            endInlineEdit(committed)
        }
        refreshTabHeader()
    }

    override fun setTabIdNative(id: String) {
        tabId = id
    }

    override fun setTitleNative(title: String) {
        tabTitle = title
        refreshTabHeader()
    }

    override fun setIconNative(icon: String?) {
        tabIcon = icon
        selectButton.setImage(UikitGeometryAndColorUtil.systemIconImage(icon), forState = UIControlStateNormal)
    }

    override fun setIconSizePxNative(sizePx: Int) {
        // Icon sizing tracked alongside button layout
    }

    override fun setIconPlacementNative(placement: TabIconPlacement) {
        // Icon placement tracked for button configuration
    }

    override fun setTintImageToThemeNative(tintToTheme: Boolean) {
        selectButton.tintColor = if (tintToTheme) UIColor.labelColor else UIColor.systemBlueColor
    }

    override fun setTooltipNative(tooltip: String?) {
        setTooltipText(tooltip)
    }

    override fun setClosableNative(closable: Boolean) {
        closableState = closable
        refreshTabHeader()
    }

    override fun setDirtyNative(dirty: Boolean) {
        dirtyState = dirty
        refreshTabHeader()
    }

    override fun setPinnedNative(pinned: Boolean) {
        pinnedState = pinned
        refreshTabHeader()
    }

    override fun setEditableNative(editable: Boolean) {
        editableState = editable
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        val clean = badgeText?.takeIf { it.isNotEmpty() }
        badgeLabel.hidden = clean == null
        badgeLabel.text = clean
        if (clean != null) {
            badgeLabel.backgroundColor = when (variantCss?.lowercase()) {
                "danger", "bg-danger" -> UIColor.systemRedColor
                "success", "bg-success" -> UIColor.systemGreenColor
                "warning", "bg-warning" -> UIColor.systemOrangeColor
                else -> UIColor.systemBlueColor
            }
            badgeLabel.textColor = UIColor.whiteColor
        }
    }

    override fun setSelectedNative(selected: Boolean) {
        selectedState = selected
        refreshTabHeader()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        (customHeaderImpl?.nativeWidget as? UikitViewHandle)?.uiView?.removeFromSuperview()
        customHeaderImpl = customImpl
        val view = (customImpl?.nativeWidget as? UikitViewHandle)?.uiView
        selectButton.hidden = view != null
        if (view != null) {
            (uiView as UikitFlexContainerView).insertSubview(view, atIndex = 0L)
        }
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }

    private fun beginInlineEdit() {
        inlineEditor.text = tabTitle
        inlineEditor.hidden = false
        selectButton.hidden = true
        inlineEditor.becomeFirstResponder()
    }

    private fun endInlineEdit(newTitle: String) {
        inlineEditor.hidden = true
        selectButton.hidden = customHeaderImpl != null
        val trimmed = newTitle.trim()
        if (trimmed.isNotEmpty() && trimmed != tabTitle) {
            tabTitle = trimmed
            refreshTabHeader()
            onTitleEditedListener?.invoke(trimmed)
        }
    }

    private fun refreshTabHeader() {
        val display = if (dirtyState) "$tabTitle •" else tabTitle
        selectButton.setTitle(display, forState = UIControlStateNormal)
        closeButton.hidden = !closableState || pinnedState
        uiView.backgroundColor = if (selectedState) UIColor.secondarySystemBackgroundColor else UIColor.clearColor
        uiView.setNeedsLayout()
    }
}
