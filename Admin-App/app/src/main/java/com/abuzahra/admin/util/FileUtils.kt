package com.abuzahra.admin.util

import com.abuzahra.admin.R
import com.abuzahra.admin.data.model.RemoteFile

/**
 * FileUtils — centralized helpers for the file manager UI:
 *
 * - [formatFileSize] formats a byte count as a human-readable string
 *   (`B`, `KB`, `MB`, `GB`).
 * - [iconForFile] / [iconForExtension] return the appropriate vector
 *   drawable for a file's type (image, video, audio, document, archive,
 *   APK, …).
 * - [iconTintForFile] / [iconTintForExtension] return the color resource
 *   used to tint the icon (matching the file's category).
 * - [extensionOf] derives the lowercase extension from a [RemoteFile]
 *   (preferring the server-provided `extension`, then the filename).
 * - [isImageExtension] / [isVideoExtension] / [isAudioExtension] /
 *   [isDocumentExtension] / [isArchiveExtension] / [isApkExtension]
 *   classify an extension string.
 *
 * These helpers consolidate logic that was previously duplicated in
 * [com.abuzahra.admin.ui.files.FileListAdapter] and
 * [com.abuzahra.admin.data.model.RemoteFile.displaySize].
 */
object FileUtils {

    // ── Size formatting ────────────────────────────────────────────────

    /**
     * Format a byte count as a human-readable string.
     *
     * Examples: `512 B`, `1.5 KB`, `12.3 MB`, `4.27 GB`.
     * Returns `"—"` for non-positive inputs (so the UI can show a dash
     * instead of an ugly `0 B` for missing/unknown sizes).
     */
    fun formatFileSize(bytes: Long): String {
        if (bytes <= 0) return "—"
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format("%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format("%.1f MB", mb)
        val gb = mb / 1024.0
        return String.format("%.2f GB", gb)
    }

    // ── Extension classification ───────────────────────────────────────

    /** Image extensions the app knows how to render as a thumbnail. */
    val IMAGE_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "gif", "webp", "bmp"
    )

    /** Video extensions the app can extract a frame thumbnail from. */
    val VIDEO_EXTENSIONS = setOf(
        "mp4", "avi", "mkv", "mov", "3gp", "webm", "flv", "m4v"
    )

    /** Audio extensions — shown with the audio icon. */
    val AUDIO_EXTENSIONS = setOf(
        "mp3", "wav", "ogg", "flac", "aac", "m4a", "opus", "amr"
    )

    /** Document extensions — shown with the document icon. */
    val DOCUMENT_EXTENSIONS = setOf(
        "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
        "txt", "log", "csv", "json", "xml", "rtf", "md"
    )

    /** Archive extensions — shown with the archive icon. */
    val ARCHIVE_EXTENSIONS = setOf(
        "zip", "rar", "7z", "tar", "gz", "bz2", "xz"
    )

    fun isImageExtension(ext: String): Boolean =
        ext.lowercase() in IMAGE_EXTENSIONS

    fun isVideoExtension(ext: String): Boolean =
        ext.lowercase() in VIDEO_EXTENSIONS

    fun isAudioExtension(ext: String): Boolean =
        ext.lowercase() in AUDIO_EXTENSIONS

    fun isDocumentExtension(ext: String): Boolean =
        ext.lowercase() in DOCUMENT_EXTENSIONS

    fun isArchiveExtension(ext: String): Boolean =
        ext.lowercase() in ARCHIVE_EXTENSIONS

    fun isApkExtension(ext: String): Boolean =
        ext.lowercase() == "apk"

    // ── RemoteFile helpers ─────────────────────────────────────────────

    /**
     * Derive the lowercase file extension from a [RemoteFile], preferring
     * the server-provided `extension` field, then falling back to parsing
     * the file's `name` / `filename`.
     */
    fun extensionOf(file: RemoteFile): String {
        file.extension.takeIf { it.isNotBlank() }?.let { return it.lowercase() }
        val name = file.displayName.ifBlank { file.name }.ifBlank { file.filename }
        val lastDot = name.lastIndexOf('.')
        return if (lastDot in 0 until name.length - 1) {
            name.substring(lastDot + 1).lowercase()
        } else ""
    }

    // ── Icon lookup ────────────────────────────────────────────────────

    /** Returns the drawable resource id for the given file. */
    fun iconForFile(file: RemoteFile): Int {
        return if (file.isDirectory) R.drawable.ic_folder
        else iconForExtension(extensionOf(file))
    }

    /** Returns the drawable resource id for the given extension. */
    fun iconForExtension(ext: String): Int {
        val e = ext.lowercase()
        return when {
            isImageExtension(e) -> R.drawable.ic_file_image
            isVideoExtension(e) -> R.drawable.ic_file_video
            isAudioExtension(e) -> R.drawable.ic_file_audio
            isDocumentExtension(e) -> R.drawable.ic_file_document
            isApkExtension(e) -> R.drawable.ic_file_apk
            isArchiveExtension(e) -> R.drawable.ic_file_archive
            else -> R.drawable.ic_file
        }
    }

    /** Returns the color resource id used to tint the file's icon. */
    fun iconTintForFile(file: RemoteFile): Int {
        return if (file.isDirectory) R.color.warning
        else iconTintForExtension(extensionOf(file))
    }

    /** Returns the color resource id used to tint an icon by extension. */
    fun iconTintForExtension(ext: String): Int {
        val e = ext.lowercase()
        return when {
            isImageExtension(e) -> R.color.secondary
            isVideoExtension(e) -> R.color.info
            isAudioExtension(e) -> R.color.secondary_variant
            isApkExtension(e) -> R.color.warning
            isDocumentExtension(e) -> R.color.text_secondary
            isArchiveExtension(e) -> R.color.pending_color
            else -> R.color.text_hint
        }
    }

    // ── File-type label (Arabic) ───────────────────────────────────────

    /** Arabic human label for the file's category, used in subtitles. */
    fun typeLabel(ext: String): String {
        val e = ext.lowercase()
        return when {
            isImageExtension(e) -> "صورة"
            isVideoExtension(e) -> "فيديو"
            isAudioExtension(e) -> "صوت"
            isApkExtension(e) -> "تطبيق"
            isDocumentExtension(e) -> "مستند"
            isArchiveExtension(e) -> "أرشيف"
            else -> "ملف"
        }
    }
}
