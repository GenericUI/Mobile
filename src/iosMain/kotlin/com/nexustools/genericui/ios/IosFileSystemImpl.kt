@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.nexustools.genericui.ios

import com.nexustools.genericui.apple.AppleFileSystemBase
import com.nexustools.genericui.apple.AppleUniformTypeHelper
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.nativeimpl.io.FileReadableNativeImpl
import com.nexustools.genericui.nativeimpl.io.FileWritableNativeImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.lastPathComponent
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIModalPresentationFormSheet
import platform.UIKit.popoverPresentationController
import platform.UniformTypeIdentifiers.UTTypeFolder
import platform.UniformTypeIdentifiers.UTTypeItem
import kotlin.coroutines.resume

/**
 * iOS / iPadOS implementation of [FileSystemNativeImpl] inheriting all POSIX/Foundation disk
 * and security-scoped stream I/O directly from `:apple`'s [AppleFileSystemBase], while
 * launching genuine `UIDocumentPickerViewController` panels for file open/save and folder access.
 */
class IosFileSystemImpl : AppleFileSystemBase() {

    private var activeDelegate: UikitDocumentPickerDelegate? = null

    override suspend fun openReadableFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): Pair<String, FileReadableNativeImpl>? {
        val urls = presentDocumentPickerForOpen(
            initialDirectory = initialDirectory,
            filters = filters,
            allowsMultiple = false,
            folderMode = false
        )
        val first = urls.firstOrNull() ?: return null
        return createSecurityScopedReadable(first)
    }

    override suspend fun openReadablesFromFileDialog(
        title: String,
        initialDirectory: String?,
        filters: List<FileTypeFilter>
    ): List<Pair<String, FileReadableNativeImpl>> {
        val urls = presentDocumentPickerForOpen(
            initialDirectory = initialDirectory,
            filters = filters,
            allowsMultiple = true,
            folderMode = false
        )
        return urls.mapNotNull { createSecurityScopedReadable(it) }
    }

    override suspend fun openWritableFromFileDialog(
        title: String,
        initialDirectory: String?,
        suggestedFileName: String,
        filters: List<FileTypeFilter>
    ): Pair<String, FileWritableNativeImpl>? {
        val picked = presentDocumentPickerForExport(
            initialDirectory = initialDirectory,
            suggestedFileName = suggestedFileName
        ) ?: return null
        return createSecurityScopedWritable(picked, append = false)
    }

    override suspend fun requestDirectoryAccess(initialPath: String?): String? {
        val urls = presentDocumentPickerForOpen(
            initialDirectory = initialPath,
            filters = emptyList(),
            allowsMultiple = false,
            folderMode = true
        )
        val folderUrl = urls.firstOrNull() ?: return super.requestDirectoryAccess(initialPath)
        return registerSecurityScopedUrl(folderUrl)
    }

    private suspend fun presentDocumentPickerForOpen(
        initialDirectory: String?,
        filters: List<FileTypeFilter>,
        allowsMultiple: Boolean,
        folderMode: Boolean
    ): List<NSURL> = suspendCancellableCoroutine { continuation ->
        val contentTypes = if (folderMode) {
            listOf(UTTypeFolder)
        } else {
            AppleUniformTypeHelper.utTypesForFilters(filters).ifEmpty { listOf(UTTypeItem) }
        }
        val picker = UIDocumentPickerViewController(forOpeningContentTypes = contentTypes, asCopy = false)
        picker.allowsMultipleSelection = allowsMultiple && !folderMode
        picker.modalPresentationStyle = UIModalPresentationFormSheet
        if (!initialDirectory.isNullOrBlank()) {
            picker.directoryURL = NSURL.fileURLWithPath(initialDirectory)
        }

        val delegate = UikitDocumentPickerDelegate(
            onPickedUrls = { urls ->
                activeDelegate = null
                if (continuation.isActive) continuation.resume(urls)
            },
            onCancelled = {
                activeDelegate = null
                if (continuation.isActive) continuation.resume(emptyList())
            }
        )
        activeDelegate = delegate
        picker.delegate = delegate

        UikitEnvironment.topMostViewController()?.view?.let { anchorView ->
            picker.popoverPresentationController?.sourceView = anchorView
            picker.popoverPresentationController?.sourceRect = anchorView.bounds
        }

        if (!UikitEnvironment.presentIfWindowAttached(picker, animated = true)) {
            activeDelegate = null
            if (continuation.isActive) continuation.resume(emptyList())
        }
    }

    private suspend fun presentDocumentPickerForExport(
        initialDirectory: String?,
        suggestedFileName: String
    ): NSURL? = suspendCancellableCoroutine { continuation ->
        val safeName = suggestedFileName.trim().ifEmpty { "untitled.txt" }
        val tempDir = NSURL.fileURLWithPath(NSTemporaryDirectory())
        val tempUrl = tempDir.URLByAppendingPathComponent(safeName) ?: run {
            continuation.resume(null)
            return@suspendCancellableCoroutine
        }
        val tempPath = tempUrl.path
        if (tempPath != null && !NSFileManager.defaultManager.fileExistsAtPath(tempPath)) {
            NSFileManager.defaultManager.createFileAtPath(tempPath, contents = NSData(), attributes = null)
        }

        val picker = UIDocumentPickerViewController(forExportingURLs = listOf(tempUrl), asCopy = true)
        picker.modalPresentationStyle = UIModalPresentationFormSheet
        if (!initialDirectory.isNullOrBlank()) {
            picker.directoryURL = NSURL.fileURLWithPath(initialDirectory)
        }

        val delegate = UikitDocumentPickerDelegate(
            onPickedUrls = { urls ->
                activeDelegate = null
                val dest = urls.firstOrNull()
                if (continuation.isActive) continuation.resume(dest)
            },
            onCancelled = {
                activeDelegate = null
                if (continuation.isActive) continuation.resume(null)
            }
        )
        activeDelegate = delegate
        picker.delegate = delegate

        UikitEnvironment.topMostViewController()?.view?.let { anchorView ->
            picker.popoverPresentationController?.sourceView = anchorView
            picker.popoverPresentationController?.sourceRect = anchorView.bounds
        }

        if (!UikitEnvironment.presentIfWindowAttached(picker, animated = true)) {
            activeDelegate = null
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
