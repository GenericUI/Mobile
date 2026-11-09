package com.nexustools.genericui.ios

import com.nexustools.genericui.threading.DefaultUiThreadDispatcherNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.threading.UiThreadDispatcherNativeImpl].
 *
 * Separates UIKit main-queue rendering/widget updates (`dispatch_get_main_queue()`) from the
 * Logic/event-handler execution context ([isSplitThreadingEnabled] == `true`).
 */
class IosUiThreadDispatcherNativeImpl : DefaultUiThreadDispatcherNativeImpl(
    isSplitThreadingEnabled = true
)
