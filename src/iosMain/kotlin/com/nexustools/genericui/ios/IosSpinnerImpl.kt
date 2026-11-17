@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.AlignItems
import com.nexustools.genericui.enums.bootstrap.FlexDirection
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.feedback.SpinnerNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityIndicatorView
import platform.UIKit.UIActivityIndicatorViewStyleLarge
import platform.UIKit.UIActivityIndicatorViewStyleMedium
import platform.UIKit.UIFont
import platform.UIKit.UILabel

/**
 * Native UIKit `UIActivityIndicatorView` implementation of [SpinnerNativeImpl].
 */
class IosSpinnerImpl : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UikitFlexContainerView(CGRectMake(0.0, 0.0, 120.0, 40.0)).apply {
            flexDirection = FlexDirection.ROW
            alignItems = AlignItems.CENTER
            itemGapPt = 8.0
        }
    ),
    componentClassPrefix = "spinner"
), SpinnerNativeImpl<UikitViewHandle> {

    val activityIndicator: UIActivityIndicatorView = UIActivityIndicatorView(
        activityIndicatorStyle = UIActivityIndicatorViewStyleMedium
    ).apply {
        hidesWhenStopped = false
        startAnimating()
    }
    private var captionLabel: UILabel? = null

    init {
        val host = uiView as UikitFlexContainerView
        host.addSubview(activityIndicator)
        host.setChildPreferredSize(activityIndicator, 28.0, 28.0)
    }

    override fun setGrowStyle(grow: Boolean) {
        activityIndicator.activityIndicatorViewStyle = if (grow) {
            UIActivityIndicatorViewStyleLarge
        } else {
            UIActivityIndicatorViewStyleMedium
        }
    }

    override fun setLabel(label: String?) {
        val host = uiView as UikitFlexContainerView
        val clean = label?.takeIf { it.isNotEmpty() }
        if (clean == null) {
            captionLabel?.removeFromSuperview()
            captionLabel = null
        } else {
            val lbl = captionLabel ?: UILabel(frame = CGRectMake(0.0, 0.0, 100.0, 20.0)).apply {
                font = UIFont.systemFontOfSize(13.0)
                host.addSubview(this)
                captionLabel = this
            }
            lbl.text = clean
        }
    }

    override fun onBootstrapAppearanceChanged() {
        UikitGeometryAndColorUtil.resolveBootstrapColor(getType())?.toUiColor()?.let {
            activityIndicator.color = it
        }
    }
}
