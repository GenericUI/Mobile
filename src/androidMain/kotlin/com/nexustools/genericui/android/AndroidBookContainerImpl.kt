package com.nexustools.genericui.android

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.view.View
import android.view.ViewGroup
import com.nexustools.genericui.enums.document.PrintPageOrientation
import com.nexustools.genericui.enums.document.PrintPageSize
import com.nexustools.genericui.nativeimpl.document.BookContainerNativeImpl
import com.nexustools.genericui.style.css.PrecompiledStyleSheet
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Android (`android.print.PrintManager` + `PrintedPdfDocument`) implementation of [BookContainerNativeImpl].
 *
 * Uses Android's native system print dialog (`PrintManager.print(...)`), which natively supports
 * "Save as PDF" and physical/Wi-Fi printers, rendering attached book pages into [PrintedPdfDocument] pages.
 */
class AndroidBookContainerImpl(
    private val context: Context? = AndroidPreferenceStoreImpl.applicationContext ?: AndroidViewHandle.activeContext
) : AndroidBaseContainerNativeImpl(
    "android.widget.LinearLayout",
    "book-container"
), BookContainerNativeImpl<AndroidViewHandle> {

    private var currentPageSize: PrintPageSize = PrintPageSize.A4
    private var currentOrientation: PrintPageOrientation = PrintPageOrientation.PORTRAIT
    private var currentMarginMm: Float = 12f
    private var currentTitle: String = "Document"
    private var printCount: Int = 0
    private val activeStyleSheets = mutableListOf<PrecompiledStyleSheet>()

    override fun setPageSize(
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ) {
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        nativeWidget.attributes["data-page-size"] = pageSize.cssSize
        nativeWidget.attributes["data-page-orientation"] = orientation.cssOrientation
        nativeWidget.attributes["data-margin-mm"] = marginMm.toString()
    }

    override fun setDocumentTitle(title: String) {
        currentTitle = title
        nativeWidget.attributes["title"] = title
    }

    override fun addStyleSheet(styleSheet: PrecompiledStyleSheet) {
        if (styleSheet !in activeStyleSheets) {
            activeStyleSheets.add(styleSheet)
        }
    }

    override fun removeStyleSheet(styleSheet: PrecompiledStyleSheet) {
        activeStyleSheets.remove(styleSheet)
    }

    override fun clearStyleSheets() {
        activeStyleSheets.clear()
    }

    override fun printBook(
        documentTitle: String,
        pageSize: PrintPageSize,
        orientation: PrintPageOrientation,
        marginMm: Float
    ): Boolean {
        val resolvedTitle = documentTitle.ifBlank { currentTitle }.ifBlank { "Document" }
        currentTitle = resolvedTitle
        currentPageSize = pageSize
        currentOrientation = orientation
        currentMarginMm = marginMm
        printCount++
        nativeWidget.attributes["data-print-count"] = printCount.toString()
        nativeWidget.attributes["data-last-printed-title"] = resolvedTitle
        nativeWidget.attributes["data-printed"] = "true"

        val hostCtx = AndroidActivityRegistry.resolveActiveActivity()
            ?: context
            ?: AndroidViewHandle.activeContext
            ?: AndroidPreferenceStoreImpl.applicationContext

        val printManager = runCatching {
            hostCtx?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        }.getOrNull()

        if (hostCtx != null && printManager != null) {
            val landscape = orientation == PrintPageOrientation.LANDSCAPE
            val mediaSize = resolvePrintMediaSize(pageSize, landscape)
            val attrs = PrintAttributes.Builder()
                .setMediaSize(mediaSize)
                .setResolution(PrintAttributes.Resolution("pdf", "pdf", 300, 300))
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()

            val hostContainer = ensureAndroidView(hostCtx)
            val adapter = BookPrintDocumentAdapter(
                hostContext = hostCtx,
                docTitle = resolvedTitle,
                pageSize = pageSize,
                orientation = orientation,
                marginMm = marginMm,
                bookRootView = hostContainer
            )
            runCatching {
                printManager.print(resolvedTitle, adapter, attrs)
                return true
            }
        }
        return true
    }

    private fun resolvePrintMediaSize(pageSize: PrintPageSize, landscape: Boolean): PrintAttributes.MediaSize {
        val base = when (pageSize) {
            PrintPageSize.A4 -> PrintAttributes.MediaSize.ISO_A4
            PrintPageSize.A3 -> PrintAttributes.MediaSize.ISO_A3
            PrintPageSize.A5 -> PrintAttributes.MediaSize.ISO_A5
            PrintPageSize.LETTER -> PrintAttributes.MediaSize.NA_LETTER
            PrintPageSize.LEGAL -> PrintAttributes.MediaSize.NA_LEGAL
            PrintPageSize.TABLOID -> PrintAttributes.MediaSize.NA_TABLOID
            PrintPageSize.B4 -> PrintAttributes.MediaSize.ISO_B4
            PrintPageSize.B5 -> PrintAttributes.MediaSize.ISO_B5
        }
        return if (landscape) base.asLandscape() else base.asPortrait()
    }

    private class BookPrintDocumentAdapter(
        private val hostContext: Context,
        private val docTitle: String,
        private val pageSize: PrintPageSize,
        private val orientation: PrintPageOrientation,
        private val marginMm: Float,
        private val bookRootView: View?
    ) : PrintDocumentAdapter() {

        private var pdfDocument: PrintedPdfDocument? = null
        private var pageViews: List<View> = emptyList()

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            pdfDocument = PrintedPdfDocument(hostContext, newAttributes)
            pageViews = collectPageViews(bookRootView)
            val totalPages = max(1, pageViews.size)
            val info = PrintDocumentInfo.Builder(docTitle)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(totalPages)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument
            if (doc == null) {
                callback.onWriteFailed("Print document unavailable")
                return
            }
            try {
                val totalPages = max(1, pageViews.size)
                val writtenRanges = mutableListOf<PageRange>()
                for (index in 0 until totalPages) {
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    if (!isPageInRanges(index, pages)) continue
                    val pdfPage = doc.startPage(index)
                    renderPageCanvas(pdfPage.canvas, index, totalPages)
                    doc.finishPage(pdfPage)
                    writtenRanges.add(PageRange(index, index))
                }
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                val resultArray = if (writtenRanges.isEmpty()) pages else writtenRanges.toTypedArray()
                callback.onWriteFinished(resultArray)
            } catch (t: Throwable) {
                callback.onWriteFailed(t.message ?: "Failed writing PDF pages")
            } finally {
                runCatching { doc.close() }
                pdfDocument = null
            }
        }

        override fun onFinish() {
            runCatching { pdfDocument?.close() }
            pdfDocument = null
            super.onFinish()
        }

        private fun renderPageCanvas(canvas: Canvas, pageIndex: Int, totalPages: Int) {
            canvas.drawColor(Color.WHITE)
            val marginPt = ((marginMm.coerceAtLeast(0f) / 25.4f) * 72f)
            val contentW = (canvas.width - 2f * marginPt).coerceAtLeast(1f)
            val contentH = (canvas.height - 2f * marginPt).coerceAtLeast(1f)
            val view = pageViews.getOrNull(pageIndex)
            if (view != null) {
                val vw = view.width.takeIf { it > 0 } ?: pageSize.widthPx(orientation = orientation)
                val vh = view.height.takeIf { it > 0 } ?: pageSize.heightPx(orientation = orientation)
                view.measure(
                    View.MeasureSpec.makeMeasureSpec(vw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(vh, View.MeasureSpec.AT_MOST)
                )
                view.layout(0, 0, vw, max(vh, view.measuredHeight))
                val scale = minOf(contentW / vw.toFloat(), contentH / max(1, view.height).toFloat())
                canvas.save()
                canvas.translate(marginPt, marginPt)
                canvas.scale(scale, scale)
                view.draw(canvas)
                canvas.restore()
            } else {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.BLACK
                    textSize = 14f
                }
                val headerY = marginPt + 24f
                canvas.drawText(docTitle, marginPt, headerY, paint)
                paint.textSize = 10f
                paint.color = Color.DKGRAY
                canvas.drawText("Page ${pageIndex + 1} / $totalPages", marginPt, headerY + 20f, paint)
            }
        }

        private fun collectPageViews(root: View?): List<View> {
            val group = root as? ViewGroup ?: return emptyList()
            val out = mutableListOf<View>()
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.visibility != View.GONE) {
                    out.add(child)
                }
            }
            return out
        }

        private fun isPageInRanges(pageIndex: Int, ranges: Array<out PageRange>): Boolean {
            for (range in ranges) {
                if (pageIndex in range.start..range.end) return true
            }
            return false
        }
    }
}
