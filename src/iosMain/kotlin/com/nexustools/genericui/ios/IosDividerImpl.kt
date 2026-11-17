@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIColor
import platform.UIKit.UIView
import platform.UIKit.separatorColor

/**
 * Native UIKit horizontal/vertical hairline divider backed by a `UIView` using `UIColor.separatorColor`.
 */
class IosDividerImpl : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(
        UIView(frame = CGRectMake(0.0, 0.0, 200.0, 1.0)).apply {
            backgroundColor = UIColor.separatorColor
        }
    ),
    componentClassPrefix = "divider"
), DividerNativeImpl<UikitViewHandle>
