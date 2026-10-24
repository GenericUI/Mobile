package com.nexustools.genericui.mobile.system

import com.nexustools.genericui.component.fragment.Fragment
import com.nexustools.genericui.window.PipAction
import com.nexustools.genericui.window.PipConfig
import kotlin.math.roundToInt

/**
 * Shared Picture-in-Picture (`PipConfig`, aspect ratio clamping to Android/iOS platform limits `[1/2.39 .. 2.39/1]`,
 * and remote transport action dispatch) state coordinator for `:android` and `:ios`.
 */
class MobilePipController {
    var activeConfig: PipConfig = PipConfig()
        private set

    var isInPictureInPictureMode: Boolean = false
        private set

    private val modeChangeListeners = mutableListOf<(Boolean) -> Unit>()

    /**
     * Clamps the aspect ratio numerator/denominator to Android `Rational` / iOS `AVPictureInPictureController`
     * valid bounds (`0.41841` to `2.39`).
     */
    fun clampAspectRatio(numerator: Int, denominator: Int): Pair<Int, Int> {
        val safeNum = numerator.coerceAtLeast(1)
        val safeDen = denominator.coerceAtLeast(1)
        val ratio = safeNum.toDouble() / safeDen.toDouble()
        val clamped = ratio.coerceIn(1.0 / 2.39, 2.39)
        return (clamped * 1000.0).roundToInt() to 1000
    }

    fun configure(config: PipConfig) {
        val (clampedNum, clampedDen) = clampAspectRatio(config.aspectRatioNumerator, config.aspectRatioDenominator)
        activeConfig = config.copy(
            aspectRatioNumerator = clampedNum,
            aspectRatioDenominator = clampedDen
        )
    }

    fun onPictureInPictureModeChanged(inPip: Boolean, targetFragment: Fragment? = null) {
        if (isInPictureInPictureMode != inPip) {
            isInPictureInPictureMode = inPip
            targetFragment?.onPictureInPictureModeChanged(inPip)
            for (listener in modeChangeListeners.toList()) {
                listener(inPip)
            }
        }
    }

    fun dispatchRemoteAction(actionId: String): Boolean {
        val matched: PipAction = activeConfig.actions.firstOrNull { it.id == actionId } ?: return false
        matched.onAction.invoke()
        return true
    }

    fun addModeChangeListener(listener: (Boolean) -> Unit) {
        modeChangeListeners.add(listener)
    }

    fun removeModeChangeListener(listener: (Boolean) -> Unit) {
        modeChangeListeners.remove(listener)
    }
}
