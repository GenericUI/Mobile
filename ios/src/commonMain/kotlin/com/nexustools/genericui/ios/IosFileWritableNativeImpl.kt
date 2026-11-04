package com.nexustools.genericui.ios

import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileWritableNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl]
 * backed by Foundation `NSOutputStream` / `NSFileHandle` (`synchronizeFile`) on a GCD queue.
 */
class IosFileWritableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    appendMode: Boolean = false,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onWriteChunk: ((ByteArray, Int, Int) -> Int)? = null,
    onFlushCallback: (suspend (ByteArray) -> Unit)? = null,
    onCloseCallback: (suspend () -> Unit)? = null,
    onCommit: (suspend (ByteArray) -> Unit)? = null
) : DefaultFileWritableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    appendMode = appendMode,
    byteOrder = byteOrder,
    onWriteChunk = onWriteChunk,
    onFlushCallback = onFlushCallback,
    onCloseCallback = onCloseCallback,
    onCommit = onCommit
) {
    val nativeApi: String = "Foundation_NSOutputStream_NSFileHandle_GCD"

    var flushCount: Int = 0
        private set

    override suspend fun flush() {
        if (!isDisposed) {
            flushCount++
        }
        super.flush()
    }
}
