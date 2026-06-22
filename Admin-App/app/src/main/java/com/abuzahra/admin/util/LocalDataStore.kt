package com.abuzahra.admin.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * LocalDataStore — saves data snapshots (fetched from Firebase) to the
 * app's private filesDir so the user can view them later even when the
 * device is offline. Backs the "حفظ البيانات محلياً" choice in the
 * data-command choice dialog.
 *
 * Files are stored under:
 *   filesDir/AbuZahraLocalData/{deviceId}_{type}_{timestamp}.json
 *
 * Each file is a plain UTF-8 JSON document. The list of saved snapshots
 * can be retrieved via [listSaved] for browsing in LocalDataListActivity.
 */
object LocalDataStore {

    private const val DIR_NAME = "AbuZahraLocalData"

    private fun dir(context: Context): File {
        val d = File(context.filesDir, DIR_NAME)
        if (!d.exists()) d.mkdirs()
        return d
    }

    /**
     * Save a JSON snapshot. Returns the absolute path of the saved file
     * (or null on failure).
     */
    fun save(context: Context, deviceId: String, type: String, json: String): String? {
        return try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
                .format(Date())
            val safeDevice = deviceId.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val safeType = type.replace(Regex("[^A-Za-z0-9_-]"), "_")
            val filename = "${safeDevice}_${safeType}_${ts}.json"
            val file = File(dir(context), filename)
            file.writeText(json, Charsets.UTF_8)
            file.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    /**
     * List all saved snapshots as (file, deviceId, type, timestamp) tuples.
     * Sorted newest-first by file modification time.
     */
    data class SavedSnapshot(
        val file: File,
        val deviceId: String,
        val type: String,
        val savedAt: Long,
        val sizeBytes: Long
    )

    fun listSaved(context: Context): List<SavedSnapshot> {
        val files = dir(context).listFiles()?.toList() ?: emptyList()
        return files.mapNotNull { f ->
            val name = f.nameWithoutExtension
            // Expected pattern: {deviceId}_{type}_{yyyyMMdd_HHmmss}
            val parts = name.split("_")
            if (parts.size < 4) return@mapNotNull null
            // Last 2 parts are date + time
            val typeAndDevice = parts.dropLast(2)
            // deviceId may contain underscores too — be lenient: take the
            // last segment before the timestamp as the type, and the rest
            // joined as deviceId.
            val type = typeAndDevice.lastOrNull() ?: ""
            val deviceId = typeAndDevice.dropLast(1).joinToString("_")
            SavedSnapshot(
                file = f,
                deviceId = deviceId,
                type = type,
                savedAt = f.lastModified(),
                sizeBytes = f.length()
            )
        }.sortedByDescending { it.savedAt }
    }

    /**
     * Delete a saved snapshot by absolute path. Returns true on success.
     */
    fun delete(path: String): Boolean {
        return try {
            File(path).delete()
        } catch (e: Exception) {
            false
        }
    }
}
