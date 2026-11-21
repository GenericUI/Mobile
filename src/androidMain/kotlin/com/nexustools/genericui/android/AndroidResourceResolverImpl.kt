package com.nexustools.genericui.android

import android.content.Context
import android.os.Looper
import com.nexustools.genericui.io.Readable
import com.nexustools.genericui.jvm.JvmResourceResolverBase
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android implementation of [com.nexustools.genericui.resources.ResourceResolverNativeImpl] extending
 * [JvmResourceResolverBase] to check Android `AssetManager` and `Context.resources` (`getIdentifier`)
 * first before falling back to JVM classpath/local resources and [DefaultResourceResolverNativeImpl].
 */
class AndroidResourceResolverImpl(
    private val context: Context? = null
) : JvmResourceResolverBase(
    customClassLoader = runCatching {
        (context ?: AndroidViewHandle.activeContext ?: AndroidPreferenceStoreImpl.applicationContext)?.classLoader
    }.getOrNull(),
    isOnMainThread = {
        runCatching {
            val mainLooper = Looper.getMainLooper()
            mainLooper != null && Thread.currentThread() === mainLooper.thread
        }.getOrDefault(false)
    }
) {
    private fun activeContext(): Context? =
        context ?: AndroidViewHandle.activeContext ?: AndroidPreferenceStoreImpl.applicationContext

    private fun lookupAndroidResId(ctx: Context, resId: ResourceID): Int = runCatching {
        val pkg = ctx.packageName ?: return 0
        val defType = resId.type.directoryName
        val candidateNames = linkedSetOf(resId.flatName, resId.name).filter { it.isNotBlank() }
        for (entryName in candidateNames) {
            val found = ctx.resources.getIdentifier(entryName, defType, pkg)
            if (found != 0) return found
            if (defType == "drawable") {
                val mipmap = ctx.resources.getIdentifier(entryName, "mipmap", pkg)
                if (mipmap != 0) return mipmap
            }
        }
        0
    }.getOrDefault(0)

    private fun openAssetStream(ctx: Context, resId: ResourceID) = candidateClasspathPaths(resId)
        .asSequence()
        .mapNotNull { rel ->
            val clean = rel.removePrefix("assets/").trimStart('/')
            runCatching { ctx.assets.open(clean) }.getOrNull()
        }
        .firstOrNull()

    private fun assetExists(ctx: Context, resId: ResourceID): String? {
        for (rel in candidateClasspathPaths(resId)) {
            val clean = rel.removePrefix("assets/").trimStart('/')
            val opened = runCatching {
                ctx.assets.open(clean).use { true }
            }.getOrDefault(false)
            if (opened) return "file:///android_asset/$clean"
        }
        return null
    }

    override fun resolveClassLoader(): ClassLoader? =
        runCatching { activeContext()?.classLoader }.getOrNull() ?: super.resolveClassLoader()

    override fun resolveString(resId: ResourceID): String? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                runCatching { return ctx.resources.getString(id) }
            }
        }
        return super.resolveString(resId)
    }

    override fun resolveColor(resId: ResourceID): RgbaColor? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                runCatching {
                    val argb = ctx.resources.getColor(id, ctx.theme)
                    val a = ((argb ushr 24) and 0xFF) / 255f
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    return RgbaColor(r, g, b, a)
                }
            }
        }
        return super.resolveColor(resId)
    }

    override fun resolveDimenPx(resId: ResourceID): Float? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                runCatching { return ctx.resources.getDimension(id) }
            }
        }
        return super.resolveDimenPx(resId)
    }

    override fun resolveBytes(resId: ResourceID): ByteArray? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                runCatching {
                    return ctx.resources.openRawResource(id).use { it.readBytes() }
                }
            }
            openAssetStream(ctx, resId)?.use { stream ->
                return stream.readBytes()
            }
        }
        return super.resolveBytes(resId)
    }

    override fun resolveUrl(resId: ResourceID): String {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                return "android.resource://${ctx.packageName}/$id"
            }
            assetExists(ctx, resId)?.let { return it }
        }
        return super.resolveUrl(resId)
    }

    override suspend fun openResourceStream(resId: ResourceID): Readable? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                runCatching {
                    val stream = ctx.resources.openRawResource(id)
                    return AndroidFileReadableNativeImpl(
                        inputStream = stream,
                        filePath = "android.resource://${ctx.packageName}/$id"
                    )
                }
            }
            for (rel in candidateClasspathPaths(resId)) {
                val clean = rel.removePrefix("assets/").trimStart('/')
                val existsInAssets = runCatching {
                    ctx.assets.open(clean).use { true }
                }.getOrDefault(false)
                if (existsInAssets) {
                    return AndroidFileReadableNativeImpl(
                        assetManager = ctx.assets,
                        assetPath = clean
                    )
                }
            }
        }
        return super.openResourceStream(resId)
    }
}
