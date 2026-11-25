@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl
import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake

/**
 * Standard non-skinned iOS / iPadOS container backed by a genuine [UikitFlexContainerView].
 * Silently ignores aesthetic styling and typography CSS per backend contract.
 */
open class IosContainerImpl(
    frame: CValue<CGRect> = CGRectMake(0.0, 0.0, 0.0, 0.0),
    classPrefix: String = "container",
    tagName: String = ""
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UikitFlexContainerView(frame)),
    componentClassPrefix = classPrefix,
    initialTagName = tagName
), ContainerNativeImpl<UikitViewHandle>
