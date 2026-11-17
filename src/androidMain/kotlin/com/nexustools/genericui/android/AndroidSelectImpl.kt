package com.nexustools.genericui.android

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.appcompat.widget.AppCompatSpinner
import com.nexustools.genericui.nativeimpl.form.SelectNativeImpl

/**
 * Android `androidx.appcompat.widget.AppCompatSpinner` implementation of [SelectNativeImpl].
 */
class AndroidSelectImpl : AndroidBaseNativeImpl("androidx.appcompat.widget.AppCompatSpinner", "form-select"), SelectNativeImpl<AndroidViewHandle> {
    private var optionsList: List<Pair<String, String>> = emptyList()
    private var selectedValue: String? = null
    private var onSelectionChangedListener: ((String) -> Unit)? = null
    private var suppressSelectionListener: Boolean = false
    private var lastSyncedLabels: List<String> = emptyList()

    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        AppCompatSpinner(context, Spinner.MODE_DROPDOWN).apply {
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    if (suppressSelectionListener) return
                    val value = optionsList.getOrNull(position)?.first ?: return
                    if (selectedValue != value) {
                        selectedValue = value
                        onSelectionChangedListener?.invoke(value)
                    }
                }

                override fun onNothingSelected(parent: AdapterView<*>?) = Unit
            }
        }

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun setOptions(options: List<Pair<String, String>>) {
        optionsList = options.toList()
        if (selectedValue == null && optionsList.isNotEmpty()) {
            selectedValue = optionsList.first().first
        }
        syncAndroidView()
    }

    override fun setSelectedValue(value: String?) {
        selectedValue = value
        syncAndroidView()
        if (value != null) {
            onSelectionChangedListener?.invoke(value)
        }
    }

    override fun getSelectedValue(): String? = selectedValue

    override fun setOnSelectionChanged(listener: ((String) -> Unit)?) {
        onSelectionChangedListener = listener
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val spinner = nativeWidget.androidView as? Spinner ?: return@runCatching
                val isDark = AndroidViewHandle.isDarkTheme()
                val bgArgb = customStyleBackgroundColor?.toArgbInt()
                    ?: if (isDark) 0xFF161920.toInt() else 0xFFFFFFFF.toInt()
                val borderArgb = customStyleBorderColor?.toArgbInt()
                    ?: if (isDark) 0xFF343B4A.toInt() else 0xFFCBD2DF.toInt()
                val bg = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = resolveEffectiveBorderRadiusPx(6f)
                    setColor(bgArgb)
                    val strokePx = AndroidViewHandle.dpToPx((customStyleBorderWidthPx ?: 1f).coerceAtLeast(1f)).coerceAtLeast(1)
                    setStroke(strokePx, borderArgb)
                }
                spinner.background = bg
                spinner.minimumHeight = AndroidViewHandle.dpToPx(38)

                val p = getPadding()
                if (p.left == 0 && p.top == 0 && p.right == 0 && p.bottom == 0 && getPaddingScale() == null) {
                    val hPad = AndroidViewHandle.dpToPx(8)
                    val vPad = AndroidViewHandle.dpToPx(4)
                    spinner.setPadding(hPad, vPad, hPad, vPad)
                }

                val labels = optionsList.map { it.second }
                suppressSelectionListener = true
                try {
                    if (labels != lastSyncedLabels || spinner.adapter == null) {
                        lastSyncedLabels = labels
                        val adapter = ArrayAdapter(
                            spinner.context,
                            android.R.layout.simple_spinner_item,
                            labels
                        )
                        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = adapter
                    }
                    val idx = optionsList.indexOfFirst { it.first == selectedValue }
                    if (idx >= 0 && spinner.selectedItemPosition != idx) {
                        spinner.setSelection(idx, false)
                    }
                } finally {
                    suppressSelectionListener = false
                }
            }
        }
    }
}
