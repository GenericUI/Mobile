package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import com.nexustools.genericui.component.dock.ToolPaletteItem
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.dock.ToolPaletteNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android `GridLayout` + interactive foreground/background color swatch implementation of
 * [ToolPaletteNativeImpl].
 */
class AndroidToolPaletteImpl : AndroidBaseContainerNativeImpl("android.widget.GridLayout", "tool-palette"), ToolPaletteNativeImpl<AndroidViewHandle> {
    private var columnsCount: Int = 2
    private var toolsList: List<ToolPaletteItem> = emptyList()
    private var activeToolIdVal: String? = null
    private var foregroundColorVal: RgbaColor = RgbaColor(0, 0, 0, 255)
    private var backgroundColorVal: RgbaColor = RgbaColor(255, 255, 255, 255)
    private var onToolSelectedListener: ((String) -> Unit)? = null
    private var onColorsSwappedListener: ((RgbaColor, RgbaColor) -> Unit)? = null

    private var paletteGridView: GridLayout? = null
    private var foregroundSwatchView: View? = null
    private var backgroundSwatchView: View? = null

    override fun createDefaultAndroidView(context: Context): View? {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = AndroidViewHandle.dpToPx(6)
            setPadding(pad, pad, pad, pad)
        }

        val grid = GridLayout(context).apply {
            columnCount = columnsCount.coerceAtLeast(1)
        }.also {
            paletteGridView = it
            nativeWidget.childHostViewGroup = it
        }

