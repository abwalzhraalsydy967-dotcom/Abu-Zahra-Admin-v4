package com.abuzahra.admin.data.model

import com.google.gson.annotations.SerializedName
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

data class Device(
    @SerializedName("id") val id: String = "",
    @SerializedName("token") val token: String = "",
    @SerializedName("name") val name: String = "",
    @SerializedName("model") val model: String = "",
    @SerializedName("brand") val brand: String = "",
    @SerializedName("os") val os: String = "",
    @SerializedName("battery") val battery: Int = -1,
    @SerializedName("network") val network: String = "",
    @SerializedName("location") val location: String? = null,
    @SerializedName("last_seen") val lastSeen: String? = null,
    @SerializedName("created_at") val createdAt: String? = null,
    @SerializedName("owner_id") val ownerId: String = "",
    @SerializedName("online") val online: Boolean = false,
    @SerializedName("ip") val ip: String? = null,
    @SerializedName("settings") val settings: Map<String, Any?>? = null,
    @SerializedName("imei") val imei: String = "",
    @SerializedName("phone_number") val phoneNumber: String = "",

    // Server transforms some fields for web dashboard - accept both names
    @SerializedName("is_online") val isOnlineFromServer: Boolean? = null,
    @SerializedName("battery_level") val batteryLevelFromServer: Int? = null,
    @SerializedName("android_version") val androidVersionFromServer: String? = null,
    @SerializedName("linked_at") val linkedAtFromServer: String? = null
) : Serializable {

    companion object {
        private const val serialVersionUID = 1L
    }

    val isOnline: Boolean get() = online || (isOnlineFromServer ?: false)

    val batteryLevel: Int get() {
        val level = if (battery >= 0) battery else (batteryLevelFromServer ?: -1)
        return level.coerceIn(0, 100)
    }

    val displayBattery: String
        get() {
            val level = batteryLevel
        return if (level < 0) "N/A" else "$level%"
    }

    val osVersion: String get() = androidVersionFromServer.ifEmpty { os }

    val androidVersion: String get() = osVersion

    val ipAddress: String get() = ip ?: ""

    val displayLastSeen: String
        get() {
            val time = lastSeen ?: linkedAtFromServer
            if (time.isNullOrEmpty()) return "أبداً"
            return try {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                val date = sdf.parse(time) ?: return time
                val now = System.currentTimeMillis()
                val diff = now - date.time
                when {
                    diff < TimeUnit.MINUTES.toMillis(1) -> "الآن"
                    diff < TimeUnit.HOURS.toMillis(1) -> "منذ ${TimeUnit.MILLISECONDS.toMinutes(diff)} دقيقة"
                    diff < TimeUnit.DAYS.toMillis(1) -> "منذ ${TimeUnit.MILLISECONDS.toHours(diff)} ساعة"
                    diff < TimeUnit.DAYS.toMillis(7) -> "منذ ${TimeUnit.MILLISECONDS.toDays(diff)} يوم"
                    else -> SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(date)
                }
            } catch (e: Exception) {
                time
            }
        }
}