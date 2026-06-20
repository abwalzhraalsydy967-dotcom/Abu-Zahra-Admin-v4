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

    /**
     * Fetch a single command's current state by polling /api/web/commands?device_id=X.
     * Used by StreamingActivity to wait for the device's start_*_stream command
     * to complete and return its result (which contains the stream_id).
     *
     * Note: the server's /api/web/commands endpoint filters OUT pending/sent
     * commands, so this will only return a non-null Command once the device
     * has executed the command and submitted its result.
     */
    suspend fun getCommand(deviceId: String, commandId: String): Command?

    // ── Link Code ─────────────────────────────────────────────────
    suspend fun getLinkCode(): String

    // Regenerate the current user's permanent link code.
    // POST /api/web/regenerate_code — returns {ok, code: "NEWCODE"}.
    suspend fun regenerateCode(): RegenerateCodeResponse

    // ── Files ─────────────────────────────────────────────────────
    suspend fun getFiles(deviceId: String, path: String = "/"): List<RemoteFile>
    suspend fun listDeviceFiles(deviceId: String, path: String): List<RemoteFile>
    suspend fun downloadFile(url: String): ResponseBody

    // Requested files: files uploaded by devices to the server (auto-expire after 1 hour).
    // GET /api/web/files?device_id=X — returns {ok, files: [...]}.
    suspend fun getRequestedFiles(deviceId: String? = null): DeviceFilesResponse

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
    val result: String? = null,
    /**
     * Server returns the full queued command object under the "command" key:
     * {"ok": true, "command": {id, device_id, command, params, status, created_at, ...}}.
     * We capture it here so callers can read command_id (= command.id) and
     * route DATA-retrieval commands to the result viewer.
     */
    val command: Command? = null
) {
    /**
     * The server returns the command id nested under `command.id`, NOT at the
     * top level. Older code paths still read `command_id` directly, so we
     * expose this derived property that prefers the nested value but falls
     * back to the legacy top-level field.
     */
    val effectiveCommandId: String
        get() = command?.id?.takeIf { it.isNotEmpty() } ?: command_id
}

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