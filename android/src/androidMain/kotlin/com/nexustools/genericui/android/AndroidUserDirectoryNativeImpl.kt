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

    private val targetDir: File? = context?.let { ctx ->
        val root = if (isCache) ctx.cacheDir else ctx.filesDir
        val appDir = File(root, sanitizeName(appName))
        val cleanSub = subdirectory.trim().trim('/', '\\')
        val resolved = if (cleanSub.isEmpty()) appDir else File(appDir, cleanSub)
        resolved.mkdirs()
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
