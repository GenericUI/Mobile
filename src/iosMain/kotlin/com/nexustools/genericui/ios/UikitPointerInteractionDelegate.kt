@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.css.CssCursor
import com.nexustools.genericui.style.css.CssCursorValue
import platform.UIKit.UIPointerEffect
import platform.UIKit.UIPointerHoverEffect
import platform.UIKit.UIPointerInteraction
import platform.UIKit.UIPointerInteractionDelegateProtocol
import platform.UIKit.UIPointerLiftEffect
import platform.UIKit.UIPointerRegion
import platform.UIKit.UIPointerRegionRequest
import platform.UIKit.UIPointerShape
import platform.UIKit.UIPointerStyle
import platform.UIKit.UITargetedPreview
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * `UIPointerInteractionDelegate` mapping GenericUI [CssCursorValue] hints onto genuine UIKit
 * pointer effects (`UIPointerHoverEffect`, `UIPointerLiftEffect`, `UIPointerShape.beamWithPreferredLength`,
 * and system pointer style) for iPadOS trackpad/mouse and iOS pointer input.
 */
class UikitPointerInteractionDelegate(
    private val hostView: UIView,
    var cursorValue: CssCursorValue? = null
) : NSObject(), UIPointerInteractionDelegateProtocol {

    override fun pointerInteraction(
        interaction: UIPointerInteraction,
        regionForRequest: UIPointerRegionRequest,
        defaultRegion: UIPointerRegion
    ): UIPointerRegion? {
        if (cursorValue == CssCursor.NONE) return null
        return defaultRegion
    }

    override fun pointerInteraction(
        interaction: UIPointerInteraction,
        styleForRegion: UIPointerRegion
    ): UIPointerStyle? {
        val cursor = cursorValue ?: return UIPointerStyle.systemPointerStyle()
        return when (cursor) {
            CssCursor.TEXT -> {
                UIPointerStyle.styleWithShape(
                    UIPointerShape.beamWithPreferredLength(20.0, axis = platform.UIKit.UIAxisVertical),
                    constrainedAxes = 0uL
                )
            }
            CssCursor.POINTER, CssCursor.GRAB, CssCursor.GRABBING -> {
                val preview = UITargetedPreview(view = hostView)
                val effect: UIPointerEffect = UIPointerLiftEffect.effectWithPreview(preview)
                UIPointerStyle.styleWithEffect(effect, shape = null)
            }
            CssCursor.MOVE, CssCursor.ZOOM_IN, CssCursor.ZOOM_OUT -> {
                val preview = UITargetedPreview(view = hostView)
                val effect: UIPointerEffect = UIPointerHoverEffect.effectWithPreview(preview)
                UIPointerStyle.styleWithEffect(effect, shape = null)
            }
            CssCursor.NONE -> UIPointerStyle.hiddenPointerStyle()
            else -> UIPointerStyle.systemPointerStyle()
        }
    }
}
