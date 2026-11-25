package com.nexustools.genericui.android

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.PopupMenu
import android.widget.TextView
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android `androidx.appcompat.widget.AppCompatButton` + `PopupMenu` implementation of [DropdownNativeImpl].
 */
class AndroidDropdownImpl : AndroidBaseContainerNativeImpl("com.google.android.material.button.MaterialButton", "dropdown"), DropdownNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""
    private val dropdownItems = mutableListOf<Pair<String, String>>()
    private var onItemSelectedListener: ((String) -> Unit)? = null
    private var onPrimaryActionListener: (() -> Unit)? = null
    private var lastTouchX: Float = -1f
    private var hasTouchPosition: Boolean = false

    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AndroidControlTintRenderUtil.createNativeOrTintedButton(
            context = context,
            implProvider = { this }
        ).apply {
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN || event.actionMasked == MotionEvent.ACTION_UP) {
                    lastTouchX = event.x
                    hasTouchPosition = true
                }
                false
            }
            setOnClickListener { v ->
                val primary = onPrimaryActionListener
                val hitSplitCaret = primary != null &&
                    hasTouchPosition &&
                    dropdownItems.isNotEmpty() &&
                    v.width > 0 &&
                    lastTouchX >= (v.width - AndroidViewHandle.dpToPx(32)).coerceAtLeast(v.width * 2 / 3)
                hasTouchPosition = false
                when {
                    hitSplitCaret -> showDropdownPopup(v)
                    primary != null -> primary.invoke()
                    else -> showDropdownPopup(v)
                }
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun syncNativeBootstrapAppearance() {
        super.syncNativeBootstrapAppearance()
        AndroidControlTintRenderUtil.syncButtonBootstrapAttributes(
            nativeWidget = nativeWidget,
            type = BootstrapType.NONE,
            outline = false
        )
    }

    override fun setLabel(label: String) {
        labelText = label
        nativeWidget.text = label
        syncAndroidView()
    }

    fun getLabel(): String = labelText

    override fun setItems(items: List<Pair<String, String>>) {
        dropdownItems.clear()
        dropdownItems.addAll(items)
        nativeWidget.attributes["itemCount"] = items.size.toString()
        nativeWidget.attributes["itemLabels"] = items.joinToString("|") { it.second }
        syncAndroidView()
    }

    fun getItems(): List<Pair<String, String>> = dropdownItems

    override fun setOnItemSelected(listener: ((String) -> Unit)?) {
        onItemSelectedListener = listener
    }

    override fun setOnPrimaryAction(listener: (() -> Unit)?) {
        onPrimaryActionListener = listener
        nativeWidget.attributes["splitDropdown"] = (listener != null).toString()
        syncAndroidView()
    }

    fun triggerPrimaryAction(): Boolean {
        val listener = onPrimaryActionListener ?: return false
        listener()
        return true
    }

    fun selectItem(id: String): Boolean {
        val listener = onItemSelectedListener ?: return false
        listener(id)
        return true
    }

    fun selectItemAt(index: Int): Boolean {
        val entry = dropdownItems.getOrNull(index) ?: return false
        return selectItem(entry.first)
    }

    private fun showDropdownPopup(anchor: View) {
        if (dropdownItems.isEmpty()) return
        val popup = PopupMenu(anchor.context, anchor)
        dropdownItems.forEachIndexed { idx, pair ->
            popup.menu.add(Menu.NONE, idx, idx, pair.second)
        }
        popup.setOnMenuItemClickListener { item ->
            selectItemAt(item.itemId)
            true
        }
        try {
            popup.show()
        } catch (_: WindowManager.BadTokenException) {
            // Detached host views in unit tests have no window token for PopupMenu display.
        }
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            val tv = nativeWidget.androidView as? TextView ?: return@runOnMainThread
            tv.isAllCaps = false
            tv.gravity = Gravity.CENTER
            tv.includeFontPadding = false
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)

            val currentLabel = (labelText as String?) ?: nativeWidget.text
            val displayLabel = if (currentLabel.isNotEmpty()) "$currentLabel \u25BE" else "\u25BE"
            if (tv.text?.toString() != displayLabel) {
                tv.text = displayLabel
            }

            val isDark = AndroidViewHandle.isDarkTheme()
            val activeTheme = BootstrapColorPalette.activeThemeConfig
            val fgArgb = AndroidControlTintRenderUtil.resolveContrastingForeground(
                type = BootstrapType.NONE,
                darkMode = isDark,
                outline = false,
                themeConfig = activeTheme
            ).toArgbInt()

            tv.backgroundTintList = null
            tv.setTextColor(fgArgb)
            tv.invalidate()
            nativeWidget.onViewMutated?.invoke()
        }
    }
}
