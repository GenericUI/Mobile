package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.jvm.JvmPreferenceStoreBase
import java.io.File

/**
 * Android implementation of [com.nexustools.genericui.nativeimpl.system.PreferenceStoreNativeImpl]
 * extending [JvmPreferenceStoreBase] and persisting preference keys as binary `.bson` files inside
 * `Context.filesDir` (`<filesDir>/genericui-prefs/<appName>/`), falling back to [JvmPreferenceStoreBase]
 * host storage when no Android [Context] is attached in uninstrumented host tests.
 */
class AndroidPreferenceStoreImpl(
    appName: String = "GenericUI",
    private val context: Context? = applicationContext ?: AndroidViewHandle.activeContext
) : JvmPreferenceStoreBase(
    appName = appName,
    customDirectory = null
) {
    override val useBinaryPlist: Boolean
        get() = false

    override fun resolveDefaultDirectory(appName: String): File {
        val cleanName = sanitizePrefsName(appName)
        val ctx = context ?: applicationContext ?: AndroidViewHandle.activeContext
        val filesDir = runCatching { ctx?.filesDir }.getOrNull()
        if (filesDir != null) {
            return File(filesDir, "genericui-prefs/$cleanName")
        }
        return super.resolveDefaultDirectory(cleanName)
    }

    companion object {
        var applicationContext: Context? = null

        fun sanitizePrefsName(raw: String): String {
            val trimmed = raw.trim().ifEmpty { "GenericUI" }
            return trimmed.replace(Regex("[^A-Za-z0-9._-]"), "_")
        }
    }
}
