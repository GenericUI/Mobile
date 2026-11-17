package com.nexustools.genericui.android

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Looper
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.jvm.JvmStreamFileWritableNativeImpl
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl]
 * extending [JvmStreamFileWritableNativeImpl] with constructors for Android `ContentResolver`,
 * [Context], [File], and [OutputStream].
 */
class AndroidFileWritableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    appendMode: Boolean = false,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onWriteChunk: ((ByteArray, Int, Int) -> Int)? = null,
    onFlushCallback: (suspend (ByteArray) -> Unit)? = null,
    onCloseCallback: (suspend () -> Unit)? = null,
    onCommit: (suspend (ByteArray) -> Unit)? = onFlushCallback
) : JvmStreamFileWritableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    appendMode = appendMode,
    byteOrder = byteOrder,
    onWriteChunk = onWriteChunk,
    onFlushCallback = onFlushCallback,
    onCloseCallback = onCloseCallback,
    onCommit = onCommit
) {
    override val nativeApi: String = "ContentResolver_FileOutputStream_Dispatchers_IO"

    private var delegatedWriter: JvmStreamFileWritableNativeImpl? = null

    constructor(
        outputStream: OutputStream,
        filePath: String? = null,
        appendMode: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
        onCloseCommit: (suspend () -> Unit)? = null
    ) : this(
        delegate = JvmStreamFileWritableNativeImpl(
            outputStream = outputStream,
            filePath = filePath,
            appendMode = appendMode,
            byteOrder = byteOrder,
            isOnMainThread = Companion::isMainThread,
            onCommit = onCloseCommit
        ),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder
    )

    constructor(
        file: File,
        append: Boolean = false,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        delegate = JvmStreamFileWritableNativeImpl(
            streamOpener = {
                file.parentFile?.mkdirs()
                FileOutputStream(file, append)
            },
            filePath = file.absolutePath,
            appendMode = append,
            byteOrder = byteOrder,
            isOnMainThread = Companion::isMainThread
        ),
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
        delegate = JvmStreamFileWritableNativeImpl(
            streamOpener = {
                contentResolver.openOutputStream(uri, if (append) "wa" else "wt") ?: ByteArrayOutputStream()
            },
            filePath = uri.toString(),
            appendMode = append,
            byteOrder = byteOrder,
            isOnMainThread = Companion::isMainThread
        ),
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

    private constructor(
        delegate: JvmStreamFileWritableNativeImpl,
        filePath: String?,
        appendMode: Boolean,
        byteOrder: ByteOrder
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        appendMode = appendMode,
        byteOrder = byteOrder,
        onWriteChunk = { chunk, offset, length ->
            val before = delegate.bytesWritten
            delegate.write(chunk, offset, length)
            (delegate.bytesWritten - before).toInt()
        },
        onFlushCallback = { delegate.flush() },
        onCloseCallback = { delegate.close() },
        onCommit = null
    ) {
        this.delegatedWriter = delegate
    }

    override fun dispose() {
        delegatedWriter?.dispose()
        delegatedWriter = null
        super.dispose()
    }

    private companion object {
        fun isMainThread(): Boolean = runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
}
