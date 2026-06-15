package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

data class LoginResponse(
    @SerializedName("ok") val ok: Boolean = false,
    @SerializedName("token") val token: String = "",
    @SerializedName("user_id") val userId: String = "",
    @SerializedName("username") val username: String = "",
    @SerializedName("role") val role: String = "",
    @SerializedName("email") val email: String = "",
    @SerializedName("message") val message: String = ""
)