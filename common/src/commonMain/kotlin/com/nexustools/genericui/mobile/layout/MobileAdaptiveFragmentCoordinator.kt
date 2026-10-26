package com.nexustools.genericui.mobile.layout

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.component.fragment.helper.AdaptiveSplitTabFragment
import com.nexustools.genericui.enums.ui.ThemeMode
import com.nexustools.genericui.window.WindowConfiguration
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Shared mobile layout state machine for [AdaptiveSplitTabFragment] across `:android` and `:ios`.
 * Coordinates the 3-mode responsive layout (tablet/foldable landscape multi-column vs
 * phone landscape optional immersive fullscreen vs phone/tablet portrait single-flow with tabs).
 */
class MobileAdaptiveFragmentCoordinator(
    val safeAreaTracker: MobileSafeAreaTracker = MobileSafeAreaTracker()
) {
    var formFactor: MobileDeviceFormFactor = MobileDeviceFormFactor.PHONE
        private set

    var posture: MobileOrientationPosture = MobileOrientationPosture.PORTRAIT
        private set

    fun evaluateConfiguration(
        widthPx: Int,
        heightPx: Int,
        densityScale: Float,
        themeMode: ThemeMode = ThemeMode.DARK,
        isHalfOpenedHinge: Boolean = false,
        isHorizontalHinge: Boolean = false
    ): WindowConfiguration {
        val safeDensity = if (densityScale > 0f) densityScale else 1f
        val widthDp = widthPx / safeDensity
        val heightDp = heightPx / safeDensity

        formFactor = MobileDeviceFormFactor.fromWidthDp(widthDp, heightDp)
        posture = MobileOrientationPosture.fromDimensions(widthDp, heightDp, isHalfOpenedHinge, isHorizontalHinge)

        val smallestDp = min(widthDp, heightDp).roundToInt()
        return WindowConfiguration(
            widthPx = widthPx,
            heightPx = heightPx,
            smallestScreenWidthDp = smallestDp,
            densityScale = safeDensity,
            themeMode = themeMode
        )
    }

    fun resolveLayoutMode(
        fragment: AdaptiveSplitTabFragment,
        config: WindowConfiguration
    ): AdaptiveSplitTabFragment.LayoutMode {
        return when {
            config.isLandscape && !formFactor.isLargeScreen && fragment.phoneLandscapeFullscreen ->
                AdaptiveSplitTabFragment.LayoutMode.PHONE_LANDSCAPE_FULLSCREEN
            config.isLandscape || posture == MobileOrientationPosture.BOOK_HALF_OPEN ->
                AdaptiveSplitTabFragment.LayoutMode.TABLET_DESKTOP_COLUMNS
            else ->
                AdaptiveSplitTabFragment.LayoutMode.PORTRAIT_SINGLE_FLOW_TABS
        }
    }

    fun dispatchMetricsToFragment(
        fragment: Fragment,
        widthPx: Int,
        heightPx: Int,
        densityScale: Float,
        themeMode: ThemeMode = ThemeMode.DARK
    ) {
        val config = evaluateConfiguration(widthPx, heightPx, densityScale, themeMode)
        fragment.onConfigurationChanged(config)
    }
}
