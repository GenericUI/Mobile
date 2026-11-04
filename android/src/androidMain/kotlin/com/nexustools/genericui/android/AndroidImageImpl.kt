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
    private var activeLoadJob: Job? = null
    private var retainedBuffer: ImageBuffer? = null

    init {
        nativeWidget.attributes["antialiasing"] = "true"
        nativeWidget.attributes["filterBitmap"] = "true"
    }

    override fun setSourceUrl(url: String?) {
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
                    retainedBuffer = buf
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    onLoadCallback?.invoke()
                },
                onError = {
                    loadingState = false
                    nativeWidget.attributes["isLoading"] = "false"
                    onErrorCallback?.invoke()
                }
            )
        } else {
            loadingState = false
            retainedBuffer = null
            nativeWidget.attributes.remove("imageUri")
            nativeWidget.attributes["isLoading"] = "false"
        }
    }

    override fun getSourceUrl(): String? = sourceUrl

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
