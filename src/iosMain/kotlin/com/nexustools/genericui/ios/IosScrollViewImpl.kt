@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.mobile.gesture.MobileFlingPhysics
import com.nexustools.genericui.nativeimpl.layout.ScrollViewNativeImpl
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGPointMake
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIScrollView
import platform.UIKit.UIView

/**
 * Native UIKit `UIScrollView` implementation of [ScrollViewNativeImpl], integrating
 * [UikitFlexContainerView] content hosting, [UikitScrollViewDelegate], and [MobileFlingPhysics].
 */
class IosScrollViewImpl(
    val scrollView: UIScrollView = UIScrollView(frame = CGRectMake(0.0, 0.0, 360.0, 480.0))
) : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(scrollView),
    componentClassPrefix = "scroll-view"
), ScrollViewNativeImpl<UikitViewHandle> {

    private val contentHost = UikitFlexContainerView(CGRectMake(0.0, 0.0, 360.0, 480.0))
    private val delegateBridge = UikitScrollViewDelegate()
    val flingPhysics: MobileFlingPhysics = MobileFlingPhysics()

    init {
        scrollView.addSubview(contentHost)
        scrollView.delegate = delegateBridge
        scrollView.showsVerticalScrollIndicator = true
        scrollView.showsHorizontalScrollIndicator = true
        delegateBridge.onWillEndDragging = { _, vy ->
            flingPhysics.predictFlingDistancePx(vy * 1000f)
        }
    }

    override fun contentContainerView(): UIView = contentHost

    override fun endBatchAttach() {
        super<IosBaseContainerNativeImpl>.endBatchAttach()
        syncContentSize()
    }

    override fun scrollTo(x: Int, y: Int) {
        syncContentSize()
        scrollView.setContentOffset(CGPointMake(x.toDouble(), y.toDouble()), animated = false)
    }

    override fun scrollToBottom() {
        syncContentSize()
        val maxY = scrollView.contentSize.useContents {
            val viewH = scrollView.bounds.useContents { size.height }
            (height - viewH).coerceAtLeast(0.0)
        }
        scrollView.setContentOffset(CGPointMake(0.0, maxY), animated = true)
    }

    private fun syncContentSize() {
        val viewportSize = scrollView.bounds.useContents { CGSizeMake(size.width.coerceAtLeast(320.0), 10000.0) }
        val fitted = contentHost.sizeThatFits(viewportSize)
        fitted.useContents {
            contentHost.setFrame(CGRectMake(0.0, 0.0, width, height))
            scrollView.setContentSize(CGSizeMake(width, height))
        }
    }
}
