package com.abuzahra.admin.data.api

import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.data.model.RemoteFile
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

// ═══════════════════════════════════════════════════════════════════
// Internal Retrofit interface (maps server endpoints directly)
// ═══════════════════════════════════════════════════════════════════

private data class DeviceDetailEnvelope(
    val ok: Boolean = true,
    val device: Device? = null
)

private data class JpegStreamRequest(
    val device_id: String
)

private interface RetrofitApiService {

    @POST("api/web/login")
    suspend fun login(@Body request: LoginRequest): LoginResponse

    @GET("api/web/devices")
    suspend fun getDevices(): DevicesEnvelope

    @GET("api/web/stats")
    suspend fun getStats(): StatsResponse

    @GET("api/web/commands")
    suspend fun getCommands(@Query("device_id") deviceId: String): CommandsEnvelope

    @GET("api/web/events")
    suspend fun getEvents(): EventsEnvelope

    @GET("api/web/device/{deviceId}")
    suspend fun getDeviceDetail(@Path("deviceId") deviceId: String): DeviceDetailEnvelope

    @POST("api/web/send_command")
    suspend fun sendCommand(@Body request: SendCommandRequest): CommandResponse

    @GET("api/web/link_code")
    suspend fun getLinkCode(): LinkCodeResponse

    @GET("api/web/files")
    suspend fun getFiles(
        @Query("device_id") deviceId: String,
        @Query("path") path: String = "/"
    ): FilesEnvelope

    @GET("api/web/device/files")
    suspend fun listDeviceFiles(
        @Query("device_id") deviceId: String,
        @Query("path") path: String
    ): CommandResponse

    @GET
    suspend fun downloadFile(@Url url: String): ResponseBody

    @GET("api/stream/frame/{deviceId}")
    suspend fun getStreamFrame(
        @Path("deviceId") deviceId: String,
        @Query("type") type: String
    ): StreamFrameResponse

    @POST("api/stream/jpeg_start")
    suspend fun startJpegStream(@Body request: JpegStreamRequest): CommandResponse

    @POST("api/stream/jpeg_stop")
    suspend fun stopJpegStream(@Body request: JpegStreamRequest): CommandResponse

    @GET("api/web/users")
    suspend fun getUsers(): UsersEnvelope

    @POST("api/web/users")
    suspend fun createUser(@Body request: CreateUserRequest): UserResponse

    @DELETE("api/web/users/{userId}")
    suspend fun deleteUser(@Path("userId") userId: String): DeleteResponse

    @Multipart
    @POST("api/upload")
    suspend fun uploadFile(
        @Part("device_id") deviceId: String,
        @Part("path") path: String,
        @Part file: MultipartBody.Part
    ): CommandResponse
}

// ═══════════════════════════════════════════════════════════════════
// ApiService implementation — unwraps server {"ok": true, ...} envelopes
// ═══════════════════════════════════════════════════════════════════

private class ApiServiceImpl(private val retrofit: RetrofitApiService) : ApiService {

    override suspend fun login(request: LoginRequest): LoginResponse {
        return retrofit.login(request)
    }

    override suspend fun getDevices(): List<Device> {
        val envelope = retrofit.getDevices()
        if (!envelope.ok) throw ApiException(envelope.toString())
        return envelope.devices
    }

    override suspend fun getStats(): StatsResponse {
        val response = retrofit.getStats()
        if (!response.ok) throw ApiException(response.toString())
        return response
    }

    override suspend fun getDeviceDetail(deviceId: String): Device {
        val envelope = retrofit.getDeviceDetail(deviceId)
        if (!envelope.ok || envelope.device == null) throw ApiException("Device not found")
        return envelope.device
    }

    override suspend fun getCommands(deviceId: String): List<Command> {
        val envelope = retrofit.getCommands(deviceId)
        if (!envelope.ok) throw ApiException(envelope.toString())
        return envelope.commands
    }

    override suspend fun getEvents(): List<Event> {
        val envelope = retrofit.getEvents()
        if (!envelope.ok) throw ApiException(envelope.toString())
        return envelope.events
    }

    override suspend fun sendCommand(deviceId: String, request: SendCommandRequest): CommandResponse {
        val fullRequest = request.copy(deviceId = deviceId)
        val response = retrofit.sendCommand(fullRequest)
        if (!response.ok) throw ApiException(response.message.ifEmpty { "Command failed" })
        return response
    }

    override suspend fun getLinkCode(): String {
        val response = retrofit.getLinkCode()
        if (!response.ok) throw ApiException("Failed to get link code")
        return response.link_code
    }

