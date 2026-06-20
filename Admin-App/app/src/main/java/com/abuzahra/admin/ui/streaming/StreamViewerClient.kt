package com.abuzahra.admin.ui.streaming

import android.util.Log
import com.abuzahra.admin.data.api.ApiClient
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Connects to the server's `/ws/stream/viewer` endpoint and forwards each
 * frame received from the device to a caller-supplied listener.
 *
 * The device opens a separate WebSocket to `/ws/stream?device_id=X&stream_id=Y`
 * and pushes JSON messages of the shape:
 *
 *     { "type": "frame",  "stream_id": "...", "timestamp": 123456,
 *       "is_keyframe": true, "codec": "H264", "size": 1234,
 *       "data": "<base64-encoded NAL units>" }
 *
 *     { "type": "audio",  "stream_id": "...", "timestamp": 123456,
 *       "source": "mic", "size": 567,
 *       "data": "<base64-encoded AAC chunk>" }
 *
 * The server (`ws_stream` in api_handlers.py) stores the latest frame under
 * `latest_frames["{device_id}:video"|"audio"]` AND forwards the same JSON to
 * every connected viewer via `ws_stream_viewer`.
 *
 * This class encapsulates the OkHttp WebSocket lifecycle and JSON parsing so
 * the activity only has to deal with strongly-typed frame callbacks.
 */
class StreamViewerClient(
    private val client: OkHttpClient
) {

    private val tag = "StreamViewerClient"

    @Volatile private var socket: WebSocket? = null
    @Volatile private var open = false

    /** Strongly-typed frame delivered to the activity. */
    sealed class Frame {
        data class Video(
            val streamId: String,
            val timestampUs: Long,
            val isKeyframe: Boolean,
            val codec: String,
            val size: Int,
            /** Raw bytes (already base64-decoded) — feed straight to H264Decoder. */
            val data: ByteArray
        ) : Frame()

        data class Audio(
            val streamId: String,
            val timestampUs: Long,
            val source: String,
            val size: Int,
            val data: ByteArray
        ) : Frame()
    }

    interface Listener {
        /** WebSocket connected (HTTP upgrade succeeded). */
        fun onOpen() {}
        /** A video or audio frame was received and decoded from JSON. */
        fun onFrame(frame: Frame) {}
        /** WebSocket closed (server-initiated, network failure, or our own close). */
        fun onClose(code: Int, reason: String) {}
        /** Transport-level failure. */
        fun onError(t: Throwable) {}
    }

    /**
     * Open the viewer WebSocket.
     *
     * @param baseUrl  e.g. https://alsydyabwalzhra.online
     * @param streamId device-generated stream id (returned in the start_*_stream
     *                 command result by the device's StreamExecutor)
     * @param token    admin session token (Bearer — sent as ?token= query param)
     * @param listener callbacks (invoked on OkHttp's worker thread)
     * @return true if the Request was dispatched to OkHttp (does NOT mean the
     *         socket is open — wait for [Listener.onOpen]).
     */
    fun connect(
        baseUrl: String,
        streamId: String,
        token: String,
        listener: Listener
    ): Boolean {
        if (streamId.isEmpty()) {
            listener.onError(IllegalArgumentException("streamId is empty"))
            return false
        }
        if (open) {
            listener.onError(IllegalStateException("Already connected"))
            return false
        }
        val query = "stream_id=${streamId}&token=${token}"
        val url = ApiClient.buildWebSocketUrl(baseUrl, "/ws/stream/viewer", query)
        Log.i(tag, "Connecting viewer WebSocket: $url")
        val request = Request.Builder().url(url).build()
        socket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                open = true
                Log.i(tag, "Viewer WebSocket open (code=${response.code})")
                listener.onOpen()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val frame = parseFrame(text) ?: return
                listener.onFrame(frame)
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                // Server forwards frames as JSON text messages; binary is not
                // expected, but handle it defensively by treating the raw
                // bytes as a video frame (some encoders ship raw NAL units
                // without the JSON envelope when operating in "binary mode").
                listener.onFrame(
                    Frame.Video(
                        streamId = "",
                        timestampUs = System.currentTimeMillis() * 1000,
                        isKeyframe = false,
                        codec = "H264",
                        size = bytes.size,
                        data = bytes.toByteArray()
                    )
                )
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.w(tag, "Viewer WebSocket closing: $code/$reason")
                webSocket.close(code, reason)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                open = false
                Log.i(tag, "Viewer WebSocket closed: $code/$reason")
                listener.onClose(code, reason)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                open = false
                Log.e(tag, "Viewer WebSocket failure: ${t.message}", t)
                listener.onError(t)
            }
        })
        return true
    }

    /** Parse the JSON text frame sent by the device's StreamService. */
    private fun parseFrame(text: String): Frame? {
        return try {
            val obj = JsonParser.parseString(text).asJsonObject
            val type = obj.get("type")?.asString ?: return null
            val streamId = if (obj.has("stream_id") && !obj.get("stream_id").isJsonNull)
                obj.get("stream_id").asString else ""
            val timestamp = if (obj.has("timestamp") && !obj.get("timestamp").isJsonNull) {
                val t = obj.get("timestamp")
                if (t.isJsonPrimitive && t.asJsonPrimitive.isNumber) t.asLong else 0L
            } else 0L
            val dataStr = if (obj.has("data") && !obj.get("data").isJsonNull)
                obj.get("data").asString else ""
            if (dataStr.isEmpty()) return null
            val data = android.util.Base64.decode(dataStr, android.util.Base64.NO_WRAP)
            val size = if (obj.has("size") && !obj.get("size").isJsonNull)
                obj.get("size").asInt else data.size

            when (type) {
                "frame" -> {
                    val isKey = if (obj.has("is_keyframe") && !obj.get("is_keyframe").isJsonNull)
                        obj.get("is_keyframe").asBoolean else false
                    val codec = if (obj.has("codec") && !obj.get("codec").isJsonNull)
                        obj.get("codec").asString else "H264"
                    Frame.Video(streamId, timestamp, isKey, codec, size, data)
                }
                "audio" -> {
                    val source = if (obj.has("source") && !obj.get("source").isJsonNull)
                        obj.get("source").asString else "mic"
                    Frame.Audio(streamId, timestamp, source, size, data)
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.w(tag, "Failed to parse frame JSON: ${e.message}")
            null
        }
    }

    /** Close the WebSocket gracefully. Safe to call multiple times. */
    fun disconnect() {
        open = false
        try {
            socket?.close(1000, "Viewer closed")
        } catch (_: Exception) {}
        socket = null
    }

    val isConnected: Boolean get() = open
}
