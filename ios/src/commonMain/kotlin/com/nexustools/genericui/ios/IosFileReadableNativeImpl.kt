package com.nexustools.genericui.ios

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileReadableNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl]
 * backed by Foundation `NSInputStream` / `NSFileHandle` on a GCD queue.
 */
class IosFileReadableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onReadChunk: (suspend (Int) -> ByteArray?)? = null,
    onCloseCallback: (suspend () -> Unit)? = null
) : DefaultFileReadableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    byteOrder = byteOrder,
    onReadChunk = onReadChunk,
    onCloseCallback = onCloseCallback
) {
    val nativeApi: String = "Foundation_NSInputStream_NSFileHandle_GCD"
}
