package com.nexustools.genericui.ios

import platform.Foundation.NSURL
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.darwin.NSObject

/**
 * `UIDocumentPickerDelegate` bridge resolving picked security-scoped `NSURL`s or cancellation.
 */
class UikitDocumentPickerDelegate(
    private val onPickedUrls: (List<NSURL>) -> Unit,
    private val onCancelled: () -> Unit
) : NSObject(), UIDocumentPickerDelegateProtocol {

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentsAtURLs: List<*>) {
        val urls = didPickDocumentsAtURLs.filterIsInstance<NSURL>()
        onPickedUrls(urls)
    }

    override fun documentPicker(controller: UIDocumentPickerViewController, didPickDocumentAtURL: NSURL) {
        onPickedUrls(listOf(didPickDocumentAtURL))
    }

    override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
        onCancelled()
    }
}
