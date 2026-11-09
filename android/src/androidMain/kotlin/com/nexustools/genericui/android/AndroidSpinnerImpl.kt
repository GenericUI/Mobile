package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ProgressBar
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android circular `android.widget.ProgressBar` implementation of [SpinnerNativeImpl].
 */
class AndroidSpinnerImpl : AndroidBaseNativeImpl("com.google.android.material.progressindicator.CircularProgressIndicator", "spinner-border"), SpinnerNativeImpl<AndroidViewHandle> {
    init {
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        ProgressBar(context).apply {
            isIndeterminate = true
        }

    override fun setGrowStyle(grow: Boolean) {
        if (grow) {
            nativeWidget.classes.remove("spinner-border")
            nativeWidget.classes.add("spinner-grow")
        } else {
            nativeWidget.classes.remove("spinner-grow")
            nativeWidget.classes.add("spinner-border")
        }
        syncAndroidView()
    }

    override fun setLabel(label: String?) {
        nativeWidget.tagId = label ?: ""
        syncAndroidView()
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val pb = nativeWidget.androidView as? ProgressBar ?: return@runCatching
            pb.isIndeterminate = true
            val isDark = AndroidViewHandle.isDarkTheme()
            val resolvedType = getType()?.takeIf { it != BootstrapType.NONE } ?: BootstrapType.PRIMARY
            val tintArgb = BootstrapColorPalette.resolveColor(resolvedType, isDark, BootstrapColorPalette.activeThemeConfig).toArgbInt()
            pb.indeterminateTintList = ColorStateList.valueOf(tintArgb)
        }
    }
}
