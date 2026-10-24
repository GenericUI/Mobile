package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl

/**
 * iOS `UIProgressView` implementation of [ProgressBarNativeImpl].
 */
class IosProgressBarImpl : IosBaseNativeImpl("UIProgressView", "progress"), ProgressBarNativeImpl<IosNativeView> {
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
