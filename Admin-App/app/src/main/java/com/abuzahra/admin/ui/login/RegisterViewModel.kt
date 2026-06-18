package com.abuzahra.admin.ui.login

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.abuzahra.admin.data.api.LoginResponse
import com.abuzahra.admin.util.Preferences
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

class RegisterViewModel(private val preferences: Preferences) : ViewModel() {

    companion object {
        private const val TAG = "RegisterViewModel"
    }

    private val _registerResult = MutableLiveData<com.abuzahra.admin.data.api.Result<LoginResponse>>()
    val registerResult: MutableLiveData<com.abuzahra.admin.data.api.Result<LoginResponse>> = _registerResult

    private val gson: Gson = GsonBuilder().setLenient().create()

    fun register(username: String, email: String, password: String) {
        _registerResult.value = com.abuzahra.admin.data.api.Result.Loading

        viewModelScope.launch {
            try {
                val serverUrl = preferences.serverUrl
                Log.d(TAG, "Registering: $email → $serverUrl")

                val jsonBody = gson.toJson(mapOf(
                    "username" to username,
                    "email" to email,
                    "password" to password
                ))

                // Trust all certs
                val trustAllCerts = arrayOf<javax.net.ssl.TrustManager>(
                    object : javax.net.ssl.X509TrustManager {
                        override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) {}
                        override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = arrayOf()
                    }
                )
                val sslContext = javax.net.ssl.SSLContext.getInstance("TLS")
                sslContext.init(null, trustAllCerts, java.security.SecureRandom())

                val client = OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as javax.net.ssl.X509TrustManager)
                    .hostnameVerifier { _, _ -> true }
                    .build()

                val baseUrl = if (serverUrl.endsWith("/")) serverUrl else "$serverUrl/"
                val requestBody = jsonBody.toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("${baseUrl}api/web/register")
                    .post(requestBody)
                    .addHeader("Accept", "application/json")
                    .build()

                val response = withContext(Dispatchers.IO) {
                    client.newCall(request).execute()
                }

                val responseBody = response.body?.string() ?: ""

                Log.d(TAG, "Register response: ${response.code} - ${responseBody.take(200)}")

                if (responseBody.isEmpty()) {
                    _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("لم يتم استلام استجابة من الخادم"))
                    return@launch
                }

                val loginResponse = try {
                    gson.fromJson(responseBody, LoginResponse::class.java)
                } catch (e: Exception) {
                    Log.e(TAG, "JSON parse error", e)
                    _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("خطأ في تحليل استجابة الخادم"))
                    return@launch
                }

                if (loginResponse.ok && loginResponse.token.isNotEmpty()) {
                    preferences.token = loginResponse.token
                    preferences.permanentCode = loginResponse.permanentCode
                    preferences.userEmail = loginResponse.email
                    preferences.userName = loginResponse.username
                    preferences.userRole = loginResponse.role
                    preferences.userId = loginResponse.userId
                    Log.d(TAG, "Register success: user=${loginResponse.username}")
                    _registerResult.postValue(com.abuzahra.admin.data.api.Result.Success(loginResponse))
                } else {
                    Log.e(TAG, "Register failed: ${loginResponse.message}")
                    _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error(loginResponse.message.ifEmpty { "فشل إنشاء الحساب" }))
                }
            } catch (e: SocketTimeoutException) {
                Log.e(TAG, "Register timeout", e)
                _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("انتهت مهلة الاتصال بالخادم"))
            } catch (e: UnknownHostException) {
                Log.e(TAG, "Register unknown host", e)
                _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("لا يمكن الوصول إلى الخادم"))
            } catch (e: SSLException) {
                Log.e(TAG, "Register SSL error", e)
                _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("خطأ في شهادة الأمان"))
            } catch (e: Exception) {
                Log.e(TAG, "Register error", e)
                _registerResult.postValue(com.abuzahra.admin.data.api.Result.Error("خطأ في الاتصال: ${e.message}"))
            }
        }
    }
}