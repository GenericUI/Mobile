package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.ProgressBar
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import com.nexustools.genericui.style.color.BootstrapColorPalette

/**
 * Android horizontal `android.widget.ProgressBar` implementation of [ProgressBarNativeImpl].
 */
class AndroidProgressBarImpl : AndroidBaseNativeImpl("com.google.android.material.progressindicator.LinearProgressIndicator", "progress"), ProgressBarNativeImpl<AndroidViewHandle> {
    private var progressValue: Float = 0f
    private var indeterminateFlag: Boolean = false
    private var busyFlag: Boolean = false

    init {
        nativeWidget.fillMaxWidth = true
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View =
        ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 1000
        }

    override fun setProgress(progress: Float) {
        progressValue = progress.coerceIn(0f, 1f)
        syncAndroidView()
    }

    override fun getProgress(): Float = progressValue

    override fun setIndeterminate(indeterminate: Boolean) {
        indeterminateFlag = indeterminate
        syncAndroidView()
    }

    override fun isIndeterminate(): Boolean = indeterminateFlag

    override fun setBusy(busy: Boolean) {
        busyFlag = busy
        syncAndroidView()
    }

    override fun isBusy(): Boolean = busyFlag

    override fun setLabel(label: String?) {
        nativeWidget.text = label ?: ""
        syncAndroidView()
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        runCatching {
            val pb = nativeWidget.androidView as? ProgressBar ?: return@runCatching
            pb.max = 1000
            val activeIndeterminate = indeterminateFlag || busyFlag
            pb.isIndeterminate = activeIndeterminate
            if (!activeIndeterminate) {
                pb.progress = (progressValue * 1000f).toInt().coerceIn(0, 1000)
            }
            val isDark = AndroidViewHandle.isDarkTheme()
            val resolvedType = getType()?.takeIf { it != BootstrapType.NONE } ?: BootstrapType.PRIMARY
            val tintArgb = BootstrapColorPalette.resolveColor(resolvedType, isDark, BootstrapColorPalette.activeThemeConfig).toArgbInt()
            val tintList = ColorStateList.valueOf(tintArgb)
            pb.progressTintList = tintList
            pb.indeterminateTintList = tintList
        }
    }
}
