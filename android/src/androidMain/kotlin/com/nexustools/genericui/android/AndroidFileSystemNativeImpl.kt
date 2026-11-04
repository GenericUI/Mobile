package com.nexustools.genericui.android

import android.content.Context
import android.net.Uri
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileStat
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.DefaultFileSystemNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.coroutineContext

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl]
 * supporting Storage Access Framework (`content://` URIs via `ContentResolver` and `DocumentsContract`),
 * `AssetManager` (`file:///android_asset/` and `asset://`), and app-private sandbox directories (`filesDir` / `cacheDir`)
 * on [Dispatchers.IO], with an automatic in-memory fallback ([DefaultFileSystemNativeImpl]) for headless unit tests.
 */
class AndroidFileSystemNativeImpl(
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : DefaultFileSystemNativeImpl() {

    private val activeContext: Context?
        get() = context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext

    override fun isAvailable(): FileSystemAvailability = FileSystemAvailability.WithPermission

    override fun hasPermission(path: String?, write: Boolean): FilePermissionStatus {
        if (path == null) return FilePermissionStatus.PROMPT_REQUIRED
        val trimmed = path.trim()
        if (isAppSandboxPath(trimmed)) {
            return FilePermissionStatus.GRANTED
        }
        if (trimmed.startsWith("content://")) {
            val ctx = activeContext ?: return FilePermissionStatus.PROMPT_REQUIRED
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return FilePermissionStatus.PROMPT_REQUIRED
            val persisted = runCatching { ctx.contentResolver.persistedUriPermissions }.getOrDefault(emptyList())
            for (perm in persisted) {
                if (perm.uri == uri) {
                    if (!write && perm.isReadPermission) return FilePermissionStatus.GRANTED
                    if (write && perm.isWritePermission) return FilePermissionStatus.GRANTED
                }
            }
            return FilePermissionStatus.GRANTED
        }
        return FilePermissionStatus.PROMPT_REQUIRED
    }

    override suspend fun openReadable(path: String): AndroidFileReadableNativeImpl? =
        createReadStream(path)

    override suspend fun openWritable(path: String, append: Boolean): AndroidFileWritableNativeImpl =
        createWriteStream(path, if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE)

    override suspend fun createReadStream(path: String): AndroidFileReadableNativeImpl? {
        if (isDisposed) return null
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
            return AndroidFileReadableNativeImpl(ctx.contentResolver, uri)
        }
        if (isAssetPath(trimmed) && ctx != null) {
            val assetRel = extractAssetRelativePath(trimmed)
            return AndroidFileReadableNativeImpl(ctx.assets, assetRel)
        }
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            return AndroidFileReadableNativeImpl(localFile)
        }
        val fallbackBytes = super.readFile(path) ?: return null
        return AndroidFileReadableNativeImpl(initialBytes = fallbackBytes, filePath = normalizeKey(path))
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): AndroidFileWritableNativeImpl {
        val append = mode == FileOpenMode.WRITE_APPEND
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
            if (uri != null) {
                return AndroidFileWritableNativeImpl(ctx.contentResolver, uri, append = append)
            }
        }
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            return AndroidFileWritableNativeImpl(localFile, append = append)
        }
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

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? {
        val fallback = super.openReadableFromFileDialog(title, initialDirectory, filters) ?: return null
        val bytes = super.readFile(fallback.first) ?: return fallback
        return fallback.first to AndroidFileReadableNativeImpl(initialBytes = bytes, filePath = fallback.first)
    }

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? {
        val target = resolve(initialDirectory ?: "/", suggestedFileName.ifEmpty { "untitled.bin" })
        return target to createWriteStream(target, FileOpenMode.WRITE_TRUNCATE)
    }

    override suspend fun readFile(path: String): ByteArray? {
        if (isDisposed) return null
        val norm = normalizeKey(path)
        if (files.containsKey(norm)) {
            return super.readFile(path)
        }
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            return runOnAndroidIo {
                runCatching {
                    val uri = Uri.parse(trimmed)
                    ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
            }
        }
        if (isAssetPath(trimmed) && ctx != null) {
            val assetRel = extractAssetRelativePath(trimmed)
            return runOnAndroidIo {
                runCatching {
                    ctx.assets.open(assetRel).use { it.readBytes() }
                }.getOrNull()
            }
        }
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            return runOnAndroidIo {
                runCatching {
                    if (localFile.isFile) localFile.readBytes() else null
                }.getOrNull()
            }
        }
        return super.readFile(path)
    }

    override suspend fun writeFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val written = runOnAndroidIo {
                runCatching {
                    val uri = Uri.parse(trimmed)
                    ctx.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        out.write(bytes)
                        out.flush()
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            if (written) return true
        }
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val written = runOnAndroidIo {
                runCatching {
                    localFile.parentFile?.mkdirs()
                    localFile.writeBytes(bytes)
                    true
                }.getOrDefault(false)
            }
            if (written) return true
        }
        return super.writeFile(path, bytes)
    }

    override suspend fun appendFile(path: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val appended = runOnAndroidIo {
                runCatching {
                    val uri = Uri.parse(trimmed)
                    ctx.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                        out.write(bytes)
                        out.flush()
                        true
                    } ?: false
                }.getOrDefault(false)
            }
            if (appended) return true
        }
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val appended = runOnAndroidIo {
                runCatching {
                    localFile.parentFile?.mkdirs()
                    FileOutputStream(localFile, true).use { out ->
                        out.write(bytes)
                        out.flush()
                    }
                    true
                }.getOrDefault(false)
            }
            if (appended) return true
        }
        return super.appendFile(path, bytes)
    }

    override suspend fun exists(path: String): Boolean {
        if (isDisposed) return false
        if (super.exists(path)) return true
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            return runOnAndroidIo {
                runCatching {
                    val uri = Uri.parse(trimmed)
                    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                        c.moveToFirst()
                    } ?: false
                }.getOrDefault(false)
            }
        }
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        return localFile != null
    }

    override suspend fun stat(path: String): FileStat? {
        if (isDisposed) return null
        super.stat(path)?.let { return it }
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            return runOnAndroidIo {
                runCatching {
                    val uri = Uri.parse(trimmed)
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
                        if (cursor.moveToFirst()) {
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
                                path = trimmed,
                                name = name,
                                size = size,
                                isFile = !isDir,
                                isDirectory = isDir,
                                lastModifiedEpochMs = mod,
                                createdEpochMs = mod
                            )
                        } else {
                            null
                        }
                    }
                }.getOrNull()
            }
        }
        val localFile = resolveLocalFile(trimmed, mustExist = true) ?: return null
        return runOnAndroidIo {
            runCatching {
                FileStat(
                    path = localFile.absolutePath,
                    name = localFile.name,
                    size = if (localFile.isFile) localFile.length() else 0L,
                    isFile = localFile.isFile,
                    isDirectory = localFile.isDirectory,
                    lastModifiedEpochMs = localFile.lastModified(),
                    createdEpochMs = localFile.lastModified(),
                    isReadable = localFile.canRead(),
                    isWritable = localFile.canWrite()
                )
            }.getOrNull()
        }
    }

    override suspend fun readdir(path: String): List<String> {
        if (isDisposed) return emptyList()
        val combined = LinkedHashSet<String>()
        combined.addAll(super.readdir(path))
        val localFile = resolveLocalFile(path.trim(), mustExist = true)
        if (localFile != null) {
            val diskEntries = runOnAndroidIo {
                runCatching {
                    localFile.list()?.sorted() ?: emptyList()
                }.getOrDefault(emptyList())
            }
            combined.addAll(diskEntries)
        }
        return combined.toList()
    }

    override suspend fun mkdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed) return false
        val trimmed = path.trim()
        val localFile = resolveLocalFile(trimmed, mustExist = false)
        if (localFile != null && !isVirtualPath(trimmed)) {
            val created = runOnAndroidIo {
                runCatching {
                    if (recursive) localFile.mkdirs() || localFile.isDirectory else localFile.mkdir() || localFile.isDirectory
                }.getOrDefault(false)
            }
            if (created) return true
        }
        return super.mkdir(path, recursive)
    }

    override suspend fun unlink(path: String): Boolean {
        if (isDisposed) return false
        val removedMem = super.unlink(path)
        val trimmed = path.trim()
        val ctx = activeContext
        if (trimmed.startsWith("content://") && ctx != null) {
            val deletedContent = runOnAndroidIo {
                runCatching {
                    DocumentsContract.deleteDocument(ctx.contentResolver, Uri.parse(trimmed))
                }.getOrDefault(false)
            }
            return removedMem || deletedContent
        }
        val localFile = resolveLocalFile(trimmed, mustExist = true)
        if (localFile != null) {
            val deletedDisk = runOnAndroidIo {
                runCatching { localFile.isFile && localFile.delete() }.getOrDefault(false)
            }
            return removedMem || deletedDisk
        }
        return removedMem
    }

    override suspend fun rmdir(path: String, recursive: Boolean): Boolean {
        if (isDisposed) return false
        val removedMem = super.rmdir(path, recursive)
        val localFile = resolveLocalFile(path.trim(), mustExist = true)
        if (localFile != null) {
            val deletedDisk = runOnAndroidIo {
                runCatching {
                    if (!localFile.isDirectory) return@runCatching false
                    if (recursive) localFile.deleteRecursively() else localFile.delete()
                }.getOrDefault(false)
            }
            return removedMem || deletedDisk
        }
        return removedMem
    }

    override suspend fun rename(oldPath: String, newPath: String): Boolean {
        if (isDisposed) return false
        val oldFile = resolveLocalFile(oldPath.trim(), mustExist = true)
        val newFile = resolveLocalFile(newPath.trim(), mustExist = false)
        if (oldFile != null && newFile != null && !isVirtualPath(newPath)) {
            val renamed = runOnAndroidIo {
                runCatching {
                    newFile.parentFile?.mkdirs()
                    oldFile.renameTo(newFile)
                }.getOrDefault(false)
            }
            if (renamed) return true
        }
        return super.rename(oldPath, newPath)
    }

    override suspend fun copyFile(srcPath: String, dstPath: String, overwrite: Boolean): Boolean {
        if (isDisposed) return false
        val srcFile = resolveLocalFile(srcPath.trim(), mustExist = true)
        val dstFile = resolveLocalFile(dstPath.trim(), mustExist = false)
        if (srcFile != null && dstFile != null && !isVirtualPath(dstPath)) {
            val copied = runOnAndroidIo {
                runCatching {
                    srcFile.copyTo(dstFile, overwrite = overwrite)
                    true
                }.getOrDefault(false)
            }
            if (copied) return true
        }
        return super.copyFile(srcPath, dstPath, overwrite)
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

    private fun isVirtualPath(rawPath: String): Boolean {
        val trimmed = rawPath.trim()
        if (trimmed.contains("://") && !trimmed.startsWith("file://")) return true
        val norm = normalizeKey(trimmed)
        if (files.containsKey(norm) || (norm != "/" && directories.contains(norm))) {
            return true
        }
        if (File.separatorChar == '\\') {
            if (trimmed.startsWith("/") && !(trimmed.length >= 3 && trimmed[2] == ':')) {
                val candidate = File(trimmed)
                return !candidate.exists()
            }
        } else if (trimmed.startsWith("/projects/") || trimmed == "/projects") {
            val candidate = File(trimmed)
            return !candidate.exists()
        }
        return false
    }

    private fun resolveLocalFile(rawPath: String, mustExist: Boolean): File? {
        if (rawPath.isEmpty()) return null
        if (rawPath.contains("://") && !rawPath.startsWith("file://")) return null
        val clean = if (rawPath.startsWith("file://")) rawPath.removePrefix("file://") else rawPath
        return try {
            val file = File(clean)
            if (mustExist) {
                if (file.exists()) file else null
            } else {
                file
            }
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun <T> runOnAndroidIo(block: () -> T): T {
        val hasInterceptor = coroutineContext[ContinuationInterceptor] != null
        return if (hasInterceptor || isOnAndroidMainThread()) {
            withContext(Dispatchers.IO) { block() }
        } else {
            block()
        }
    }

    private fun isOnAndroidMainThread(): Boolean =
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
}
