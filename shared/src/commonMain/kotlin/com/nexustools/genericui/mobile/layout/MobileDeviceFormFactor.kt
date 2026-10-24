package com.nexustools.genericui.mobile.layout

import kotlin.math.max
import kotlin.math.min

/**
 * Classifies mobile hardware form factors across `:android` and `:ios` based on density-independent pixel breakpoints.
 */
enum class MobileDeviceFormFactor {
    PHONE,
    FOLDABLE_UNFOLDED,
    TABLET;

    val isLargeScreen: Boolean
        get() = this != PHONE

    companion object {
        fun fromWidthDp(widthDp: Float, heightDp: Float): MobileDeviceFormFactor {
            val smallestDp = min(widthDp, heightDp)
            val largestDp = max(widthDp, heightDp)
            val aspectRatio = if (smallestDp > 0f) largestDp / smallestDp else 1f

            return when {
                smallestDp >= 580f && aspectRatio <= 1.32f && largestDp < 900f -> FOLDABLE_UNFOLDED
                smallestDp >= 600f || widthDp >= 840f -> TABLET
                else -> PHONE
            }
        }
    }
}
