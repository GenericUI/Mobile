package com.nexustools.genericui.android

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.core.app.ActivityCompat
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.fragment.FragmentBundle
import com.nexustools.genericui.component.fragment.FragmentLaunchCoordinator
import com.nexustools.genericui.component.fragment.FragmentPlatformLauncher
import com.nexustools.genericui.component.fragment.IFragment
import com.nexustools.genericui.component.fragment.helper.FragmentHostFragment
import com.nexustools.genericui.core.GenericUiAppDescriptor
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.enums.fragment.FragmentLaunchMode
import com.nexustools.genericui.mobile.system.MobileDocumentPickerBridge
import com.nexustools.genericui.window.IWindow
import kotlinx.coroutines.CompletableDeferred
import java.lang.ref.WeakReference
import kotlin.reflect.KClass

/**
 * Registry mapping GenericUI [IFragment] classes 1:1 to generated or registered Android
 * [GenericUiFragmentActivity] classes, and implementing [FragmentPlatformLauncher] so
 * `launchFragment(...)` starts the corresponding Android `Activity` with serialized [FragmentBundle] extras.
 *
 * Also coordinates host [Activity] resolution, runtime permission launchers, and Storage Access Framework
 * (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`) results.
 */
object AndroidActivityRegistry : FragmentPlatformLauncher {

    const val EXTRA_FRAGMENT_BUNDLE_KEYS: String = "genericui:fragment_bundle_keys"
    const val EXTRA_FRAGMENT_BUNDLE_TYPES: String = "genericui:fragment_bundle_types"
    const val EXTRA_FRAGMENT_BUNDLE_JSON: String = "genericui:fragment_bundle_json"
    const val EXTRA_KEY_PREFIX: String = "genericui:arg:"

    private val activityMap = mutableMapOf<KClass<out IFragment>, Class<out GenericUiFragmentActivity>>()
    private val safBridge = MobileDocumentPickerBridge()

    private var activeFragmentActivityRef: WeakReference<GenericUiFragmentActivity>? = null
    private var nextPermissionRequestCode: Int = 5100
    private val legacyPermissionCallbacks = mutableMapOf<Int, (Map<String, Boolean>) -> Unit>()
    private val pendingLauncherPermissionCallbacks = ArrayDeque<(Map<String, Boolean>) -> Unit>()
    private var pendingSafResultDeferred: CompletableDeferred<List<String>?>? = null

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

    internal fun setActiveFragmentActivity(activity: GenericUiFragmentActivity?) {
        activeFragmentActivityRef = activity?.let { WeakReference(it) }
    }

