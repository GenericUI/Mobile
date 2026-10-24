package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

/**
 * iOS 1pt hairline separator `UIView` implementation of [DividerNativeImpl].
 */
class IosDividerImpl : IosBaseNativeImpl("UIView", "dropdown-divider"), DividerNativeImpl<IosNativeView>
