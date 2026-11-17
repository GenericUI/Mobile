package com.nexustools.genericui.android

import android.content.ContentResolver
import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import android.os.Looper
import com.nexustools.genericui.io.ByteOrder
import com.nexustools.genericui.jvm.JvmStreamFileReadableNativeImpl
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl]
 * extending [JvmStreamFileReadableNativeImpl] with constructors for Android `ContentResolver`,
 * `AssetManager`, [Context], [File], and [InputStream].
 */
class AndroidFileReadableNativeImpl(
    initialBytes: ByteArray = ByteArray(0),
    filePath: String? = null,
    byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN,
    onReadChunk: (suspend (Int) -> ByteArray?)? = null,
    onCloseCallback: (suspend () -> Unit)? = null
) : JvmStreamFileReadableNativeImpl(
    initialBytes = initialBytes,
    filePath = filePath,
    byteOrder = byteOrder,
    onReadChunk = onReadChunk,
    onCloseCallback = onCloseCallback
) {
    override val nativeApi: String = "ContentResolver_AssetManager_InputStream_Dispatchers_IO"

    constructor(
        inputStream: InputStream,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        streamOpener = { inputStream },
        filePath = filePath,
        byteOrder = byteOrder
    )

    constructor(
        streamOpener: () -> InputStream,
        filePath: String? = null,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        delegate = JvmStreamFileReadableNativeImpl(
            streamOpener = streamOpener,
            filePath = filePath,
            byteOrder = byteOrder,
            isOnMainThread = Companion::isMainThread
        ),
        filePath = filePath,
        byteOrder = byteOrder
    )

    constructor(
        file: File,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        delegate = JvmStreamFileReadableNativeImpl(
            file = file,
            byteOrder = byteOrder,
            isOnMainThread = Companion::isMainThread
        ),
        filePath = file.absolutePath,
        byteOrder = byteOrder
    )

    constructor(
        contentResolver: ContentResolver,
        uri: Uri,
        byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN
    ) : this(
        streamOpener = { contentResolver.openInputStream(uri) ?: ByteArrayInputStream(ByteArray(0)) },
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
        streamOpener = { assetManager.open(assetPath.trimStart('/')) },
        filePath = "file:///android_asset/${assetPath.trimStart('/')}",
        byteOrder = byteOrder
    )

    private var delegatedCloser: (() -> Unit)? = null

    private constructor(
        delegate: JvmStreamFileReadableNativeImpl,
        filePath: String?,
        byteOrder: ByteOrder
    ) : this(
        initialBytes = ByteArray(0),
        filePath = filePath,
        byteOrder = byteOrder,
        onReadChunk = { maxBytes ->
            val buf = delegate.read(maxBytes)
            buf?.toByteArray()
        },
        onCloseCallback = { delegate.close() }
    ) {
        this.delegatedCloser = { delegate.dispose() }
    }

    override fun dispose() {
        delegatedCloser?.invoke()
        delegatedCloser = null
        super.dispose()
    }

    private companion object {
        fun isMainThread(): Boolean = runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
}
