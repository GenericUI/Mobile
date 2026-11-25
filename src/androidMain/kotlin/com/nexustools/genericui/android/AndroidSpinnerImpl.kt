package com.nexustools.genericui.android

import android.content.Context
import android.view.View
import android.widget.ProgressBar
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl

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
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val pb = nativeWidget.androidView as? ProgressBar ?: return@runCatching
                pb.isIndeterminate = true
                pb.indeterminateTintList = null
            }
        }
    }
}
