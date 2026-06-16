package com.abuzahra.admin.data.api

import com.google.gson.annotations.SerializedName

data class LoginRequest(
    val username: String,
    val password: String
)

data class FirebaseAuthRequest(
    @SerializedName("email") val email: String = "",
    @SerializedName("display_name") val displayName: String = "",
    @SerializedName("firebase_token") val firebaseToken: String = "",
    @SerializedName("id_token") val idToken: String = ""
)

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("email") val email: String,
    @SerializedName("password") val password: String
)