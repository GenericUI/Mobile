@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.style.color.RgbaColor
import kotlinx.cinterop.DoubleVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import platform.UIKit.UIColor
import platform.UIKit.UIColorPickerViewController
import platform.UIKit.UIColorPickerViewControllerDelegateProtocol
import platform.darwin.NSObject
import kotlin.math.roundToInt

/**
 * `UIColorPickerViewControllerDelegate` extracting live RGBA components and completion events.
 */
class UikitColorPickerDelegate(
    private val onFinished: (RgbaColor?) -> Unit
) : NSObject(), UIColorPickerViewControllerDelegateProtocol {

    private var lastPickedColor: RgbaColor? = null
    private var finishedDispatched: Boolean = false

    override fun colorPickerViewController(
        viewController: UIColorPickerViewController,
        didSelectColor: UIColor,
        continuously: Boolean
    ) {
        lastPickedColor = extractRgba(didSelectColor)
    }

    override fun colorPickerViewControllerDidSelectColor(viewController: UIColorPickerViewController) {
        lastPickedColor = extractRgba(viewController.selectedColor)
    }

    override fun colorPickerViewControllerDidFinish(viewController: UIColorPickerViewController) {
        if (finishedDispatched) return
        finishedDispatched = true
        onFinished(lastPickedColor ?: extractRgba(viewController.selectedColor))
    }

    private fun extractRgba(color: UIColor): RgbaColor = memScoped {
        val r = alloc<DoubleVar>()
        val g = alloc<DoubleVar>()
        val b = alloc<DoubleVar>()
        val a = alloc<DoubleVar>()
        val converted = color.getRed(r.ptr, green = g.ptr, blue = b.ptr, alpha = a.ptr)
        if (!converted) {
            return@memScoped RgbaColor(0, 0, 0, 255)
        }
        RgbaColor(
            r = (r.value.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
            g = (g.value.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
            b = (b.value.coerceIn(0.0, 1.0) * 255.0).roundToInt(),
            a = (a.value.coerceIn(0.0, 1.0) * 255.0).roundToInt()
        )
    }
}
