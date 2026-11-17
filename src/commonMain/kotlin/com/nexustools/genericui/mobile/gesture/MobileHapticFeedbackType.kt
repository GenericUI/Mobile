package com.nexustools.genericui.mobile.gesture

/**
 * Tactile haptic feedback presets mapped to `HapticFeedbackConstants` on `:android`
 * and `UIImpactFeedbackGenerator` / `UISelectionFeedbackGenerator` / `UINotificationFeedbackGenerator` on `:ios`.
 */
enum class MobileHapticFeedbackType {
    LIGHT_IMPACT,
    MEDIUM_IMPACT,
    HEAVY_IMPACT,
    SELECTION_TICK,
    SUCCESS,
    WARNING,
    ERROR
}
