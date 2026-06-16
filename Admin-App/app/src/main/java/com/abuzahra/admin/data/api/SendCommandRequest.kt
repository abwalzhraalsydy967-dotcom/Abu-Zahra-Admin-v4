package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

data class SendCommandRequest(
    @SerializedName("command") val command: String,
    @SerializedName("params") val params: Map<String, String> = emptyMap(),
    @SerializedName("device_id") val deviceId: String = ""
)