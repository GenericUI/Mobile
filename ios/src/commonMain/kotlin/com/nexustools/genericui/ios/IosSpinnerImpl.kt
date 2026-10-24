package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl

/**
 * iOS `UIActivityIndicatorView` implementation of [SpinnerNativeImpl].
 */
class IosSpinnerImpl : IosBaseNativeImpl("UIActivityIndicatorView", "spinner-border"), SpinnerNativeImpl<IosNativeView> {
    override fun setGrowStyle(grow: Boolean) {
        nativeWidget.metadata["style"] = if (grow) "large" else "medium"
    }

    override fun setLabel(label: String?) {
        nativeWidget.text = label ?: ""
    }
}
