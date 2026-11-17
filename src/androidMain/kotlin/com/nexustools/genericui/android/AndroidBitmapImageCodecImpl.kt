package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import com.nexustools.genericui.image.AnimatedImage
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.ImageDecodeOptions
import com.nexustools.genericui.image.ImageEncodeOptions
import com.nexustools.genericui.image.ImageFormat
import com.nexustools.genericui.image.ImageFrame
import com.nexustools.genericui.image.ImageMetadata
import com.nexustools.genericui.image.ImageSource
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.jvm.JvmImageCodecBase
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android `android.graphics.ImageDecoder` (API 28+) / `android.graphics.BitmapFactory` /
 * `Bitmap.compress` implementation of [com.nexustools.genericui.nativeimpl.image.ImageCodecNativeImpl],
 * built on [JvmImageCodecBase] with pure-Kotlin codec fallback for unmocked JVM host tests.
 */
class AndroidBitmapImageCodecImpl(
    private val contextProvider: () -> Context? = {
        AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
    }
) : JvmImageCodecBase(installResolverHooks = true) {

    constructor(context: Context?) : this(
        contextProvider = {
            context ?: AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
        }
    )

    override val codecBackendName: String = "android-bitmap-factory"

    override fun resourceClassLoader(): ClassLoader? =
        runCatching { contextProvider()?.classLoader }.getOrNull() ?: this::class.java.classLoader

    override fun readPlatformResourceBytes(uriOrPath: String): ByteArray? {
        val ctx = contextProvider() ?: return null
        val trimmed = uriOrPath.trim()
        if (trimmed.isEmpty()) return null

        return runCatching {
            when {
                trimmed.startsWith("content://") -> {
                    ctx.contentResolver?.openInputStream(Uri.parse(trimmed))?.use { it.readBytes() }
                }
                trimmed.startsWith("assets://") || trimmed.startsWith("asset://") -> {
                    val rel = trimmed.substringAfter("://").trimStart('/')
                    ctx.assets.open(rel).use { it.readBytes() }
                }
                trimmed.startsWith("file:///android_asset/") -> {
                    val rel = trimmed.removePrefix("file:///android_asset/").trimStart('/')
                    ctx.assets.open(rel).use { it.readBytes() }
                }
                trimmed.startsWith("res://") -> readAndroidResourceBytes(ctx, trimmed.removePrefix("res://"))
                else -> null
            }
        }.getOrNull()?.takeIf { it.isNotEmpty() }
    }

    override fun probeMetadataPlatform(bytes: ByteArray, detectedFormat: ImageFormat): ImageMetadata? {
        if (bytes.isEmpty()) return null
        return runCatching {
            val opts = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) {
                ImageMetadata(
                    width = opts.outWidth,
                    height = opts.outHeight,
                    format = detectedFormat,
                    pixelFormat = PixelFormat.RGBA_8888,
                    colorSpace = ImageColorSpace.SRGB,
                    hasAlpha = detectedFormat == ImageFormat.PNG ||
                        detectedFormat == ImageFormat.WEBP ||
                        detectedFormat == ImageFormat.GIF
                )
            } else {
                null
            }
        }.getOrNull()
    }

    override fun decodePlatform(
        bytes: ByteArray,
        detectedFormat: ImageFormat?,
        options: ImageDecodeOptions
    ): ImageBuffer? {
        if (bytes.isEmpty()) return null

        if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
                val bitmap = ImageDecoder.decodeBitmap(src) { decoder, info, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                    decoder.isUnpremultipliedRequired = !options.premultiplyAlpha
                    val naturalW = info.size.width
                    val naturalH = info.size.height
                    val (scaledW, scaledH) = computeTargetDimensions(naturalW, naturalH, options)
                    if (scaledW != null && scaledH != null && (scaledW != naturalW || scaledH != naturalH)) {
                        decoder.setTargetSize(scaledW, scaledH)
                    }
                }
                val converted = bitmapToImageBuffer(bitmap)
                if (!bitmap.isRecycled) bitmap.recycle()
                if (converted != null) return converted
            }
        }

        return runCatching {
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
            val sampleSize = computeInSampleSize(
                boundsOpts.outWidth,
                boundsOpts.outHeight,
                options.targetWidth,
                options.targetHeight
            )
            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
                inPremultiplied = options.premultiplyAlpha
            }
            val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return@runCatching null
            val (scaledW, scaledH) = computeTargetDimensions(decoded.width, decoded.height, options)
            val finalBitmap = if (
                scaledW != null &&
                scaledH != null &&
                (scaledW != decoded.width || scaledH != decoded.height)
            ) {
                Bitmap.createScaledBitmap(decoded, scaledW, scaledH, options.antialiasing)?.also {
                    if (it !== decoded && !decoded.isRecycled) decoded.recycle()
                } ?: decoded
            } else {
                decoded
            }
            val buffer = bitmapToImageBuffer(finalBitmap)
            if (!finalBitmap.isRecycled) finalBitmap.recycle()
            buffer
        }.getOrNull()
    }

    override fun decodeAnimatedPlatform(source: ImageSource, options: ImageDecodeOptions): AnimatedImage? {
        if (Build.VERSION.SDK_INT < 28) return null
        val bytes = source.encodedBytes ?: resolveSourceBytes(source) ?: return null
        if (bytes.isEmpty()) return null
        val detected = ImageFormat.detectFromHeader(bytes)
        if (detected != ImageFormat.GIF && detected != ImageFormat.WEBP && detected != ImageFormat.PNG) {
            return null
        }

        return runCatching {
            val src = ImageDecoder.createSource(ByteBuffer.wrap(bytes))
            val drawable = ImageDecoder.decodeDrawable(src)
            if (drawable is AnimatedImageDrawable) {
                val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
                val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888) ?: return@runCatching null
                val canvas = Canvas(bmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
                val frameBuf = bitmapToImageBuffer(bmp)
                if (!bmp.isRecycled) bmp.recycle()
                val loops = if (drawable.repeatCount == AnimatedImageDrawable.REPEAT_INFINITE) {
                    0
                } else {
                    (drawable.repeatCount + 1).coerceAtLeast(1)
                }
                if (frameBuf != null) {
                    AnimatedImage(
                        width = w,
                        height = h,
                        frames = listOf(ImageFrame(frameIndex = 0, buffer = frameBuf, durationMs = 100)),
                        loopCount = loops
                    )
                } else {
                    null
                }
            } else {
                null
            }
        }.getOrNull()
    }

    override fun encodePlatform(buffer: ImageBuffer, options: ImageEncodeOptions): ByteArray? {
        val compressFormat = when (options.format) {
            ImageFormat.PNG -> Bitmap.CompressFormat.PNG
            ImageFormat.JPEG -> Bitmap.CompressFormat.JPEG
            ImageFormat.WEBP -> if (Build.VERSION.SDK_INT >= 30) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            else -> return null
        }

        return runCatching {
            val rgba = buffer.asRgba8888()
            val w = rgba.width
            val h = rgba.height
            if (w <= 0 || h <= 0) return@runCatching null
            val argb = IntArray(w * h)
            val raw = rgba.bytes
            var bi = 0
            for (i in argb.indices) {
                if (bi + 3 >= raw.size) break
                val r = raw[bi].toInt() and 0xFF
                val g = raw[bi + 1].toInt() and 0xFF
                val b = raw[bi + 2].toInt() and 0xFF
                val a = raw[bi + 3].toInt() and 0xFF
                argb[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
                bi += 4
            }
            val bitmap = Bitmap.createBitmap(argb, w, h, Bitmap.Config.ARGB_8888) ?: return@runCatching null
            val out = ByteArrayOutputStream()
            val quality = if (options.format == ImageFormat.PNG) 100 else options.quality.coerceIn(0, 100)
            val compressed = bitmap.compress(compressFormat, quality, out)
            if (!bitmap.isRecycled) bitmap.recycle()
            if (compressed) out.toByteArray().takeIf { it.isNotEmpty() } else null
        }.getOrNull()
    }

    private fun readAndroidResourceBytes(ctx: Context, resPath: String): ByteArray? {
        val parts = resPath.trimStart('/').split('/', limit = 2)
        if (parts.size != 2) return null
        val defType = parts[0]
        val entryName = parts[1].substringBeforeLast('.')
        val resId = ctx.resources.getIdentifier(entryName, defType, ctx.packageName)
        if (resId == 0) return null
        if (defType == "raw") {
            return ctx.resources.openRawResource(resId).use { it.readBytes() }
        }
        val drawable = ctx.resources.getDrawable(resId, ctx.theme) ?: return null
        val bmp = if (drawable is BitmapDrawable && drawable.bitmap != null) {
            drawable.bitmap
        } else {
            val w = drawable.intrinsicWidth.takeIf { it > 0 } ?: 1
            val h = drawable.intrinsicHeight.takeIf { it > 0 } ?: 1
            Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)?.also { canvasBmp ->
                val canvas = Canvas(canvasBmp)
                drawable.setBounds(0, 0, w, h)
                drawable.draw(canvas)
            } ?: return null
        }
        val out = ByteArrayOutputStream()
        return if (bmp.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
    }

    private fun bitmapToImageBuffer(bitmap: Bitmap): ImageBuffer? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val argbSource = if (bitmap.config == Bitmap.Config.ARGB_8888) {
            bitmap
        } else {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        }
        val argb = IntArray(w * h)
        argbSource.getPixels(argb, 0, w, 0, 0, w, h)
        if (argbSource !== bitmap && !argbSource.isRecycled) {
            argbSource.recycle()
        }
        val rgba = ByteArray(w * h * 4)
        var bi = 0
        for (px in argb) {
            rgba[bi] = ((px ushr 16) and 0xFF).toByte()
            rgba[bi + 1] = ((px ushr 8) and 0xFF).toByte()
            rgba[bi + 2] = (px and 0xFF).toByte()
            rgba[bi + 3] = ((px ushr 24) and 0xFF).toByte()
            bi += 4
        }
        return ImageBuffer(
            width = w,
            height = h,
            pixelFormat = PixelFormat.RGBA_8888,
            colorSpace = ImageColorSpace.SRGB,
            bytes = rgba
        )
    }

    private fun computeInSampleSize(rawW: Int, rawH: Int, targetW: Int?, targetH: Int?): Int {
        if (rawW <= 0 || rawH <= 0) return 1
        var inSample = 1
        val reqW = targetW ?: Int.MAX_VALUE
        val reqH = targetH ?: Int.MAX_VALUE
        if (rawW > reqW || rawH > reqH) {
            val halfW = rawW / 2
            val halfH = rawH / 2
            while ((halfW / inSample) >= reqW && (halfH / inSample) >= reqH) {
                inSample *= 2
            }
        }
        return max(1, inSample)
    }

    private fun computeTargetDimensions(
        naturalW: Int,
        naturalH: Int,
        options: ImageDecodeOptions
    ): Pair<Int?, Int?> {
        val reqW = options.targetWidth?.takeIf { it > 0 }
        val reqH = options.targetHeight?.takeIf { it > 0 }
        if (reqW == null && reqH == null) return null to null
        if (naturalW <= 0 || naturalH <= 0) return reqW to reqH
        if (!options.preserveAspectRatio) {
            return (reqW ?: naturalW) to (reqH ?: naturalH)
        }
        val scale = when {
            reqW != null && reqH != null -> minOf(reqW.toFloat() / naturalW, reqH.toFloat() / naturalH)
            reqW != null -> reqW.toFloat() / naturalW
            else -> reqH!!.toFloat() / naturalH
        }
        return max(1, (naturalW * scale).roundToInt()) to max(1, (naturalH * scale).roundToInt())
    }
}
