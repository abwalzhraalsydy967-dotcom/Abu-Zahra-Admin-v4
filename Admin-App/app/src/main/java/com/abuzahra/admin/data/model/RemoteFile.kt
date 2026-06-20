package com.abuzahra.admin.data.model

import com.abuzahra.admin.util.FileUtils
import com.google.gson.annotations.SerializedName

data class RemoteFile(
    @SerializedName("id") val id: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("filename") val filename: String = "",
    @SerializedName("file_type") val fileType: String = "",
    @SerializedName("size") val size: Long = 0L,
    @SerializedName("uploaded_at") val uploadedAt: String? = null,
    @SerializedName("expires_at") val expiresAt: String? = null,
    @SerializedName("retrieved") val retrieved: Boolean = false,
    @SerializedName("command_id") val commandId: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("path") val path: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("is_directory") val isDirectory: Boolean = false,
    @SerializedName("extension") val extension: String = "",
    @SerializedName("modified") val modified: String? = null
) {

    val displayName: String
        get() = name.ifEmpty { filename }

    /**
     * Human-readable file size, e.g. `1.5 KB`, `12.3 MB`, `4.27 GB`.
     * Delegates to [FileUtils.formatFileSize] so the formatting is
     * consistent across the app (file list, requested-files list,
     * download dialogs, …).
     */
    val displaySize: String
        get() = if (isDirectory) "مجلد" else FileUtils.formatFileSize(size)

    val displayExtension: String
        get() = extension.ifEmpty {
            val lastDot = displayName.lastIndexOf('.')
            if (lastDot >= 0) displayName.substring(lastDot + 1) else ""
        }
}