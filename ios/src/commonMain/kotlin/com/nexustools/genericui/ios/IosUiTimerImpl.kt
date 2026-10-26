package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.system.DefaultUiTimerNativeImpl
import com.nexustools.genericui.nativeimpl.system.UiTimerNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [UiTimerNativeImpl] backed by `NSTimer` / `DispatchQueue.main`
 * and [DefaultUiTimerNativeImpl].
 */
class IosUiTimerImpl : DefaultUiTimerNativeImpl()
