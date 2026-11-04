package com.nexustools.genericui.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileWritableNativeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl]
 * backed by `ContentResolver.openOutputStream(uri, mode)` or `FileOutputStream` executing writes and flushes
 * on [Dispatchers.IO].
 */
class AndroidFileWritableNativeImpl(
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
    val nativeApi: String = "ContentResolver_FileOutputStream_Dispatchers_IO"

    var flushCount: Int = 0
        private set

    constructor(
        outputStream: OutputStream,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        onCloseCommit: (suspend () -> Unit)? = null
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder,
        onWriteChunk = { chunk, offset, length ->
            outputStream.write(chunk, offset, length)
            length
        },
        onFlushCallback = {
            withContext(Dispatchers.IO) {
                outputStream.flush()
            }
        },
        onCloseCallback = {
            withContext(Dispatchers.IO) {
                runCatching { outputStream.flush() }
                runCatching { outputStream.close() }
            }
            onCloseCommit?.invoke()
        }
    )

    constructor(
        file: File,
        append: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        outputStream = run {
            file.parentFile?.mkdirs()
            FileOutputStream(file, append)
        },
        filePath = file.absolutePath,
        appendMode = append,
        byteOrder = byteOrder
    )

    constructor(
        contentResolver: ContentResolver,
        uri: Uri,
        append: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        outputStream = contentResolver.openOutputStream(uri, if (append) "wa" else "wt")
            ?: ByteArrayOutputStream(),
        filePath = uri.toString(),
        appendMode = append,
        byteOrder = byteOrder
    )

    constructor(
        context: Context,
        uri: Uri,
        append: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        contentResolver = context.contentResolver,
        uri = uri,
        append = append,
        byteOrder = byteOrder
    )

    override suspend fun flush() {
        if (!isDisposed) {
            flushCount++
        }
        super.flush()
    }
}
