@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageLoader
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.ios.UikitGeometryAndColorUtil.toUiColor
import com.nexustools.genericui.nativeimpl.content.ImageNativeImpl
import com.nexustools.genericui.style.color.ImageTintMode
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.css.CssFilter
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import platform.UIKit.UIImageRenderingMode
import platform.UIKit.UIImageView
import platform.UIKit.UILabel

/**
 * Native UIKit `UIImageView` implementation of [ImageNativeImpl], backing bitmaps directly with
 * `UIImage.imageWithCGImage` / `ImageLoader` async decoding and `UIViewContentMode`.
 */
class IosImageImpl(
    val imageView: UIImageView = UIImageView(frame = CGRectMake(0.0, 0.0, 100.0, 100.0))
) : IosBaseNativeImpl(
    nativeWidget = UikitViewHandle(imageView),
    componentClassPrefix = "image"
), ImageNativeImpl<UikitViewHandle> {

    private var sourceUrl: String? = null
    private var placeholderUrl: String? = null
    private var fallbackText: String? = null
    private var objectFit: ObjectFit = ObjectFit.CONTAIN
    private var antialiasing: Boolean = true
    private var loading: Boolean = false
    private var tintColorValue: RgbaColor? = null
    private var tintModeValue: ImageTintMode = ImageTintMode.NONE
    private var cssFilterValue: CssFilter? = null
    private var onLoadListener: (() -> Unit)? = null
    private var onErrorListener: (() -> Unit)? = null
    private var rawDecodedImage: UIImage? = null
    private var fallbackLabel: UILabel? = null

    init {
        imageView.clipsToBounds = true
        imageView.contentMode = UikitGeometryAndColorUtil.resolveContentMode(objectFit)
    }

    override fun setSourceUrl(url: String?) {
        sourceUrl = url
        if (url.isNullOrBlank()) {
            rawDecodedImage = null
            imageView.image = null
            loading = false
            return
        }
        loading = true
        placeholderUrl?.let { ph ->
            ImageLoader.loadAsync(
                source = ImageSource.fromUrl(ph),
                options = ImageDecodeOptions(),
                onLoaded = { buf ->
                    if (loading && !isDisposed) {
                        applyUiImage(UikitGeometryAndColorUtil.imageBufferToUiImage(buf))
                    }
                }
            )
        }
        ImageLoader.loadAsync(
            source = ImageSource.fromUrl(url),
            options = ImageDecodeOptions(),
            onLoaded = { buffer ->
                loading = false
                if (!isDisposed) {
                    setImageBuffer(buffer)
                    onLoadListener?.invoke()
                }
            },
            onError = { _ ->
                loading = false
                if (!isDisposed) {
                    showFallbackIfConfigured()
                    onErrorListener?.invoke()
                }
            }
        )
    }

    override fun getSourceUrl(): String? = sourceUrl

    override fun setImageBuffer(buffer: ImageBuffer?) {
        loading = false
        fallbackLabel?.hidden = true
        val uiImage = if (buffer != null) UikitGeometryAndColorUtil.imageBufferToUiImage(buffer) else null
        applyUiImage(uiImage)
    }

    override fun setPlaceholderUrl(url: String?) {
        placeholderUrl = url
    }

    override fun getPlaceholderUrl(): String? = placeholderUrl

    override fun setFallbackText(fallback: String?) {
        fallbackText = fallback
    }

    override fun setObjectFit(fit: ObjectFit) {
        objectFit = fit
        imageView.contentMode = UikitGeometryAndColorUtil.resolveContentMode(fit)
    }

    override fun getObjectFit(): ObjectFit = objectFit

    override fun setAntialiasing(enabled: Boolean) {
        antialiasing = enabled
        imageView.layer.allowsEdgeAntialiasing = enabled
    }

    override fun isAntialiasing(): Boolean = antialiasing

    override fun isLoading(): Boolean = loading

    override fun setTintColor(color: RgbaColor?) {
        tintColorValue = color
        refreshTint()
    }

    override fun getTintColor(): RgbaColor? = tintColorValue

    override fun setTintMode(mode: ImageTintMode) {
        tintModeValue = mode
        refreshTint()
    }

    override fun getTintMode(): ImageTintMode = tintModeValue

    override fun setCssFilter(filter: CssFilter?) {
        cssFilterValue = filter
        imageView.alpha = ((filter?.opacity ?: 1f) * getOpacity()).coerceIn(0f, 1f).toDouble()
    }

    override fun getCssFilter(): CssFilter? = cssFilterValue

    override fun setOnLoad(listener: (() -> Unit)?) {
        onLoadListener = listener
    }

    override fun setOnError(listener: (() -> Unit)?) {
        onErrorListener = listener
    }

    private fun applyUiImage(image: UIImage?) {
        rawDecodedImage = image
        refreshTint()
    }

    private fun refreshTint() {
        val base = rawDecodedImage
        if (base == null) {
            imageView.image = null
            return
        }
        val activeTint = when (tintModeValue) {
            ImageTintMode.NONE -> null
            ImageTintMode.MONOCOLOR_AUTO_THEME -> tintColorValue ?: if (UikitGeometryAndColorUtil.isDarkActive()) RgbaColor(245, 245, 247) else RgbaColor(28, 28, 30)
            ImageTintMode.SRC_IN, ImageTintMode.MULTIPLY -> tintColorValue
        }
        if (activeTint != null) {
            val uiTint = activeTint.toUiColor()
            imageView.tintColor = uiTint
            imageView.image = base.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysTemplate)
        } else {
            imageView.image = base.imageWithRenderingMode(UIImageRenderingMode.UIImageRenderingModeAlwaysOriginal)
        }
    }

    private fun showFallbackIfConfigured() {
        val text = fallbackText?.takeIf { it.isNotEmpty() } ?: return
        val lbl = fallbackLabel ?: UILabel(frame = imageView.bounds).apply {
            textAlignment = platform.UIKit.NSTextAlignmentCenter
            numberOfLines = 2L
            imageView.addSubview(this)
            fallbackLabel = this
        }
        lbl.text = text
        lbl.setFrame(imageView.bounds)
        lbl.hidden = false
    }
}