    override suspend fun getFiles(deviceId: String, path: String): List<RemoteFile> {
        val envelope = retrofit.getFiles(deviceId, path)
        if (!envelope.ok) throw ApiException(envelope.toString())
        return envelope.files
    }

    override suspend fun listDeviceFiles(deviceId: String, path: String): CommandResponse {
        return retrofit.listDeviceFiles(deviceId, path)
    }

    override suspend fun downloadFile(url: String): ResponseBody {
        return retrofit.downloadFile(url)
    }

    override suspend fun getStreamFrame(deviceId: String, type: String): StreamFrameResponse {
        return retrofit.getStreamFrame(deviceId, type)
    }

    override suspend fun startJpegStream(deviceId: String): CommandResponse {
        return retrofit.startJpegStream(JpegStreamRequest(deviceId))
    }

    override suspend fun stopJpegStream(deviceId: String): CommandResponse {
        return retrofit.stopJpegStream(JpegStreamRequest(deviceId))
    }

    override suspend fun getUsers(): List<User> {
        val envelope = retrofit.getUsers()
        if (!envelope.ok) throw ApiException(envelope.toString())
        return envelope.users
    }

    override suspend fun createUser(request: CreateUserRequest): UserResponse {
        return retrofit.createUser(request)
    }

    override suspend fun deleteUser(userId: String): Boolean {
        val response = retrofit.deleteUser(userId)
        return response.ok
    }
}

// ═══════════════════════════════════════════════════════════════════
// ApiClient — builds OkHttp + Retrofit, provides ApiService instances
// ═══════════════════════════════════════════════════════════════════

class ApiClient private constructor() {

    companion object {

        internal val gson = com.google.gson.GsonBuilder()
            .setLenient()
            .create()

        /**
         * Create an ApiService with Bearer token authentication.
         */
        fun createWithToken(serverUrl: String, token: String): ApiService {
            val client = buildOkHttpClient(token)
            val retrofit = buildRetrofit(serverUrl, client)
            val service = retrofit.create(RetrofitApiService::class.java)
            return ApiServiceImpl(service)
        }

        /**
         * Create an ApiService without authentication (for login).
         */
        fun create(serverUrl: String): ApiService {
            val client = buildOkHttpClient(null)
            val retrofit = buildRetrofit(serverUrl, client)
            val service = retrofit.create(RetrofitApiService::class.java)
            return ApiServiceImpl(service)
        }

        /**
         * Upload a file to the server. Returns Result for use from coroutine contexts.
         * Called from FilesActivity. Must be called from a coroutine scope.
         */
        suspend fun uploadFile(
            serverUrl: String,
            token: String,
            deviceId: String,
            path: String,
            file: File
        ): Result<String> {
            return try {
                val client = buildOkHttpClient(token)
                val retrofit = buildRetrofit(serverUrl, client)
                val service = retrofit.create(RetrofitApiService::class.java)

                val requestFile = file.asRequestBody("application/octet-stream".toMediaType())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)

                val response = service.uploadFile(deviceId, path, body)

                if (response.ok) {
                    Result.Success("تم رفع الملف بنجاح")
                } else {
                    Result.Error(response.message.ifEmpty { "فشل رفع الملف" })
                }
            } catch (e: Exception) {
                Result.Error(e.message ?: "خطأ في رفع الملف")
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// OkHttp + Retrofit builders
// ═══════════════════════════════════════════════════════════════════

private fun buildOkHttpClient(token: String?): OkHttpClient {
    return OkHttpClient.Builder().apply {
        // Trust-all SSL for self-signed certificate compatibility
        trustAllCertificates()

        // Timeouts
        connectTimeout(30, TimeUnit.SECONDS)
        readTimeout(30, TimeUnit.SECONDS)
        writeTimeout(60, TimeUnit.SECONDS)

        // Auth interceptor
        if (!token.isNullOrBlank()) {
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
        } else {
            addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }
        }

        // Logging interceptor
        addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
    }.build()
}

private fun buildRetrofit(baseUrl: String, client: OkHttpClient): Retrofit {
    val normalizedUrl = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    return Retrofit.Builder()
        .baseUrl(normalizedUrl)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(ApiClient.gson))
        .build()
}

private fun OkHttpClient.Builder.trustAllCertificates() {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, trustAllCerts, SecureRandom())
    }

    sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
    hostnameVerifier { _, _ -> true }
}

/**
 * Exception thrown when the server returns {"ok": false, ...}
 */
class ApiException(message: String) : Exception(message)