@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.event.PointerEvent
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIHoverGestureRecognizer
import platform.UIKit.UILongPressGestureRecognizer
import platform.UIKit.UITapGestureRecognizer
import platform.UIKit.UIView
import platform.darwin.NSObject
import platform.objc.sel_registerName

/**
 * Objective-C `@ObjCAction` trampoline connecting genuine UIKit control events
 * (`UIControlEventTouchUpInside`, `UIControlEventValueChanged`, `UIControlEventEditingChanged`,
 * `UITapGestureRecognizer`, `UILongPressGestureRecognizer`, `UIHoverGestureRecognizer`) to Kotlin
 * callbacks without any synthetic simulation.
 */
class UikitControlActionTarget(
    private val hostView: UIView? = null
) : NSObject() {
    var onPrimaryAction: (() -> Unit)? = null
    var onValueChanged: (() -> Unit)? = null
    var onEditingChanged: (() -> Unit)? = null
    var onEditingDidEndOnExit: (() -> Unit)? = null
    var onClick: (() -> Unit)? = null
    var onDoubleClick: (() -> Unit)? = null
    var onContextMenu: ((PointerEvent) -> Unit)? = null
    var onPointerEnter: ((PointerEvent) -> Unit)? = null
    var onPointerMove: ((PointerEvent) -> Unit)? = null
    var onPointerLeave: ((PointerEvent) -> Unit)? = null
    var onHoveredChanged: ((Boolean) -> Unit)? = null

    @ObjCAction
    fun handlePrimaryAction(sender: NSObject?) {
        onPrimaryAction?.invoke()
    }

    @ObjCAction
    fun handleValueChanged(sender: NSObject?) {
        onValueChanged?.invoke()
    }

    @ObjCAction
    fun handleEditingChanged(sender: NSObject?) {
        onEditingChanged?.invoke()
    }

    @ObjCAction
    fun handleEditingDidEndOnExit(sender: NSObject?) {
        onEditingDidEndOnExit?.invoke()
    }

    @ObjCAction
    fun handleSingleTap(recognizer: UITapGestureRecognizer?) {
        if (recognizer?.state == UIGestureRecognizerStateEnded) {
            onClick?.invoke()
        }
    }

    @ObjCAction
    fun handleDoubleTap(recognizer: UITapGestureRecognizer?) {
        if (recognizer?.state == UIGestureRecognizerStateEnded) {
            onDoubleClick?.invoke()
        }
    }

    @ObjCAction
    fun handleLongPress(recognizer: UILongPressGestureRecognizer?) {
        val r = recognizer ?: return
        if (r.state == UIGestureRecognizerStateBegan) {
            val view = hostView ?: r.view
            val point = if (view != null) r.locationInView(view) else return
            point.useContents {
                onContextMenu?.invoke(
                    PointerEvent(
                        x = x.toFloat(),
                        y = y.toFloat(),
                        screenX = x.toFloat(),
                        screenY = y.toFloat(),
                        button = 2,
                        buttonsMask = 2
                    )
                )
            }
        }
    }

    @ObjCAction
    fun handleHover(recognizer: UIHoverGestureRecognizer?) {
        val r = recognizer ?: return
        val view = hostView ?: r.view
        val evt = if (view != null) {
            r.locationInView(view).useContents {
                PointerEvent(
                    x = x.toFloat(),
                    y = y.toFloat(),
                    screenX = x.toFloat(),
                    screenY = y.toFloat(),
                    isPrimary = true
                )
            }
        } else {
            PointerEvent(isPrimary = true)
        }
        when (r.state) {
            UIGestureRecognizerStateBegan -> {
                onHoveredChanged?.invoke(true)
                onPointerEnter?.invoke(evt)
            }
            UIGestureRecognizerStateChanged -> {
                onPointerMove?.invoke(evt)
            }
            UIGestureRecognizerStateEnded -> {
                onHoveredChanged?.invoke(false)
                onPointerLeave?.invoke(evt)
            }
            else -> {
                onHoveredChanged?.invoke(false)
                onPointerLeave?.invoke(evt)
            }
        }
    }

    companion object {
        val SEL_PRIMARY_ACTION get() = sel_registerName("handlePrimaryAction:")
        val SEL_VALUE_CHANGED get() = sel_registerName("handleValueChanged:")
        val SEL_EDITING_CHANGED get() = sel_registerName("handleEditingChanged:")
        val SEL_EDITING_END_EXIT get() = sel_registerName("handleEditingDidEndOnExit:")
        val SEL_SINGLE_TAP get() = sel_registerName("handleSingleTap:")
        val SEL_DOUBLE_TAP get() = sel_registerName("handleDoubleTap:")
        val SEL_LONG_PRESS get() = sel_registerName("handleLongPress:")
        val SEL_HOVER get() = sel_registerName("handleHover:")
    }
}
