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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
        var modified = false
        val combined = ColorMatrix()

        filter.grayscale?.takeIf { it > 0f }?.let { amt ->
            val g = amt.coerceIn(0f, 1f)
            val m = ColorMatrix().apply { setSaturation(1f - g) }
            combined.postConcat(m)
            modified = true
        }

        filter.sepia?.takeIf { it > 0f }?.let { amt ->
            val s = amt.coerceIn(0f, 1f)
            val invS = 1f - s
            val sepiaMatrix = ColorMatrix(
                floatArrayOf(
                    invS + 0.393f * s, 0.769f * s, 0.189f * s, 0f, 0f,
                    0.349f * s, invS + 0.686f * s, 0.168f * s, 0f, 0f,
                    0.272f * s, 0.534f * s, invS + 0.131f * s, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            combined.postConcat(sepiaMatrix)
            modified = true
        }

        filter.saturate?.takeIf { it != 1f }?.let { sat ->
            val m = ColorMatrix().apply { setSaturation(sat.coerceAtLeast(0f)) }
            combined.postConcat(m)
            modified = true
        }

        filter.hueRotateDeg?.takeIf { it != 0f }?.let { deg ->
            val rad = deg * (PI.toFloat() / 180f)
            val cosVal = cos(rad)
            val sinVal = sin(rad)
            val lumR = 0.213f
            val lumG = 0.715f
            val lumB = 0.072f
            val hueMatrix = ColorMatrix(
                floatArrayOf(
                    lumR + cosVal * (1f - lumR) + sinVal * (-lumR), lumG + cosVal * (-lumG) + sinVal * (-lumG), lumB + cosVal * (-lumB) + sinVal * (1f - lumB), 0f, 0f,
                    lumR + cosVal * (-lumR) + sinVal * 0.143f, lumG + cosVal * (1f - lumG) + sinVal * 0.140f, lumB + cosVal * (-lumB) + sinVal * (-0.283f), 0f, 0f,
                    lumR + cosVal * (-lumR) + sinVal * (-(1f - lumR)), lumG + cosVal * (-lumG) + sinVal * lumG, lumB + cosVal * (1f - lumB) + sinVal * lumB, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f
                )
            )
            combined.postConcat(hueMatrix)
            modified = true
        }

        val brightnessBeforeInvert = filter.brightness == 0f && (filter.invert ?: 0f) > 0f
        if (brightnessBeforeInvert) {
            filter.brightness?.takeIf { it != 1f }?.let { b ->
                val br = b.coerceAtLeast(0f)
                combined.postConcat(ColorMatrix().apply { setScale(br, br, br, 1f) })
                modified = true
            }
            filter.invert?.takeIf { it > 0f }?.let { amt ->
                val inv = amt.coerceIn(0f, 1f)
                val scale = 1f - 2f * inv
                val offset = 255f * inv
                combined.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            scale, 0f, 0f, 0f, offset,
                            0f, scale, 0f, 0f, offset,
                            0f, 0f, scale, 0f, offset,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
                modified = true
            }
        } else {
            filter.invert?.takeIf { it > 0f }?.let { amt ->
                val inv = amt.coerceIn(0f, 1f)
                val scale = 1f - 2f * inv
                val offset = 255f * inv
                combined.postConcat(
                    ColorMatrix(
                        floatArrayOf(
                            scale, 0f, 0f, 0f, offset,
                            0f, scale, 0f, 0f, offset,
                            0f, 0f, scale, 0f, offset,
                            0f, 0f, 0f, 1f, 0f
                        )
                    )
                )
                modified = true
            }
            filter.brightness?.takeIf { it != 1f }?.let { b ->
                val br = b.coerceAtLeast(0f)
                combined.postConcat(ColorMatrix().apply { setScale(br, br, br, 1f) })
                modified = true
            }
        }

        filter.contrast?.takeIf { it != 1f }?.let { c ->
            val scale = c.coerceAtLeast(0f)
            val translate = (-0.5f * scale + 0.5f) * 255f
            combined.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        scale, 0f, 0f, 0f, translate,
                        0f, scale, 0f, 0f, translate,
                        0f, 0f, scale, 0f, translate,
                        0f, 0f, 0f, 1f, 0f
                    )
                )
            )
            modified = true
        }

        return if (modified) combined else null
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
                } else if (loadingState && !placeholderUrl.isNullOrBlank()) {
                    val placeholder = resolveDrawable(iv.context, placeholderUrl!!, effectiveTint)
                    if (placeholder != null) {
                        iv.setImageDrawable(placeholder)
                    }
                } else if (!sourceUrl.isNullOrBlank()) {
                    val d = resolveDrawable(iv.context, sourceUrl!!, effectiveTint)
                    if (d != null) {
                        iv.setImageDrawable(d)
                    }
                } else {
                    iv.setImageDrawable(null)
                }
                applyFilterEffects(iv)
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
                        applyFilterEffects(iv)
                    }
                }
                onLoadCallback?.invoke()
                return
            }
            retainedBuffer = null
            loadingState = true
            nativeWidget.attributes["isLoading"] = "true"
            val placeholder = placeholderUrl?.takeIf { it.isNotBlank() }
            if (placeholder != null && ctx != null) {
                AndroidViewHandle.runOnMainThread {
                    runCatching {
                        val iv = nativeWidget.androidView as? ImageView ?: return@runCatching
                        resolveDrawable(ctx, placeholder, effectiveTint)?.let {
                            iv.setImageDrawable(it)
                            applyFilterEffects(iv)
                        }
                    }
                }
            }
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
                                runCatching {
                                    imageView.setImageBitmap(bmp)
                                    applyFilterEffects(imageView)
                                }
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
                    runCatching {
                        iv.setImageDrawable(null)
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
