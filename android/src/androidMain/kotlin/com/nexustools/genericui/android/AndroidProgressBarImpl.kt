package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl

/**
 * Android `com.google.android.material.progressindicator.LinearProgressIndicator` implementation of [ProgressBarNativeImpl].
 */
class AndroidProgressBarImpl : AndroidBaseNativeImpl("com.google.android.material.progressindicator.LinearProgressIndicator", "progress"), ProgressBarNativeImpl<AndroidViewHandle> {
    private var progressValue: Float = 0f
    private var indeterminateFlag: Boolean = false

    override fun setProgress(progress: Float) {
        progressValue = progress.coerceIn(0f, 1f)
    }

    override fun getProgress(): Float = progressValue

    override fun setIndeterminate(indeterminate: Boolean) {
        indeterminateFlag = indeterminate
    }

    override fun isIndeterminate(): Boolean = indeterminateFlag

    override fun setLabel(label: String?) {
        nativeWidget.text = label ?: ""
    }
}
