package com.nexustools.genericui.android

import com.nexustools.genericui.nativeimpl.layout.DividerNativeImpl

/**
 * Android `com.google.android.material.divider.MaterialDivider` implementation of [DividerNativeImpl].
 */
class AndroidDividerImpl : AndroidBaseNativeImpl("com.google.android.material.divider.MaterialDivider", "dropdown-divider"), DividerNativeImpl<AndroidViewHandle>
