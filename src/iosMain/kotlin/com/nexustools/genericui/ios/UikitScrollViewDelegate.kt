@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import kotlinx.cinterop.CPointer
import kotlinx.cinterop.CValue
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPoint
import platform.UIKit.UIScrollView
import platform.UIKit.UIScrollViewDelegateProtocol
import platform.UIKit.UIView
import platform.darwin.NSObject

/**
 * `UIScrollViewDelegate` bridge forwarding real `UIScrollView` scroll offsets, deceleration,
 * dragging velocity, and zoom events to Kotlin containers and lazy viewport pools.
 */
class UikitScrollViewDelegate : NSObject(), UIScrollViewDelegateProtocol {
    var onScrolled: ((offsetX: Float, offsetY: Float, viewportW: Float, viewportH: Float) -> Unit)? = null
    var onWillEndDragging: ((velocityX: Float, velocityY: Float) -> Unit)? = null
    var zoomTargetView: UIView? = null

    override fun scrollViewDidScroll(scrollView: UIScrollView) {
        val (ox, oy) = scrollView.contentOffset.useContents { x.toFloat() to y.toFloat() }
        val (vw, vh) = scrollView.bounds.useContents { size.width.toFloat() to size.height.toFloat() }
        onScrolled?.invoke(ox, oy, vw, vh)
    }

    override fun scrollViewWillEndDragging(
        scrollView: UIScrollView,
        withVelocity: CValue<CGPoint>,
        targetContentOffset: CPointer<CGPoint>?
    ) {
        withVelocity.useContents {
            onWillEndDragging?.invoke(x.toFloat(), y.toFloat())
        }
    }

    override fun viewForZoomingInScrollView(scrollView: UIScrollView): UIView? = zoomTargetView
}
