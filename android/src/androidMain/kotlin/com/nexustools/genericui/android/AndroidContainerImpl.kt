package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.layout.ContainerNativeImpl

/**
 * Android `LinearLayout` / `FrameLayout` / `GridLayout` implementation of [ContainerNativeImpl].
 */
class AndroidContainerImpl(
    classPrefix: String = "container"
) : AndroidBaseContainerNativeImpl("android.widget.LinearLayout", classPrefix), ContainerNativeImpl<AndroidViewHandle>
