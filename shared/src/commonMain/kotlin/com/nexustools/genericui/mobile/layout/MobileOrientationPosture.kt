package com.nexustools.genericui.mobile.layout

/**
 * Represents mobile device orientation and foldable hinge posture across `:android` (Jetpack WindowManager)
 * and `:ios` (`UITraitCollection` / `UIDeviceOrientation`).
 */
enum class MobileOrientationPosture {
    PORTRAIT,
    LANDSCAPE,
    TABLETOP_HALF_OPEN,
    BOOK_HALF_OPEN;

    val isLandscapeLike: Boolean
        get() = this == LANDSCAPE || this == BOOK_HALF_OPEN

    val isHalfOpenedFoldable: Boolean
        get() = this == TABLETOP_HALF_OPEN || this == BOOK_HALF_OPEN

    companion object {
        fun fromDimensions(
            widthDp: Float,
            heightDp: Float,
            isHalfOpenedHinge: Boolean = false,
            isHorizontalHinge: Boolean = false
        ): MobileOrientationPosture {
            if (isHalfOpenedHinge) {
                return if (isHorizontalHinge) TABLETOP_HALF_OPEN else BOOK_HALF_OPEN
            }
            return if (widthDp > heightDp) LANDSCAPE else PORTRAIT
        }
    }
}
