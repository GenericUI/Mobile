package com.nexustools.genericui.ios

import com.nexustools.genericui.image.*
import com.nexustools.genericui.image.codec.LibJpegCodec
import com.nexustools.genericui.image.codec.LibPngCodec
import com.nexustools.genericui.image.codec.LibWebpCodec
import com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl

/**
 * iOS Apple `ImageIO` (`CGImageSource` / `CGImageDestination` / `UIImage`) implementation of [ImageCodecNativeImpl].
 */
class IosImageIoCodecImpl : ImageCodecNativeImpl {
    override val codecBackendName: String = "ios-imageio-cgimagesource"

    override fun probeMetadata(source: ImageSource): ImageMetadata? {
        source.memoryBuffer?.let { buf ->
            return ImageMetadata(
                width = buf.width,
                height = buf.height,
                format = ImageFormat.RAW_RGBA,
                pixelFormat = buf.pixelFormat,
                colorSpace = buf.colorSpace
            )
        }
        val bytes = source.encodedBytes ?: return null
        if (bytes.isEmpty()) return null
        return when (ImageFormat.detectFromHeader(bytes)) {
            ImageFormat.JPEG -> LibJpegCodec.probeJpegMetadata(bytes)
            ImageFormat.PNG -> LibPngCodec.probePngMetadata(bytes)
            ImageFormat.WEBP -> LibWebpCodec.probeWebpMetadata(bytes)
            else -> null
        }
    }

    override fun decode(source: ImageSource, options: ImageDecodeOptions): ImageBuffer? {
        source.memoryBuffer?.let { return it.copy() }
        val bytes = source.encodedBytes ?: return null
        if (bytes.isEmpty()) return null
        return when (ImageFormat.detectFromHeader(bytes)) {
            ImageFormat.JPEG -> LibJpegCodec.decode(bytes)
            ImageFormat.PNG -> LibPngCodec.decode(bytes)
            ImageFormat.WEBP -> LibWebpCodec.decode(bytes)
            else -> null
        }
    }

    override fun decodeAnimated(source: ImageSource, options: ImageDecodeOptions): AnimatedImage? {
        val frame = decode(source, options) ?: return null
        return AnimatedImage(
            width = frame.width,
            height = frame.height,
            frames = listOf(ImageFrame(0, frame, durationMs = 100))
        )
    }

    override fun encode(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray? {
        return when (options.format) {
            ImageFormat.JPEG -> LibJpegCodec.encode(buffer, options.quality)
            ImageFormat.PNG -> LibPngCodec.encode(buffer, options.compressionLevel)
            ImageFormat.RAW_RGBA -> buffer.toRgba8888().bytes
            else -> null
        }
    }

    override fun dispose() {}
}
