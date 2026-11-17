@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toCgColor
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.color.RgbaColor
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIButton
import platform.UIKit.UIButtonTypeSystem
import platform.UIKit.UIColor
import platform.UIKit.UIControlEventTouchUpInside
import platform.UIKit.UIControlStateNormal
import platform.UIKit.UIView
import platform.UIKit.labelColor
import platform.UIKit.separatorColor
import platform.UIKit.systemBlueColor
import platform.UIKit.tertiarySystemBackgroundColor

/**
 * Native UIKit multi-column tool button grid and foreground/background color swatch widget
 * implementing [ToolPaletteNativeImpl].
 */
class IosToolPaletteImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UIView(frame = CGRectMake(0.0, 0.0, 96.0, 320.0))),
    componentClassPrefix = "tool-palette"
), ToolPaletteNativeImpl<UikitViewHandle> {

    private var columnCount: Int = 2
    private var toolsList: List<ToolPaletteItem> = emptyList()
    private var activeId: String? = null
    private var foregroundColor: RgbaColor = RgbaColor(0, 0, 0, 255)
    private var backgroundColorValue: RgbaColor = RgbaColor(255, 255, 255, 255)
    private var onToolSelectedListener: ((String) -> Unit)? = null
    private var onColorsSwappedListener: ((RgbaColor, RgbaColor) -> Unit)? = null

    private val toolButtons = LinkedHashMap<String, UIButton>()
    private val targets = mutableListOf<UikitControlActionTarget>()

    private val bgSwatchButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(26.0, 26.0, 26.0, 26.0))
        layer.borderWidth = 1.0
        layer.borderColor = UIColor.separatorColor.CGColor
        layer.cornerRadius = 4.0
    }
    private val fgSwatchButton = UIButton.buttonWithType(UIButtonTypeSystem).apply {
        setFrame(CGRectMake(12.0, 12.0, 26.0, 26.0))
        layer.borderWidth = 1.0
        layer.borderColor = UIColor.separatorColor.CGColor
        layer.cornerRadius = 4.0
    }
    private val swatchContainer = UIView(frame = CGRectMake(8.0, 8.0, 64.0, 60.0)).apply {
        addSubview(bgSwatchButton)
        addSubview(fgSwatchButton)
    }
    private val swapTarget = UikitControlActionTarget()

    init {
        fgSwatchButton.addTarget(
            target = swapTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        bgSwatchButton.addTarget(
            target = swapTarget,
            action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
            forControlEvents = UIControlEventTouchUpInside
        )
        swapTarget.onPrimaryAction = {
            val prevFg = foregroundColor
            foregroundColor = backgroundColorValue
            backgroundColorValue = prevFg
            refreshSwatches()
            onColorsSwappedListener?.invoke(foregroundColor, backgroundColorValue)
        }
        refreshSwatches()
    }

    override fun setColumns(columns: Int) {
        columnCount = columns.coerceAtLeast(1)
        rebuildToolGrid()
    }

    override fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?) {
        toolsList = tools
        activeId = activeToolId
        rebuildToolGrid()
    }

    override fun setActiveToolId(toolId: String?) {
        activeId = toolId
        for ((id, btn) in toolButtons) {
            btn.selected = id == activeId
            btn.backgroundColor = if (id == activeId) UIColor.systemBlueColor else UIColor.tertiarySystemBackgroundColor
        }
    }

    override fun setForegroundColor(color: RgbaColor) {
        foregroundColor = color
        refreshSwatches()
    }

    override fun setBackgroundColor(color: RgbaColor) {
        backgroundColorValue = color
        refreshSwatches()
    }

    override fun setOnToolSelected(listener: ((String) -> Unit)?) {
        onToolSelectedListener = listener
    }

    override fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?) {
        onColorsSwappedListener = listener
    }

    private fun refreshSwatches() {
        fgSwatchButton.backgroundColor = foregroundColor.toUiColor()
        bgSwatchButton.backgroundColor = backgroundColorValue.toUiColor()
        fgSwatchButton.layer.borderColor = UIColor.labelColor.CGColor
        bgSwatchButton.layer.borderColor = UIColor.labelColor.CGColor
    }

    private fun rebuildToolGrid() {
        for (btn in toolButtons.values) btn.removeFromSuperview()
        toolButtons.clear()
        targets.clear()

        val btnSize = 36.0
        val gap = 4.0
        var maxY = 8.0
        for ((idx, item) in toolsList.withIndex()) {
            val col = idx % columnCount
            val row = idx / columnCount
            val x = 8.0 + col * (btnSize + gap)
            val y = 8.0 + row * (btnSize + gap)
            maxY = y + btnSize + gap
            val target = UikitControlActionTarget()
            target.onPrimaryAction = {
                setActiveToolId(item.id)
                onToolSelectedListener?.invoke(item.id)
            }
            val btn = UIButton.buttonWithType(UIButtonTypeSystem).apply {
                setFrame(CGRectMake(x, y, btnSize, btnSize))
                layer.cornerRadius = 6.0
                val sym = UikitGeometryAndColorUtil.systemIconImage(item.iconSvgOrPath)
                if (sym != null) {
                    setImage(sym, forState = UIControlStateNormal)
                } else {
                    setTitle(item.name.take(2), forState = UIControlStateNormal)
                }
                addTarget(
                    target = target,
                    action = UikitControlActionTarget.SEL_PRIMARY_ACTION,
                    forControlEvents = UIControlEventTouchUpInside
                )
            }
            targets += target
            toolButtons[item.id] = btn
            uiView.addSubview(btn)
        }
        swatchContainer.removeFromSuperview()
        swatchContainer.setFrame(CGRectMake(8.0, maxY + 4.0, 64.0, 60.0))
        uiView.addSubview(swatchContainer)
        setActiveToolId(activeId)
    }
}
