package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import kotlinx.coroutines.Job

/**
 * Android `androidx.appcompat.widget.AppCompatImageView` implementation of [ImageNativeImpl].
 *
 * Uses Kotlin coroutines via [ImageLoader.launchLoad] for non-blocking image loading, supports
 * placeholder loading images (`placeholderUri`), resolves compiled Android vector/raster drawables,
 * and enables antialiased bitmap filtering by default.
 */
class AndroidImageImpl : AndroidBaseNativeImpl("androidx.appcompat.widget.AppCompatImageView", "img-fluid"), ImageNativeImpl<AndroidViewHandle> {
    private var sourceUrl: String? = null
    private var placeholderUrl: String? = "res://drawable/loading"
    private var objectFit: ObjectFit = ObjectFit.CONTAIN
    private var antialiasing: Boolean = true
    private var loadingState: Boolean = false
    private var tintColor: RgbaColor? = null
    private var tintMode: ImageTintMode = ImageTintMode.NONE
    private var cssFilter: CssFilter? = null
    private var onLoadCallback: (() -> Unit)? = null
    private var onErrorCallback: (() -> Unit)? = null
    private var activeLoadGeneration: Int = 0
    private var activeLoadJob: Job? = null
    private var retainedBuffer: ImageBuffer? = null

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        nativeWidget.attributes["filterBitmap"] = "true"
        syncAndroidView()
    }

    override fun createDefaultAndroidView(context: Context): View? =
        AppCompatImageView(context).apply {
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

    private fun toAndroidBitmap(buffer: ImageBuffer): Bitmap? = runCatching {
        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOff = y * w
            for (x in 0 until w) {
                val px = buffer.getPixel(x, y)
                pixels[rowOff + x] = ((px.a and 0xFF) shl 24) or
                    ((px.r and 0xFF) shl 16) or
                    ((px.g and 0xFF) shl 8) or
                    (px.b and 0xFF)
            }
        }
        Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    private fun resolveEffectiveTintArgb(): Int? {
        tintColor?.let { return it.toArgbInt() }
        if (tintMode == ImageTintMode.MONOCOLOR_AUTO_THEME) {
            return if (AndroidViewHandle.isDarkTheme()) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
        }
        return null
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                iv.scaleType = when (objectFit) {
                    ObjectFit.CONTAIN -> ImageView.ScaleType.FIT_CENTER
                    ObjectFit.COVER -> ImageView.ScaleType.CENTER_CROP
                    ObjectFit.FILL -> ImageView.ScaleType.FIT_XY
                    ObjectFit.NONE, ObjectFit.SCALE_DOWN -> ImageView.ScaleType.CENTER_INSIDE
                }
                val effectiveTint = resolveEffectiveTintArgb()
                ImageViewCompat.setImageTintList(
                    iv,
                    effectiveTint?.let { ColorStateList.valueOf(it) }
                )
                val buf = retainedBuffer
                if (buf != null) {
                    toAndroidBitmap(buf)?.let { iv.setImageBitmap(it) }
                } else if (!sourceUrl.isNullOrBlank()) {
                    val d = resolveDrawable(iv.context, sourceUrl!!, effectiveTint)
                    if (d != null) {
                        iv.setImageDrawable(d)
                    }
                } else {
                    iv.setImageDrawable(null)
                }
            }
        }
    }

    override fun setSourceUrl(url: String?) {
        val trimmed = url?.trim()
        if (!trimmed.isNullOrEmpty() && trimmed.startsWith("memory://", ignoreCase = true)) {
            ImageLoader.resolveMemoryBuffer(trimmed)?.let {
                setImageBuffer(it)
                return
            }
        }
        val gen = ++activeLoadGeneration
        activeLoadJob?.cancel()
        activeLoadJob = null
        sourceUrl = url
        if (!url.isNullOrBlank()) {
            nativeWidget.attributes["imageUri"] = url
            val ctx = (nativeWidget.androidView?.context) ?: AndroidViewHandle.activeContext
            val effectiveTint = resolveEffectiveTintArgb()
            val resolvedDrawable = if (ctx != null) {
                runCatching { resolveDrawable(ctx, url, effectiveTint) }.getOrNull()
            } else {
                null
            }
            if (resolvedDrawable != null) {
                retainedBuffer = null
                loadingState = false
                nativeWidget.attributes["isLoading"] = "false"
                nativeWidget.attributes["imageWidth"] = resolvedDrawable.intrinsicWidth.coerceAtLeast(1).toString()
                nativeWidget.attributes["imageHeight"] = resolvedDrawable.intrinsicHeight.coerceAtLeast(1).toString()
                AndroidViewHandle.runOnMainThread {
                    runCatching {
                        val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                        ImageViewCompat.setImageTintList(
                            iv,
                            effectiveTint?.let { ColorStateList.valueOf(it) }
                        )
                        iv.setImageDrawable(resolvedDrawable)
                    }
                }
                onLoadCallback?.invoke()
                return
            }
            loadingState = true
            nativeWidget.attributes["isLoading"] = "true"
            activeLoadJob = ImageLoader.launchLoad(
                source = ImageSource.fromUrl(url),
                options = ImageDecodeOptions(antialiasing = antialiasing),
                onLoaded = { buf ->
                    if (gen != activeLoadGeneration) return@launchLoad
                    retainedBuffer = buf
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    nativeWidget.attributes["imageWidth"] = buf.width.toString()
                    nativeWidget.attributes["imageHeight"] = buf.height.toString()
                    val bmp = toAndroidBitmap(buf)
                    AndroidViewHandle.runOnMainThread {
                        if (gen != activeLoadGeneration) return@runOnMainThread
                        (nativeWidget.androidView as? ImageView)?.let { imageView ->
                            if (bmp != null) {
                                runCatching { imageView.setImageBitmap(bmp) }
                            }
                        }
                        onLoadCallback?.invoke()
                    }
                },
                onError = {
                    if (gen != activeLoadGeneration) return@launchLoad
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    AndroidViewHandle.runOnMainThread {
                        if (gen != activeLoadGeneration) return@runOnMainThread
                        onErrorCallback?.invoke()
                    }
                }
            )
        } else {
            loadingState = false
            retainedBuffer = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes.remove("imageWidth")
            nativeWidget.attributes.remove("imageHeight")
            nativeWidget.attributes["isLoading"] = "false"
            AndroidViewHandle.runOnMainThread {
                (nativeWidget.androidView as? ImageView)?.let { iv ->
                    runCatching { iv.setImageDrawable(null) }
                }
            }
        }
    }

    override fun getSourceUrl(): String? = sourceUrl

    override fun setImageBuffer(buffer: ImageBuffer?) {
        activeLoadGeneration++
        activeLoadJob?.cancel()
        activeLoadJob = null
        if (buffer == null) {
            loadingState = false
            sourceUrl = null
            retainedBuffer = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes.remove("imageWidth")
            nativeWidget.attributes.remove("imageHeight")
            nativeWidget.attributes["isLoading"] = "false"
            AndroidViewHandle.runOnMainThread {
                (nativeWidget.androidView as? ImageView)?.let { iv ->
                    runCatching { iv.setImageDrawable(null) }
                }
            }
            return
        }
        val memoryUri = "memory://imagebuffer/${buffer.width}x${buffer.height}@${buffer.hashCode()}"
        ImageLoader.registerMemoryBuffer(memoryUri, buffer)
        sourceUrl = memoryUri
        loadingState = false
        retainedBuffer = buffer
        nativeWidget.attributes["imageUri"] = memoryUri
        nativeWidget.attributes["imageWidth"] = buffer.width.toString()
        nativeWidget.attributes["imageHeight"] = buffer.height.toString()
        nativeWidget.attributes["isLoading"] = "false"
        syncAndroidView()
        onLoadCallback?.invoke()
    }

    override fun setPlaceholderUrl(url: String?) {
        placeholderUrl = url
        if (url != null) {
            nativeWidget.attributes["placeholderUri"] = url
        } else {
            nativeWidget.attributes.remove("placeholderUri")
        }
    }

    override fun getPlaceholderUrl(): String? = placeholderUrl

    override fun setAntialiasing(enabled: Boolean) {
        antialiasing = enabled
        nativeWidget.attributes["antialiasing"] = enabled.toString()
        nativeWidget.attributes["filterBitmap"] = enabled.toString()
    }

    override fun isAntialiasing(): Boolean = antialiasing

    override fun isLoading(): Boolean = loadingState

    override fun setFallbackText(fallback: String?) {
        nativeWidget.tagId = fallback ?: ""
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFit = fit
        nativeWidget.attributes["scaleType"] = fit.name.lowercase()
        syncAndroidView()
    }

    override fun getObjectFit(): ObjectFit = objectFit

    override fun setTintColor(color: RgbaColor?) {
        tintColor = color
        if (color != null) {
            nativeWidget.attributes["tintColor"] = color.toHex()
        } else {
            nativeWidget.attributes.remove("tintColor")
        }
        syncAndroidView()
    }

    override fun getTintColor(): RgbaColor? = tintColor

    override fun setTintMode(mode: ImageTintMode) {
        tintMode = mode
        nativeWidget.attributes["tintMode"] = mode.name
        syncAndroidView()
    }

    override fun getTintMode(): ImageTintMode = tintMode

    override fun setCssFilter(filter: CssFilter?) {
        cssFilter = filter
        if (filter != null && !filter.isNone) {
            nativeWidget.attributes["cssFilter"] = filter.toCssString()
        } else {
            nativeWidget.attributes.remove("cssFilter")
        }
    }

    override fun getCssFilter(): CssFilter? = cssFilter

    override fun setOnLoad(listener: (() -> Unit)?) {
        onLoadCallback = listener
    }

    override fun setOnError(listener: (() -> Unit)?) {
        onErrorCallback = listener
    }

    override fun dispose() {
        activeLoadJob?.cancel()
        activeLoadJob = null
        retainedBuffer = null
        super.dispose()
    }

    companion object {
        internal fun resolveDrawable(context: Context, source: String, tintArgb: Int? = null): Drawable? = runCatching {
            val trimmed = source.trim()
            if (trimmed.isEmpty()) return null

            if (trimmed.startsWith("android.resource://", ignoreCase = true)) {
                val idPart = trimmed.substringAfterLast('/').toIntOrNull()
                if (idPart != null && idPart != 0) {
                    val d = runCatching { AppCompatResources.getDrawable(context, idPart)?.mutate() }.getOrNull()
                    if (d != null) {
                        if (tintArgb != null) DrawableCompat.setTint(d, tintArgb)
                        return d
                    }
                }
            }

            if (trimmed.startsWith("data:image/", ignoreCase = true) && trimmed.contains(";base64,")) {
                val b64 = trimmed.substringAfter(";base64,")
                val bytes = Base64.decode(b64, Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    val d = BitmapDrawable(context.resources, bmp)
                    if (tintArgb != null) DrawableCompat.setTint(d, tintArgb)
                    return d
                }
            }

            val cleanName = trimmed
                .removePrefix("res://drawable/")
                .removePrefix("@drawable/")
                .removePrefix("drawable/")
                .removePrefix("res://mipmap/")
                .removePrefix("@mipmap/")
                .removePrefix("mipmap/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .replace('-', '_')
                .lowercase()

            if (cleanName.isNotEmpty()) {
                var resId = runCatching {
                    context.resources.getIdentifier(cleanName, "drawable", context.packageName)
                }.getOrDefault(0)
                if (resId == 0) {
                    resId = runCatching {
                        context.resources.getIdentifier(cleanName, "mipmap", context.packageName)
                    }.getOrDefault(0)
                }
                if (resId != 0) {
                    val d = runCatching { AppCompatResources.getDrawable(context, resId)?.mutate() }.getOrNull()
                    if (d != null) {
                        if (tintArgb != null) DrawableCompat.setTint(d, tintArgb)
                        return d
                    }
                }
            }

            val drawableRes = Resources.getDrawable(trimmed)
                ?: Resources.getDrawable(cleanName)
                ?: Resources.activeTable.getDrawableByName(cleanName)

            if (drawableRes != null) {
                val b64 = drawableRes.base64Data
                if (!b64.isNullOrBlank()) {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    if (bmp != null) {
                        val d = BitmapDrawable(context.resources, bmp)
                        if (tintArgb != null) DrawableCompat.setTint(d, tintArgb)
                        return d
                    }
                }
                if (drawableRes.svgPathDataList.isNotEmpty()) {
                    val sizePx = AndroidViewHandle.dpToPx(24).coerceAtLeast(24)
                    val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bmp)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        style = Paint.Style.FILL
                        color = tintArgb ?: if (AndroidViewHandle.isDarkTheme()) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                    }
                    val vbW = drawableRes.viewBoxWidth.takeIf { it > 0f } ?: 24f
                    val vbH = drawableRes.viewBoxHeight.takeIf { it > 0f } ?: 24f
                    val matrix = Matrix().apply {
                        setRectToRect(
                            RectF(0f, 0f, vbW, vbH),
                            RectF(0f, 0f, sizePx.toFloat(), sizePx.toFloat()),
                            Matrix.ScaleToFit.CENTER
                        )
                    }
                    for (pathData in drawableRes.svgPathDataList) {
                        val path = runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull()
                        if (path != null) {
                            path.transform(matrix)
                            canvas.drawPath(path, paint)
                        }
                    }
                    return BitmapDrawable(context.resources, bmp)
                }
            }
            null
        }.getOrNull()
    }
}
