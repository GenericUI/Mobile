package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import com.nexustools.genericui.geometry.Rect
import com.nexustools.genericui.nativeimpl.dock.DockWindowNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Android floating [PopupWindow] utility palette implementation of [DockWindowNativeImpl].
 */
class AndroidDockWindowImpl : AndroidBaseContainerNativeImpl("android.widget.FrameLayout", "dock-window"), DockWindowNativeImpl<AndroidViewHandle> {
    override val isToolModalWindow: Boolean = true
    override val hasTaskbarEntry: Boolean = false
    override val isMinimizable: Boolean = false
    override val isAlwaysOnTopOfOwner: Boolean = true

    var ownerWindow: WindowNativeImpl<*>? = null
        private set

    private var titleText: String = "Tool Panel"
    private var closeListener: (() -> Unit)? = null

    private var titleLabelView: TextView? = null
    private var bodyHost: AndroidFlexContainerView? = null
    private var popupWindow: PopupWindow? = null

    init {
        setAttribute("tool-modal", "true")
        setAttribute("show-in-taskbar", "false")
        setAttribute("minimizable", "false")
        setAttribute("always-on-top-of-owner", "true")
    }

    override fun createDefaultAndroidView(context: Context): View? {
        val isDark = AndroidViewHandle.isDarkTheme()
        val bgColor = if (isDark) 0xFF1F242E.toInt() else 0xFFFFFFFF.toInt()
        val headerBg = if (isDark) 0xFF282E3A.toInt() else 0xFFEFF2F7.toInt()
        val borderColor = if (isDark) 0xFF3A4254.toInt() else 0xFFC8D0DC.toInt()
        val fgColor = if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            elevation = AndroidViewHandle.dpToPxFloat(8f)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = AndroidViewHandle.dpToPxFloat(8f)
                setColor(bgColor)
                setStroke(AndroidViewHandle.dpToPx(1).coerceAtLeast(1), borderColor)
            }
        }

        val titleBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(headerBg)
            val padH = AndroidViewHandle.dpToPx(10)
            val padV = AndroidViewHandle.dpToPx(6)
            setPadding(padH, padV, padH, padV)
        }

        val titleTv = TextView(context).apply {
            text = titleText
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(fgColor)
            isSingleLine = true
        }.also { titleLabelView = it }

        val closeBtn = AppCompatButton(context).apply {
            text = "✕"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            setTextColor(fgColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setPadding(AndroidViewHandle.dpToPx(6), 0, AndroidViewHandle.dpToPx(2), 0)
            setOnClickListener {
                closeListener?.invoke() ?: hideDockWindow()
            }
        }

        titleBar.addView(
            titleTv,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        titleBar.addView(
            closeBtn,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )

        val content = AndroidFlexContainerView(context).apply {
            val pad = AndroidViewHandle.dpToPx(8)
            setPadding(pad, pad, pad, pad)
        }.also {
            bodyHost = it
            nativeWidget.childHostViewGroup = it
        }

        card.addView(
            titleBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        card.addView(
            content,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        return card
    }

    override fun setTitle(title: String) {
        titleText = title
        nativeWidget.text = title
        AndroidViewHandle.runOnMainThread {
            titleLabelView?.text = title
        }
    }

    override fun setWindowBounds(bounds: Rect) {
        nativeWidget.bounds = bounds
        AndroidViewHandle.runOnMainThread {
            runCatching {
                popupWindow?.takeIf { it.isShowing }?.update(
                    AndroidViewHandle.dpToPx(bounds.x),
                    AndroidViewHandle.dpToPx(bounds.y),
                    AndroidViewHandle.dpToPx(bounds.width.coerceAtLeast(180)),
                    AndroidViewHandle.dpToPx(bounds.height.coerceAtLeast(120))
                )
            }
        }
    }

    override fun showDockWindow() {
        setVisible(true)
        bringToFront()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val hostActivity = AndroidActivityRegistry.resolveActiveActivity() ?: return@runCatching
                val anchor = hostActivity.window?.decorView ?: return@runCatching
                val contentRoot = ensureAndroidView(hostActivity) ?: return@runCatching
                (contentRoot.parent as? ViewGroup)?.removeView(contentRoot)

                val wPx = AndroidViewHandle.dpToPx(nativeWidget.bounds.width.takeIf { it > 0 } ?: 260)
                val hPx = AndroidViewHandle.dpToPx(nativeWidget.bounds.height.takeIf { it > 0 } ?: 320)
                val xPx = AndroidViewHandle.dpToPx(nativeWidget.bounds.x.coerceAtLeast(16))
                val yPx = AndroidViewHandle.dpToPx(nativeWidget.bounds.y.coerceAtLeast(64))

                val existing = popupWindow
                if (existing != null && existing.isShowing) {
                    existing.update(xPx, yPx, wPx, hPx)
                } else {
                    val pw = PopupWindow(contentRoot, wPx, hPx, false).apply {
                        isOutsideTouchable = false
                        isFocusable = false
                        elevation = AndroidViewHandle.dpToPxFloat(8f)
                    }
                    popupWindow = pw
                    pw.showAtLocation(anchor, Gravity.TOP or Gravity.START, xPx, yPx)
                }
            }
        }
    }

    override fun hideDockWindow() {
        setVisible(false)
        AndroidViewHandle.runOnMainThread {
            runCatching {
                popupWindow?.dismiss()
            }
            popupWindow = null
        }
    }

    override fun setOnDockWindowCloseRequested(listener: (() -> Unit)?) {
        closeListener = listener
    }

    override fun attachToOwnerWindow(ownerWindow: WindowNativeImpl<*>?) {
        this.ownerWindow = ownerWindow
        setAttribute("ownerWindow", ownerWindow?.toString() ?: "")
        bringToFront()
    }

    override fun bringToFront() {
        setAttribute("broughtToFront", "true")
    }

    override fun dispose() {
        runCatching { popupWindow?.dismiss() }
        popupWindow = null
        closeListener = null
        super.dispose()
    }
}
