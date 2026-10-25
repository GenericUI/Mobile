@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.nativeimpl.system.UserDirectoryNativeImpl
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.toKString
import kotlinx.cinterop.usePinned
import platform.posix.SEEK_END
import platform.posix.SEEK_SET
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell
import platform.posix.fwrite
import platform.posix.getenv
import platform.posix.mkdir
import platform.posix.remove

/**
 * iOS (`:mobile:ios`) implementation of [UserDirectoryNativeImpl] backed by `<Home>/Library/Application Support/<appName>`
 * (for [com.nexustools.genericui.util.ConfigDirectory]) and `<Home>/Library/Caches/<appName>`
 * (for [com.nexustools.genericui.util.CacheDirectory]).
 */
class IosUserDirectoryNativeImpl(
    override val appName: String = "GenericUI",
    override val subdirectory: String = "",
    val isCache: Boolean = false
) : UserDirectoryNativeImpl {

    override val fileSeparator: Char = '/'

    override val directoryPath: String = resolveIosDirectory(appName, subdirectory, isCache)

    override var isDisposed: Boolean = false
        private set

    private val memoryCache = LinkedHashMap<String, ByteArray>()
    private val knownFiles = LinkedHashSet<String>()

    init {
        ensureDirectoryExists()
        loadManifest()
    }

    override fun ensureDirectoryExists(): Boolean {
        if (isDisposed) return false
        val normalized = directoryPath.replace('\\', '/')
        val isAbsolute = normalized.startsWith("/")
        val parts = normalized.split('/')
        var current = ""
        for (part in parts) {
            if (part.isEmpty()) continue
            current = if (current.isEmpty()) {
                if (isAbsolute) "/$part" else part
            } else {
                "$current/$part"
            }
            mkdir(current, 0x1EDu.toUShort())
        }
        return true
    }

    override fun resolveFilePath(fileName: String): String {
        val clean = sanitizeFileName(fileName)
        return "${directoryPath.trimEnd('/')}/$clean"
    }

    override fun writeBytes(fileName: String, bytes: ByteArray): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        ensureDirectoryExists()
        memoryCache[clean] = bytes.copyOf()
        knownFiles.add(clean)
        val written = writeBytesRaw(resolveFilePath(clean), bytes)
        saveManifest()
        return written || memoryCache.containsKey(clean)
    }

    override fun readBytes(fileName: String): ByteArray? {
        if (isDisposed) return null
        val clean = sanitizeFileName(fileName)
        val disk = readBytesRaw(resolveFilePath(clean))
        if (disk != null) {
            memoryCache[clean] = disk
            if (!clean.startsWith(".")) knownFiles.add(clean)
            return disk
        }
        return memoryCache[clean]?.copyOf()
    }

    override fun exists(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        return clean in knownFiles || memoryCache.containsKey(clean) || fileExistsRaw(resolveFilePath(clean))
    }

    override fun fileSize(fileName: String): Long {
        if (isDisposed) return 0L
        val clean = sanitizeFileName(fileName)
        val diskBytes = readBytesRaw(resolveFilePath(clean))
        if (diskBytes != null) return diskBytes.size.toLong()
        return memoryCache[clean]?.size?.toLong() ?: 0L
    }

    override fun delete(fileName: String): Boolean {
        if (isDisposed) return false
        val clean = sanitizeFileName(fileName)
        val memRemoved = memoryCache.remove(clean) != null
        val knownRemoved = knownFiles.remove(clean)
        val diskRemoved = remove(resolveFilePath(clean)) == 0
        if (memRemoved || knownRemoved || diskRemoved) {
            saveManifest()
            return true
        }
        return false
    }

    override fun listFiles(): List<String> {
        if (isDisposed) return emptyList()
        loadManifest()
        return knownFiles.filter { !it.startsWith(".") }
    }

    override fun clearAll(): Int {
        if (isDisposed) return 0
        loadManifest()
        val snapshot = knownFiles.toList()
        var deleted = 0
        for (name in snapshot) {
            remove(resolveFilePath(name))
            memoryCache.remove(name)
            deleted++
        }
        memoryCache.clear()
        knownFiles.clear()
        remove(manifestFilePath())
        return deleted
    }

    override fun totalByteSize(): Long {
        if (isDisposed) return 0L
        var total = 0L
        for (name in listFiles()) {
            total += fileSize(name)
        }
        return total
    }

    override fun dispose() {
        isDisposed = true
        memoryCache.clear()
    }

    private fun manifestFilePath(): String =
        "${directoryPath.trimEnd('/')}/.user_dir_manifest"

    private fun loadManifest() {
        val bytes = readBytesRaw(manifestFilePath()) ?: return
        for (line in bytes.decodeToString().lineSequence()) {
            val k = line.trim()
            if (k.isNotEmpty() && !k.startsWith(".")) knownFiles.add(k)
        }
    }

    private fun saveManifest() {
        val content = knownFiles.filter { !it.startsWith(".") }.joinToString("\n").encodeToByteArray()
        writeBytesRaw(manifestFilePath(), content)
    }

    private fun writeBytesRaw(filePath: String, bytes: ByteArray): Boolean {
        val file = fopen(filePath, "wb") ?: return false
        return try {
            if (bytes.isNotEmpty()) {
                bytes.usePinned { pinned ->
                    fwrite(pinned.addressOf(0), 1u, bytes.size.toULong(), file)
                }
            }
            true
        } finally {
            fclose(file)
        }
    }

    private fun readBytesRaw(filePath: String): ByteArray? {
        val file = fopen(filePath, "rb") ?: return null
        return try {
            if (fseek(file, 0, SEEK_END) != 0) return null
            val len = ftell(file).toInt()
            if (fseek(file, 0, SEEK_SET) != 0 || len < 0) return null
            if (len == 0) return ByteArray(0)
            val buf = ByteArray(len)
            buf.usePinned { pinned ->
                fread(pinned.addressOf(0), 1u, len.toULong(), file)
            }
            buf
        } finally {
            fclose(file)
        }
    }

    private fun fileExistsRaw(filePath: String): Boolean {
        val file = fopen(filePath, "rb") ?: return false
        fclose(file)
        return true
    }

    private fun sanitizeFileName(raw: String): String {
        val trimmed = raw.trim().trim('/', '\\').ifEmpty { "unnamed" }
        return trimmed.replace(Regex("[\\\\/:*?\"<>|]"), "_")
    }

    companion object {
        fun resolveIosDirectory(appName: String, subdirectory: String = "", isCache: Boolean = false): String {
            val cleanApp = appName.trim().ifEmpty { "GenericUI" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val home = getenv("HOME")?.toKString()?.trim()?.takeIf { it.isNotEmpty() }
            val folder = if (isCache) "Library/Caches" else "Library/Application Support"
            val base = if (home != null) {
                "${home.trimEnd('/')}/$folder/$cleanApp"
            } else {
                "$folder/$cleanApp"
            }
            val cleanSub = subdirectory.trim().trim('/', '\\')
            return if (cleanSub.isEmpty()) base else "$base/$cleanSub"
        }
    }
}
