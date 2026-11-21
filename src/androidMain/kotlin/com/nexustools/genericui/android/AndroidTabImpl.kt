package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.AppCompatEditText
import androidx.appcompat.widget.AppCompatImageView
import com.nexustools.genericui.component.tabs.TabIconPlacement
import com.nexustools.genericui.core.NativeImpl
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.tabs.TabNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android tab header view implementation of [TabNativeImpl], rendering icon, pin indicator,
 * dirty indicator, title label + inline [AppCompatEditText] editor (`EditableTab`), badge pill,
 * close button (`CloseableTab`), and custom child component views.
 */
class AndroidTabImpl(
    initialClosable: Boolean = false
) : AndroidBaseContainerNativeImpl("com.google.android.material.tabs.TabLayout.TabView", "nav-item"), TabNativeImpl<AndroidViewHandle> {

    var tabIdVal: String = ""
        private set
    var titleVal: String = ""
        private set
    var iconVal: String? = null
        private set
    var iconSizePxVal: Int = 20
        private set
    var iconPlacementVal: TabIconPlacement = TabIconPlacement.START
        private set
    var tintImageToThemeVal: Boolean = true
        private set
    var closableVal: Boolean = initialClosable
        private set
    var dirtyVal: Boolean = false
        private set
    var pinnedVal: Boolean = false
        private set
    var editableVal: Boolean = false
        private set
    var badgeTextVal: String? = null
        private set
    var badgeVariantVal: String = "primary"
        private set
    var selectedVal: Boolean = false
        private set
    var customHeaderImpl: NativeImpl<*>? = null
        private set

    internal var onTabUpdated: (() -> Unit)? = null
    private var onCloseClickedListener: (() -> Unit)? = null
    private var onTitleEditedListener: ((String) -> Unit)? = null

    private var rootTabRow: LinearLayout? = null
    private var pinIndicatorView: TextView? = null
    private var iconView: AppCompatImageView? = null
    private var dirtyIndicatorView: TextView? = null
    private var titleLabelView: TextView? = null
    private var titleEditView: AppCompatEditText? = null
    private var badgeView: TextView? = null
    private var closeButtonView: AppCompatButton? = null
    private var customHeaderHost: FrameLayout? = null
    private var isEditingTitle: Boolean = false

    init {
        nativeWidget.attributes["closable"] = initialClosable.toString()
        nativeWidget.attributes["iconSizePx"] = iconSizePxVal.toString()
        nativeWidget.attributes["iconPlacement"] = iconPlacementVal.name
        nativeWidget.attributes["tintImageToTheme"] = tintImageToThemeVal.toString()
    }

    override fun createDefaultAndroidView(context: Context): View {
        val row = LinearLayout(context).apply {
            visibility = View.GONE
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = AndroidViewHandle.dpToPx(8)
            val padV = AndroidViewHandle.dpToPx(6)
            setPadding(padH, padV, padH, padV)
        }
        rootTabRow = row

        val pinView = TextView(context).apply {
            text = "📌"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            visibility = View.GONE
            setPadding(0, 0, AndroidViewHandle.dpToPx(4), 0)
        }.also { pinIndicatorView = it }

        val imgView = AppCompatImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            visibility = View.GONE
        }.also { iconView = it }

        val dirtyView = TextView(context).apply {
            text = "•"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTypeface(null, Typeface.BOLD)
            visibility = View.GONE
            setPadding(AndroidViewHandle.dpToPx(2), 0, AndroidViewHandle.dpToPx(4), 0)
        }.also { dirtyIndicatorView = it }

        val titleTv = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
        }.also { titleLabelView = it }

        val editTv = AppCompatEditText(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_DONE
            visibility = View.GONE
            minWidth = AndroidViewHandle.dpToPx(64)
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    commitInlineTitleEdit()
                    true
                } else {
                    false
                }
            }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP) {
                    commitInlineTitleEdit()
                    true
                } else {
                    false
                }
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && isEditingTitle) {
                    commitInlineTitleEdit()
                }
            }
        }.also { titleEditView = it }

        val badgeTv = TextView(context).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.WHITE)
            visibility = View.GONE
            val hPad = AndroidViewHandle.dpToPx(6)
            val vPad = AndroidViewHandle.dpToPx(2)
            setPadding(hPad, vPad, hPad, vPad)
        }.also { badgeView = it }

        val closeBtn = AppCompatButton(context).apply {
            text = "✕"
            isAllCaps = false
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setBackgroundColor(Color.TRANSPARENT)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            val pad = AndroidViewHandle.dpToPx(4)
            setPadding(pad, 0, pad, 0)
            visibility = View.GONE
            setOnClickListener {
                dispatchCloseClicked()
            }
        }.also { closeButtonView = it }

        val customHost = FrameLayout(context).apply {
            visibility = View.GONE
        }.also {
            customHeaderHost = it
            nativeWidget.childHostViewGroup = it
        }

        val detector = runCatching {
            GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: MotionEvent): Boolean {
                    if (editableVal) {
                        beginInlineTitleEdit()
                        return true
                    }
                    return false
                }
            })
        }.getOrNull()
        titleTv.setOnTouchListener { _, event ->
            if (editableVal && detector != null) {
                detector.onTouchEvent(event)
            } else {
                false
            }
        }
        titleTv.setOnLongClickListener {
            if (editableVal) {
                beginInlineTitleEdit()
                true
            } else {
                false
            }
        }

        val gap = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            leftMargin = AndroidViewHandle.dpToPx(4)
        }
        row.addView(pinView)
        row.addView(imgView)
        row.addView(dirtyView)
        row.addView(titleTv)
        row.addView(editTv)
        row.addView(badgeTv, gap)
        row.addView(closeBtn, gap)
        row.addView(customHost, gap)
        return row
    }

    internal fun beginInlineTitleEdit() {
        if (!editableVal) return
        isEditingTitle = true
        val edit = titleEditView ?: return
        val label = titleLabelView ?: return
        edit.setText(titleVal)
        label.visibility = View.GONE
        edit.visibility = View.VISIBLE
        edit.requestFocus()
        edit.selectAll()
    }

    private fun commitInlineTitleEdit() {
        if (!isEditingTitle) return
        isEditingTitle = false
        val updated = titleEditView?.text?.toString()?.trim().orEmpty().ifEmpty { titleVal }
        titleEditView?.visibility = View.GONE
        titleLabelView?.visibility = View.VISIBLE
        if (updated != titleVal) {
            setTitleNative(updated)
            dispatchTitleEdited(updated)
        }
    }

    internal fun dispatchCloseClicked() {
        onCloseClickedListener?.invoke()
    }

    internal fun dispatchTitleEdited(newTitle: String) {
        onTitleEditedListener?.invoke(newTitle)
    }

    override fun setTabIdNative(id: String) {
        tabIdVal = id
        nativeWidget.attributes["tabId"] = id
    }

    override fun setTitleNative(title: String) {
        titleVal = title
        nativeWidget.text = title
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconNative(icon: String?) {
        iconVal = icon
        if (icon != null) {
            nativeWidget.attributes["icon"] = icon
        } else {
            nativeWidget.attributes.remove("icon")
        }
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconSizePxNative(sizePx: Int) {
        iconSizePxVal = sizePx.coerceAtLeast(1)
        nativeWidget.attributes["iconSizePx"] = iconSizePxVal.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setIconPlacementNative(placement: TabIconPlacement) {
        iconPlacementVal = placement
        nativeWidget.attributes["iconPlacement"] = placement.name
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setTintImageToThemeNative(tintToTheme: Boolean) {
        tintImageToThemeVal = tintToTheme
        nativeWidget.attributes["tintImageToTheme"] = tintToTheme.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setTooltipNative(tooltip: String?) {
        nativeWidget.tooltip = tooltip
    }

    override fun setClosableNative(closable: Boolean) {
        closableVal = closable
        nativeWidget.attributes["closable"] = closable.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setDirtyNative(dirty: Boolean) {
        dirtyVal = dirty
        nativeWidget.attributes["dirty"] = dirty.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setPinnedNative(pinned: Boolean) {
        pinnedVal = pinned
        nativeWidget.attributes["pinned"] = pinned.toString()
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setEditableNative(editable: Boolean) {
        editableVal = editable
        nativeWidget.attributes["editable"] = editable.toString()
        syncAndroidView()
    }

    override fun setBadgeNative(badgeText: String?, variantCss: String?) {
        badgeTextVal = badgeText
        badgeVariantVal = variantCss ?: "primary"
        if (badgeText != null) {
            nativeWidget.attributes["badgeText"] = badgeText
            nativeWidget.attributes["badgeVariant"] = badgeVariantVal
        } else {
            nativeWidget.attributes.remove("badgeText")
            nativeWidget.attributes.remove("badgeVariant")
        }
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setSelectedNative(selected: Boolean) {
        selectedVal = selected
        nativeWidget.attributes["selected"] = selected.toString()
        syncAndroidView()
    }

    override fun setCustomHeaderComponentNative(customImpl: NativeImpl<*>?) {
        customHeaderImpl?.let { removeChildNative(it) }
        customHeaderImpl = customImpl
        if (customImpl != null) {
            addChildNative(customImpl)
            nativeWidget.attributes["hasCustomView"] = "true"
        } else {
            nativeWidget.attributes["hasCustomView"] = "false"
        }
        syncAndroidView()
        onTabUpdated?.invoke()
    }

    override fun setOnCloseClicked(listener: (() -> Unit)?) {
        onCloseClickedListener = listener
    }

    override fun setOnTitleEdited(listener: ((String) -> Unit)?) {
        onTitleEditedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val row = rootTabRow ?: (nativeWidget.androidView as? LinearLayout)
                if (row != null && row.parent is AndroidFlexContainerView) {
                    row.visibility = View.GONE
                }
                val ctx = (nativeWidget.androidView?.context) ?: AndroidViewHandle.activeContext
                val isDark = AndroidViewHandle.isDarkTheme()
                val textColor = if (selectedVal) {
                    BootstrapColorPalette.resolveColor(BootstrapType.PRIMARY, isDark).toArgbInt()
                } else if (isDark) {
                    0xFFECEFF4.toInt()
                } else {
                    0xFF1C1B1F.toInt()
                }

                pinIndicatorView?.visibility = if (pinnedVal) View.VISIBLE else View.GONE
                dirtyIndicatorView?.apply {
                    visibility = if (dirtyVal) View.VISIBLE else View.GONE
                    setTextColor(BootstrapColorPalette.resolveColor(BootstrapType.WARNING, isDark).toArgbInt())
                }

                val hasCustom = customHeaderImpl != null
                customHeaderHost?.visibility = if (hasCustom) View.VISIBLE else View.GONE

                titleLabelView?.apply {
                    text = titleVal
                    setTextColor(textColor)
                    setTypeface(null, if (selectedVal) Typeface.BOLD else Typeface.NORMAL)
                    visibility = if (isEditingTitle || hasCustom) View.GONE else View.VISIBLE
                }

                val icon = iconVal
                val iv = iconView
                if (iv != null) {
                    if (!icon.isNullOrBlank() && ctx != null) {
                        val tintArgb = if (tintImageToThemeVal) textColor else null
                        val drawable = AndroidImageImpl.resolveDrawable(ctx, icon, tintArgb)
                        if (drawable != null) {
                            val px = AndroidViewHandle.dpToPx(iconSizePxVal).coerceAtLeast(1)
                            iv.layoutParams = LinearLayout.LayoutParams(px, px).apply {
                                rightMargin = AndroidViewHandle.dpToPx(6)
                            }
                            iv.setImageDrawable(drawable)
                            iv.visibility = View.VISIBLE
                        } else {
                            iv.visibility = View.GONE
                        }
                    } else {
                        iv.visibility = View.GONE
                    }
                }

                badgeView?.let { bv ->
                    val bText = badgeTextVal
                    if (!bText.isNullOrEmpty()) {
                        bv.text = bText
                        bv.visibility = View.VISIBLE
                        val badgeType = when (badgeVariantVal.lowercase()) {
                            "danger", "error" -> BootstrapType.DANGER
                            "success" -> BootstrapType.SUCCESS
                            "warning" -> BootstrapType.WARNING
                            "secondary" -> BootstrapType.SECONDARY
                            "info" -> BootstrapType.INFO
                            else -> BootstrapType.PRIMARY
                        }
                        val pillColor = BootstrapColorPalette.resolveColor(badgeType, isDark).toArgbInt()
                        bv.background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            cornerRadius = AndroidViewHandle.dpToPxFloat(10f)
                            setColor(pillColor)
                        }
                    } else {
                        bv.visibility = View.GONE
                    }
                }

                closeButtonView?.apply {
                    setTextColor(textColor)
                    visibility = if (closableVal && !pinnedVal) View.VISIBLE else View.GONE
                }
            }
        }
    }
}
