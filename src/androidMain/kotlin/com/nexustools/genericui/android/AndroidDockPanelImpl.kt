package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.ViewCompat
import com.nexustools.genericui.component.dock.DockHeaderAction
import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.nativeimpl.dock.DockPanelNativeImpl
import com.nexustools.genericui.window.TitleBar

/**
 * Android collapsible card/panel implementation of [DockPanelNativeImpl] with header bar
 * (icon, title, [TitleBar] instructions, custom [DockHeaderAction] buttons, and collapse toggle)
 * plus a collapsible child body container.
 */
class AndroidDockPanelImpl : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", "dock-panel"), DockPanelNativeImpl<AndroidViewHandle> {
    private var titleText: String = ""
    private var iconName: String? = null
    private var collapsedState: Boolean = false
    private var floatingState: Boolean = false
    private var headerActions: List<DockHeaderAction> = emptyList()
    private var titleBarInstructions: TitleBar? = null
    private var onCollapsedChangedListener: ((Boolean) -> Unit)? = null

    private var rootColumn: LinearLayout? = null
    private var headerBar: LinearLayout? = null
    private var titleBarLeadingRow: LinearLayout? = null
    private var headerIconView: AppCompatImageView? = null
    private var headerTitleView: TextView? = null
    private var titleBarCustomRow: LinearLayout? = null
    private var headerActionsRow: LinearLayout? = null
    private var collapseToggleButton: AppCompatButton? = null
    private var bodyContainer: AndroidFlexContainerView? = null

    internal var onPanelMetadataChanged: (() -> Unit)? = null

    override fun createDefaultAndroidView(context: Context): View? {
        val isDark = AndroidViewHandle.isDarkTheme()
        val cardBg = if (isDark) 0xFF1E222B.toInt() else 0xFFFFFFFF.toInt()
        val headerBg = if (isDark) 0xFF252A36.toInt() else 0xFFEFF2F7.toInt()
        val borderColor = if (isDark) 0xFF323846.toInt() else 0xFFD4DAE3.toInt()
        val fgColor = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AndroidViewHandle.dpToPxFloat(6f)
                setColor(cardBg)
                setStroke(AndroidViewHandle.dpToPx(1).coerceAtLeast(1), borderColor)
            }
        }.also { rootColumn = it }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(headerBg)
            val padH = AndroidViewHandle.dpToPx(8)
            val padV = AndroidViewHandle.dpToPx(6)
            setPadding(padH, padV, padH, padV)
        }.also { headerBar = it }

        val leadingRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }.also { titleBarLeadingRow = it }

        val iconIv = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }.also { headerIconView = it }

        val titleTv = TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(fgColor)
            isSingleLine = true
        }.also { headerTitleView = it }

        val customRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }.also { titleBarCustomRow = it }

        val actionsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }.also { headerActionsRow = it }

        val collapseBtn = AppCompatButton(context).apply {
            text = if (collapsedState) "▸" else "▾"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setPadding(AndroidViewHandle.dpToPx(6), AndroidViewHandle.dpToPx(2), AndroidViewHandle.dpToPx(4), AndroidViewHandle.dpToPx(2))
            setOnClickListener {
                val next = !collapsedState
                setCollapsed(next)
                onCollapsedChangedListener?.invoke(next)
            }
        }.also { collapseToggleButton = it }

        header.addView(
            leadingRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        header.addView(
            iconIv,
            LinearLayout.LayoutParams(AndroidViewHandle.dpToPx(16), AndroidViewHandle.dpToPx(16)).apply {
                rightMargin = AndroidViewHandle.dpToPx(6)
            }
        )
        header.addView(
            titleTv,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        header.addView(
            customRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        header.addView(
            actionsRow,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        header.addView(
            collapseBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val body = AndroidFlexContainerView(context).apply {
            flexDirection = FlexDirection.COLUMN
            alignItems = AlignItems.STRETCH
            val bodyPad = AndroidViewHandle.dpToPx(6)
            setPadding(bodyPad, bodyPad, bodyPad, bodyPad)
        }.also {
            bodyContainer = it
            nativeWidget.childHostViewGroup = it
        }

        root.addView(
            header,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        root.addView(
            body,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        return root
    }

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
        syncAndroidView()
        onPanelMetadataChanged?.invoke()
    }

    override fun getTitle(): String = titleText

    override fun setIcon(icon: String?) {
        iconName = icon
        if (icon != null) {
            nativeWidget.attributes["icon"] = icon
        } else {
            nativeWidget.attributes.remove("icon")
        }
        syncAndroidView()
        onPanelMetadataChanged?.invoke()
    }

    override fun setCollapsed(collapsed: Boolean) {
        collapsedState = collapsed
        nativeWidget.attributes["collapsed"] = collapsed.toString()
        syncAndroidView()
    }

    override fun isCollapsed(): Boolean = collapsedState

    override fun setOnCollapsedChanged(listener: ((Boolean) -> Unit)?) {
        onCollapsedChangedListener = listener
    }

    override fun setFloating(floating: Boolean) {
        floatingState = floating
        nativeWidget.attributes["floating"] = floating.toString()
        syncAndroidView()
        onPanelMetadataChanged?.invoke()
    }

    override fun isFloating(): Boolean = floatingState

    override fun setHeaderActions(actions: List<DockHeaderAction>) {
        headerActions = actions
        syncAndroidView()
    }

    override fun getHeaderActions(): List<DockHeaderAction> = headerActions

    override fun applyTitleBarInstructions(titleBar: TitleBar?) {
        if (titleBarInstructions !== titleBar) {
            titleBarInstructions?.setHostEmbedsComponents(false)
        }
        titleBarInstructions = titleBar
        titleBar?.setHostEmbedsComponents(true)
        syncAndroidView()
    }

    override fun getTitleBarInstructions(): TitleBar? = titleBarInstructions

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val root = rootColumn ?: return@runCatching
                val ctx = root.context ?: return@runCatching
                val isDark = AndroidViewHandle.isDarkTheme()
                val fgColor = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                val tb = titleBarInstructions

                tb?.heightPx?.takeIf { it > 0 }?.let { hp ->
                    headerBar?.minimumHeight = AndroidViewHandle.dpToPx(hp)
                }

                val showTitle = tb?.showWindowTitle != false
                headerTitleView?.apply {
                    text = if (showTitle) titleText else ""
                    visibility = if (showTitle) View.VISIBLE else View.GONE
                    setTextColor(fgColor)
                }

                val showIcon = tb?.showWindowIcon != false
                headerIconView?.let { iv ->
                    val src = if (showIcon) (iconName ?: tb?.iconPath) else null
                    if (!src.isNullOrBlank()) {
                        val drawable = AndroidImageImpl.resolveDrawable(ctx, src, fgColor)
                        if (drawable != null) {
                            iv.setImageDrawable(drawable)
                            iv.visibility = View.VISIBLE
                        } else {
                            iv.visibility = View.GONE
                        }
                    } else {
                        iv.visibility = View.GONE
                    }
                }

                titleBarLeadingRow?.let { leading ->
                    leading.removeAllViews()
                    val comps = tb?.leadingComponents.orEmpty()
                    for (comp in comps) {
                        val compImpl = (comp.nativeImpl ?: comp.ensureNativeImpl()) as? AndroidBaseNativeImpl ?: continue
                        val childView = compImpl.ensureAndroidView(ctx) ?: continue
                        (childView.parent as? ViewGroup)?.removeView(childView)
                        leading.addView(childView)
                    }
                    leading.visibility = if (leading.childCount > 0) View.VISIBLE else View.GONE
                }

                titleBarCustomRow?.let { custom ->
                    custom.removeAllViews()
                    val comps = (tb?.centerComponents.orEmpty() + tb?.trailingComponents.orEmpty())
                    for (comp in comps) {
                        val compImpl = (comp.nativeImpl ?: comp.ensureNativeImpl()) as? AndroidBaseNativeImpl ?: continue
                        val childView = compImpl.ensureAndroidView(ctx) ?: continue
                        (childView.parent as? ViewGroup)?.removeView(childView)
                        custom.addView(childView)
                    }
                    custom.visibility = if (custom.childCount > 0) View.VISIBLE else View.GONE
                }

                collapseToggleButton?.apply {
                    text = if (collapsedState) "▸" else "▾"
                    setTextColor(fgColor)
                }

                headerActionsRow?.let { actionsRow ->
                    actionsRow.removeAllViews()
                    for (action in headerActions) {
                        val actionBtn = AppCompatButton(ctx).apply {
                            text = if (action.iconSvgOrPath != null) "" else action.label
                            isAllCaps = false
                            minWidth = AndroidViewHandle.dpToPx(24)
                            minimumWidth = AndroidViewHandle.dpToPx(24)
                            minHeight = AndroidViewHandle.dpToPx(24)
                            minimumHeight = AndroidViewHandle.dpToPx(24)
                            setBackgroundColor(Color.TRANSPARENT)
                            setTextColor(fgColor)
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                            setPadding(AndroidViewHandle.dpToPx(4), 0, AndroidViewHandle.dpToPx(4), 0)
                            contentDescription = action.tooltip ?: action.label
                            ViewCompat.setTooltipText(this, action.tooltip ?: action.label)
                            action.iconSvgOrPath?.let { iconPath ->
                                AndroidImageImpl.resolveDrawable(ctx, iconPath, fgColor)?.let { d ->
                                    val sz = AndroidViewHandle.dpToPx(14)
                                    d.setBounds(0, 0, sz, sz)
                                    setCompoundDrawables(d, null, null, null)
                                }
                            }
                            setOnClickListener {
                                action.onClick()
                            }
                        }
                        actionsRow.addView(
                            actionBtn,
                            LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                        )
                    }
                }

                bodyContainer?.visibility = if (collapsedState) View.GONE else View.VISIBLE
            }
        }
    }
}
