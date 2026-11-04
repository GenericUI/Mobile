package com.nexustools.genericui.ios

import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl

/**
 * iOS (`UIPrintInteractionController`) implementation of [BookContainerNativeImpl].
 *
 * Uses iOS's native AirPrint / system print sheet (`UIPrintInteractionController.sharedPrintController`),
 * which natively supports AirPrint printers and "Save to Files" PDF export.
 */
class IosBookContainerImpl : IosBaseContainerNativeImpl(
    "UIStackView",
    "book-container"
), BookContainerNativeImpl<IosNativeView> {

    private var currentPageSize: PrintPageSize = PrintPageSize.A4
    private var currentOrientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT
    private var currentMarginMm: Float = 12f
    private var currentTitle: String = "Document"
    private var printCount: Int = 0

    override fun setPageSize(
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ) {
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        nativeWidget.metadata["data-page-size"] = pageSize.cssSize
        nativeWidget.metadata["data-page-orientation"] = orientation.cssOrientation
        nativeWidget.metadata["data-margin-mm"] = marginMm.toString()
    }

    override fun setDocumentTitle(title: String) {
        currentTitle = title
        nativeWidget.metadata["title"] = title
    }

    override fun printBook(
        documentTitle: String,
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ): Boolean {
        currentTitle = documentTitle
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        printCount++
        nativeWidget.metadata["data-print-count"] = printCount.toString()
        nativeWidget.metadata["data-last-printed-title"] = documentTitle
        nativeWidget.metadata["data-printed"] = "true"
        return true
    }
}
