package com.abuzahra.admin.data.model

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import java.text.SimpleDateFormat
import java.util.Locale

data class Command(
    @SerializedName("id") val id: String = "",
    @SerializedName("device_id") val deviceId: String = "",
    @SerializedName("command") val command: String = "",
    @SerializedName("params") val params: Map<String, Any>? = null,
    @SerializedName("status") val status: String = "",
    @SerializedName("created_at") val createdAt: String = "",
    @SerializedName("sent_at") val sentAt: String? = null,
    @SerializedName("result") val result: String? = null,
    @SerializedName("completed_at") val completedAt: String? = null,
    @SerializedName("requested_by") val requestedBy: String? = null,
    @SerializedName("source") val source: String? = null
) {

    /**
     * Pretty-printed version of [result].
     *
     * The server stores `result` as a JSON-serialised string (double-encoded:
     * the wire payload is itself a JSON string whose value is another JSON
     * document). Gson already deserialises that into a Kotlin `String`, so
     * [result] holds the inner JSON text — e.g. `[{"address":"+123", ...}]` or
     * `{"battery": 87, ...}`.
     *
     * This helper attempts to pretty-print the inner JSON so the admin UI
     * shows readable output instead of a single-line escaped blob. If the
     * content is not JSON, it is returned verbatim.
     */
    val prettyResult: String
        get() {
            val raw = result ?: return ""
            return try {
                val element = JsonParser.parseString(raw)
                GsonBuilder().setPrettyPrinting().create().toJson(element)
            } catch (_: Exception) {
                raw
            }
        }

    val displayStatus: String
        get() = when (status.lowercase()) {
            "completed", "success" -> "ناجح"
            "failed", "error" -> "فاشل"
            "delivered" -> "تم التسليم"
            "pending", "queued" -> "قيد الانتظار"
            "sent" -> "تم الإرسال"
            else -> status.ifEmpty { "قيد الانتظار" }
        }

    val displayTime: String
        get() {
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(createdAt) ?: return createdAt
                SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
            } catch (e: Exception) {
                createdAt
            }
        }

    /**
     * Color code for the status indicator:
     * 0 = success (green)
     * 1 = failed (red)
     * 2 = pending (yellow)
     */
    val statusColor: Int
        get() = when (status.lowercase()) {
            "completed", "success" -> 0
            "failed", "error" -> 1
            else -> 2
        }
}