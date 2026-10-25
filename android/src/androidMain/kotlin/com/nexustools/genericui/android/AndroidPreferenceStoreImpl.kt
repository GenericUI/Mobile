package com.nexustools.genericui.android

import android.content.Context
import android.content.SharedPreferences
import com.nexustools.genericui.component.system.PreferenceChangeListener
import com.nexustools.genericui.component.system.PreferenceElement
import com.nexustools.genericui.component.system.PreferenceElementKind
import com.nexustools.genericui.component.system.PreferenceObjectCodec
import com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl

/**
 * Android (`:mobile:android`) implementation of [PreferenceStoreNativeImpl] backed by
 * `android.content.SharedPreferences` (`context.getSharedPreferences(appName, Context.MODE_PRIVATE)`)
 * and a strong-referenced `SharedPreferences.OnSharedPreferenceChangeListener`.
 */
class AndroidPreferenceStoreImpl(
    override val appName: String = "GenericUI",
    context: Context? = applicationContext ?: AndroidViewHandle.activeContext
) : PreferenceStoreNativeImpl {

    private val sharedPrefs: SharedPreferences? =
        context?.getSharedPreferences(sanitizePrefsName(appName), Context.MODE_PRIVATE)

    override val storageLocationDescription: String =
        "SharedPreferences(${sanitizePrefsName(appName)})"

    override val usesNativeChangeListener: Boolean = true

    override var isDisposed: Boolean = false
        private set

    private val fallbackMap = LinkedHashMap<String, PreferenceElement>()
    private val listeners = mutableListOf<PreferenceChangeListener>()

    /**
     * Strong reference to [SharedPreferences.OnSharedPreferenceChangeListener] so Android's
     * internal `WeakHashMap` never garbage-collects the listener while this store is active.
     */
    private val nativeSharedPrefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null) {
            notifyListeners(null, null)
        } else {
            val updated = readElement(key)?.toPlainValue()
            notifyListeners(key, updated)
        }
    }

    init {
        sharedPrefs?.registerOnSharedPreferenceChangeListener(nativeSharedPrefsListener)
    }

    override fun writeElement(key: String, element: PreferenceElement) {
        if (element.isNull) {
            remove(key)
            return
        }
        val prefs = sharedPrefs
        if (prefs != null) {
            val editor = prefs.edit()
            when (element.kind) {
                PreferenceElementKind.BOOLEAN -> editor.putBoolean(key, element.booleanValue == true)
                PreferenceElementKind.NUMBER -> {
                    when (element.numberTypeTag) {
                        PreferenceElement.NUMBER_TAG_INT -> editor.putInt(key, element.asInt())
                        PreferenceElement.NUMBER_TAG_LONG -> editor.putLong(key, element.asLong())
                        PreferenceElement.NUMBER_TAG_FLOAT -> editor.putFloat(key, element.asFloat())
                        else -> editor.putString(key, PreferenceObjectCodec.elementToJson(element))
                    }
                }
                PreferenceElementKind.STRING -> editor.putString(key, element.stringValue ?: "")
                PreferenceElementKind.ARRAY,
                PreferenceElementKind.MAP,
                PreferenceElementKind.OBJECT -> {
                    editor.putString(key, PreferenceObjectCodec.elementToJson(element))
                }
                PreferenceElementKind.NULL -> editor.remove(key)
            }
            editor.apply()
        } else {
            fallbackMap[key] = element
            notifyListeners(key, element.toPlainValue())
        }
    }

    override fun readElement(key: String): PreferenceElement? {
        val prefs = sharedPrefs
        if (prefs != null) {
            if (!prefs.contains(key)) return null
            val raw = prefs.all[key] ?: return null
            return when (raw) {
                is Boolean -> PreferenceElement.ofBoolean(raw)
                is Int -> PreferenceElement.ofInt(raw)
                is Long -> PreferenceElement.ofLong(raw)
                is Float -> PreferenceElement.ofFloat(raw)
                is String -> {
                    val trimmed = raw.trim()
                    if ((trimmed.startsWith("{") && trimmed.endsWith("}")) ||
                        (trimmed.startsWith("[") && trimmed.endsWith("]"))
                    ) {
                        runCatching { PreferenceObjectCodec.fromJsonElement(trimmed) }
                            .getOrElse { PreferenceElement.ofString(raw) }
                    } else {
                        val numericDouble = trimmed.toDoubleOrNull()
                        if (numericDouble != null && trimmed.contains('.')) {
                            PreferenceElement.ofDouble(numericDouble)
                        } else {
                            PreferenceElement.ofString(raw)
                        }
                    }
                }
                is Set<*> -> PreferenceElement.ofArray(
                    raw.map { PreferenceElement.ofString(it?.toString() ?: "") }
                )
                else -> PreferenceElement.ofString(raw.toString())
            }
        }
        return fallbackMap[key]
    }

    override fun contains(key: String): Boolean {
        val prefs = sharedPrefs
        return if (prefs != null) prefs.contains(key) else fallbackMap.containsKey(key)
    }

    override fun keys(): Set<String> {
        val prefs = sharedPrefs
        return if (prefs != null) LinkedHashSet(prefs.all.keys) else LinkedHashSet(fallbackMap.keys)
    }

    override fun remove(key: String) {
        val prefs = sharedPrefs
        if (prefs != null) {
            if (prefs.contains(key)) {
                prefs.edit().remove(key).apply()
            }
        } else {
            if (fallbackMap.remove(key) != null) {
                notifyListeners(key, null)
            }
        }
    }

    override fun clear() {
        val prefs = sharedPrefs
        if (prefs != null) {
            prefs.edit().clear().apply()
        } else {
            if (fallbackMap.isNotEmpty()) {
                fallbackMap.clear()
                notifyListeners(null, null)
            }
        }
    }

    override fun flush(): Boolean {
        val prefs = sharedPrefs
        return if (prefs != null) prefs.edit().commit() else !isDisposed
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
        if (!isDisposed) {
            isDisposed = true
            sharedPrefs?.unregisterOnSharedPreferenceChangeListener(nativeSharedPrefsListener)
            listeners.clear()
        }
    }

    private fun notifyListeners(key: String?, newValue: Any?) {
        val snapshot = listeners.toList()
        for (listener in snapshot) {
            listener.onPreferenceChanged(appName, key, newValue)
        }
    }

    companion object {
        var applicationContext: Context? = null

        fun sanitizePrefsName(raw: String): String {
            val trimmed = raw.trim().ifEmpty { "GenericUI" }
            return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
    }
}
