package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)