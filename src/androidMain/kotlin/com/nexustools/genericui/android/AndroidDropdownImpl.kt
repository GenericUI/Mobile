package com.nexustools.genericui.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.PopupMenu
import com.nexustools.genericui.nativeimpl.action.DropdownNativeImpl

/**
 * Android `androidx.appcompat.widget.PopupMenu` implementation of [DropdownNativeImpl].
 */
class AndroidDropdownImpl : AndroidBaseContainerNativeImpl("com.google.android.material.button.MaterialButton", "dropdown"), DropdownNativeImpl<AndroidViewHandle> {
    private var labelText: String = ""
    private val dropdownItems = mutableListOf<Pair<String, String>>()
    private var onItemSelectedListener: ((String) -> Unit)? = null

    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AppCompatButton(context).apply {
            isAllCaps = false
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            setOnClickListener { v ->
                showDropdownPopup(v)
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

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
        runCatching {
            if (dropdownItems.isEmpty()) return@runCatching
            val popup = PopupMenu(anchor.context, anchor)
            dropdownItems.forEachIndexed { idx, pair ->
                popup.menu.add(Menu.NONE, idx, idx, pair.second)
            }
            popup.setOnMenuItemClickListener { item ->
                selectItemAt(item.itemId)
                true
            }
            popup.show()
        }
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val tv = nativeWidget.androidView as? TextView ?: return@runCatching
            val displayLabel = if (labelText.isNotEmpty()) "$labelText \u25BE" else "\u25BE"
            if (tv.text?.toString() != displayLabel) {
                tv.text = displayLabel
            }
            val isDark = AndroidViewHandle.isDarkTheme()
            val bgArgb = customStyleBackgroundColor?.toArgbInt()
                ?: if (isDark) 0xFF2B313E.toInt() else 0xFFE4E7EE.toInt()
            val fgArgb = customStyleTextColor?.toArgbInt()
                ?: if (isDark) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
            val borderArgb = customStyleBorderColor?.toArgbInt()
                ?: if (isDark) 0xFF3E4656.toInt() else 0xFFCDD2DE.toInt()
            tv.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = resolveEffectiveBorderRadiusPx(6f)
                setColor(bgArgb)
                setStroke(AndroidViewHandle.dpToPx(1).coerceAtLeast(1), borderArgb)
            }
            tv.setTextColor(fgArgb)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            val hPad = AndroidViewHandle.dpToPx(12)
            val vPad = AndroidViewHandle.dpToPx(7)
            tv.setPadding(hPad, vPad, hPad, vPad)
        }
    }
}
