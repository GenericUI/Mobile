@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.bootstrap.BorderRadiusStyle
import com.nexustools.genericui.enums.bootstrap.BootstrapType
import com.nexustools.genericui.enums.bootstrap.FontWeight
import com.nexustools.genericui.enums.bootstrap.ShadowElevation
import com.nexustools.genericui.enums.bootstrap.SpacingScale
import com.nexustools.genericui.enums.bootstrap.TextAlignment
import com.nexustools.genericui.enums.css.CssFontStyle
import com.nexustools.genericui.enums.css.CssTextDecoration
import com.nexustools.genericui.enums.ui.ObjectFit
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.event.KeyEvent
import com.nexustools.genericui.event.KeyShortcut
import com.nexustools.genericui.image.ImageBuffer
import com.nexustools.genericui.image.ImageColorSpace
import com.nexustools.genericui.image.PixelFormat
import com.nexustools.genericui.style.color.BootstrapColorPalette
import com.nexustools.genericui.style.color.RgbaColor
import com.nexustools.genericui.style.theme.SystemThemeDetector
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorRef
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.kCGBitmapByteOrder32Big
import platform.Foundation.NSAttributedString
import platform.Foundation.NSNumber
import platform.Foundation.create
import platform.Foundation.numberWithInt
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.NSStrikethroughStyleAttributeName
import platform.UIKit.NSTextAlignmentCenter
import platform.UIKit.NSTextAlignmentJustified
import platform.UIKit.NSTextAlignmentLeft
import platform.UIKit.NSTextAlignmentRight
import platform.UIKit.NSUnderlineStyleAttributeName
import platform.UIKit.NSUnderlineStyleSingle
import platform.UIKit.UIColor
import platform.UIKit.UIFont
import platform.UIKit.UIFontDescriptorTraitItalic
import platform.UIKit.UIFontWeightBold
import platform.UIKit.UIFontWeightLight
import platform.UIKit.UIFontWeightMedium
import platform.UIKit.UIFontWeightRegular
import platform.UIKit.UIFontWeightSemibold
import platform.UIKit.UIImage
import platform.UIKit.UIKey
import platform.UIKit.UIKeyModifierAlternate
import platform.UIKit.UIKeyModifierCommand
import platform.UIKit.UIKeyModifierControl
import platform.UIKit.UIKeyModifierShift
import platform.UIKit.UIViewContentMode

/**
 * Shared conversion helpers between GenericUI styles, geometry, colors, bitmaps, and shortcuts
 * and their `UIKit` / `CoreGraphics` / `Foundation` equivalents.
 */
object UikitGeometryAndColorUtil {

    fun RgbaColor.toUiColor(): UIColor =
        UIColor.colorWithRed(
            red = r / 255.0,
            green = g / 255.0,
            blue = b / 255.0,
            alpha = a / 255.0
        )

    fun RgbaColor.toCgColor(): CGColorRef? = toUiColor().CGColor

    fun isDarkActive(): Boolean =
        SystemThemeDetector.detectSystemTheme() == ThemeMode.DARK

    fun resolveBootstrapColor(type: BootstrapType?): RgbaColor? {
        if (type == null || type == BootstrapType.NONE) return null
        return BootstrapColorPalette.resolveColor(type, isDarkActive())
    }

    fun contrastForegroundFor(background: RgbaColor): RgbaColor {
        val luminance = (0.299 * background.r + 0.587 * background.g + 0.114 * background.b) / 255.0
        return if (luminance > 0.62) RgbaColor(17, 24, 39, 255) else RgbaColor(255, 255, 255, 255)
    }

    fun resolveBorderRadiusPt(radius: BorderRadiusStyle?): Double =
        (radius?.radiusPx ?: 0).toDouble()

    fun resolveShadow(shadow: ShadowElevation?): Triple<Float, Double, Double> = when (shadow) {
        ShadowElevation.NONE, null -> Triple(0f, 0.0, 0.0)
        ShadowElevation.SMALL -> Triple(0.12f, 2.0, 1.0)
        ShadowElevation.DEFAULT -> Triple(0.18f, 6.0, 3.0)
        ShadowElevation.LARGE -> Triple(0.24f, 14.0, 6.0)
    }

    fun resolveSpacingPt(scale: SpacingScale?): Double =
        scale?.toPixels()?.toDouble() ?: 0.0

    fun resolveTextAlignment(alignment: TextAlignment?): Long = when (alignment) {
        TextAlignment.START, null -> NSTextAlignmentLeft
        TextAlignment.CENTER -> NSTextAlignmentCenter
        TextAlignment.END -> NSTextAlignmentRight
        TextAlignment.JUSTIFY -> NSTextAlignmentJustified
    }

