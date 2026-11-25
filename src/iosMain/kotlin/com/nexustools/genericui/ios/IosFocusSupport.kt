@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import platform.UIKit.UIView

/**
 * Internal per-`UIView` focusable registry for `:mobile` (`iosMain`), tracking explicit
 * non-focusable views and resolving inherited focusability up the `superview` chain.
 */
internal object UikitFocusRegistry {
    private val viewFocusableMap = LinkedHashMap<UIView, Boolean>()

    fun setFocusable(view: UIView, focusable: Boolean) {
        if (focusable) {
            viewFocusableMap.remove(view)
        } else {
            viewFocusableMap[view] = false
        }
    }

    fun isOwnFocusable(view: UIView): Boolean = viewFocusableMap[view] != false

    fun isEffectivelyFocusable(view: UIView?): Boolean {
        var cur: UIView? = view
        while (cur != null) {
            if (viewFocusableMap[cur] == false) return false
            cur = cur.superview
        }
        return true
    }

    fun remove(view: UIView) {
        viewFocusableMap.remove(view)
    }
}
