package com.nexustools.genericui.android

import android.content.Context
import android.content.SharedPreferences
import android.os.Looper
import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl
import com.nexustools.genericui.util.BinaryJson
import java.io.File

/**
 * Android implementation of [PreferenceStoreNativeImpl] backed directly by Android's native
 * [SharedPreferences] (`context.getSharedPreferences(sanitizePrefsName(appName), Context.MODE_PRIVATE)`)
 * and `SharedPreferences.OnSharedPreferenceChangeListener`.
 *
 * Uses a companion metadata [SharedPreferences] file (`"<cleanName>_genericui_kinds"`) to disambiguate
 * `Double` values, `String` values that look numeric or JSON-like, and JSON-serialized
 * arrays/maps/objects, while buffering pre-[Context] writes in memory until a [Context] binds and
 * migrating any legacy `.bson`/`.bson.old` files from `<filesDir>/genericui-prefs/<cleanName>/` once.
 */
class AndroidPreferenceStoreImpl(
    override val appName: String = "GenericUI",
    private val context: Context? = null
) : PreferenceStoreNativeImpl {

    private val fallbackMap: LinkedHashMap<String, PreferenceElement> = sharedFallbackStoreFor(sanitizePrefsName(appName))
    private val lock: Any = fallbackMap
    private var boundPrefs: SharedPreferences? = null
    private var boundMetaPrefs: SharedPreferences? = null
    private var listenerRegistered = false
    private val listeners = ArrayList<PreferenceChangeListener>()

    private var suppressNativeCallback = false
    private var activeSelfMutationObservedNativeCallback = false
    private val pendingSelfKeyNotifications = HashMap<String, Int>()
    private var pendingSelfClearNotifications = 0

    @Volatile
    private var disposedState = false

    private val nativeSharedPrefsListener =
        SharedPreferences.OnSharedPreferenceChangeListener { prefs, changedKey ->
            handleNativeSharedPreferenceChanged(prefs, changedKey)
        }

    override val storageLocationDescription: String
        get() = "SharedPreferences(${sanitizePrefsName(appName)})"

    override val usesNativeChangeListener: Boolean = true

    override val isDisposed: Boolean
        get() = disposedState

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        val snapshot: List<PreferenceChangeListener>
        synchronized(lock) {
            if (disposedState) return
            val prefs = resolveSharedPreferences()
            if (prefs != null) {
                val (rawValue, metaKind) = toSharedPrefsStoragePair(element)
                val willTriggerNative = !prefs.contains(key) || prefs.all[key] != rawValue
                boundMetaPrefs?.edit()?.apply {
                    if (metaKind != null) {
                        putString(key, metaKind)
                    } else {
                        remove(key)
                    }
                    commit()
                }
                val editor = prefs.edit()
                putStorageValue(editor, key, rawValue)
                commitWithSuppressedNativeCallback(editor)
                if (willTriggerNative &&
                    !activeSelfMutationObservedNativeCallback &&
                    !isOnMainLooper() &&
                    hasMainLooper()
                ) {
                    pendingSelfKeyNotifications[key] = (pendingSelfKeyNotifications[key] ?: 0) + 1
                }
            } else {
                fallbackMap[key] = element
            }
            if (listeners.isEmpty()) return
            snapshot = listeners.toList()
        }
        val plain = element.toPlainValue()
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, plain)
        }
    }

    override fun readElement(key: String): PreferenceElement? = synchronized(lock) {
        if (disposedState) return null
        val prefs = resolveSharedPreferences()
        if (prefs != null) {
            readFromSharedPreferences(prefs, boundMetaPrefs, key)
        } else {
            fallbackMap[key]
        }
    }

    override fun contains(key: String): Boolean = synchronized(lock) {
        if (disposedState) return false
        val prefs = resolveSharedPreferences()
        if (prefs != null) {
            prefs.contains(key)
        } else {
            fallbackMap.containsKey(key)
        }
    }

    override fun keys(): Set<String> = synchronized(lock) {
        if (disposedState) return emptySet()
        val prefs = resolveSharedPreferences()
        val rawKeys = prefs?.all?.keys ?: fallbackMap.keys
        rawKeys.sorted().toCollection(LinkedHashSet())
    }

    override fun remove(key: String) {
        val snapshot: List<PreferenceChangeListener>
        synchronized(lock) {
            if (disposedState) return
            val prefs = resolveSharedPreferences()
            val existed = if (prefs != null) {
                val hadKey = prefs.contains(key)
                boundMetaPrefs?.edit()?.apply {
                    remove(key)
                    commit()
                }
                commitWithSuppressedNativeCallback(prefs.edit().remove(key))
                if (hadKey &&
                    !activeSelfMutationObservedNativeCallback &&
                    !isOnMainLooper() &&
                    hasMainLooper()
                ) {
                    pendingSelfKeyNotifications[key] = (pendingSelfKeyNotifications[key] ?: 0) + 1
                }
                hadKey
            } else {
                fallbackMap.remove(key) != null
            }
            if (!existed || listeners.isEmpty()) return
            snapshot = listeners.toList()
        }
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, null)
        }
    }

    override fun clear() {
        val snapshot: List<PreferenceChangeListener>
        synchronized(lock) {
            if (disposedState) return
            val prefs = resolveSharedPreferences()
            val hadAny = if (prefs != null) {
                val hasEntries = prefs.all.isNotEmpty()
                boundMetaPrefs?.edit()?.apply {
                    clear()
                    commit()
                }
                commitWithSuppressedNativeCallback(prefs.edit().clear())
                if (hasEntries &&
                    !activeSelfMutationObservedNativeCallback &&
                    !isOnMainLooper() &&
                    hasMainLooper()
                ) {
                    pendingSelfClearNotifications++
                }
                hasEntries
            } else {
                val nonEmpty = fallbackMap.isNotEmpty()
                fallbackMap.clear()
                nonEmpty
            }
            if (!hadAny || listeners.isEmpty()) return
            snapshot = listeners.toList()
        }
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, null, null)
        }
    }

    override fun flush(): Boolean = synchronized(lock) {
        if (disposedState) return false
        val prefs = resolveSharedPreferences() ?: return true
        val metaCommitted = boundMetaPrefs?.edit()?.commit() ?: true
        val prefsCommitted = prefs.edit().commit()
        metaCommitted && prefsCommitted
    }

    override fun addChangeListener(listener: PreferenceChangeListener) {
        synchronized(lock) {
            if (!disposedState && listener !in listeners) {
                listeners.add(listener)
            }
        }
    }

    override fun removeChangeListener(listener: PreferenceChangeListener) {
        synchronized(lock) {
            listeners.remove(listener)
        }
    }

    override fun dispose() {
        synchronized(lock) {
            if (disposedState) return
            if (fallbackMap.isNotEmpty()) {
                resolveSharedPreferences()
            }
            if (listenerRegistered) {
                runCatching {
                    boundPrefs?.unregisterOnSharedPreferenceChangeListener(nativeSharedPrefsListener)
                }
                listenerRegistered = false
            }
            boundPrefs = null
            boundMetaPrefs = null
            pendingSelfKeyNotifications.clear()
            pendingSelfClearNotifications = 0
            disposedState = true
            listeners.clear()
        }
    }

    private fun resolveSharedPreferences(): SharedPreferences? {
        if (disposedState) return null
        boundPrefs?.let { return it }

        val ctx = context ?: applicationContext ?: AndroidViewHandle.activeContext ?: return null
        val cleanName = sanitizePrefsName(appName)
        val prefs = ctx.getSharedPreferences(cleanName, Context.MODE_PRIVATE)
        val metaPrefs = ctx.getSharedPreferences("${cleanName}$META_PREFS_SUFFIX", Context.MODE_PRIVATE)

        migrateLegacyBsonDirectory(ctx, cleanName, prefs, metaPrefs)

        if (fallbackMap.isNotEmpty()) {
            val editor = prefs.edit()
            val metaEditor = metaPrefs.edit()
            for ((key, element) in fallbackMap) {
                writeToSharedPreferences(key, element, editor, metaEditor)
            }
            metaEditor.commit()
            editor.commit()
            fallbackMap.clear()
        }

        if (!listenerRegistered) {
            prefs.registerOnSharedPreferenceChangeListener(nativeSharedPrefsListener)
            listenerRegistered = true
        }

        boundPrefs = prefs
        boundMetaPrefs = metaPrefs
        return prefs
    }

    private fun migrateLegacyBsonDirectory(
        ctx: Context,
        cleanName: String,
        prefs: SharedPreferences,
        metaPrefs: SharedPreferences
    ) {
        val filesDir = runCatching { ctx.filesDir }.getOrNull() ?: return
        val legacyParent = File(filesDir, LEGACY_BSON_ROOT_DIR)
        val legacyDir = File(legacyParent, cleanName)
        if (!legacyDir.exists() || !legacyDir.isDirectory) return

        val files = legacyDir.listFiles() ?: return
        val primaryFiles = LinkedHashMap<String, File>()
        val backupFiles = LinkedHashMap<String, File>()
        val filesToDelete = ArrayList<File>()

        for (file in files) {
            if (!file.isFile) continue
            val name = file.name
            when {
                name == LEGACY_MIGRATION_MARKER_FILE -> {
                    filesToDelete.add(file)
                }
                name.endsWith(LEGACY_BSON_BACKUP_EXT) && name.length > LEGACY_BSON_BACKUP_EXT.length -> {
                    filesToDelete.add(file)
                    if (!name.startsWith(".")) {
                        backupFiles[name.removeSuffix(LEGACY_BSON_BACKUP_EXT)] = file
                    }
                }
                name.endsWith(LEGACY_BSON_EXT) && name.length > LEGACY_BSON_EXT.length -> {
                    filesToDelete.add(file)
                    if (!name.startsWith(".")) {
                        primaryFiles[name.removeSuffix(LEGACY_BSON_EXT)] = file
                    }
                }
            }
        }

        val legacyKeys = LinkedHashSet<String>(primaryFiles.size + backupFiles.size).apply {
            addAll(primaryFiles.keys)
            addAll(backupFiles.keys)
        }

        var wroteAny = false
        val editor = prefs.edit()
        val metaEditor = metaPrefs.edit()
        for (key in legacyKeys) {
            if (prefs.contains(key)) continue
            val primaryDecoded = primaryFiles[key]
                ?.let { runCatching { it.readBytes() }.getOrNull() }
                ?.let { BinaryJson.decodeElementOrNull(it) }
            val decoded = primaryDecoded ?: backupFiles[key]
                ?.let { runCatching { it.readBytes() }.getOrNull() }
                ?.let { BinaryJson.decodeElementOrNull(it) }
            if (decoded != null && !decoded.isNull) {
                writeToSharedPreferences(key, decoded, editor, metaEditor)
                wroteAny = true
            }
        }

        val committed = if (wroteAny) {
            metaEditor.commit()
            editor.commit()
        } else {
            true
        }

        if (committed) {
            for (file in filesToDelete) {
                runCatching { file.delete() }
            }
            if (legacyDir.listFiles()?.isEmpty() == true) {
                runCatching { legacyDir.delete() }
            }
            if (legacyParent.listFiles()?.isEmpty() == true) {
                runCatching { legacyParent.delete() }
            }
        }
    }

    private fun handleNativeSharedPreferenceChanged(prefs: SharedPreferences, changedKey: String?) {
        val newValue: Any?
        val snapshot: List<PreferenceChangeListener>
        synchronized(lock) {
            if (disposedState || prefs !== boundPrefs) return
            if (suppressNativeCallback) {
                activeSelfMutationObservedNativeCallback = true
                return
            }
            if (changedKey == null) {
                if (pendingSelfClearNotifications > 0) {
                    pendingSelfClearNotifications--
                    return
                }
                if (prefs.all.isEmpty()) {
                    boundMetaPrefs?.edit()?.clear()?.apply()
                }
                newValue = null
            } else {
                val pending = pendingSelfKeyNotifications[changedKey] ?: 0
                if (pending > 0) {
                    if (pending == 1) {
                        pendingSelfKeyNotifications.remove(changedKey)
                    } else {
                        pendingSelfKeyNotifications[changedKey] = pending - 1
                    }
                    return
                }
                if (!prefs.contains(changedKey)) {
                    boundMetaPrefs?.edit()?.remove(changedKey)?.apply()
                    newValue = null
                } else {
                    newValue = readFromSharedPreferences(prefs, boundMetaPrefs, changedKey)?.toPlainValue()
                }
            }
            if (listeners.isEmpty()) return
            snapshot = listeners.toList()
        }
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, changedKey, newValue)
        }
    }

    private fun commitWithSuppressedNativeCallback(editor: SharedPreferences.Editor) {
        suppressNativeCallback = true
        activeSelfMutationObservedNativeCallback = false
        try {
            editor.commit()
        } finally {
            suppressNativeCallback = false
        }
    }

    private fun toSharedPrefsStoragePair(element: PreferenceElement): Pair<Any, String?> =
        when (element.kind) {
            PreferenceElementKind.NULL -> "" to null
            PreferenceElementKind.BOOLEAN -> (element.booleanValue == true) to null
            PreferenceElementKind.NUMBER -> when (element.numberTypeTag) {
                PreferenceElement.NUMBER_TAG_BYTE,
                PreferenceElement.NUMBER_TAG_SHORT,
                PreferenceElement.NUMBER_TAG_INT -> element.asInt() to null
                PreferenceElement.NUMBER_TAG_LONG -> element.asLong() to null
                PreferenceElement.NUMBER_TAG_FLOAT -> element.asFloat() to null
                else -> element.asDouble().toString() to KIND_DOUBLE
            }
            PreferenceElementKind.STRING -> (element.stringValue ?: "") to KIND_STRING
            PreferenceElementKind.ARRAY,
            PreferenceElementKind.MAP,
            PreferenceElementKind.OBJECT -> PreferenceObjectCodec.elementToJson(element) to KIND_JSON
        }

    private fun putStorageValue(
        editor: SharedPreferences.Editor,
        key: String,
        rawValue: Any
    ) {
        when (rawValue) {
            is Boolean -> editor.putBoolean(key, rawValue)
            is Int -> editor.putInt(key, rawValue)
            is Long -> editor.putLong(key, rawValue)
            is Float -> editor.putFloat(key, rawValue)
            is String -> editor.putString(key, rawValue)
        }
    }

    private fun writeToSharedPreferences(
        key: String,
        element: PreferenceElement,
        editor: SharedPreferences.Editor,
        metaEditor: SharedPreferences.Editor
    ) {
        if (element.isNull) {
            editor.remove(key)
            metaEditor.remove(key)
            return
        }
        val (rawValue, metaKind) = toSharedPrefsStoragePair(element)
        putStorageValue(editor, key, rawValue)
        if (metaKind != null) {
            metaEditor.putString(key, metaKind)
        } else {
            metaEditor.remove(key)
        }
    }

    private fun readFromSharedPreferences(
        prefs: SharedPreferences,
        metaPrefs: SharedPreferences?,
        key: String
    ): PreferenceElement? {
        if (!prefs.contains(key)) return null
        val raw = prefs.all[key] ?: return null
        return when (raw) {
            is Boolean -> PreferenceElement.ofBoolean(raw)
            is Int -> PreferenceElement.ofInt(raw)
            is Long -> PreferenceElement.ofLong(raw)
            is Float -> PreferenceElement.ofFloat(raw)
            is Double -> PreferenceElement.ofDouble(raw)
            is Set<*> -> PreferenceElement.ofArray(
                raw.map { PreferenceElement.ofString(it?.toString() ?: "") }
            )
            is String -> when (metaPrefs?.getString(key, null)) {
                KIND_DOUBLE -> raw.toDoubleOrNull()?.let { PreferenceElement.ofDouble(it) }
                    ?: decodeUnmarkedString(raw)
                KIND_STRING -> PreferenceElement.ofString(raw)
                KIND_JSON -> runCatching { PreferenceObjectCodec.fromJsonElement(raw) }
                    .getOrElse { PreferenceElement.ofString(raw) }
                else -> decodeUnmarkedString(raw)
            }
            else -> null
        }
    }

    private fun decodeUnmarkedString(raw: String): PreferenceElement {
        val trimmed = raw.trim()
        val looksLikeJsonContainer =
            (trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                (trimmed.startsWith("[") && trimmed.endsWith("]"))
        if (looksLikeJsonContainer) {
            return runCatching { PreferenceObjectCodec.fromJsonElement(trimmed) }
                .getOrElse { PreferenceElement.ofString(raw) }
        }
        return PreferenceElement.ofString(raw)
    }

    private fun isOnMainLooper(): Boolean = runCatching {
        val main = Looper.getMainLooper()
        main != null && Looper.myLooper() === main
    }.getOrDefault(false)

    private fun hasMainLooper(): Boolean = runCatching {
        Looper.getMainLooper() != null
    }.getOrDefault(false)

    companion object {
        private const val META_PREFS_SUFFIX: String = "_genericui_kinds"
        private const val KIND_DOUBLE: String = "D"
        private const val KIND_STRING: String = "S"
        private const val KIND_JSON: String = "J"
        private const val LEGACY_BSON_ROOT_DIR: String = "genericui-prefs"
        private const val LEGACY_BSON_EXT: String = ".bson"
        private const val LEGACY_BSON_BACKUP_EXT: String = ".bson.old"
        private const val LEGACY_MIGRATION_MARKER_FILE: String = ".sharedprefs-migrated"

        var applicationContext: Context? = null

        private val sharedFallbackStores = java.util.concurrent.ConcurrentHashMap<String, LinkedHashMap<String, PreferenceElement>>()

        private fun sharedFallbackStoreFor(cleanName: String): LinkedHashMap<String, PreferenceElement> =
            sharedFallbackStores.getOrPut(cleanName) { LinkedHashMap() }

        fun sanitizePrefsName(raw: String): String {
            val trimmed = raw.trim().ifEmpty { "GenericUI" }
            return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
    }
}
