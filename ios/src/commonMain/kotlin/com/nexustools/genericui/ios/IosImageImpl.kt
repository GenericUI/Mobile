package com.nexustools.genericui.ios

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
 * iOS `UIImageView` implementation of [ImageNativeImpl].
 *
 * Uses Kotlin coroutines via [ImageLoader.launchLoad] for non-blocking image loading, supports
 * placeholder loading images (`placeholderUrl`), and enables high-quality antialiased interpolation by default.
 */
class IosImageImpl : IosBaseNativeImpl("UIImageView", "img-fluid"), ImageNativeImpl<IosNativeView> {
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
        nativeWidget.metadata["antialiasing"] = "true"
        nativeWidget.metadata["magnificationFilter"] = "trilinear"
        nativeWidget.metadata["minificationFilter"] = "trilinear"
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
            nativeWidget.metadata["sourceUrl"] = url
            loadingState = true
            nativeWidget.metadata["isLoading"] = "true"
            activeLoadJob = ImageLoader.launchLoad(
                source = ImageSource.fromUrl(url),
                options = ImageDecodeOptions(antialiasing = antialiasing),
                onLoaded = { buf ->
                    if (gen != activeLoadGeneration) return@launchLoad
                    retainedBuffer = buf
                    loadingState = false
                    nativeWidget.metadata["isLoading"] = "false"
                    nativeWidget.metadata["imageWidth"] = buf.width.toString()
                    nativeWidget.metadata["imageHeight"] = buf.height.toString()
                    onLoadCallback?.invoke()
                },
                onError = {
                    if (gen != activeLoadGeneration) return@launchLoad
                    loadingState = false
                    nativeWidget.metadata["isLoading"] = "false"
                    onErrorCallback?.invoke()
                }
            )
        } else {
            loadingState = false
            retainedBuffer = null
            nativeWidget.metadata.remove("sourceUrl")
            nativeWidget.metadata.remove("imageWidth")
            nativeWidget.metadata.remove("imageHeight")
            nativeWidget.metadata["isLoading"] = "false"
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
            nativeWidget.metadata.remove("sourceUrl")
            nativeWidget.metadata.remove("imageWidth")
            nativeWidget.metadata.remove("imageHeight")
            nativeWidget.metadata["isLoading"] = "false"
            return
        }
        val memoryUri = "memory://imagebuffer/${buffer.width}x${buffer.height}@${buffer.hashCode()}"
        ImageLoader.registerMemoryBuffer(memoryUri, buffer)
        sourceUrl = memoryUri
        loadingState = false
        retainedBuffer = buffer
        nativeWidget.metadata["sourceUrl"] = memoryUri
        nativeWidget.metadata["imageWidth"] = buffer.width.toString()
        nativeWidget.metadata["imageHeight"] = buffer.height.toString()
        nativeWidget.metadata["isLoading"] = "false"
        onLoadCallback?.invoke()
    }

    override fun setPlaceholderUrl(url: String?) {
        placeholderUrl = url
        if (url != null) {
            nativeWidget.metadata["placeholderUrl"] = url
        } else {
            nativeWidget.metadata.remove("placeholderUrl")
        }
    }

    override fun getPlaceholderUrl(): String? = placeholderUrl

    override fun setAntialiasing(enabled: Boolean) {
        antialiasing = enabled
        nativeWidget.metadata["antialiasing"] = enabled.toString()
        nativeWidget.metadata["magnificationFilter"] = if (enabled) "trilinear" else "nearest"
        nativeWidget.metadata["minificationFilter"] = if (enabled) "trilinear" else "nearest"
    }

    override fun isAntialiasing(): Boolean = antialiasing

    override fun isLoading(): Boolean = loadingState

    override fun setFallbackText(fallback: String?) {
        nativeWidget.accessibilityIdentifier = fallback ?: ""
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFit = fit
        nativeWidget.metadata["contentMode"] = fit.name
    }

    override fun getObjectFit(): ObjectFit = objectFit

    override fun setTintColor(color: RgbaColor?) {
        tintColor = color
        if (color != null) nativeWidget.metadata["tintColor"] = color.toHex() else nativeWidget.metadata.remove("tintColor")
    }

    override fun getTintColor(): RgbaColor? = tintColor

    override fun setTintMode(mode: ImageTintMode) {
        tintMode = mode
        nativeWidget.metadata["tintMode"] = mode.name
    }

    override fun getTintMode(): ImageTintMode = tintMode

    override fun setCssFilter(filter: CssFilter?) {
        cssFilter = filter
        if (filter != null && !filter.isNone) {
            nativeWidget.metadata["cssFilter"] = filter.toCssString()
        } else {
            nativeWidget.metadata.remove("cssFilter")
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