    fun resolveTextAlignmentFromCss(textAlign: String?): Long = when (textAlign?.trim()?.lowercase()) {
        "center" -> NSTextAlignmentCenter
        "right", "end" -> NSTextAlignmentRight
        "justify" -> NSTextAlignmentJustified
        else -> NSTextAlignmentLeft
    }

    fun resolveFont(
        sizePt: Double = 15.0,
        weight: FontWeight? = null,
        weightNumeric: Int? = null,
        fontFamily: String? = null,
        italic: Boolean = false
    ): UIFont {
        val cleanFamily = fontFamily?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedNumeric = weightNumeric ?: weight?.numericWeight ?: 400
        val uiWeight = when {
            resolvedNumeric <= 350 -> UIFontWeightLight
            resolvedNumeric < 475 -> UIFontWeightRegular
            resolvedNumeric < 575 -> UIFontWeightMedium
            resolvedNumeric < 675 -> UIFontWeightSemibold
            else -> UIFontWeightBold
        }
        val baseFont = when {
            cleanFamily?.contains("mono", ignoreCase = true) == true ||
                cleanFamily?.contains("courier", ignoreCase = true) == true ||
                cleanFamily?.contains("menlo", ignoreCase = true) == true ->
                UIFont.monospacedSystemFontOfSize(sizePt, weight = uiWeight)
            cleanFamily != null ->
                UIFont.fontWithName(cleanFamily, size = sizePt) ?: UIFont.systemFontOfSize(sizePt, weight = uiWeight)
            else -> UIFont.systemFontOfSize(sizePt, weight = uiWeight)
        }
        if (!italic) return baseFont
        val italicDesc = baseFont.fontDescriptor.fontDescriptorWithSymbolicTraits(UIFontDescriptorTraitItalic)
        return if (italicDesc != null) UIFont.fontWithDescriptor(italicDesc, size = sizePt) else baseFont
    }

    fun buildAttributedText(
        text: String,
        font: UIFont,
        color: UIColor?,
        decoration: CssTextDecoration? = null
    ): NSAttributedString {
        val attrs = LinkedHashMap<Any?, Any?>()
        NSFontAttributeName?.let { attrs[it] = font }
        if (color != null) {
            NSForegroundColorAttributeName?.let { attrs[it] = color }
        }
        when (decoration) {
            CssTextDecoration.UNDERLINE -> {
                NSUnderlineStyleAttributeName?.let { attrs[it] = NSNumber.numberWithInt(NSUnderlineStyleSingle.toInt()) }
            }
            CssTextDecoration.LINE_THROUGH -> {
                NSStrikethroughStyleAttributeName?.let { attrs[it] = NSNumber.numberWithInt(NSUnderlineStyleSingle.toInt()) }
            }
            else -> Unit
        }
        return NSAttributedString.create(string = text, attributes = attrs)
    }

    fun resolveContentMode(fit: ObjectFit): UIViewContentMode = when (fit) {
        ObjectFit.FILL -> UIViewContentMode.UIViewContentModeScaleToFill
        ObjectFit.CONTAIN, ObjectFit.SCALE_DOWN -> UIViewContentMode.UIViewContentModeScaleAspectFit
        ObjectFit.COVER -> UIViewContentMode.UIViewContentModeScaleAspectFill
        ObjectFit.NONE -> UIViewContentMode.UIViewContentModeCenter
    }

    fun resolveSymbolName(iconNameOrPath: String?): String? {
        val clean = iconNameOrPath?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        return when {
            clean.contains("close") || clean.contains("times") || clean == "x" -> "xmark"
            clean.contains("add") || clean.contains("plus") -> "plus"
            clean.contains("menu") || clean.contains("bars") || clean.contains("list") -> "line.3.horizontal"
            clean.contains("search") -> "magnifyingglass"
            clean.contains("settings") || clean.contains("gear") -> "gearshape"
            clean.contains("home") -> "house"
            clean.contains("folder") -> "folder"
            clean.contains("file") || clean.contains("doc") -> "doc"
            clean.contains("save") -> "square.and.arrow.down"
            clean.contains("edit") || clean.contains("pen") -> "pencil"
            clean.contains("trash") || clean.contains("delete") -> "trash"
            clean.contains("check") -> "checkmark"
            clean.contains("info") -> "info.circle"
            clean.contains("warn") -> "exclamationmark.triangle"
            clean.contains("error") -> "xmark.octagon"
            clean.contains("chevron-down") || clean.contains("down") -> "chevron.down"
            clean.contains("chevron-right") || clean.contains("right") -> "chevron.right"
            clean.contains("back") || clean.contains("left") -> "chevron.left"
            clean.contains("refresh") || clean.contains("sync") -> "arrow.clockwise"
            clean.contains("copy") -> "doc.on.doc"
            else -> clean.removePrefix("sf:").takeIf { it.matches(Regex("[a-z0-9._-]+")) }
        }
    }

