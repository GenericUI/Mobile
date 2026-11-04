package com.nexustools.genericui.android

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.nativeimpl.io.DefaultFileReadableNativeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl]
 * backed by `ContentResolver.openInputStream(uri)`, `AssetManager.open(assetPath)`, or `FileInputStream`
 * executing non-blocking chunk reads on [Dispatchers.IO].
 */
class AndroidFileReadableNativeImpl(
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
    val nativeApi: String = "ContentResolver_AssetManager_InputStream_Dispatchers_IO"

    constructor(
        inputStream: InputStream,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        byteOrder = byteOrder,
        onReadChunk = { maxBytes ->
            withContext(Dispatchers.IO) {
                val target = maxBytes.coerceAtLeast(1)
                val buf = ByteArray(target)
                val n = inputStream.read(buf, 0, target)
                if (n <= 0) {
                    runCatching { inputStream.close() }
                    null
                } else if (n == target) {
                    buf
                } else {
                    buf.copyOfRange(0, n)
                }
            }
        },
        onCloseCallback = {
            withContext(Dispatchers.IO) {
                runCatching { inputStream.close() }
            }
        }
    )

    constructor(
        file: File,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        inputStream = FileInputStream(file),
        filePath = file.absolutePath,
        byteOrder = byteOrder
    )

    constructor(
        contentResolver: ContentResolver,
        uri: Uri,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        inputStream = contentResolver.openInputStream(uri) ?: ByteArrayInputStream(ByteArray(0)),
        filePath = uri.toString(),
        byteOrder = byteOrder
    )

    constructor(
        context: Context,
        uri: Uri,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        contentResolver = context.contentResolver,
        uri = uri,
        byteOrder = byteOrder
    )

    constructor(
        assetManager: AssetManager,
        assetPath: String,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        inputStream = assetManager.open(assetPath.trimStart('/')),
        filePath = assetPath,
        byteOrder = byteOrder
    )
}
