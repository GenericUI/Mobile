package com.nexustools.genericui.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.FileObserver
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileStat
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.jvm.JvmFileSystemBase
import com.nexustools.genericui.jvm.JvmStreamFileReadableNativeImpl
import com.nexustools.genericui.jvm.JvmStreamFileWritableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import java.io.File

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl] extending
 * [JvmFileSystemBase] with Storage Access Framework (`content://`), `AssetManager`, [FileObserver]
 * directory watching, and [AndroidActivityRegistry] SAF document picker integration.
 */
class AndroidFileSystemNativeImpl(
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : JvmFileSystemBase(
    isOnMainThread = {
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
) {
    private val activeContext: Context?
        get() = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext

    private val activeObservers = LinkedHashMap<FileObserver, String>()
    private val mutationListeners = mutableListOf<(path: String, eventKind: String) -> Unit>()

    override fun isAvailable(): FileSystemAvailability = FileSystemAvailability.WithPermission

    override fun hasUriPermission(path: String, write: Boolean): FilePermissionStatus? {
        if (isAppSandboxPath(path)) {
            return FilePermissionStatus.GRANTED
        }
        if (path.startsWith("content://")) {
            val ctx = activeContext ?: return FilePermissionStatus.PROMPT_REQUIRED
            val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return FilePermissionStatus.PROMPT_REQUIRED
            val persisted = runCatching { ctx.contentResolver.persistedUriPermissions }.getOrDefault(emptyList())
            for (perm in persisted) {
                if (perm.uri == uri) {
                    if (!write && perm.isReadPermission) return FilePermissionStatus.GRANTED
                    if (write && perm.isWritePermission) return FilePermissionStatus.GRANTED
                }
            }
            return FilePermissionStatus.GRANTED
        }
        return if (isAssetPath(path)) FilePermissionStatus.GRANTED else FilePermissionStatus.PROMPT_REQUIRED
    }

    override suspend fun requestPermission(path: String?, write: Boolean): FilePermissionStatus {
        val trimmed = path?.trim().orEmpty()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            if (uri != null) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
                runCatching { ctx.contentResolver.takePersistableUriPermission(uri, flags) }
                return FilePermissionStatus.GRANTED
            }
        }
        return super.requestPermission(path, write)
    }

    override suspend fun requestDirectoryAccess(initialPath: String?): String? {
        if (isDisposed) return null
        val uris = AndroidActivityRegistry.launchSafDocumentPicker(
            FileBrowserRequest(
                mode = FileBrowserMode.SELECT_FOLDER,
                title = "Select Directory",
                initialDirectory = initialPath
            )
        )
        val picked = uris?.firstOrNull()
        if (picked != null) {
            persistUriPermission(picked, write = true)
            return picked
        }
        return super.requestDirectoryAccess(initialPath)
    }

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? {
        val uris = AndroidActivityRegistry.launchSafDocumentPicker(
            FileBrowserRequest(
                mode = FileBrowserMode.OPEN_SINGLE_FILE,
                title = title,
                initialDirectory = initialDirectory,
                filters = filters
            )
        )
        val picked = uris?.firstOrNull()
        if (picked != null) {
            persistUriPermission(picked, write = false)
            val stream = createReadStream(picked) ?: return null
            return picked to stream
        }
        return super.openReadableFromFileDialog(title, initialDirectory, filters)
    }

    override suspend fun openReadablesFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): List<Pair<String, FileReadableNativeImpl>> {
        val uris = AndroidActivityRegistry.launchSafDocumentPicker(
            FileBrowserRequest(
                mode = FileBrowserMode.OPEN_MULTIPLE_FILES,
                title = title,
                initialDirectory = initialDirectory,
                filters = filters
            )
        )
        if (!uris.isNullOrEmpty()) {
            return uris.mapNotNull { uri ->
                persistUriPermission(uri, write = false)
                createReadStream(uri)?.let { uri to it }
            }
        }
        return super.openReadablesFromFileDialog(title, initialDirectory, filters)
    }

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? {
        val uris = AndroidActivityRegistry.launchSafDocumentPicker(
            FileBrowserRequest(
                mode = FileBrowserMode.SAVE_FILE,
                title = title,
                initialDirectory = initialDirectory,
                defaultFileName = suggestedFileName,
                filters = filters
            )
        )
        val picked = uris?.firstOrNull()
        if (picked != null) {
            persistUriPermission(picked, write = true)
            val stream = createWriteStream(picked, FileOpenMode.WRITE_TRUNCATE)
            return picked to stream
        }
        return super.openWritableFromFileDialog(title, initialDirectory, suggestedFileName, filters)
    }

    override suspend fun openReadable(path: String): AndroidFileReadableNativeImpl? =
        createReadStream(path)

    override suspend fun openWritable(path: String, append: Boolean): AndroidFileWritableNativeImpl =
        createWriteStream(path, if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE)

    override suspend fun createReadStream(path: String): AndroidFileReadableNativeImpl? {
        val base = super.createReadStream(path) ?: return null
        return base as? AndroidFileReadableNativeImpl ?: AndroidFileReadableNativeImpl(
            initialBytes = base.read(Int.MAX_VALUE)?.toByteArray() ?: ByteArray(0),
            filePath = base.filePath
        )
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): AndroidFileWritableNativeImpl {
        val base = super.createWriteStream(path, mode)
        if (base is AndroidFileWritableNativeImpl) return base
        val append = mode == FileOpenMode.WRITE_APPEND
        val norm = normalizeKey(path)
        val initial = if (append) files[norm] ?: ByteArray(0) else ByteArray(0)
        return AndroidFileWritableNativeImpl(
            initialBytes = initial,
            filePath = norm,
            appendMode = append,
            onCommit = { committed ->
                super.writeFile(norm, committed)
            }
        )
    }

    override suspend fun openUriReadStream(path: String): JvmStreamFileReadableNativeImpl? {
        val ctx = activeContext ?: return null
        if (path.startsWith("content://")) {
            val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return null
            return AndroidFileReadableNativeImpl(ctx.contentResolver, uri)
        }
        if (isAssetPath(path)) {
            return AndroidFileReadableNativeImpl(ctx.assets, extractAssetRelativePath(path))
        }
        return null
    }

    override suspend fun openUriWriteStream(path: String, append: Boolean): JvmStreamFileWritableNativeImpl? {
        val ctx = activeContext ?: return null
        if (path.startsWith("content://")) {
            val uri = runCatching { Uri.parse(path) }.getOrNull() ?: return null
            return AndroidFileWritableNativeImpl(ctx.contentResolver, uri, append = append)
        }
        return null
    }

    override fun createDiskReadStream(file: File): JvmStreamFileReadableNativeImpl =
        AndroidFileReadableNativeImpl(file)

    override fun createDiskWriteStream(file: File, append: Boolean): JvmStreamFileWritableNativeImpl =
        AndroidFileWritableNativeImpl(file, append = append)

    override fun readUriBytes(path: String): ByteArray? {
        val ctx = activeContext ?: return null
        if (path.startsWith("content://")) {
            return runCatching {
                ctx.contentResolver.openInputStream(Uri.parse(path))?.use { it.readBytes() }
            }.getOrNull()
        }
        if (isAssetPath(path)) {
            val assetRel = extractAssetRelativePath(path)
            return runCatching {
                ctx.assets.open(assetRel).use { it.readBytes() }
            }.getOrNull()
        }
        return null
    }

    override fun writeUriBytes(path: String, bytes: ByteArray, append: Boolean): Boolean? {
        if (!path.startsWith("content://")) return null
        val ctx = activeContext ?: return null
        return runCatching {
            val mode = if (append) "wa" else "wt"
            ctx.contentResolver.openOutputStream(Uri.parse(path), mode)?.use { out ->
                out.write(bytes)
                out.flush()
                true
            } ?: false
        }.getOrDefault(false)
    }

    override fun existsUri(path: String): Boolean? {
        if (!path.startsWith("content://")) return null
        val ctx = activeContext ?: return null
        return runCatching {
            ctx.contentResolver.query(Uri.parse(path), arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                c.moveToFirst()
            } ?: false
        }.getOrDefault(false)
    }

    override fun statUri(path: String): FileStat? {
        if (!path.startsWith("content://")) return null
        val ctx = activeContext ?: return null
        return runCatching {
            val uri = Uri.parse(path)
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    OpenableColumns.DISPLAY_NAME,
                    OpenableColumns.SIZE,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED
                ),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                val mimeIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val modIdx = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) ?: path else path
                val size = if (sizeIdx >= 0 && !cursor.isNull(sizeIdx)) cursor.getLong(sizeIdx) else 0L
                val mime = if (mimeIdx >= 0) cursor.getString(mimeIdx) else null
                val isDir = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val mod = if (modIdx >= 0 && !cursor.isNull(modIdx)) cursor.getLong(modIdx) else 0L
                FileStat(
                    path = path,
                    name = name,
                    size = size,
                    isFile = !isDir,
                    isDirectory = isDir,
                    lastModifiedEpochMs = mod,
                    createdEpochMs = mod
                )
            }
        }.getOrNull()
    }

    override fun unlinkUri(path: String): Boolean? {
        if (!path.startsWith("content://")) return null
        val ctx = activeContext ?: return null
        return runCatching {
            DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(path))
        }.getOrDefault(false)
    }

    override fun onDiskFileMutated(file: File, eventKind: String) {
        val snapshot = synchronized(mutationListeners) { mutationListeners.toList() }
        for (listener in snapshot) {
            listener(file.absolutePath, eventKind)
        }
    }

    /**
     * Watches a local directory path using Android's `android.os.FileObserver` and returns an
     * [AutoCloseable] handle to stop watching.
     */
    fun watchDirectory(
        directoryPath: String,
        onEvent: (eventKind: String, targetPath: String) -> Unit
    ): AutoCloseable {
        val localDir = resolveLocalFile(directoryPath.trim(), mustExist = false)
            ?: return AutoCloseable {}
        runCatching { localDir.mkdirs() }
        val mask = FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MODIFY or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO
        val listener: (String, String) -> Unit = { path, kind ->
            if (path.startsWith(localDir.absolutePath)) {
                onEvent(kind, path)
            }
        }
        synchronized(mutationListeners) {
            mutationListeners.add(listener)
        }

        val observer: FileObserver? = runCatching {
            val basePath = localDir.absolutePath
            val obs = if (Build.VERSION.SDK_INT >= 29) {
                object : FileObserver(localDir, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        val full = if (path.isNullOrEmpty()) basePath else File(localDir, path).absolutePath
                        onEvent(mapObserverEvent(event), full)
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                object : FileObserver(basePath, mask) {
                    override fun onEvent(event: Int, path: String?) {
                        val full = if (path.isNullOrEmpty()) basePath else File(localDir, path).absolutePath
                        onEvent(mapObserverEvent(event), full)
                    }
                }
            }
            obs.startWatching()
            synchronized(activeObservers) {
                activeObservers[obs] = basePath
            }
            obs
        }.getOrNull()

        return AutoCloseable {
            synchronized(mutationListeners) {
                mutationListeners.remove(listener)
            }
            if (observer != null) {
                runCatching { observer.stopWatching() }
                synchronized(activeObservers) {
                    activeObservers.remove(observer)
                }
            }
        }
    }

    override fun dispose() {
        val observers = synchronized(activeObservers) {
            val list = activeObservers.keys.toList()
            activeObservers.clear()
            list
        }
        for (obs in observers) {
            runCatching { obs.stopWatching() }
        }
        synchronized(mutationListeners) {
            mutationListeners.clear()
        }
        super.dispose()
    }

    private fun persistUriPermission(uriString: String, write: Boolean) {
        val ctx = activeContext ?: return
        runCatching {
            val uri = Uri.parse(uriString)
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0
            ctx.contentResolver.takePersistableUriPermission(uri, flags)
        }
    }

    private fun mapObserverEvent(event: Int): String = when (event and FileObserver.ALL_EVENTS) {
        FileObserver.CREATE -> "create"
        FileObserver.DELETE -> "delete"
        FileObserver.MODIFY -> "modify"
        FileObserver.MOVED_FROM -> "moved_from"
        FileObserver.MOVED_TO -> "moved_to"
        else -> "change"
    }

    private fun isAppSandboxPath(path: String): Boolean {
        if (path.startsWith("android-files://") || path.startsWith("android-cache://")) return true
        val ctx = activeContext ?: return false
        val filesPath = runCatching { ctx.filesDir?.absolutePath }.getOrNull()
        val cachePath = runCatching { ctx.cacheDir?.absolutePath }.getOrNull()
        return (filesPath != null && path.startsWith(filesPath)) ||
            (cachePath != null && path.startsWith(cachePath))
    }

    private fun isAssetPath(path: String): Boolean =
        path.startsWith("file:///android_asset/") || path.startsWith("asset://")

    private fun extractAssetRelativePath(path: String): String = when {
        path.startsWith("file:///android_asset/") -> path.removePrefix("file:///android_asset/").trimStart('/')
        path.startsWith("asset://") -> path.removePrefix("asset://").trimStart('/')
        else -> path.trimStart('/')
    }
}
