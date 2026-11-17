@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.window.TitleBar
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIView
import platform.UIKit.labelColor
import platform.UIKit.secondarySystemBackgroundColor
import platform.UIKit.tertiarySystemBackgroundColor

/**
 * Native UIKit dock panel container with real header bar (`UILabel`, collapse chevron `UIButton`,
 * action buttons, and [TitleBar] instruction support) and collapsible body `UIView`.
 */
class IosDockPanelImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 280.0, 240.0)).apply {
            flexDirection = FlexDirection.COLUMN
            backgroundColor = UIColor.secondarySystemBackgroundColor
        }
    ),
    componentClassPrefix = "dock-panel"
), DockPanelNativeImpl<UikitViewHandle> {

    private val headerBar = UikitFlexContainerView(CGRectMake(0.0, 0.0, 280.0, 34.0)).apply {
        flexDirection = FlexDirection.ROW
        alignItems = AlignItems.CENTER
        backgroundColor = UIColor.tertiarySystemBackgroundColor
        itemGapPt = 6.0
    }
    private val titleLabel = UILabel(frame = CGRectMake(0.0, 0.0, 160.0, 24.0)).apply {
        font = UIFont.boldSystemFontOfSize(13.0)
        textColor = UIColor.labelColor
    }
    private val collapseButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(0.0, 0.0, 26.0, 26.0))
        setTitle("▾", forState = UIControlStateNormal)
    }
    private val bodyHost = UikitFlexContainerView(CGRectMake(0.0, 34.0, 280.0, 206.0)).apply {
        flexDirection = FlexDirection.COLUMN
    }

    private var titleText: String = ""
    private var iconPath: String? = null
    private var collapsedState: Boolean = false
    private var floatingState: Boolean = false
    private var headerActions: List<DockHeaderAction> = emptyList()
    private var titleBarInstructions: TitleBar? = null
    private var collapseListener: ((Boolean) -> Unit)? = null
    private val actionButtons = mutableListOf<UIButton>()
    private val actionTargets = mutableListOf<UikitControlActionTarget>()
    private val collapseTarget = UikitControlActionTarget()

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(headerBar)
        host.setChildPreferredSize(headerBar, 280.0, 34.0)
        host.addSubview(bodyHost)
        headerBar.addSubview(collapseButton)
        headerBar.setChildPreferredSize(collapseButton, 26.0, 26.0)
        headerBar.addSubview(titleLabel)

        collapseButton.addTarget(
            target = collapseTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        collapseTarget.onPrimaryAction = {
            setCollapsed(!collapsedState)
        }
    }

    override fun contentContainerView(): UIView = bodyHost

    override fun setTitle(title: String) {
        titleText = title
        titleLabel.text = title
    }

    override fun getTitle(): String = titleText

    override fun setIcon(icon: String?) {
        iconPath = icon
    }

    override fun setCollapsed(collapsed: Boolean) {
        if (collapsedState == collapsed) return
        collapsedState = collapsed
        bodyHost.hidden = collapsed
        collapseButton.setTitle(if (collapsed) "▸" else "▾", forState = UIControlStateNormal)
        uiView.setNeedsLayout()
        collapseListener?.invoke(collapsed)
    }

    override fun isCollapsed(): Boolean = collapsedState

    override fun setOnCollapsedChanged(listener: ((Boolean) -> Unit)?) {
        collapseListener = listener
    }

    override fun setFloating(floating: Boolean) {
        floatingState = floating
    }

    override fun isFloating(): Boolean = floatingState

    override fun setHeaderActions(actions: List<DockHeaderAction>) {
        headerActions = actions
        for (btn in actionButtons) btn.removeFromSuperview()
        actionButtons.clear()
        actionTargets.clear()
        for (action in actions) {
            val target = UikitControlActionTarget()
            target.onPrimaryAction = action.onClick
            val btn = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                setFrame(CGRectMake(0.0, 0.0, 28.0, 26.0))
                val sym = UikitGeometryAndColorUtil.systemIconImage(action.iconSvgOrPath)
                if (sym != null) {
                    setImage(sym, forState = UIControlStateNormal)
                } else {
                    setTitle(action.label, forState = UIControlStateNormal)
                }
                addTarget(
                    target = target,
                    action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
                    forControlEvents = UIControlEventTouchUpInside
                )
            }
            actionTargets += target
            actionButtons += btn
            headerBar.addSubview(btn)
            headerBar.setChildPreferredSize(btn, 28.0, 26.0)
        }
        headerBar.setNeedsLayout()
    }

    override fun getHeaderActions(): List<DockHeaderAction> = headerActions

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        titleBarInstructions = titleBar
        titleBar?.setHostEmbedsComponents(false)
    }

    override fun getTitleBarInstructions(): TitleBar? = titleBarInstructions
}
