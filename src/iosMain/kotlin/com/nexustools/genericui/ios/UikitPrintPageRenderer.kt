@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.nexustools.genericui.ios

import kotlinx.cinterop.CValue
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectGetWidth
import platform.UIKit.UIFont
import platform.UIKit.UIPrintPageRenderer
import platform.UIKit.UIView
import platform.Foundation.NSString
import platform.Foundation.create
import platform.UIKit.NSFontAttributeName
import platform.UIKit.NSForegroundColorAttributeName
import platform.UIKit.UIColor
import platform.UIKit.drawAtPoint
import platform.CoreGraphics.CGPointMake

/**
 * `UIPrintPageRenderer` subclass rendering the document hierarchy of [IosBookContainerImpl]
 * into real PDF pages for `UIGraphicsPDFRenderer` and `UIPrintInteractionController`.
 */
class UikitPrintPageRenderer(
    private val documentTitle: String,
    private val sourceView: UIView
) : UIPrintPageRenderer() {

    override fun numberOfPages(): Long = 1L

    override fun drawPageAtIndex(pageIndex: Long, inRect: CValue<CGRect>) {
        super.drawPageAtIndex(pageIndex, inRect)
        val headerAttrs = mapOf<Any?, Any?>(
            NSFontAttributeName to UIFont.boldSystemFontOfSize(12.0),
            NSForegroundColorAttributeName to UIColor.darkGrayColor
        )
        NSString.create(string = documentTitle).drawAtPoint(
            CGPointMake(36.0, 20.0),
            withAttributes = headerAttrs
        )
        val width = CGRectGetWidth(inRect).coerceAtLeast(100.0)
        sourceView.setNeedsLayout()
        sourceView.layoutIfNeeded()
        val ctx = platform.UIKit.UIGraphicsGetCurrentContext()
        if (ctx != null && width > 0.0) {
            sourceView.layer.renderInContext(ctx)
        }
    }
}