        val swatchRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, AndroidViewHandle.dpToPx(8), 0, 0)
        }

        val swatchStackSize = AndroidViewHandle.dpToPx(36)
        val singleSwatchSize = AndroidViewHandle.dpToPx(22)
        val swatchBox = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(swatchStackSize, swatchStackSize)
        }

        val bgView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(singleSwatchSize, singleSwatchSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
            }
        }.also { backgroundSwatchView = it }

        val fgView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams(singleSwatchSize, singleSwatchSize).apply {
                gravity = Gravity.TOP or Gravity.START
            }
        }.also { foregroundSwatchView = it }

        swatchBox.addView(bgView)
        swatchBox.addView(fgView)

        val swapButton = AppCompatButton(context).apply {
            text = "⇄"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(AndroidViewHandle.dpToPx(6), AndroidViewHandle.dpToPx(4), AndroidViewHandle.dpToPx(6), AndroidViewHandle.dpToPx(4))
            contentDescription = "Swap foreground and background colors"
            ViewCompat.setTooltipText(this, "Swap colors")
            setOnClickListener {
                val nextFg = backgroundColorVal
                val nextBg = foregroundColorVal
                foregroundColorVal = nextFg
                backgroundColorVal = nextBg
                syncSwatchColors()
                onColorsSwappedListener?.invoke(nextFg, nextBg)
            }
        }

        swatchBox.setOnClickListener { swapButton.performClick() }

        swatchRow.addView(swatchBox)
        swatchRow.addView(
            swapButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = AndroidViewHandle.dpToPx(4)
            }
        )

        root.addView(
            grid,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            swatchRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        return root
    }

    override fun setColumns(columns: Int) {
        columnsCount = columns.coerceAtLeast(1)
        nativeWidget.attributes["columns"] = columnsCount.toString()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                paletteGridView?.columnCount = columnsCount
            }
        }
        rebuildToolsGrid()
    }

    override fun setTools(tools: List<ToolPaletteItem>, activeToolId: String?) {
        toolsList = tools.toList()
        if (activeToolId != null) {
            activeToolIdVal = activeToolId
            nativeWidget.attributes["activeToolId"] = activeToolId
        }
        rebuildToolsGrid()
    }

    override fun setActiveToolId(toolId: String?) {
        activeToolIdVal = toolId
        if (toolId != null) {
            nativeWidget.attributes["activeToolId"] = toolId
        } else {
            nativeWidget.attributes.remove("activeToolId")
        }
        rebuildToolsGrid()
        if (toolId != null) {
            onToolSelectedListener?.invoke(toolId)
        }
    }

    override fun setForegroundColor(color: RgbaColor) {
        foregroundColorVal = color
        nativeWidget.attributes["foregroundColor"] = color.toHex()
        syncSwatchColors()
    }

    override fun setBackgroundColor(color: RgbaColor) {
        backgroundColorVal = color
        nativeWidget.attributes["backgroundColor"] = color.toHex()
        syncSwatchColors()
    }

    override fun setOnToolSelected(listener: ((String) -> Unit)?) {
        onToolSelectedListener = listener
    }

    override fun setOnColorsSwapped(listener: ((RgbaColor, RgbaColor) -> Unit)?) {
        onColorsSwappedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        rebuildToolsGrid()
        syncSwatchColors()
    }

    private fun syncSwatchColors() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val isDark = AndroidViewHandle.isDarkTheme()
                val strokeColor = if (isDark) 0xFFD8DEE9.toInt() else 0xFF2E3440.toInt()
                val radius = AndroidViewHandle.dpToPxFloat(4f)
                val strokePx = AndroidViewHandle.dpToPx(1).coerceAtLeast(1)

                foregroundSwatchView?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(foregroundColorVal.toArgbInt())
                    setStroke(strokePx, strokeColor)
                }
                backgroundSwatchView?.background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = radius
                    setColor(backgroundColorVal.toArgbInt())
                    setStroke(strokePx, strokeColor)
                }
            }
        }
    }

    private fun rebuildToolsGrid() {
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val grid = paletteGridView ?: return@runCatching
                val ctx = grid.context ?: return@runCatching
                grid.removeAllViews()
                grid.columnCount = columnsCount.coerceAtLeast(1)

                val isDark = AndroidViewHandle.isDarkTheme()
                val primaryArgb = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark).toArgbInt()
                val inactiveBg = if (isDark) 0xFF252A36.toInt() else 0xFFEDF0F5.toInt()
                val inactiveFg = if (isDark) 0xFFE5E9F0.toInt() else 0xFF2E3440.toInt()
                val borderColor = if (isDark) 0xFF3B4252.toInt() else 0xFFD0D5DD.toInt()
                val btnSize = AndroidViewHandle.dpToPx(36)
                val marginPx = AndroidViewHandle.dpToPx(2)

                for (tool in toolsList) {
                    val isSelected = tool.id == activeToolIdVal
                    val iconTint = if (isSelected) Color.WHITE else inactiveFg
                    val iconDrawable = AndroidImageImpl.resolveDrawable(ctx, tool.iconSvgOrPath, iconTint)

                    val btn = AppCompatButton(ctx).apply {
                        text = if (iconDrawable != null) "" else tool.name.take(3)
                        isAllCaps = false
                        minWidth = btnSize
                        minimumWidth = btnSize
                        minHeight = btnSize
                        minimumHeight = btnSize
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                        setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                        setTextColor(iconTint)
                        setPadding(marginPx, marginPx, marginPx, marginPx)
                        contentDescription = tool.tooltip ?: tool.name
                        ViewCompat.setTooltipText(this, tool.tooltip ?: tool.name)
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = AndroidViewHandle.dpToPxFloat(6f)
                            setColor(if (isSelected) primaryArgb else inactiveBg)
                            setStroke(AndroidViewHandle.dpToPx(1).coerceAtLeast(1), if (isSelected) primaryArgb else borderColor)
                        }
                        if (iconDrawable != null) {
                            val iconPx = AndroidViewHandle.dpToPx(18)
                            iconDrawable.setBounds(0, 0, iconPx, iconPx)
                            setCompoundDrawables(iconDrawable, null, null, null)
                        }
                        setOnClickListener {
                            setActiveToolId(tool.id)
                        }
                    }

                    val lp = GridLayout.LayoutParams().apply {
                        width = btnSize
                        height = btnSize
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    grid.addView(btn, lp)
                }
            }
        }
    }
}
