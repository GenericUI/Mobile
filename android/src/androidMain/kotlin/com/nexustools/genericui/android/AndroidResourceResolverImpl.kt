package com.nexustools.genericui.android

import android.content.Context
import com.nexustools.genericui.resources.DefaultResourceResolverNativeImpl
import com.nexustools.genericui.resources.ResourceID
import com.nexustools.genericui.style.color.RgbaColor

/**
 * Android (`:mobile:android`) implementation of [com.nexustools.genericui.resources.ResourceResolverNativeImpl]
 * that queries Android `Context.resources` (`getIdentifier`) for strings, colors, dimensions, and raw streams
 * when an Android [Context] is attached, falling back to [DefaultResourceResolverNativeImpl].
 */
class AndroidResourceResolverImpl(
    private val context: Context? = null
) : DefaultResourceResolverNativeImpl() {

    private fun activeContext(): Context? =
        context ?: AndroidViewHandle.activeContext ?: AndroidPreferenceStoreImpl.applicationContext

    private fun lookupAndroidResId(ctx: Context, resId: ResourceID): Int = try {
        val pkg = ctx.packageName ?: return 0
        ctx.resources.getIdentifier(resId.name, resId.type.directoryName, pkg)
    } catch (_: Throwable) {
        0
    }

    override fun resolveString(resId: ResourceID): String? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                try {
                    return ctx.resources.getString(id)
                } catch (_: Throwable) {
                }
            }
        }
        return super.resolveString(resId)
    }

    override fun resolveColor(resId: ResourceID): RgbaColor? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                try {
                    val argb = ctx.resources.getColor(id, ctx.theme)
                    val a = ((argb ushr 24) and 0xFF) / 255f
                    val r = (argb ushr 16) and 0xFF
                    val g = (argb ushr 8) and 0xFF
                    val b = argb and 0xFF
                    return RgbaColor(r, g, b, a)
                } catch (_: Throwable) {
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
                try {
                    return ctx.resources.getDimension(id)
                } catch (_: Throwable) {
                }
            }
        }
        return super.resolveDimenPx(resId)
    }

    override fun resolveBytes(resId: ResourceID): ByteArray? {
        val ctx = activeContext()
        if (ctx != null) {
            val id = lookupAndroidResId(ctx, resId)
            if (id != 0) {
                try {
                    return ctx.resources.openRawResource(id).use { it.readBytes() }
                } catch (_: Throwable) {
                }
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
        }
        return super.resolveUrl(resId)
    }
}
