package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentLaunchCoordinator
import com.nexustools.genericui.component.fragment.FragmentPlatformLauncher
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.window.IWindow
import kotlin.reflect.KClass

/**
 * Registry mapping GenericUI [IFragment] classes 1:1 to generated or registered Android
 * [GenericUiFragmentActivity] classes, and implementing [FragmentPlatformLauncher] so
 * `launchFragment(...)` starts the corresponding Android `Activity` with serialized [FragmentBundle] extras.
 */
object AndroidActivityRegistry : FragmentPlatformLauncher {

    const val EXTRA_FRAGMENT_BUNDLE_KEYS: String = "genericui:fragment_bundle_keys"
    const val EXTRA_FRAGMENT_BUNDLE_TYPES: String = "genericui:fragment_bundle_types"
    const val EXTRA_FRAGMENT_BUNDLE_JSON: String = "genericui:fragment_bundle_json"
    const val EXTRA_KEY_PREFIX: String = "genericui:arg:"

    private val activityMap = mutableMapOf<KClass<out IFragment>, Class<out GenericUiFragmentActivity>>()

    var mainFragmentClass: KClass<out IFragment>? = null
    var mainActivityClass: Class<out GenericUiFragmentActivity>? = null
    var activeDescriptor: GenericUiAppDescriptor? = null

    var lastLaunchedIntent: Intent? = null
        private set
    var lastLaunchedFragmentClass: KClass<out IFragment>? = null
        private set
    var lastLaunchedActivityClass: Class<out GenericUiFragmentActivity>? = null
        private set
    var lastLaunchedArgs: FragmentBundle? = null
        private set
    var lastLaunchedMode: FragmentLaunchMode? = null
        private set

    internal var pendingLaunchBundle: FragmentBundle? = null

    /**
     * Registers a 1:1 mapping from [fragmentClass] to [activityClass].
     */
    fun <F : IFragment, A : GenericUiFragmentActivity> register(
        fragmentClass: KClass<F>,
        activityClass: Class<A>,
        isMain: Boolean = false
    ) {
        activityMap[fragmentClass] = activityClass
        if (isMain || mainFragmentClass == null && isMain) {
            mainFragmentClass = fragmentClass
            mainActivityClass = activityClass
        }
    }

    /**
     * Removes the registration for [fragmentClass], if present.
     */
    fun unregister(fragmentClass: KClass<out IFragment>) {
        activityMap.remove(fragmentClass)
        if (mainFragmentClass == fragmentClass) {
            mainFragmentClass = null
            mainActivityClass = null
        }
    }

    /**
     * Returns `true` if [fragmentClass] is registered with a 1:1 [GenericUiFragmentActivity].
     */
    fun isRegistered(fragmentClass: KClass<out IFragment>): Boolean =
        activityMap.containsKey(fragmentClass)

    /**
     * Returns the registered [GenericUiFragmentActivity] class for [fragmentClass], or `null` if not registered.
     */
    fun getActivityClass(fragmentClass: KClass<out IFragment>): Class<out GenericUiFragmentActivity>? =
        activityMap[fragmentClass]

    /**
     * Returns the registered [GenericUiFragmentActivity] class for [fragmentClass], or throws an
     * [IllegalArgumentException] with a clear diagnostic listing all registered Android app fragments.
     */
    fun requireActivityClass(fragmentClass: KClass<out IFragment>): Class<out GenericUiFragmentActivity> {
        return activityMap[fragmentClass] ?: throw IllegalArgumentException(
            "Fragment '${fragmentClass.qualifiedName ?: fragmentClass.simpleName}' is not registered as an " +
                "@AppFragment or @MainAppFragment on Android. Registered Android app fragments: " +
                "${activityMap.keys.mapNotNull { it.qualifiedName ?: it.simpleName }}"
        )
    }

    /**
     * Returns an immutable snapshot of all registered [IFragment] classes.
     */
    fun registeredFragmentClasses(): Set<KClass<out IFragment>> = activityMap.keys.toSet()

