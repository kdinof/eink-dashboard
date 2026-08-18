package com.eink.dashboard.modules.taskforge.data

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.system.ErrnoException
import android.system.Os
import com.eink.dashboard.modules.taskforge.model.TaskSourceRef
import java.io.FileNotFoundException

data class TaskForgeFile(val bytes: ByteArray, val name: String, val canWrite: Boolean)
data class PersistedTaskForgeFile(val uri: String, val name: String, val canWrite: Boolean)

sealed interface FileCompletionResult {
    data object Done : FileCompletionResult
    data object Conflict : FileCompletionResult
    data object ReadOnly : FileCompletionResult
    data object Unavailable : FileCompletionResult
}

interface TaskForgeFileStore {
    fun persist(uri: Uri): PersistedTaskForgeFile
    fun release(uri: String)
    fun read(uri: String): TaskForgeFile

    /**
     * Flip the checkbox byte of the task at [source] to `x`. [expectedMarker] is
     * the status character the caller last saw (`' '`, `'/'`, `'>'`, `'!'`); the
     * write is refused as a conflict if the file no longer holds that byte.
     */
    fun complete(uri: String, source: TaskSourceRef, expectedMarker: Char): FileCompletionResult
}

/** SAF-backed access. Completion patches one ASCII status byte; it never truncates the document. */
class AndroidTaskForgeFileStore(context: Context) : TaskForgeFileStore {
    private val resolver = context.applicationContext.contentResolver

    override fun persist(uri: Uri): PersistedTaskForgeFile {
        runCatching {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            // Some providers deliberately grant a durable read-only document.
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }.getOrThrow()
        val name = displayName(uri) ?: "TaskForge.md"
        require(name.endsWith(".md", ignoreCase = true)) { "Select a Markdown file" }
        return PersistedTaskForgeFile(uri.toString(), name, supportsWrite(uri))
    }

    override fun release(uri: String) {
        runCatching {
            resolver.releasePersistableUriPermission(
                Uri.parse(uri),
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }.recoverCatching {
            resolver.releasePersistableUriPermission(Uri.parse(uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    override fun read(uri: String): TaskForgeFile {
        val parsed = Uri.parse(uri)
        val bytes = resolver.openInputStream(parsed)?.use { it.readBytes() } ?: throw FileNotFoundException(uri)
        return TaskForgeFile(bytes, displayName(parsed) ?: "TaskForge.md", supportsWrite(parsed))
    }

    override fun complete(uri: String, source: TaskSourceRef, expectedMarker: Char): FileCompletionResult {
        val descriptor = try {
            resolver.openFileDescriptor(Uri.parse(uri), "rw") ?: return FileCompletionResult.Unavailable
        } catch (_: SecurityException) {
            return FileCompletionResult.Unavailable
        } catch (_: FileNotFoundException) {
            return FileCompletionResult.ReadOnly
        } catch (_: RuntimeException) {
            // Providers surface "will never open this for writing" as unchecked
            // exceptions — e.g. MediaDocumentsProvider throws
            // IllegalArgumentException("Media is read-only").
            return FileCompletionResult.ReadOnly
        }
        descriptor.use { pfd ->
            return try {
                val size = Os.fstat(pfd.fileDescriptor).st_size
                if (size < 0 || size > MAX_FILE_SIZE) return FileCompletionResult.Unavailable
                val current = ByteArray(size.toInt())
                var read = 0
                while (read < current.size) {
                    val count = Os.pread(pfd.fileDescriptor, current, read, current.size - read, read.toLong())
                    if (count <= 0) break
                    read += count
                }
                if (read != current.size) return FileCompletionResult.Conflict
                val offset = TaskForgeParser.resolveCheckboxOffset(current, source)
                    ?: return FileCompletionResult.Conflict
                if (offset < 0 || offset >= current.size || current[offset.toInt()] != expectedMarker.code.toByte()) {
                    return FileCompletionResult.Conflict
                }
                val written = Os.pwrite(pfd.fileDescriptor, byteArrayOf('x'.code.toByte()), 0, 1, offset)
                if (written != 1) return FileCompletionResult.Unavailable
                Os.fsync(pfd.fileDescriptor)
                val verify = ByteArray(1)
                val verified = Os.pread(pfd.fileDescriptor, verify, 0, 1, offset)
                if (verified == 1 && verify[0] == 'x'.code.toByte()) FileCompletionResult.Done
                else FileCompletionResult.Unavailable
            } catch (_: ErrnoException) {
                FileCompletionResult.ReadOnly
            } catch (_: SecurityException) {
                FileCompletionResult.Unavailable
            }
        }
    }

    private fun supportsWrite(uri: Uri): Boolean = try {
        resolver.openFileDescriptor(uri, "rw")?.use { true } ?: false
    } catch (_: Exception) {
        false
    }

    private fun displayName(uri: Uri): String? = resolver.query(
        uri,
        arrayOf(OpenableColumns.DISPLAY_NAME),
        null,
        null,
        null,
    )?.use { cursor -> cursor.readDisplayName() }

    private fun Cursor.readDisplayName(): String? =
        if (moveToFirst()) getString(getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null

    private companion object { const val MAX_FILE_SIZE = 10L * 1024 * 1024 }
}
