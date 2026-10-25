@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.util.BinaryPlist
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
 * iOS (`:mobile:ios`) implementation of [PreferenceStoreNativeImpl].
 *
 * Stores each preference key in its own binary property list file (`<Home>/Library/Application Support/<appName>/<key>.plist`)
 * encoded and decoded via [BinaryPlist] (`bplist00`), and dispatches [PreferenceChangeListener] notifications.
 */
class IosPreferenceStoreImpl(
    override val appName: String = "GenericUI"
) : PreferenceStoreNativeImpl {

    val configDirectory: String = resolveIosAppConfigDir(appName)

    override val storageLocationDescription: String = "$configDirectory/*.plist (BinaryPlist bplist00)"
    override val usesNativeChangeListener: Boolean = true

    override var isDisposed: Boolean = false
        private set

    private val memoryCache = LinkedHashMap<String, ByteArray>()
    private val knownKeys = LinkedHashSet<String>()
    private val listeners = mutableListOf<PreferenceChangeListener>()

    init {
        ensureDirectoryExists(configDirectory)
        loadPersistedKeySet()
    }

    fun filePathForKey(key: String): String {
        val cleanKey = key.trim().ifEmpty { "default" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "${configDirectory.trimEnd('/')}/$cleanKey.plist"
    }

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        ensureDirectoryExists(configDirectory)
        val encoded = BinaryPlist.encodeElement(element)
        memoryCache[key] = encoded
        knownKeys.add(key)
        writeBytes(filePathForKey(key), encoded)
        savePersistedKeySet()
        notifyListeners(key, element.toPlainValue())
    }

    override fun readElement(key: String): PreferenceElement? {
        val fileBytes = readBytes(filePathForKey(key))
        if (fileBytes != null && fileBytes.isNotEmpty()) {
            memoryCache[key] = fileBytes
            knownKeys.add(key)
            return BinaryPlist.decodeElement(fileBytes)
        }
        val cached = memoryCache[key] ?: return null
        return BinaryPlist.decodeElement(cached)
    }

    override fun contains(key: String): Boolean =
        key in knownKeys || memoryCache.containsKey(key) || fileExists(filePathForKey(key))

    override fun keys(): Set<String> = LinkedHashSet(knownKeys)

    override fun remove(key: String) {
        val existed = contains(key)
        memoryCache.remove(key)
        knownKeys.remove(key)
        remove(filePathForKey(key))
        savePersistedKeySet()
        if (existed) {
            notifyListeners(key, null)
        }
    }

    override fun clear() {
        val snapshotKeys = knownKeys.toList()
        for (k in snapshotKeys) {
            remove(filePathForKey(k))
        }
        memoryCache.clear()
        knownKeys.clear()
        savePersistedKeySet()
        if (snapshotKeys.isNotEmpty()) {
            notifyListeners(null, null)
        }
    }

    override fun flush(): Boolean {
        if (isDisposed) return false
        ensureDirectoryExists(configDirectory)
        for ((k, bytes) in memoryCache) {
            writeBytes(filePathForKey(k), bytes)
        }
        savePersistedKeySet()
        return true
    }

    override fun addChangeListener(listener: PreferenceChangeListener) {
        if (listener !in listeners) {
            listeners.add(listener)
        }
    }

    override fun removeChangeListener(listener: PreferenceChangeListener) {
        listeners.remove(listener)
    }

    override fun dispose() {
        isDisposed = true
        listeners.clear()
    }

    private fun resolveIosAppConfigDir(appName: String): String {
        val cleanApp = appName.trim().ifEmpty { "GenericUI" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val home = getenv("HOME")?.toKString()?.trim()?.takeIf { it.isNotEmpty() }
        return if (home != null) {
            "${home.trimEnd('/')}/Library/Application Support/$cleanApp"
        } else {
            "Library/Application Support/$cleanApp"
        }
    }

    private fun ensureDirectoryExists(path: String) {
        val normalized = path.replace('\\', '/')
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
    }

    private fun writeBytes(filePath: String, bytes: ByteArray): Boolean {
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

    private fun readBytes(filePath: String): ByteArray? {
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

    private fun fileExists(filePath: String): Boolean {
        val file = fopen(filePath, "rb") ?: return false
        fclose(file)
        return true
    }

    private fun loadPersistedKeySet() {
        val idxFile = "${configDirectory.trimEnd('/')}/.preference_keys"
        val bytes = readBytes(idxFile) ?: return
        for (line in bytes.decodeToString().lineSequence()) {
            val k = line.trim()
            if (k.isNotEmpty()) knownKeys.add(k)
        }
    }

    private fun savePersistedKeySet() {
        val idxFile = "${configDirectory.trimEnd('/')}/.preference_keys"
        writeBytes(idxFile, knownKeys.joinToString("\n").encodeToByteArray())
    }

    private fun notifyListeners(key: String?, newValue: Any?) {
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, newValue)
        }
    }
}