    /**
     * Installs [AndroidActivityRegistry] as the active [FragmentLaunchCoordinator.platformLauncher].
     */
    fun installAsPlatformLauncher() {
        FragmentLaunchCoordinator.platformLauncher = this
    }

    /**
     * Clears all registered mappings and launch tracking state.
     */
    fun clear() {
        activityMap.clear()
        mainFragmentClass = null
        mainActivityClass = null
        activeDescriptor = null
        lastLaunchedIntent = null
        lastLaunchedFragmentClass = null
        lastLaunchedActivityClass = null
        lastLaunchedArgs = null
        lastLaunchedMode = null
        pendingLaunchBundle = null
    }

    override fun launch(
        fragmentClass: KClass<out IFragment>,
        launchArgs: FragmentBundle?,
        launchMode: FragmentLaunchMode,
        sourceWindow: IWindow?
    ): Boolean = launchByClass(
        fragmentClass = fragmentClass,
        launchArgs = launchArgs,
        launchMode = launchMode,
        sourceWindow = sourceWindow
    )

    /**
     * Launches [fragmentClass] via its registered 1:1 [GenericUiFragmentActivity].
     */
    fun launchByClass(
        fragmentClass: KClass<out IFragment>,
        launchArgs: FragmentBundle? = null,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW,
        sourceWindow: IWindow? = null
    ): Boolean {
        val inFragmentHostFragment = sourceWindow?.activeFragment is FragmentHostFragment
        if (inFragmentHostFragment && !activityMap.containsKey(fragmentClass)) {
            return false
        }
        if (launchMode == FragmentLaunchMode.NEW_DIALOG_WINDOW || launchMode == FragmentLaunchMode.NEW_PANEL_WINDOW) {
            if (!activityMap.containsKey(fragmentClass)) {
                return false
            }
        }
        val context: Context? = AndroidFileBrowserDialogImpl.activeHostActivity ?: AndroidViewHandle.activeContext
        if (activityMap.isEmpty() && context == null) {
            return false
        }

        val activityClass = requireActivityClass(fragmentClass)
        lastLaunchedFragmentClass = fragmentClass
        lastLaunchedActivityClass = activityClass
        lastLaunchedArgs = launchArgs
        lastLaunchedMode = launchMode
        pendingLaunchBundle = launchArgs

        val intent = buildActivityIntent(context, activityClass, launchArgs, launchMode)
        lastLaunchedIntent = intent

        if (context != null && intent != null) {
            runCatching {
                context.startActivity(intent)
            }
        }
        return true
    }

    /**
     * Launches an already-instantiated [fragment] via its registered 1:1 [GenericUiFragmentActivity].
     */
    fun launchInstance(
        fragment: IFragment,
        launchArgs: FragmentBundle? = fragment.arguments,
        launchMode: FragmentLaunchMode = FragmentLaunchMode.SAME_WINDOW,
        sourceWindow: IWindow? = fragment.hostingWindow
    ): Boolean = launchByClass(
        fragmentClass = fragment::class,
        launchArgs = launchArgs ?: fragment.arguments,
        launchMode = launchMode,
        sourceWindow = sourceWindow
    )

