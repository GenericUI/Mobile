package com.nexustools.genericui.android

import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import kotlinx.coroutines.Job

/**
 * Android `androidx.appcompat.widget.AppCompatImageView` implementation of [ImageNativeImpl].
 *
 * Uses Kotlin coroutines via [ImageLoader.launchLoad] for non-blocking image loading, supports
 * placeholder loading images (`placeholderUri`), and enables antialiased bitmap filtering by default.
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
    }

    private fun toAndroidBitmap(buffer: ImageBuffer): android.graphics.Bitmap? = runCatching {
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
        android.graphics.Bitmap.createBitmap(pixels, w, h, android.graphics.Bitmap.Config.ARGB_8888)
    }.getOrNull()

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
                    (nativeWidget.androidView as? android.widget.ImageView)?.let { iv ->
                        toAndroidBitmap(buf)?.let { bmp -> runCatching { iv.setImageBitmap(bmp) } }
                    }
                    onLoadCallback?.invoke()
                },
                onError = {
                    if (gen != activeLoadGeneration) return@launchLoad
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    onErrorCallback?.invoke()
                }
            )
        } else {
            loadingState = false
            retainedBuffer = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes.remove("imageWidth")
            nativeWidget.attributes.remove("imageHeight")
            nativeWidget.attributes["isLoading"] = "false"
            (nativeWidget.androidView as? android.widget.ImageView)?.let { iv ->
                runCatching { iv.setImageDrawable(null) }
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
            (nativeWidget.androidView as? android.widget.ImageView)?.let { iv ->
                runCatching { iv.setImageDrawable(null) }
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
        (nativeWidget.androidView as? android.widget.ImageView)?.let { iv ->
            toAndroidBitmap(buffer)?.let { bmp -> runCatching { iv.setImageBitmap(bmp) } }
        }
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
    }

    override fun getObjectFit(): ObjectFit = objectFit

    override fun setTintColor(color: RgbaColor?) {
        tintColor = color
        if (color != null) {
            nativeWidget.attributes["tintColor"] = color.toHex()
        } else {
            nativeWidget.attributes.remove("tintColor")
        }
    }

    override fun getTintColor(): RgbaColor? = tintColor

    override fun setTintMode(mode: ImageTintMode) {
        tintMode = mode
        nativeWidget.attributes["tintMode"] = mode.name
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
}