    fun systemIconImage(iconNameOrPath: String?): UIImage? {
        val sym = resolveSymbolName(iconNameOrPath) ?: return null
        return UIImage.systemImageNamed(sym)
    }

    fun imageBufferToUiImage(buffer: ImageBuffer): UIImage? {
        if (buffer.width <= 0 || buffer.height <= 0) return null
        val rgba = if (buffer.pixelFormat == PixelFormat.RGBA_8888) buffer else buffer.toRgba8888()
        if (rgba.bytes.size < rgba.width * rgba.height * 4) return null
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        return try {
            rgba.bytes.usePinned { pinned ->
                val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = rgba.width.toULong(),
                    height = rgba.height.toULong(),
                    bitsPerComponent = 8uL,
                    bytesPerRow = (rgba.width * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = bitmapInfo
                ) ?: return null
                try {
                    val cgImage = CGBitmapContextCreateImage(context) ?: return null
                    try {
                        UIImage.imageWithCGImage(cgImage)
                    } finally {
                        CGImageRelease(cgImage)
                    }
                } finally {
                    CGContextRelease(context)
                }
            }
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    fun cgImageToImageBuffer(cgImage: CGImageRef?): ImageBuffer? {
        if (cgImage == null) return null
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        if (width <= 0 || height <= 0) return null
        val bytes = ByteArray(width * height * 4)
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null
        return try {
            bytes.usePinned { pinned ->
                val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value or kCGBitmapByteOrder32Big
                val context = CGBitmapContextCreate(
                    data = pinned.addressOf(0),
                    width = width.toULong(),
                    height = height.toULong(),
                    bitsPerComponent = 8uL,
                    bytesPerRow = (width * 4).toULong(),
                    space = colorSpace,
                    bitmapInfo = bitmapInfo
                ) ?: return null
                try {
                    CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
                } finally {
                    CGContextRelease(context)
                }
            }
            ImageBuffer(
                width = width,
                height = height,
                pixelFormat = PixelFormat.RGBA_8888,
                colorSpace = ImageColorSpace.SRGB,
                bytes = bytes
            )
        } finally {
            CGColorSpaceRelease(colorSpace)
        }
    }

    fun toUikitKeyInputAndModifiers(shortcut: KeyShortcut): Pair<String, Long> {
        var flags = 0L
        if (shortcut.ctrlOrCmd) flags = flags or UIKeyModifierCommand
        if (shortcut.shift) flags = flags or UIKeyModifierShift
        if (shortcut.alt) flags = flags or UIKeyModifierAlternate
        val key = shortcut.key.trim()
        val mappedInput = when (key.lowercase()) {
            "enter", "return" -> "\r"
            "escape", "esc" -> "\u001b"
            "tab" -> "\t"
            "backspace" -> "\b"
            "space" -> " "
            else -> key.lowercase()
        }
        return mappedInput to flags
    }

    fun uiKeyToKeyEvent(uiKey: UIKey, isTargetTextInput: Boolean): KeyEvent {
        val mods = uiKey.modifierFlags
        val chars = uiKey.charactersIgnoringModifiers.ifEmpty { uiKey.characters }
        val normalizedKey = when (chars) {
            "\r", "\n" -> "Enter"
            "\u001b" -> "Escape"
            "\t" -> "Tab"
            "\b" -> "Backspace"
            " " -> " "
            else -> chars
        }
        return KeyEvent(
            key = normalizedKey,
            code = normalizedKey,
            keyCode = uiKey.keyCode.toInt(),
            shiftKey = (mods and UIKeyModifierShift) != 0L,
            ctrlKey = (mods and UIKeyModifierControl) != 0L,
            altKey = (mods and UIKeyModifierAlternate) != 0L,
            metaKey = (mods and UIKeyModifierCommand) != 0L,
            isRepeat = false,
            isTargetTextInput = isTargetTextInput
        )
    }

    fun fontWeightToCssItalic(style: CssFontStyle?): Boolean =
        style == CssFontStyle.ITALIC || style == CssFontStyle.OBLIQUE
}