    /**
     * Builds an Android [Intent] targeting [activityClass] with [launchArgs] extras and [launchMode] flags.
     */
    fun buildActivityIntent(
        context: Context?,
        activityClass: Class<out GenericUiFragmentActivity>,
        launchArgs: FragmentBundle?,
        launchMode: FragmentLaunchMode
    ): Intent? {
        return runCatching {
            val intent = if (context != null) {
                Intent(context, activityClass)
            } else {
                Intent()
            }
            if (launchMode == FragmentLaunchMode.CLEAR_TASK) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK)
            } else if (context != null && context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (launchArgs != null) {
                putFragmentBundleExtras(intent, launchArgs)
            }
            intent
        }.getOrNull()
    }

    /**
     * Serializes [fragmentBundle] into [intent] extras (both typed extras and JSON representation).
     */
    fun putFragmentBundleExtras(intent: Intent, fragmentBundle: FragmentBundle) {
        val json = serializeFragmentBundleToJson(fragmentBundle)
        runCatching {
            intent.putExtra(EXTRA_FRAGMENT_BUNDLE_JSON, json)
            val keys = fragmentBundle.keys().toTypedArray()
            intent.putExtra(EXTRA_FRAGMENT_BUNDLE_KEYS, keys)
            for (key in keys) {
                val prefixedKey = "$EXTRA_KEY_PREFIX$key"
                when (val value = fragmentBundle[key]) {
                    is String -> intent.putExtra(prefixedKey, value)
                    is Int -> intent.putExtra(prefixedKey, value)
                    is Long -> intent.putExtra(prefixedKey, value)
                    is Double -> intent.putExtra(prefixedKey, value)
                    is Boolean -> intent.putExtra(prefixedKey, value)
                    is List<*> -> intent.putExtra(prefixedKey, value.filterIsInstance<String>().toTypedArray())
                    is Array<*> -> intent.putExtra(prefixedKey, value.filterIsInstance<String>().toTypedArray())
                }
            }
        }
    }

    /**
     * Writes [fragmentBundle] entries into an Android [Bundle].
     */
    fun putFragmentBundleToAndroidBundle(outBundle: Bundle, fragmentBundle: FragmentBundle) {
        val json = serializeFragmentBundleToJson(fragmentBundle)
        runCatching {
            outBundle.putString(EXTRA_FRAGMENT_BUNDLE_JSON, json)
            val keys = fragmentBundle.keys().toTypedArray()
            outBundle.putStringArray(EXTRA_FRAGMENT_BUNDLE_KEYS, keys)
            for (key in keys) {
                when (val value = fragmentBundle[key]) {
                    is String -> outBundle.putString(key, value)
                    is Int -> outBundle.putInt(key, value)
                    is Long -> outBundle.putLong(key, value)
                    is Double -> outBundle.putDouble(key, value)
                    is Boolean -> outBundle.putBoolean(key, value)
                    is List<*> -> outBundle.putStringArray(key, value.filterIsInstance<String>().toTypedArray())
                    is Array<*> -> outBundle.putStringArray(key, value.filterIsInstance<String>().toTypedArray())
                }
            }
        }
    }

    /**
     * Extracts a [FragmentBundle] from [intent] extras, falling back to [pendingLaunchBundle] if needed.
     */
    fun extractFragmentBundle(intent: Intent?): FragmentBundle? {
        if (intent != null) {
            val fromJson = runCatching {
                intent.getStringExtra(EXTRA_FRAGMENT_BUNDLE_JSON)
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { deserializeFragmentBundleFromJson(it) }
            if (fromJson != null) {
                pendingLaunchBundle = null
                return fromJson
            }

            val extras = runCatching { intent.extras }.getOrNull()
            if (extras != null) {
                val extracted = bundleToFragmentBundle(extras)
                if (extracted.keys().isNotEmpty()) {
                    pendingLaunchBundle = null
                    return extracted
                }
            }
        }
        val pending = pendingLaunchBundle
        pendingLaunchBundle = null
        return pending
    }

    /**
     * Converts an Android [Bundle] into a GenericUI [FragmentBundle].
     */
    fun bundleToFragmentBundle(bundle: Bundle): FragmentBundle {
        val json = runCatching { bundle.getString(EXTRA_FRAGMENT_BUNDLE_JSON) }.getOrNull()
        if (!json.isNullOrBlank()) {
            val parsed = deserializeFragmentBundleFromJson(json)
            if (parsed.keys().isNotEmpty()) {
                return parsed
            }
        }
        val out = FragmentBundle()
        val keySet = runCatching { bundle.keySet() }.getOrNull() ?: emptySet()
        for (rawKey in keySet) {
            if (rawKey == EXTRA_FRAGMENT_BUNDLE_KEYS ||
                rawKey == EXTRA_FRAGMENT_BUNDLE_TYPES ||
                rawKey == EXTRA_FRAGMENT_BUNDLE_JSON
            ) {
                continue
            }
            val targetKey = rawKey.removePrefix(EXTRA_KEY_PREFIX)
            @Suppress("DEPRECATION")
            when (val v = runCatching { bundle.get(rawKey) }.getOrNull()) {
                is String -> out.putString(targetKey, v)
                is Int -> out.putInt(targetKey, v)
                is Long -> out.putLong(targetKey, v)
                is Double -> out.putDouble(targetKey, v)
                is Boolean -> out.putBoolean(targetKey, v)
                is Array<*> -> out.put(targetKey, v.filterIsInstance<String>())
                is List<*> -> out.put(targetKey, v.filterIsInstance<String>())
            }
        }
        return out
    }

    internal fun serializeFragmentBundleToJson(bundle: FragmentBundle): String {
        val entries = mutableListOf<String>()
        for (key in bundle.keys()) {
            val escapedKey = escapeJson(key)
            val value = bundle[key]
            val encoded = when (value) {
                null -> """{"k":"$escapedKey","t":"null","v":""}"""
                is String -> """{"k":"$escapedKey","t":"string","v":"${escapeJson(value)}"}"""
                is Int -> """{"k":"$escapedKey","t":"int","v":"$value"}"""
                is Long -> """{"k":"$escapedKey","t":"long","v":"$value"}"""
                is Double -> """{"k":"$escapedKey","t":"double","v":"$value"}"""
                is Boolean -> """{"k":"$escapedKey","t":"boolean","v":"$value"}"""
                is List<*> -> {
                    val joined = value.filterIsInstance<String>().joinToString("\u001F") { escapeJson(it) }
                    """{"k":"$escapedKey","t":"string_list","v":"$joined"}"""
                }
                is Array<*> -> {
                    val joined = value.filterIsInstance<String>().joinToString("\u001F") { escapeJson(it) }
                    """{"k":"$escapedKey","t":"string_list","v":"$joined"}"""
                }
                else -> """{"k":"$escapedKey","t":"string","v":"${escapeJson(value.toString())}"}"""
            }
            entries.add(encoded)
        }
        return "[${entries.joinToString(",")}]"
    }

    internal fun deserializeFragmentBundleFromJson(json: String): FragmentBundle {
        val bundle = FragmentBundle()
        val entryRegex = Regex("""\{\s*"k"\s*:\s*"((?:\\.|[^"\\])*)"\s*,\s*"t"\s*:\s*"([^"]+)"\s*,\s*"v"\s*:\s*"((?:\\.|[^"\\])*)"\s*\}""")
        for (match in entryRegex.findAll(json)) {
            val key = unescapeJson(match.groupValues[1])
            val type = match.groupValues[2]
            val rawVal = unescapeJson(match.groupValues[3])
            when (type) {
                "null" -> bundle.putString(key, null)
                "string" -> bundle.putString(key, rawVal)
                "int" -> rawVal.toIntOrNull()?.let { bundle.putInt(key, it) }
                "long" -> rawVal.toLongOrNull()?.let { bundle.putLong(key, it) }
                "double" -> rawVal.toDoubleOrNull()?.let { bundle.putDouble(key, it) }
                "boolean" -> bundle.putBoolean(key, rawVal.toBooleanStrictOrNull() ?: false)
                "string_list" -> {
                    val items = if (rawVal.isEmpty()) emptyList() else rawVal.split("\u001F")
                    bundle.put(key, items)
                }
            }
        }
        return bundle
    }

    private fun escapeJson(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
    }

    private fun unescapeJson(value: String): String = buildString(value.length) {
        var i = 0
        while (i < value.length) {
            val ch = value[i]
            if (ch == '\\' && i + 1 < value.length) {
                when (val next = value[i + 1]) {
                    '\\' -> append('\\')
                    '"' -> append('"')
                    'n' -> append('\n')
                    'r' -> append('\r')
                    't' -> append('\t')
                    else -> append(next)
                }
                i += 2
            } else {
                append(ch)
                i++
            }
        }
    }
}
