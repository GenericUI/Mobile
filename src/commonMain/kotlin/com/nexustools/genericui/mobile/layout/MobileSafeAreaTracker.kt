package com.nexustools.genericui.mobile.layout

import com.nexustools.genericui.geometry.EdgeInsets
import kotlin.math.max

/**
 * Tracks mobile system bar insets (status bar / navigation bar / home indicator), display cutouts
 * (camera hole-punch / Dynamic Island / notch), and software IME keyboard insets (`EdgeInsets`).
 */
class MobileSafeAreaTracker {
    var systemBarInsets: EdgeInsets = EdgeInsets.ZERO
        private set

    var displayCutoutInsets: EdgeInsets = EdgeInsets.ZERO
        private set

    var imeKeyboardInsets: EdgeInsets = EdgeInsets.ZERO
        private set

    private val listeners = mutableListOf<(EdgeInsets) -> Unit>()

    val isKeyboardVisible: Boolean
        get() = imeKeyboardInsets.bottom > 0

    val combinedSafeInsets: EdgeInsets
        get() = EdgeInsets(
            top = max(systemBarInsets.top, displayCutoutInsets.top),
            right = max(systemBarInsets.right, displayCutoutInsets.right),
            bottom = max(max(systemBarInsets.bottom, displayCutoutInsets.bottom), imeKeyboardInsets.bottom),
            left = max(systemBarInsets.left, displayCutoutInsets.left)
        )

    fun updateSystemBarInsets(insets: EdgeInsets) {
        if (systemBarInsets != insets) {
            systemBarInsets = insets
            notifyListeners()
        }
    }

    fun updateDisplayCutoutInsets(insets: EdgeInsets) {
        if (displayCutoutInsets != insets) {
            displayCutoutInsets = insets
            notifyListeners()
        }
    }

    fun updateImeKeyboardInsets(insets: EdgeInsets) {
        if (imeKeyboardInsets != insets) {
            imeKeyboardInsets = insets
            notifyListeners()
        }
    }

    fun updateAllInsets(systemBars: EdgeInsets, displayCutout: EdgeInsets, imeKeyboard: EdgeInsets) {
        val changed = systemBarInsets != systemBars ||
            displayCutoutInsets != displayCutout ||
            imeKeyboardInsets != imeKeyboard
        systemBarInsets = systemBars
        displayCutoutInsets = displayCutout
        imeKeyboardInsets = imeKeyboard
        if (changed) {
            notifyListeners()
        }
    }

    fun addListener(listener: (EdgeInsets) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (EdgeInsets) -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyListeners() {
        val snapshot = combinedSafeInsets
        for (listener in listeners.toList()) {
            listener(snapshot)
        }
    }
}
