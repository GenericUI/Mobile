package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * iOS `UIStackView` / `UIView` implementation of [ContainerNativeImpl].
 */
class IosContainerImpl(
    classPrefix: String = "container"
) : IosBaseContainerNativeImpl("UIStackView", classPrefix), ContainerNativeImpl<IosNativeView>
