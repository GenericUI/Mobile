package com.nexustools.genericui.android

import android.app.Activity
import android.content.Intent
import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.mobile.system.MobileDocumentPickerBridge
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl

/**
 * Android Storage Access Framework (`Intent.ACTION_OPEN_DOCUMENT`, `Intent.ACTION_CREATE_DOCUMENT`,
 * and `Intent.ACTION_OPEN_DOCUMENT_TREE`) native file/folder browser implementation of [FileBrowserDialogNativeImpl].
 */
class AndroidFileBrowserDialogImpl(
    val documentPickerBridge: MobileDocumentPickerBridge = MobileDocumentPickerBridge()
) : FileBrowserDialogNativeImpl {

    private var activeRequestId: Int = -1
    private var lastRequest: FileBrowserRequest? = null
    private var lastCallback: FileBrowserCallback? = null

    /**
     * Last Android Storage Access Framework `Intent` action prepared for launching.
     */
    var lastIntentAction: String = Intent.ACTION_OPEN_DOCUMENT
        private set

    /**
     * Last `Intent.EXTRA_ALLOW_MULTIPLE` flag.
     */
    var lastAllowMultiple: Boolean = false
        private set

    /**
     * Last `Intent.EXTRA_MIME_TYPES` array derived from the active [FileBrowserRequest].
     */
    var lastMimeTypes: List<String> = listOf("*/*")
        private set

    override fun showFileBrowser(
        parentWindow: WindowNativeImpl<*>?,
        request: FileBrowserRequest,
        callback: FileBrowserCallback
    ) {
        lastRequest = request
        lastCallback = callback
        activeDialogInstance = this

        lastIntentAction = when (request.mode) {
            FileBrowserMode.OPEN_SINGLE_FILE, FileBrowserMode.OPEN_MULTIPLE_FILES -> Intent.ACTION_OPEN_DOCUMENT
            FileBrowserMode.SAVE_FILE -> Intent.ACTION_CREATE_DOCUMENT
            FileBrowserMode.SELECT_FOLDER -> Intent.ACTION_OPEN_DOCUMENT_TREE
        }
        lastAllowMultiple = request.mode == FileBrowserMode.OPEN_MULTIPLE_FILES
        lastMimeTypes = documentPickerBridge.resolveAndroidMimeTypes(request)
        activeRequestId = documentPickerBridge.registerRequest(request, callback)

        val host = activeHostActivity
        if (host != null) {
            val intent = Intent(lastIntentAction).apply {
                if (lastIntentAction != Intent.ACTION_OPEN_DOCUMENT_TREE) {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = lastMimeTypes.firstOrNull() ?: "*/*"
                    if (lastMimeTypes.size > 1) {
                        putExtra(Intent.EXTRA_MIME_TYPES, lastMimeTypes.toTypedArray())
                    }
                }
                if (lastAllowMultiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                val defaultName = request.defaultFileName
                if (!defaultName.isNullOrBlank()) {
                    putExtra(Intent.EXTRA_TITLE, defaultName)
                }
            }
            @Suppress("DEPRECATION")
            host.startActivityForResult(intent, REQUEST_CODE_FILE_BROWSER)
        }
    }

    /**
     * Delivers selected `content://` document or tree URIs from `ActivityResultCallback`.
     */
    fun deliverPickedUris(uris: List<String>) {
        val isDir = lastRequest?.mode == FileBrowserMode.SELECT_FOLDER
        val selectedFiles = uris.map { uri ->
            val name = uri.substringAfterLast('/').substringAfterLast(':').ifBlank { uri }
            SelectedFile(
                pathOrUri = uri,
                name = name,
                isDirectory = isDir
            )
        }
        if (activeRequestId >= 0 && selectedFiles.isNotEmpty()) {
            documentPickerBridge.completeWithFiles(activeRequestId, selectedFiles)
            activeRequestId = -1
        } else if (selectedFiles.isNotEmpty()) {
            val req = lastRequest
            val filter = req?.filters?.getOrNull(req.selectedFilterIndex) ?: req?.filters?.firstOrNull()
            lastCallback?.onFilesSelected(selectedFiles, filter)
        } else {
            cancelFromActivityResult()
        }
    }

    /**
     * Cancels the active Android SAF document picker session (`Activity.RESULT_CANCELED`).
     */
    fun cancelFromActivityResult() {
        if (activeRequestId >= 0) {
            documentPickerBridge.cancelRequest(activeRequestId)
            activeRequestId = -1
        } else {
            lastCallback?.onCancelled()
        }
    }

    override fun dispose() {
        if (activeDialogInstance === this) {
            activeDialogInstance = null
        }
        lastRequest = null
        lastCallback = null
    }

    companion object {
        const val REQUEST_CODE_FILE_BROWSER = 0x4755
        var activeHostActivity: Activity? = null
        var activeDialogInstance: AndroidFileBrowserDialogImpl? = null

        fun dispatchActivityResult(activity: Activity, requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode != REQUEST_CODE_FILE_BROWSER) return
            val dialog = activeDialogInstance ?: return
            if (resultCode == Activity.RESULT_OK && data != null) {
                val uris = mutableListOf<String>()
                val clip = data.clipData
                if (clip != null) {
                    for (i in 0 until clip.itemCount) {
                        clip.getItemAt(i)?.uri?.toString()?.let { uris.add(it) }
                    }
                } else {
                    data.data?.toString()?.let { uris.add(it) }
                }
                dialog.deliverPickedUris(uris)
            } else {
                dialog.cancelFromActivityResult()
            }
        }
    }
}
