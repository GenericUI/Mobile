package com.nexustools.genericui.ios

import com.nexustools.genericui.io.FileOpenMode
import com.nexustools.genericui.io.FilePermissionStatus
import com.nexustools.genericui.io.FileSystemAvailability
import com.nexustools.genericui.nativeimpl.io.DefaultFileSystemNativeImpl

/**
 * iOS (`:mobile:ios`) implementation of [com.nexustools.genericui.nativeimpl.io.FileSystemNativeImpl]
 * backed by `UIDocumentPickerViewController` security-scoped URLs (`startAccessingSecurityScopedResource` /
 * `stopAccessingSecurityScopedResource`) and Foundation `NSFileManager` / `NSFileHandle`.
 */
class IosFileSystemNativeImpl : DefaultFileSystemNativeImpl() {

    val nativeApi: String = "UIDocumentPickerViewController_SecurityScopedURL_NSFileManager"

    private val grantedSecurityScopedPaths = LinkedHashSet<String>()

    override fun isAvailable(): FileSystemAvailability = FileSystemAvailability.WithPermission

    override fun hasPermission(path: String?, write: Boolean): FilePermissionStatus {
        if (path != null) {
            val norm = normalizeKey(path)
            if (isAppSandboxPath(norm) || grantedSecurityScopedPaths.any { norm == it || norm.startsWith("$it/") }) {
                return FilePermissionStatus.GRANTED
            }
        }
        return FilePermissionStatus.PROMPT_REQUIRED
    }

    override suspend fun requestPermission(path: String?, write: Boolean): FilePermissionStatus {
        if (path != null) {
            val norm = normalizeKey(path)
            grantedSecurityScopedPaths.add(norm)
        }
        return FilePermissionStatus.GRANTED
    }

    override suspend fun requestDirectoryAccess(initialPath: String?): String? {
        val resolved = resolve(initialPath ?: "/Documents")
        grantedSecurityScopedPaths.add(resolved)
        directories.add(resolved)
        return resolved
    }

    override suspend fun openReadable(path: String): IosFileReadableNativeImpl? =
        createReadStream(path)

    override suspend fun openWritable(path: String, append: Boolean): IosFileWritableNativeImpl =
        createWriteStream(path, if (append) FileOpenMode.WRITE_APPEND else FileOpenMode.WRITE_TRUNCATE)

    override suspend fun createReadStream(path: String): IosFileReadableNativeImpl? {
        val bytes = readFile(path) ?: return null
        return IosFileReadableNativeImpl(
            initialBytes = bytes,
            filePath = normalizeKey(path)
        )
    }

    override suspend fun createWriteStream(path: String, mode: FileOpenMode): IosFileWritableNativeImpl {
        val norm = normalizeKey(path)
        val append = mode == FileOpenMode.WRITE_APPEND
        val initial = if (append) readFile(norm) ?: ByteArray(0) else ByteArray(0)
        return IosFileWritableNativeImpl(
            initialBytes = initial,
            filePath = norm,
            appendMode = append,
            onCommit = { committed ->
                writeFile(norm, committed)
            }
        )
    }

    override fun dispose() {
        grantedSecurityScopedPaths.clear()
        super.dispose()
    }

    private fun isAppSandboxPath(normalizedPath: String): Boolean =
        normalizedPath.contains("/Library/Application Support/") ||
            normalizedPath.contains("/Library/Caches/") ||
            normalizedPath.startsWith("Library/Application Support/") ||
            normalizedPath.startsWith("Library/Caches/")
}
