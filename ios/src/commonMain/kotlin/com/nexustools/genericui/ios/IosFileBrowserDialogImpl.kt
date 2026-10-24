package com.nexustools.genericui.ios

import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.mobile.system.MobileDocumentPickerBridge
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * iOS `UIDocumentPickerViewController` implementation of [FileBrowserDialogNativeImpl].
 *
 * Supports both file selection (`OPEN_SINGLE_FILE`, `OPEN_MULTIPLE_FILES`, `SAVE_FILE`) and folder selection
 * (`SELECT_FOLDER` via `UTTypeFolder` / `"public.folder"`), translating `FileTypeFilter` rules into iOS
 * Uniform Type Identifiers (`UTType`) via [MobileDocumentPickerBridge] and routing `documentPicker(_:didPickDocumentsAt:)`
 * / `documentPickerWasCancelled(_:)` to [FileBrowserCallback.onFilesSelected] and [FileBrowserCallback.onCancelled].
 */
class IosFileBrowserDialogImpl(
    val documentPickerBridge: MobileDocumentPickerBridge = MobileDocumentPickerBridge()
) : FileBrowserDialogNativeImpl {

    var lastRequestId: Int = -1
        private set
    var lastResolvedUtis: List<String> = emptyList()
        private set
    var allowsMultipleSelection: Boolean = false
        private set
    var isDirectoryPickerMode: Boolean = false
        private set

    override fun showFileBrowser(
        parentWindow: WindowNativeImpl<*>?,
        request: FileBrowserRequest,
        callback: FileBrowserCallback
    ) {
        isDirectoryPickerMode = request.mode == FileBrowserMode.SELECT_FOLDER
        allowsMultipleSelection = request.mode == FileBrowserMode.OPEN_MULTIPLE_FILES
        lastResolvedUtis = documentPickerBridge.resolveIosUniformTypeIdentifiers(request)
        lastRequestId = documentPickerBridge.registerRequest(request, callback)
    }

    /**
     * Invoked by the `UIDocumentPickerDelegate.documentPicker(_:didPickDocumentsAt:)` callback when
     * the user selects one or more security-scoped `NSURL`s in the native iOS Files sheet.
     */
    fun onDocumentPickerDidPickUrls(requestId: Int = lastRequestId, pickedFiles: List<SelectedFile>) {
        if (pickedFiles.isEmpty()) {
            documentPickerBridge.cancelRequest(requestId)
        } else {
            documentPickerBridge.completeWithFiles(requestId, pickedFiles)
        }
    }

    /**
     * Invoked by `UIDocumentPickerDelegate.documentPickerWasCancelled(_:)` when the user dismisses
     * the native iOS Files sheet without picking any item.
     */
    fun onDocumentPickerWasCancelled(requestId: Int = lastRequestId) {
        documentPickerBridge.cancelRequest(requestId)
    }

    override fun dispose() {
        if (lastRequestId != -1) {
            documentPickerBridge.cancelRequest(lastRequestId)
            lastRequestId = -1
        }
    }
}
