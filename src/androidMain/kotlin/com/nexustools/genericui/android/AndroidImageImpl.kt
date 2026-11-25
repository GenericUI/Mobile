package com.nexustools.genericui.android

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RenderEffect
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import android.view.View
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.graphics.PathParser
import androidx.core.graphics.drawable.DrawableCompat
import androidx.core.widget.ImageViewCompat
import com.nexustools.genericui.enums.css.CssUnit
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.geometry.Size
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.jvm.style.JvmBackdropCapturePlanner
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.res.Resources
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import com.nexustools.genericui.style.css.CssLength
import kotlinx.coroutines.Job
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/**
 * Android `androidx.appcompat.widget.AppCompatImageView` implementation of [ImageNativeImpl].
 *
 * Uses Kotlin coroutines via [ImageLoader.launchLoad] for non-blocking image loading, displays
 * `placeholderUrl` drawables while async loads are in flight, applies [CssFilter] effects via
 * `RenderEffect` (blur on API 31+) and `ColorMatrixColorFilter`, resolves compiled Android drawables,
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
    private var activeRequestedDecodeDims: Pair<Int, Int>? = null
    private var retainedBuffer: ImageBuffer? = null
    private var cachedBufferRef: ImageBuffer? = null
    private var cachedBufferBytesRef: ByteArray? = null
    private var cachedBufferWidth: Int = -1
    private var cachedBufferHeight: Int = -1
    private var cachedBufferBitmap: Bitmap? = null
    private var appliedViewIdentity: Int = 0
    private var appliedBitmapRef: Bitmap? = null
    private var appliedDrawableKey: String? = null

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

    private fun obtainBitmapForBuffer(buf: ImageBuffer): Bitmap? {
        val existing = cachedBufferBitmap
        if (cachedBufferRef === buf &&
            cachedBufferBytesRef === buf.bytes &&
            cachedBufferWidth == buf.width &&
            cachedBufferHeight == buf.height &&
            existing != null &&
            !existing.isRecycled
        ) {
            return existing
        }
        val bmp = toAndroidBitmap(buf)
        if (bmp != null) {
            cachedBufferRef = buf
            cachedBufferBytesRef = buf.bytes
            cachedBufferWidth = buf.width
            cachedBufferHeight = buf.height
            cachedBufferBitmap = bmp
        }
        return bmp
    }

    private fun toAndroidBitmap(buffer: ImageBuffer): Bitmap? = runCatching {
        val w = buffer.width.coerceAtLeast(1)
        val h = buffer.height.coerceAtLeast(1)
        val totalPixels = w * h
        val pixels = IntArray(totalPixels)
        val raw = buffer.bytes
        val format = buffer.pixelFormat
        val expectedBytes = totalPixels * format.bytesPerPixel
        if (raw.size >= expectedBytes) {
            when (format) {
                PixelFormat.RGBA_8888 -> {
                    var idx = 0
                    for (i in 0 until totalPixels) {
                        val r = raw[idx].toInt() and 0xFF
                        val g = raw[idx + 1].toInt() and 0xFF
                        val b = raw[idx + 2].toInt() and 0xFF
                        val a = raw[idx + 3].toInt() and 0xFF
                        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        idx += 4
                    }
                }
                PixelFormat.BGRA_8888 -> {
                    var idx = 0
                    for (i in 0 until totalPixels) {
                        val b = raw[idx].toInt() and 0xFF
                        val g = raw[idx + 1].toInt() and 0xFF
                        val r = raw[idx + 2].toInt() and 0xFF
                        val a = raw[idx + 3].toInt() and 0xFF
                        pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        idx += 4
                    }
                }
                PixelFormat.RGB_888 -> {
                    var idx = 0
                    for (i in 0 until totalPixels) {
                        val r = raw[idx].toInt() and 0xFF
                        val g = raw[idx + 1].toInt() and 0xFF
                        val b = raw[idx + 2].toInt() and 0xFF
                        pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
                        idx += 3
                    }
                }
                PixelFormat.GRAY_8 -> {
                    for (i in 0 until totalPixels) {
                        val lum = raw[i].toInt() and 0xFF
                        pixels[i] = (0xFF shl 24) or (lum shl 16) or (lum shl 8) or lum
                    }
                }
                PixelFormat.GRAY_ALPHA_88 -> {
                    var idx = 0
                    for (i in 0 until totalPixels) {
                        val lum = raw[idx].toInt() and 0xFF
                        val a = raw[idx + 1].toInt() and 0xFF
                        pixels[i] = (a shl 24) or (lum shl 16) or (lum shl 8) or lum
                        idx += 2
                    }
                }
                else -> {
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
                }
            }
        } else {
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

    private fun applyFilterEffects(iv: ImageView) {
        val filter = cssFilter
        if (filter == null || filter.isNone) {
            iv.colorFilter = null
            iv.imageAlpha = 255
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                iv.setRenderEffect(null)
            }
            return
        }

        val alpha = ((filter.opacity ?: 1f).coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
        iv.imageAlpha = alpha

        val cm = buildColorMatrixForCssFilter(filter)
        iv.colorFilter = cm?.let { ColorMatrixColorFilter(it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val blurPx = filter.blur?.resolvePx()?.coerceAtLeast(0f) ?: 0f
            if (blurPx > 0.1f) {
                val radiusPx = AndroidViewHandle.dpToPxFloat(blurPx).coerceAtLeast(0.5f)
                iv.setRenderEffect(RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP))
            } else {
                iv.setRenderEffect(null)
            }
        }
    }

    private fun buildColorMatrixForCssFilter(filter: CssFilter): ColorMatrix? {
        val matrix = JvmBackdropCapturePlanner.buildAndroidColorMatrix20(filter.copy(opacity = null))
            ?: return null
        return ColorMatrix(matrix)
    }

    override fun syncAndroidView() {
        super.syncAndroidView()
        AndroidViewHandle.runOnMainThread {
            runCatching {
                val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                val viewId = System.identityHashCode(iv)
                if (viewId != appliedViewIdentity) {
                    appliedViewIdentity = viewId
                    appliedBitmapRef = null
                    appliedDrawableKey = null
                }
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
                    val bmp = obtainBitmapForBuffer(buf)
                    if (bmp != null && appliedBitmapRef !== bmp) {
                        iv.setImageBitmap(bmp)
                        appliedBitmapRef = bmp
                        appliedDrawableKey = null
                    }
                } else if (loadingState && !placeholderUrl.isNullOrBlank()) {
                    val key = "ph|$placeholderUrl|$effectiveTint"
                    if (appliedDrawableKey != key) {
                        val placeholder = resolveDrawable(iv.context, placeholderUrl!!, effectiveTint)
                        if (placeholder != null) {
                            iv.setImageDrawable(placeholder)
                            appliedDrawableKey = key
                            appliedBitmapRef = null
                        }
                    }
                } else if (!sourceUrl.isNullOrBlank()) {
                    val key = "src|$sourceUrl|$effectiveTint"
                    if (appliedDrawableKey != key) {
                        val d = resolveDrawable(iv.context, sourceUrl!!, effectiveTint)
                        if (d != null) {
                            iv.setImageDrawable(d)
                            appliedDrawableKey = key
                            appliedBitmapRef = null
                        }
                    }
                } else if (appliedBitmapRef != null || appliedDrawableKey != null) {
                    iv.setImageDrawable(null)
                    appliedBitmapRef = null
                    appliedDrawableKey = null
                }
                applyFilterEffects(iv)
            }
        }
    }

    fun getResolvedDecodeTargetDimensions(): Pair<Int, Int> =
        resolveDecodeTargetDimensions((nativeWidget.androidView?.context) ?: AndroidViewHandle.activeContext)

    private fun resolveDecodeTargetDimensions(ctx: Context?): Pair<Int, Int> {
        val explicitDpW = (if (nativeWidget.hasExplicitPreferredWidth) nativeWidget.bounds.width.takeIf { it > 0 } else null)
            ?: preferredSizeValue?.width?.takeIf { it > 0 }
            ?: (baseComponentStyle?.getWidth() ?: CssLength.parse(baseComputedStyle?.width))
                ?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }
                ?.resolvePx()
                ?.roundToInt()
                ?.takeIf { it > 0 }
        val explicitDpH = (if (nativeWidget.hasExplicitPreferredHeight) nativeWidget.bounds.height.takeIf { it > 0 } else null)
            ?: preferredSizeValue?.height?.takeIf { it > 0 }
            ?: (baseComponentStyle?.getHeight() ?: CssLength.parse(baseComputedStyle?.height))
                ?.takeIf { !it.isAuto && !it.isNone && it.unit != CssUnit.PERCENT }
                ?.resolvePx()
                ?.roundToInt()
                ?.takeIf { it > 0 }
        val explicitW = explicitDpW?.let { AndroidViewHandle.dpToPx(it).coerceAtLeast(it) }
        val explicitH = explicitDpH?.let { AndroidViewHandle.dpToPx(it).coerceAtLeast(it) }
        if (explicitW != null || explicitH != null) {
            val tw = ((explicitW ?: explicitH ?: 512) * 2).coerceIn(64, 2048)
            val th = ((explicitH ?: explicitW ?: 512) * 2).coerceIn(64, 2048)
            return tw to th
        }
        val metrics = ctx?.resources?.displayMetrics
        val maxScreen = if (metrics != null) maxOf(metrics.widthPixels, metrics.heightPixels) else 1280
        val cap = maxScreen.coerceIn(512, 1600)
        return cap to cap
    }

    override fun setPreferredSize(size: Size?) {
        super.setPreferredSize(size)
        val prevDims = activeRequestedDecodeDims
        val url = sourceUrl
        if (loadingState && prevDims != null && !url.isNullOrBlank() && size != null && (size.width > 0 || size.height > 0)) {
            val newDims = resolveDecodeTargetDimensions((nativeWidget.androidView?.context) ?: AndroidViewHandle.activeContext)
            if (newDims.first <= prevDims.first / 2 || newDims.second <= prevDims.second / 2) {
                setSourceUrl(url)
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
        activeRequestedDecodeDims = null
        appliedBitmapRef = null
        appliedDrawableKey = null
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
                cachedBufferRef = null
                cachedBufferBytesRef = null
                cachedBufferWidth = -1
                cachedBufferHeight = -1
                cachedBufferBitmap = null
                loadingState = false
                nativeWidget.attributes["isLoading"] = "false"
                nativeWidget.attributes["imageWidth"] = resolvedDrawable.intrinsicWidth.coerceAtLeast(1).toString()
                nativeWidget.attributes["imageHeight"] = resolvedDrawable.intrinsicHeight.coerceAtLeast(1).toString()
                AndroidViewHandle.runOnMainThread {
                    runCatching {
                        val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                        appliedViewIdentity = System.identityHashCode(iv)
                        ImageViewCompat.setImageTintList(
                            iv,
                            effectiveTint?.let { ColorStateList.valueOf(it) }
                        )
                        iv.setImageDrawable(resolvedDrawable)
                        appliedBitmapRef = null
                        appliedDrawableKey = "src|$url|$effectiveTint"
                        applyFilterEffects(iv)
                    }
                }
                onLoadCallback?.invoke()
                return
            }
            retainedBuffer = null
            cachedBufferRef = null
            cachedBufferBytesRef = null
            cachedBufferWidth = -1
            cachedBufferHeight = -1
            cachedBufferBitmap = null
            loadingState = true
            nativeWidget.attributes["isLoading"] = "true"
            val placeholder = placeholderUrl?.takeIf { it.isNotBlank() }
            if (placeholder != null && ctx != null) {
                AndroidViewHandle.runOnMainThread {
                    runCatching {
                        val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                        appliedViewIdentity = System.identityHashCode(iv)
                        resolveDrawable(ctx, placeholder, effectiveTint)?.let {
                            iv.setImageDrawable(it)
                            appliedBitmapRef = null
                            appliedDrawableKey = "ph|$placeholder|$effectiveTint"
                            applyFilterEffects(iv)
                        }
                    }
                }
            }
            val targetDims = resolveDecodeTargetDimensions(ctx)
            val (targetW, targetH) = targetDims
            activeRequestedDecodeDims = targetDims
            activeLoadJob = ImageLoader.launchLoad(
                source = ImageSource.fromUrl(url),
                options = ImageDecodeOptions(
                    targetWidth = targetW,
                    targetHeight = targetH,
                    preserveAspectRatio = true,
                    antialiasing = antialiasing
                ),
                onLoaded = { buf ->
                    if (gen != activeLoadGeneration) return@launchLoad
                    activeRequestedDecodeDims = null
                    retainedBuffer = buf
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    nativeWidget.attributes["imageWidth"] = buf.width.toString()
                    nativeWidget.attributes["imageHeight"] = buf.height.toString()
                    val bmp = obtainBitmapForBuffer(buf)
                    AndroidViewHandle.runOnMainThread {
                        if (gen != activeLoadGeneration) return@runOnMainThread
                        (nativeWidget.androidView as? ImageView)?.let { imageView ->
                            appliedViewIdentity = System.identityHashCode(imageView)
                            if (bmp != null) {
                                runCatching {
                                    imageView.setImageBitmap(bmp)
                                    appliedBitmapRef = bmp
                                    appliedDrawableKey = null
                                    applyFilterEffects(imageView)
                                }
                            }
                        }
                        onLoadCallback?.invoke()
                    }
                },
                onError = {
                    if (gen != activeLoadGeneration) return@launchLoad
                    activeRequestedDecodeDims = null
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
            cachedBufferRef = null
            cachedBufferBytesRef = null
            cachedBufferWidth = -1
            cachedBufferHeight = -1
            cachedBufferBitmap = null
            appliedBitmapRef = null
            appliedDrawableKey = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes.remove("imageWidth")
            nativeWidget.attributes.remove("imageHeight")
            nativeWidget.attributes["isLoading"] = "false"
            AndroidViewHandle.runOnMainThread {
                (nativeWidget.androidView as? ImageView)?.let { iv ->
                    appliedViewIdentity = System.identityHashCode(iv)
                    runCatching {
                        iv.setImageDrawable(null)
                        appliedBitmapRef = null
                        appliedDrawableKey = null
                        applyFilterEffects(iv)
                    }
                }
            }
        }
    }

    override fun getSourceUrl(): String? = sourceUrl

    override fun setImageBuffer(buffer: ImageBuffer?) {
        activeLoadGeneration++
        activeLoadJob?.cancel()
        activeLoadJob = null
        activeRequestedDecodeDims = null
        appliedDrawableKey = null
        if (buffer == null) {
            loadingState = false
            sourceUrl = null
            retainedBuffer = null
            cachedBufferRef = null
            cachedBufferBytesRef = null
            cachedBufferWidth = -1
            cachedBufferHeight = -1
            cachedBufferBitmap = null
            appliedBitmapRef = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes.remove("imageWidth")
            nativeWidget.attributes.remove("imageHeight")
            nativeWidget.attributes["isLoading"] = "false"
            AndroidViewHandle.runOnMainThread {
                (nativeWidget.androidView as? ImageView)?.let { iv ->
                    appliedViewIdentity = System.identityHashCode(iv)
                    runCatching {
                        iv.setImageDrawable(null)
                        appliedBitmapRef = null
                        appliedDrawableKey = null
                    }
                }
            }
            return
        }
        val memoryUri = "memory://imagebuffer/${buffer.width}x${buffer.height}@${buffer.hashCode()}"
        ImageLoader.registerMemoryBuffer(memoryUri, buffer)
        sourceUrl = memoryUri
        loadingState = false
        retainedBuffer = buffer
        appliedBitmapRef = null
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
        if (loadingState && retainedBuffer == null) {
            syncAndroidView()
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
        syncAndroidView()
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
        activeRequestedDecodeDims = null
        retainedBuffer = null
        cachedBufferRef = null
        cachedBufferBytesRef = null
        cachedBufferWidth = -1
        cachedBufferHeight = -1
        cachedBufferBitmap = null
        appliedBitmapRef = null
        appliedDrawableKey = null
        super.dispose()
    }

    companion object {
        private val decodedResourceBitmapCache = ConcurrentHashMap<String, Bitmap>()

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

            val normalizedPath = trimmed
                .removePrefix("res://")
                .removePrefix("@")
                .replace('\\', '/')
                .trimStart('/')
            val cleanName = normalizedPath
                .removePrefix("drawable/")
                .removePrefix("mipmap/")
                .substringAfterLast('/')
                .substringBeforeLast('.')
                .replace('-', '_')
                .lowercase()

            val matchedTableResId = Resources.activeTable.findResourceId(
                com.nexustools.genericui.resources.ResourceType.DRAWABLE,
                cleanName
            ) ?: Resources.activeTable.getDrawableByName(trimmed)?.resId
                ?: Resources.activeTable.getDrawableByName(cleanName)?.resId

            val candidateIdentifiers = linkedSetOf<String>()
            matchedTableResId?.flatName?.takeIf { it.isNotBlank() }?.let { candidateIdentifiers.add(it) }
            val flattenedPathStem = normalizedPath
                .substringBeforeLast('.')
                .replace('/', '_')
                .replace('-', '_')
                .lowercase()
            if (flattenedPathStem.isNotEmpty() &&
                (flattenedPathStem.startsWith("app_") || flattenedPathStem.startsWith("gui_"))
            ) {
                candidateIdentifiers.add(flattenedPathStem)
            }
            if (cleanName.isNotEmpty()) {
                candidateIdentifiers.add("app_drawable_$cleanName")
                candidateIdentifiers.add("gui_drawable_$cleanName")
                candidateIdentifiers.add("app_$cleanName")
                candidateIdentifiers.add("gui_$cleanName")
                candidateIdentifiers.add(cleanName)
            }

            for (entryName in candidateIdentifiers) {
                var resId = runCatching {
                    context.resources.getIdentifier(entryName, "drawable", context.packageName)
                }.getOrDefault(0)
                if (resId == 0) {
                    resId = runCatching {
                        context.resources.getIdentifier(entryName, "mipmap", context.packageName)
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
                val resKeyName = cleanName.ifEmpty { trimmed }
                val b64 = drawableRes.base64Data
                if (!b64.isNullOrBlank()) {
                    val cacheKey = "b64res:$resKeyName"
                    var bmp = decodedResourceBitmapCache[cacheKey]?.takeIf { !it.isRecycled }
                    if (bmp == null) {
                        val bytes = Base64.decode(b64, Base64.DEFAULT)
                        bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            if (decodedResourceBitmapCache.size > 256) {
                                decodedResourceBitmapCache.clear()
                            }
                            decodedResourceBitmapCache[cacheKey] = bmp
                        }
                    }
                    if (bmp != null) {
                        val d = BitmapDrawable(context.resources, bmp).mutate()
                        if (tintArgb != null) DrawableCompat.setTint(d, tintArgb)
                        return d
                    }
                }
                if (drawableRes.svgPathDataList.isNotEmpty()) {
                    val sizePx = AndroidViewHandle.dpToPx(24).coerceAtLeast(24)
                    val effectiveSvgColor = tintArgb ?: if (AndroidViewHandle.isDarkTheme()) 0xFFECEFF4.toInt() else 0xFF1C1B1F.toInt()
                    val cacheKey = "svg:$resKeyName:$sizePx:$effectiveSvgColor"
                    var bmp = decodedResourceBitmapCache[cacheKey]?.takeIf { !it.isRecycled }
                    if (bmp == null) {
                        bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
                        val canvas = Canvas(bmp)
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            style = Paint.Style.FILL
                            color = effectiveSvgColor
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
                        if (decodedResourceBitmapCache.size > 256) {
                            decodedResourceBitmapCache.clear()
                        }
                        decodedResourceBitmapCache[cacheKey] = bmp
                    }
                    return BitmapDrawable(context.resources, bmp).mutate()
                }
            }
            null
        }.getOrNull()
    }
}
