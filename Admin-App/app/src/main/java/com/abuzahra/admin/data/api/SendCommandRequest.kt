package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

data class SendCommandRequest(
    val command: String,
    val params: Map<String, Any> = emptyMap(),
    @SerializedName("device_id") val deviceId: String = ""
)