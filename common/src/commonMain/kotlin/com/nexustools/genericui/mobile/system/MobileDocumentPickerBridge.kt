package com.nexustools.genericui.mobile.system

import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Shared asynchronous mobile document/folder picker request registry and MIME/UTI translator
 * used by `:android` (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`)
 * and `:ios` (`UIDocumentPickerViewController`).
 */
class MobileDocumentPickerBridge {
    private data class PendingPickerEntry(
        val requestId: Int,
        val request: FileBrowserRequest,
        val callback: FileBrowserCallback?
    )

    private var nextRequestId: Int = 4100
    private val pendingRequests = linkedMapOf<Int, PendingPickerEntry>()

    fun registerRequest(request: FileBrowserRequest, callback: FileBrowserCallback? = null): Int {
        val id = nextRequestId++
        pendingRequests[id] = PendingPickerEntry(id, request, callback)
        return id
    }

    fun getPendingRequest(requestId: Int): FileBrowserRequest? =
        pendingRequests[requestId]?.request

    fun completeWithFiles(requestId: Int, files: List<SelectedFile>): Boolean {
        val entry = pendingRequests.remove(requestId) ?: return false
        val selectedFilter = entry.request.filters.getOrNull(entry.request.selectedFilterIndex)
            ?: entry.request.filters.firstOrNull()
        entry.callback?.onFilesSelected(files, selectedFilter)
        return true
    }

    fun cancelRequest(requestId: Int): Boolean {
        val entry = pendingRequests.remove(requestId) ?: return false
        entry.callback?.onCancelled()
        return true
    }

    fun resolveAndroidMimeTypes(request: FileBrowserRequest): List<String> =
        FileTypeFilter.toAndroidMimeTypes(request.filters)

    fun resolveIosUniformTypeIdentifiers(request: FileBrowserRequest): List<String> {
        if (request.mode == FileBrowserMode.SELECT_FOLDER) {
            return listOf("public.folder")
        }
        if (request.filters.isEmpty()) {
            return listOf("public.item", "public.content", "public.data")
        }
        val utis = linkedSetOf<String>()
        for (filter in request.filters) {
            for (uti in filter.macOsUniformTypeIdentifiers) {
                if (uti.isNotBlank()) utis.add(uti.trim())
            }
            for (ext in filter.normalizedExtensions) {
                utis.add(extensionToUti(ext))
            }
            for (mime in filter.mimeTypes) {
                utis.add(mimeToUti(mime))
            }
        }
        return if (utis.isEmpty()) listOf("public.item") else utis.toList()
    }

    private fun extensionToUti(ext: String): String = when (ext.lowercase()) {
        "png" -> "public.png"
        "jpg", "jpeg" -> "public.jpeg"
        "gif" -> "com.compuserve.gif"
        "webp" -> "org.webmproject.webp"
        "svg" -> "public.svg-image"
        "pdf" -> "com.adobe.pdf"
        "json" -> "public.json"
        "xml" -> "public.xml"
        "txt", "md", "srt", "vtt" -> "public.plain-text"
        "mp4", "m4v" -> "public.mpeg-4"
        "mov" -> "com.apple.quicktime-movie"
        "mp3" -> "public.mp3"
        "wav" -> "com.microsoft.waveform-audio"
        "zip", "ora" -> "public.zip-archive"
        else -> "public.data"
    }

    private fun mimeToUti(mime: String): String {
        val lower = mime.trim().lowercase()
        return when {
            lower == "image/png" -> "public.png"
            lower == "image/jpeg" -> "public.jpeg"
            lower.startsWith("image/") -> "public.image"
            lower.startsWith("video/") -> "public.movie"
            lower.startsWith("audio/") -> "public.audio"
            lower.startsWith("text/") -> "public.text"
            lower == "application/pdf" -> "com.adobe.pdf"
            lower == "application/json" -> "public.json"
            else -> "public.data"
        }
    }
}
