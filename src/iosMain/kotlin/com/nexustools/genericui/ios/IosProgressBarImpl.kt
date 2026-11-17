@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.feedback.ProgressBarNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleMedium
import platform.UIKit.UIFont
import platform.UIKit.UILabel
import platform.UIKit.UIProgressView

/**
 * Native UIKit `UIProgressView` (plus indeterminate `UIActivityIndicatorView` & optional `UILabel`)
 * implementation of [ProgressBarNativeImpl].
 */
class IosProgressBarImpl : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 240.0, 28.0)).apply {
            flexDirection = FlexDirection.COLUMN
            itemGapPt = 4.0
        }
    ),
    componentClassPrefix = "progress"
), ProgressBarNativeImpl<UikitViewHandle> {

    val progressView: UIProgressView = UIProgressView(frame = CGRectMake(0.0, 0.0, 240.0, 6.0))
    private val activityIndicator = UIActivityIndicatorView(activityIndicatorStyle = UIActivityIndicatorViewStyleMedium)
    private var captionLabel: UILabel? = null

    private var progressValue: Float = 0f
    private var indeterminateMode: Boolean = false
    private var busyMode: Boolean = false

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(progressView)
        host.setChildPreferredSize(progressView, 240.0, 6.0)
        activityIndicator.hidden = true
        host.addSubview(activityIndicator)
    }

    override fun setProgress(progress: Float) {
        progressValue = progress.coerceIn(0f, 1f)
        progressView.setProgress(progressValue, animated = false)
    }

    override fun getProgress(): Float = progressValue

    override fun setIndeterminate(indeterminate: Boolean) {
        indeterminateMode = indeterminate
        syncActivityState()
    }

    override fun isIndeterminate(): Boolean = indeterminateMode

    override fun setBusy(busy: Boolean) {
        busyMode = busy
        syncActivityState()
    }

    override fun isBusy(): Boolean = busyMode

    override fun setLabel(label: String?) {
        val host = uiView as UikitFlexContainerView
        val clean = label?.takeIf { it.isNotEmpty() }
        if (clean == null) {
            captionLabel?.removeFromSuperview()
            captionLabel = null
        } else {
            val lbl = captionLabel ?: UILabel(frame = CGRectMake(0.0, 0.0, 240.0, 18.0)).apply {
                font = UIFont.systemFontOfSize(12.0)
                host.addSubview(this)
                captionLabel = this
            }
            lbl.text = clean
        }
    }

    override fun onBootstrapAppearanceChanged() {
        val tint = UikitGeometryAndColorUtil.resolveBootstrapColor(getType())?.toUiColor()
        progressView.progressTintColor = tint
        tint?.let { activityIndicator.color = it }
    }

    private fun syncActivityState() {
        val spinning = indeterminateMode || busyMode
        progressView.hidden = spinning
        activityIndicator.hidden = !spinning
        if (spinning) activityIndicator.startAnimating() else activityIndicator.stopAnimating()
    }
}
