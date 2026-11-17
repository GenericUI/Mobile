package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

/**
 * Android 1dp divider view implementation of [DividerNativeImpl].
 */
class AndroidDividerImpl : AndroidBaseNativeImpl("com.google.android.material.divider.MaterialDivider", "dropdown-divider"), DividerNativeImpl<AndroidViewHandle> {
    init {
        nativeWidget.fillMaxWidth = true
        nativeWidget.bounds = nativeWidget.bounds.copy(height = 1)
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        View(context)

    override fun shouldApplyBaseBackgroundDrawable(): Boolean = false

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val view = nativeWidget.androidView ?: return@runCatching
            val isDark = AndroidViewHandle.isDarkTheme()
            val colorArgb = customStyleBackgroundColor?.toArgbInt()
                ?: customStyleBorderColor?.toArgbInt()
                ?: if (isDark) 0xFF2E3440.toInt() else 0xFFE0E4EC.toInt()
            view.setBackgroundColor(colorArgb)
            val hPx = AndroidViewHandle.dpToPx(1).coerceAtLeast(1)
            view.minimumHeight = hPx
        }
    }
}
