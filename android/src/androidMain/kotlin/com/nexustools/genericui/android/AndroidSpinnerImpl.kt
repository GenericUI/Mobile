package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl

/**
 * Android `com.google.android.material.progressindicator.CircularProgressIndicator` implementation of [SpinnerNativeImpl].
 */
class AndroidSpinnerImpl : AndroidBaseNativeImpl("com.google.android.material.progressindicator.CircularProgressIndicator", "spinner-border"), SpinnerNativeImpl<AndroidViewHandle> {
    override fun setGrowStyle(grow: Boolean) {
        if (grow) {
            nativeWidget.classes.remove("spinner-border")
            nativeWidget.classes.add("spinner-grow")
        } else {
            nativeWidget.classes.remove("spinner-grow")
            nativeWidget.classes.add("spinner-border")
        }
    }

    override fun setLabel(label: String?) {
        nativeWidget.contentDescription = label ?: ""
    }
}
