package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.appcompat.widget.AppCompatButton
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.dock.DockGroupMode
import com.nexustools.genericui.nativeimpl.dock.DockGroupNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android tabbed / vertically-split / horizontally-split panel stack implementation of
 * [DockGroupNativeImpl].
 */
class AndroidDockGroupImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-group"), DockGroupNativeImpl<AndroidViewHandle> {
    private var groupMode: DockGroupMode = DockGroupMode.TABBED
    private var activeIndex: Int = 0

    private var rootColumn: LinearLayout? = null
    private var tabStripScroll: HorizontalScrollView? = null
    private var tabStripRow: LinearLayout? = null
    private var panelsHostLayout: LinearLayout? = null

    override fun createDefaultAndroidView(context: Context): View? {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }.also { rootColumn = it }

        val stripRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pad = AndroidViewHandle.dpToPx(4)
            setPadding(pad, pad, pad, pad)
        }.also { tabStripRow = it }

        val stripScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(
                stripRow,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }.also { tabStripScroll = it }

        val host = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }.also {
            panelsHostLayout = it
            nativeWidget.childHostViewGroup = it
        }

        root.addView(
            stripScroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        root.addView(
            host,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return root
    }

    override fun setGroupMode(mode: DockGroupMode) {
        groupMode = mode
        nativeWidget.attributes["groupMode"] = mode.name.lowercase()
        syncAndroidView()
    }

    override fun getGroupMode(): DockGroupMode = groupMode

    override fun setActivePanelIndex(index: Int) {
        activeIndex = index.coerceAtLeast(0)
        nativeWidget.attributes["activePanelIndex"] = activeIndex.toString()
        syncAndroidView()
    }

    override fun getActivePanelIndex(): Int = activeIndex

    override fun addChildNative(childImpl: NativeImpl<*>, index: Int) {
        if (childImpl is AndroidDockPanelImpl) {
            childImpl.onPanelMetadataChanged = { syncAndroidView() }
        }
        super.addChildNative(childImpl, index)
        syncAndroidView()
    }

    override fun removeChildNative(childImpl: NativeImpl<*>) {
        if (childImpl is AndroidDockPanelImpl) {
            childImpl.onPanelMetadataChanged = null
        }
        super.removeChildNative(childImpl)
        if (activeIndex >= childNativeImpls.size && childNativeImpls.isNotEmpty()) {
            activeIndex = childNativeImpls.lastIndex
        }
        syncAndroidView()
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val strip = tabStripScroll ?: return@runCatching
                val row = tabStripRow ?: return@runCatching
                val host = panelsHostLayout ?: return@runCatching
                val ctx = host.context ?: return@runCatching

                val isTabbed = groupMode == DockGroupMode.TABBED
                strip.visibility = if (isTabbed && childNativeImpls.isNotEmpty()) View.VISIBLE else View.GONE

                val isDark = AndroidViewHandle.isDarkTheme()
                val primaryArgb = BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark).toArgbInt()
                val inactiveBg = if (isDark) 0xFF252A36.toInt() else 0xFFE5E9F0.toInt()
                val inactiveFg = if (isDark) 0xFFD8DEE9.toInt() else 0xFF2E3440.toInt()

                if (isTabbed) {
                    row.removeAllViews()
                    childNativeImpls.forEachIndexed { idx, childImpl ->
                        val title = (childImpl as? AndroidDockPanelImpl)?.getTitle()
                            ?: (childImpl.nativeWidget as? AndroidViewHandle)?.text
                            ?: "Panel ${idx + 1}"
                        val isSelected = idx == activeIndex
                        val btn = AppCompatButton(ctx).apply {
                            text = title
                            isAllCaps = false
                            minWidth = 0
                            minimumWidth = 0
                            minHeight = AndroidViewHandle.dpToPx(28)
                            minimumHeight = AndroidViewHandle.dpToPx(28)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                            setTextColor(if (isSelected) Color.WHITE else inactiveFg)
                            val hPad = AndroidViewHandle.dpToPx(10)
                            val vPad = AndroidViewHandle.dpToPx(4)
                            setPadding(hPad, vPad, hPad, vPad)
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.RECTANGLE
                                cornerRadius = AndroidViewHandle.dpToPxFloat(5f)
                                setColor(if (isSelected) primaryArgb else inactiveBg)
                            }
                            setOnClickListener {
                                setActivePanelIndex(idx)
                            }
                        }
                        row.addView(
                            btn,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                if (idx > 0) leftMargin = AndroidViewHandle.dpToPx(4)
                            }
                        )
                    }
                }

                host.orientation = if (groupMode == DockGroupMode.HORIZONTAL_SPLIT) {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }

                host.removeAllViews()
                childNativeImpls.forEachIndexed { idx, childImpl ->
                    val childView = (childImpl as? AndroidBaseNativeImpl)?.ensureAndroidView(ctx) ?: return@forEachIndexed
                    (childView.parent as? ViewGroup)?.removeView(childView)
                    when (groupMode) {
                        DockGroupMode.TABBED -> {
                            childView.visibility = if (idx == activeIndex) View.VISIBLE else View.GONE
                            host.addView(
                                childView,
                                LinearLayout.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                            )
                        }
                        DockGroupMode.HORIZONTAL_SPLIT -> {
                            childView.visibility = View.VISIBLE
                            host.addView(
                                childView,
                                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
                            )
                        }
                        DockGroupMode.VERTICAL_SPLIT -> {
                            childView.visibility = View.VISIBLE
                            host.addView(
                                childView,
                                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
                            )
                        }
                    }
                }
            }
        }
    }
}
