@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIGraphicsPDFRenderer
import platform.UIKit.UIPrintInfo
import platform.UIKit.UIPrintInteractionController

/**
 * iOS / iPadOS printable document container backed by [UikitFlexContainerView], generating real
 * PDF page bytes via [UIGraphicsPDFRenderer] + [UikitPrintPageRenderer] and invoking AirPrint
 * ([UIPrintInteractionController]) when running inside an active `UIWindow` session.
 */
class IosBookContainerImpl : IosBaseContainerNativeImpl(
    nativeWidget = UikitViewHandle(UikitFlexContainerView(CGRectMake(0.0, 0.0, 612.0, 792.0))),
    componentClassPrefix = "book-container"
), BookContainerNativeImpl<UikitViewHandle> {

    private var currentTitle: String = "Document"
    private var currentPageSize: PrintPageSize = PrintPageSize.LETTER
    private var currentOrientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT
    private var currentMarginMm: Float = 15f
    private val styleSheets = mutableListOf<PrecompiledStyleSheet>()

    override fun setPageSize(
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ) {
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        val widthPt = pageSize.widthPt(orientation).toDouble()
        val heightPt = pageSize.heightPt(orientation).toDouble()
        uiView.setFrame(CGRectMake(0.0, 0.0, widthPt, heightPt))
        uiView.setNeedsLayout()
    }

    override fun setDocumentTitle(title: String) {
        currentTitle = title
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        if (!styleSheets.contains(styleSheet)) {
            styleSheets += styleSheet
        }
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet) {
        styleSheets.remove(styleSheet)
    }

    override fun clearStyleSheets() {
        styleSheets.clear()
    }

    override fun printBook(
        documentTitle: String,
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ): Boolean {
        setPageSize(pageSize, orientation, marginMm)
        setDocumentTitle(documentTitle)

        val widthPt = pageSize.widthPt(orientation).toDouble()
        val heightPt = pageSize.heightPt(orientation).toDouble()
        val pageBounds = CGRectMake(0.0, 0.0, widthPt, heightPt)
        val renderer = UikitPrintPageRenderer(documentTitle, uiView)
        val pdfRenderer = UIGraphicsPDFRenderer(bounds = pageBounds)
        val pdfData = pdfRenderer.PDFDataWithActions { context ->
            context?.beginPage()
            renderer.drawPageAtIndex(0L, pageBounds)
        }

        if (UikitEnvironment.safeSharedApplication() != null && UIPrintInteractionController.isPrintingAvailable()) {
            val printController = UIPrintInteractionController.sharedPrintController()
            val printInfo = UIPrintInfo.printInfo().apply {
                jobName = documentTitle
            }
            printController.printInfo = printInfo
            printController.printingItem = pdfData
            printController.presentAnimated(true, completionHandler = null)
        }
        return pdfData.length > 0uL
    }
}
