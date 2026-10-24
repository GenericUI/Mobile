package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * iOS implementation of [ToolPaletteNativeImpl].
 */
class IosToolPaletteImpl : IosBaseContainerNativeImpl("UIView", "tool-palette"), ToolPaletteNativeImpl<IosNativeView> {
    private var onToolSelectedListener: ((String) -> Unit)? = null

    override fun setColumns(columns: Int) {}
    override fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?) {}
    override fun setActiveToolId(toolId: String?) {
        if (toolId != null) onToolSelectedListener?.invoke(toolId)
    }
    override fun setForegroundColor(color: RgbaColor) {}
    override fun setBackgroundColor(color: RgbaColor) {}
    override fun setOnToolSelected(listener: ((String) -> Unit)?) { onToolSelectedListener = listener }
    override fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?) {}
}