    /**
     * Resolves the currently active foreground Android [Activity], or `null` when headless.
     */
    fun resolveActiveActivity(): Activity? {
        val fragAct = activeFragmentActivityRef?.get()
        if (fragAct != null && !fragAct.isFinishing && !fragAct.isDestroyed) {
            return fragAct
        }
        val dialogAct = AndroidFileBrowserDialogImpl.activeHostActivity
        if (dialogAct != null && !dialogAct.isFinishing && !dialogAct.isDestroyed) {
            return dialogAct
        }
        var ctx: Context? = AndroidViewHandle.activeContext
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) {
                return if (!ctx.isFinishing && !ctx.isDestroyed) ctx else null
            }
            ctx = ctx.baseContext
        }
        val act = ctx as? Activity
        return if (act != null && !act.isFinishing && !act.isDestroyed) act else null
    }

    /**
     * Launches an Android runtime permission prompt via [GenericUiFragmentActivity]'s `ActivityResultLauncher`
     * or `ActivityCompat.requestPermissions`, returning `true` when dispatched to an active `Activity`.
     */
    fun requestRuntimePermissions(
        permissions: Array<String>,
        onResult: (Map<String, Boolean>) -> Unit
    ): Boolean {
        if (permissions.isEmpty()) return false
        val fragAct = activeFragmentActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
        if (fragAct != null) {
            synchronized(pendingLauncherPermissionCallbacks) {
                pendingLauncherPermissionCallbacks.addLast(onResult)
            }
            val dispatched = runCatching {
                fragAct.launchRuntimePermissions(permissions)
            }.getOrDefault(false)
            if (dispatched) return true
            synchronized(pendingLauncherPermissionCallbacks) {
                if (pendingLauncherPermissionCallbacks.isNotEmpty()) {
                    pendingLauncherPermissionCallbacks.removeLast()
                }
            }
        }

        val activity = resolveActiveActivity() ?: return false
        val reqCode = nextPermissionRequestCode++
        synchronized(legacyPermissionCallbacks) {
            legacyPermissionCallbacks[reqCode] = onResult
        }
        val started = runCatching {
            ActivityCompat.requestPermissions(activity, permissions, reqCode)
            true
        }.getOrDefault(false)
        if (!started) {
            synchronized(legacyPermissionCallbacks) {
                legacyPermissionCallbacks.remove(reqCode)
            }
        }
        return started
    }

    internal fun deliverLauncherPermissionResult(results: Map<String, Boolean>) {
        val callback = synchronized(pendingLauncherPermissionCallbacks) {
            pendingLauncherPermissionCallbacks.removeFirstOrNull()
        }
        callback?.invoke(results)
    }

    fun dispatchPermissionResults(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val callback = synchronized(legacyPermissionCallbacks) {
            legacyPermissionCallbacks.remove(requestCode)
        } ?: return
        val map = LinkedHashMap<String, Boolean>()
        for (i in permissions.indices) {
            val granted = grantResults.getOrNull(i) == PackageManager.PERMISSION_GRANTED
            map[permissions[i]] = granted
        }
        callback(map)
    }

    internal suspend fun launchSafDocumentPicker(request: FileBrowserRequest): List<String>? {
        val fragAct = activeFragmentActivityRef?.get()?.takeIf { !it.isFinishing && !it.isDestroyed }
            ?: return null

        val action = when (request.mode) {
            FileBrowserMode.OPEN_SINGLE_FILE, FileBrowserMode.OPEN_MULTIPLE_FILES -> Intent.ACTION_OPEN_DOCUMENT
            FileBrowserMode.SAVE_FILE -> Intent.ACTION_CREATE_DOCUMENT
            FileBrowserMode.SELECT_FOLDER -> Intent.ACTION_OPEN_DOCUMENT_TREE
        }
        val mimeTypes = safBridge.resolveAndroidMimeTypes(request)
        val intent = runCatching {
            Intent(action).apply {
                if (action != Intent.ACTION_OPEN_DOCUMENT_TREE) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mimeTypes.firstOrNull() ?: "*/*"
                    if (mimeTypes.size > 1) {
                        putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes.toTypedArray())
                    }
                }
                if (request.mode == FileBrowserMode.OPEN_MULTIPLE_FILES) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                val defaultName = request.defaultFileName
                if (!defaultName.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TITLE, defaultName)
                }
            }
        }.getOrNull() ?: return null

        val deferred = CompletableDeferred<List<String>?>()
        pendingSafResultDeferred?.cancel()
        pendingSafResultDeferred = deferred
        val launched = runCatching {
            fragAct.launchSafIntent(intent)
        }.getOrDefault(false)
        if (!launched) {
            if (pendingSafResultDeferred === deferred) {
                pendingSafResultDeferred = null
            }
            return null
        }
        return runCatching { deferred.await() }.getOrNull()
    }

    internal fun deliverLauncherSafResult(resultCode: Int, data: Intent?) {
        val deferred = pendingSafResultDeferred
        pendingSafResultDeferred = null
        if (deferred == null || deferred.isCompleted) return
        if (resultCode == Activity.RESULT_OK && data != null) {
            val uris = mutableListOf<String>()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.toString()?.let { uris.add(it) }
                }
            } else {
                data.data?.toString()?.let { uris.add(it) }
            }
            deferred.complete(uris.takeIf { it.isNotEmpty() })
        } else {
            deferred.complete(null)
        }
    }

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
        activeFragmentActivityRef = null
        synchronized(legacyPermissionCallbacks) { legacyPermissionCallbacks.clear() }
        synchronized(pendingLauncherPermissionCallbacks) { pendingLauncherPermissionCallbacks.clear() }
        pendingSafResultDeferred?.cancel()
        pendingSafResultDeferred = null
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
        val context: Context? = resolveActiveActivity() ?: AndroidViewHandle.activeContext
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
