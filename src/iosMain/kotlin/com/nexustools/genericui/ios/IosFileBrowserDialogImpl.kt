@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleFileReadableNativeImpl
import com.nexustools.genericui.apple.AppleFileWritableNativeImpl
import com.nexustools.genericui.apple.AppleUniformTypeHelper
import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode
import com.nexustools.genericui.nativeimpl.dialog.FileBrowserDialogNativeImpl
import com.nexustools.genericui.nativeimpl.shell.WindowNativeImpl
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSNumber
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.dataWithBytes
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.lastPathComponent
import platform.Foundation.writeToURL
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem

/**
 * Native UIKit `UIDocumentPickerViewController` implementation of [FileBrowserDialogNativeImpl],
 * using `:apple`'s [AppleUniformTypeHelper] UTType resolution, security-scoped URLs, and iPad
 * popover anchoring.
 */
class IosFileBrowserDialogImpl : FileBrowserDialogNativeImpl {
    override var isDisposed: Boolean = false
    private var activeDelegate: UikitDocumentPickerDelegate? = null

    override fun showFileBrowser(
        parentWindow: WindowNativeImpl<*>?,
        request: FileBrowserRequest,
        callback: FileBrowserCallback
    ) {
        val picker: UIDocumentPickerViewController = when (request.mode) {
            FileBrowserMode.SELECT_FOLDER -> {
                UIDocumentPickerViewController(forOpeningContentTypes = listOf(UTTypeFolder), asCopy = false)
            }
            FileBrowserMode.SAVE_FILE -> {
                val safeName = (request.suggestedFileName ?: request.defaultFileName)?.trim()?.ifEmpty { null }
                    ?: "untitled.txt"
                val tempUrl = NSURL.fileURLWithPath(NSTemporaryDirectory()).URLByAppendingPathComponent(safeName)
                if (tempUrl == null) {
                    callback.onCancelled()
                    return
                }
                val tempPath = tempUrl.path
                if (tempPath != null && !NSFileManager.defaultManager.fileExistsAtPath(tempPath)) {
                    NSFileManager.defaultManager.createFileAtPath(tempPath, contents = NSData(), attributes = null)
                }
                UIDocumentPickerViewController(forExportingURLs = listOf(tempUrl), asCopy = true)
            }
            FileBrowserMode.OPEN_SINGLE_FILE,
            FileBrowserMode.OPEN_MULTIPLE_FILES -> {
                val types = AppleUniformTypeHelper.utTypesForFilters(request.filters).ifEmpty { listOf(UTTypeItem) }
                UIDocumentPickerViewController(forOpeningContentTypes = types, asCopy = false).apply {
                    allowsMultipleSelection = request.mode == FileBrowserMode.OPEN_MULTIPLE_FILES
                }
            }
        }

        picker.modalPresentationStyle = UIModalPresentationFormSheet
        request.initialDirectory?.takeIf { it.isNotBlank() }?.let { dir ->
            picker.directoryURL = NSURL.fileURLWithPath(dir)
        }

        val delegate = UikitDocumentPickerDelegate(
            onPickedUrls = { urls ->
                activeDelegate = null
                if (urls.isEmpty()) {
                    callback.onCancelled()
                } else {
                    val selected = urls.map { url -> buildSelectedFile(url, request.mode == FileBrowserMode.SELECT_FOLDER) }
                    val filter = request.filters.getOrNull(request.activeFilterIndex)
                    callback.onFilesSelected(selected, filter)
                }
            },
            onCancelled = {
                activeDelegate = null
                callback.onCancelled()
            }
        )
        activeDelegate = delegate
        picker.delegate = delegate

        val ownerVc: UIViewController? = (parentWindow?.nativeWidget as? UikitViewHandle)?.uiViewController
            ?: UikitEnvironment.topMostViewController()
        if (ownerVc != null) {
            picker.popoverPresentationController?.let { pop ->
                pop.sourceView = ownerVc.view
                pop.sourceRect = ownerVc.view.bounds
            }
            if (ownerVc.view.window != null || UikitEnvironment.registeredKeyWindow != null) {
                ownerVc.presentViewController(picker, animated = true, completion = null)
                return
            }
        }
        if (!UikitEnvironment.presentIfWindowAttached(picker, animated = true)) {
            activeDelegate = null
            callback.onCancelled()
        }
    }

    private fun buildSelectedFile(url: NSURL, isDir: Boolean): SelectedFile {
        val path = url.path ?: url.absoluteString ?: ""
        val name = url.lastPathComponent ?: path.substringAfterLast('/')
        var fileSize = 0L
        if (!isDir && path.isNotEmpty()) {
            val started = url.startAccessingSecurityScopedResource()
            try {
                val attrs = NSFileManager.defaultManager.attributesOfItemAtPath(path, error = null)
                fileSize = (attrs?.get(NSFileSize) as? NSNumber)?.longLongValue ?: 0L
            } finally {
                if (started) url.stopAccessingSecurityScopedResource()
            }
        }
        return SelectedFile(
            name = name,
            pathOrUri = path,
            sizeBytes = fileSize,
            isDirectory = isDir,
            bytesReader = {
                val started = url.startAccessingSecurityScopedResource()
                try {
                    val data = NSData.dataWithContentsOfURL(url)
                    UikitEnvironment.nsDataToBytes(data)
                } finally {
                    if (started) url.stopAccessingSecurityScopedResource()
                }
            },
            bytesWriter = { bytes ->
                val started = url.startAccessingSecurityScopedResource()
                try {
                    val data = bytes.usePinned { pinned ->
                        if (bytes.isEmpty()) NSData() else NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
                    }
                    data.writeToURL(url, atomically = true)
                } finally {
                    if (started) url.stopAccessingSecurityScopedResource()
                }
            },
            readableOpener = {
                if (path.isNotEmpty()) AppleFileReadableNativeImpl(path, securityScopedUrl = url) else null
            },
            writableOpener = { append ->
                AppleFileWritableNativeImpl(path, appendMode = append, securityScopedUrl = url)
            }
        )
    }

    override fun dispose() {
        isDisposed = true
        activeDelegate = null
    }
}
