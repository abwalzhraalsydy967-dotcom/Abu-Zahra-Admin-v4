package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

/**
 * Maps the server's stats response directly.
 * Server returns: {"ok": true, "total_devices": N, "online_devices": N, "offline_devices": N, ...}
 */
data class StatsResponse(
    @SerializedName("ok") val ok: Boolean = true,
    @SerializedName("total_devices") val devicesCount: Int = 0,
    @SerializedName("online_devices") val onlineCount: Int = 0,
    @SerializedName("offline_devices") val offlineCount: Int = 0,
    @SerializedName("total_commands") val totalCommands: Int = 0,
    @SerializedName("total_events") val totalEvents: Int = 0,
    @SerializedName("total_files") val totalFiles: Int = 0
)