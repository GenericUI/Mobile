package com.nexustools.genericui.mobile.system

import com.nexustools.genericui.component.dialog.FileBrowserCallback
import com.nexustools.genericui.component.dialog.FileBrowserRequest
import com.nexustools.genericui.component.dialog.FileTypeFilter
import com.nexustools.genericui.component.dialog.SelectedFile
import com.nexustools.genericui.enums.dialog.FileBrowserMode

/**
 * Shared asynchronous mobile document/folder picker request registry and MIME/UTI translator
 * used by Android (`Intent.ACTION_OPEN_DOCUMENT` / `ACTION_CREATE_DOCUMENT` / `ACTION_OPEN_DOCUMENT_TREE`)
 * and Apple iOS/iPadOS (`UIDocumentPickerViewController`).
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

    internal fun extensionToUti(ext: String): String {
        val clean = ext.trim().removePrefix("*.").removePrefix(".").lowercase()
        return when (clean) {
            "*", "" -> "public.item"
            "png" -> "public.png"
            "jpg", "jpeg" -> "public.jpeg"
            "gif" -> "com.compuserve.gif"
            "webp" -> "org.webmproject.webp"
            "svg" -> "public.svg-image"
            "bmp" -> "com.microsoft.bmp"
            "ico" -> "com.microsoft.ico"
            "tif", "tiff" -> "public.tiff"
            "heic" -> "public.heic"
            "heif" -> "public.heif"
            "pdf" -> "com.adobe.pdf"
            "json" -> "public.json"
            "xml" -> "public.xml"
            "html", "htm" -> "public.html"
            "css", "scss", "js", "mjs", "ts", "tsx", "jsx", "kt", "kts", "java", "py", "rb", "swift",
            "c", "h", "cpp", "hpp", "rs", "sh", "yaml", "yml", "toml", "ini", "conf", "log" -> "public.source-code"
            "csv", "tsv" -> "public.comma-separated-values-text"
            "rtf" -> "public.rtf"
            "txt", "md", "srt", "vtt" -> "public.plain-text"
            "mp4", "m4v" -> "public.mpeg-4"
            "mov" -> "com.apple.quicktime-movie"
            "avi" -> "public.avi"
            "webm" -> "org.webmproject.webm"
            "mp3" -> "public.mp3"
            "m4a", "aac" -> "public.mpeg-4-audio"
            "wav" -> "com.microsoft.waveform-audio"
            "flac" -> "org.xiph.flac"
            "ttf" -> "public.truetype-ttf-font"
            "otf" -> "public.opentype-font"
            "zip", "ora" -> "public.zip-archive"
            "gz", "gzip", "tgz" -> "org.gnu.gnu-zip-archive"
            "tar" -> "public.tar-archive"
            else -> "public.data"
        }
    }

    internal fun mimeToUti(mime: String): String {
        val lower = mime.substringBefore(';').trim().lowercase()
        return when {
            lower == "*/*" || lower.isEmpty() -> "public.item"
            lower == "image/png" -> "public.png"
            lower == "image/jpeg" -> "public.jpeg"
            lower == "image/gif" -> "com.compuserve.gif"
            lower == "image/webp" -> "org.webmproject.webp"
            lower == "image/svg+xml" -> "public.svg-image"
            lower == "image/bmp" || lower == "image/x-ms-bmp" -> "com.microsoft.bmp"
            lower == "image/tiff" -> "public.tiff"
            lower == "image/heic" -> "public.heic"
            lower == "image/heif" -> "public.heif"
            lower.startsWith("image/") -> "public.image"
            lower == "video/mp4" -> "public.mpeg-4"
            lower == "video/quicktime" -> "com.apple.quicktime-movie"
            lower.startsWith("video/") -> "public.movie"
            lower == "audio/mpeg" || lower == "audio/mp3" -> "public.mp3"
            lower == "audio/wav" || lower == "audio/x-wav" -> "com.microsoft.waveform-audio"
            lower.startsWith("audio/") -> "public.audio"
            lower == "text/csv" -> "public.comma-separated-values-text"
            lower == "text/html" -> "public.html"
            lower == "text/plain" -> "public.plain-text"
            lower.startsWith("text/") -> "public.text"
            lower == "application/pdf" -> "com.adobe.pdf"
            lower == "application/json" -> "public.json"
            lower == "application/xml" || lower == "text/xml" -> "public.xml"
            lower == "application/rtf" -> "public.rtf"
            lower == "application/zip" -> "public.zip-archive"
            lower == "application/gzip" || lower == "application/x-gzip" -> "org.gnu.gnu-zip-archive"
            else -> "public.data"
        }
    }
}
