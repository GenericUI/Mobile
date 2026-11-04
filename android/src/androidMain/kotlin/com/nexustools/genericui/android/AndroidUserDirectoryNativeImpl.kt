package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import java.io.File

/**
 * Android (`:mobile:android`) implementation of [UserDirectoryNativeImpl] backed by
 * `context.filesDir` (for [com.nexustools.genericui.util.ConfigDirectory]) and `context.cacheDir`
 * (for [com.nexustools.genericui.util.CacheDirectory]), with an in-memory fallback when running
 * in unit tests without an attached Android `Context`.
 */
class AndroidUserDirectoryNativeImpl(
    override val appName: String = "GenericUI",
    override val subdirectory: String = "",
    val isCache: Boolean = false,
    context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : UserDirectoryNativeImpl {

    override val fileSeparator: Char = File.separatorChar

    override val usesFileSystemBasePath: Boolean = true

    private val targetDir: File? = context?.let { ctx ->
        val root = if (isCache) runCatching { ctx.cacheDir }.getOrNull() else runCatching { ctx.filesDir }.getOrNull()
        if (root == null) return@let null
        val appDir = File(root, sanitizeName(appName))
        val cleanSub = subdirectory.trim().trim('/', '\\')
        val resolved = if (cleanSub.isEmpty()) appDir else File(appDir, cleanSub)
        runCatching { resolved.mkdirs() }
        resolved
    }

    override val directoryPath: String =
        targetDir?.absolutePath ?: buildString {
            append(if (isCache) "android-cache://" else "android-files://")
            append(sanitizeName(appName))
            val cleanSub = subdirectory.trim().trim('/', '\\')
            if (cleanSub.isNotEmpty()) {
                append('/')
                append(cleanSub)
            }
        }

    override var isDisposed: Boolean = false
        private set

    private val fallbackFiles = LinkedHashMap<String, ByteArray>()

    override suspend fun readAsync(fileName: String): com.nexustools.genericui.io.ByteBuffer? {
        if (com.nexustools.genericui.core.GenericUI.activeFactory is AndroidNativeFactory) {
            com.nexustools.genericui.io.FileSystem.read(resolveFilePath(fileName))?.let { return it }
        }
        return readBytes(fileName)?.let { com.nexustools.genericui.io.ByteBuffer.wrap(it) }
    }

    override suspend fun writeAsync(
        fileName: String,
        buffer: com.nexustools.genericui.io.ByteBuffer,
        append: Boolean
    ): Boolean {
        ensureDirectoryExists()
        val incoming = buffer.toByteArray()
        val merged = if (append) {
            val existing = readBytes(fileName)
            if (existing == null || existing.isEmpty()) incoming else existing + incoming
        } else {
            incoming
        }
        writeBytes(fileName, merged)
        if (com.nexustools.genericui.core.GenericUI.activeFactory is AndroidNativeFactory) {
            return com.nexustools.genericui.io.FileSystem.write(resolveFilePath(fileName), buffer, append)
        }
        return true
    }

    override suspend fun openReadableAsync(fileName: String): com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl? {
        if (com.nexustools.genericui.core.GenericUI.activeFactory is AndroidNativeFactory) {
            (com.nexustools.genericui.io.FileSystem.openReadable(resolveFilePath(fileName))
                as? com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl)?.let { return it }
        }
        val bytes = readBytes(fileName) ?: return null
        return AndroidFileReadableNativeImpl(initialBytes = bytes, filePath = resolveFilePath(fileName))
    }

    override suspend fun openWritableAsync(
        fileName: String,
        append: Boolean
    ): com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl {
        ensureDirectoryExists()
        if (com.nexustools.genericui.core.GenericUI.activeFactory is AndroidNativeFactory) {
            (com.nexustools.genericui.io.FileSystem.openWritable(resolveFilePath(fileName), append)
                as? com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl)?.let { return it }
        }
        val dir = targetDir
        val clean = sanitizeName(fileName)
        if (dir != null) {
            return AndroidFileWritableNativeImpl(File(dir, clean), append = append)
        }
        val initial = if (append) readBytes(fileName) ?: ByteArray(0) else ByteArray(0)
        return AndroidFileWritableNativeImpl(
            initialBytes = initial,
            filePath = resolveFilePath(fileName),
            appendMode = append,
            onCommit = { committed ->
                writeBytes(fileName, committed)
            }
        )
    }

    override fun ensureDirectoryExists(): Boolean {
        if (isDisposed) return false
        val dir = targetDir ?: return true
        return dir.exists() || dir.mkdirs()
    }

    override fun resolveFilePath(fileName: String): String {
        val clean = sanitizeName(fileName)
        val dir = targetDir
        return if (dir != null) File(dir, clean).absolutePath else "$directoryPath/$clean"
    }

    override fun writeBytes(fileName: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val clean = sanitizeName(fileName)
        val dir = targetDir
        if (dir != null) {
            return runCatching {
                dir.mkdirs()
                File(dir, clean).writeBytes(bytes)
                true
            }.getOrDefault(false)
        }
        fallbackFiles[clean] = bytes.copyOf()
        return true
    }

    override fun readBytes(fileName: String): ByteArray? {
        if (isDisposed) return null
        val clean = sanitizeName(fileName)
        val dir = targetDir
        if (dir != null) {
            val f = File(dir, clean)
            if (!f.isFile || !f.exists()) return null
            return runCatching { f.readBytes() }.getOrNull()
        }
        return fallbackFiles[clean]?.copyOf()
    }

    override fun exists(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeName(fileName)
        val dir = targetDir
        return if (dir != null) {
            val f = File(dir, clean)
            f.isFile && f.exists()
        } else {
            fallbackFiles.containsKey(clean)
        }
    }

    override fun fileSize(fileName: String): Long {
        if (isDisposed) return 0L
        val clean = sanitizeName(fileName)
        val dir = targetDir
        return if (dir != null) {
            val f = File(dir, clean)
            if (f.isFile && f.exists()) f.length() else 0L
        } else {
            fallbackFiles[clean]?.size?.toLong() ?: 0L
        }
    }

    override fun delete(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeName(fileName)
        val dir = targetDir
        return if (dir != null) {
            val f = File(dir, clean)
            f.exists() && f.delete()
        } else {
            fallbackFiles.remove(clean) != null
        }
    }

    override fun listFiles(): List<String> {
        if (isDisposed) return emptyList()
        val dir = targetDir
        if (dir != null) {
            val files = dir.listFiles() ?: return emptyList()
            return files.filter { it.isFile && !it.name.startsWith(".") }.map { it.name }.sorted()
        }
        return fallbackFiles.keys.toList()
    }

    override fun clearAll(): Int {
        if (isDisposed) return 0
        val dir = targetDir
        if (dir != null) {
            val files = dir.listFiles() ?: return 0
            var removed = 0
            for (f in files) {
                if (f.isFile && !f.name.startsWith(".") && f.delete()) {
                    removed++
                }
            }
            return removed
        }
        val count = fallbackFiles.size
        fallbackFiles.clear()
        return count
    }

    override fun totalByteSize(): Long {
        if (isDisposed) return 0L
        val dir = targetDir
        if (dir != null) {
            val files = dir.listFiles() ?: return 0L
            var total = 0L
            for (f in files) {
                if (f.isFile && !f.name.startsWith(".")) {
                    total += f.length()
                }
            }
            return total
        }
        var total = 0L
        for (b in fallbackFiles.values) {
            total += b.size.toLong()
        }
        return total
    }

    override fun dispose() {
        isDisposed = true
        fallbackFiles.clear()
    }

    private fun sanitizeName(raw: String): String {
        val trimmed = raw.trim().trim('/', '\\').ifEmpty { "GenericUI" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }
}
