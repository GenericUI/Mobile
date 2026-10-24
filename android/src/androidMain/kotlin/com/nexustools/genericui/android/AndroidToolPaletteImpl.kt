package com.nexustools.genericui.android

import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android `GridLayout` implementation of [ToolPaletteNativeImpl].
 */
class AndroidToolPaletteImpl : AndroidBaseContainerNativeImpl("android.widget.GridLayout", "tool-palette"), ToolPaletteNativeImpl<AndroidViewHandle> {
    private var onToolSelectedListener: ((String) -> Unit)? = null

    override fun setColumns(columns: Int) { nativeWidget.metadata["columns"] = columns.toString() }
    override fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?) {}
    override fun setActiveToolId(toolId: String?) {
        if (toolId != null) onToolSelectedListener?.invoke(toolId)
    }
    override fun setForegroundColor(color: RgbaColor) {}
    override fun setBackgroundColor(color: RgbaColor) {}
    override fun setOnToolSelected(listener: ((String) -> Unit)?) { onToolSelectedListener = listener }
    override fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?) {}
}
