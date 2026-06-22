package com.abuzahra.manager.streaming

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.util.Size
import android.view.Gravity
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.abuzahra.manager.Config
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * CameraStreamService - Service for front/back camera streaming using Camera2 API
 * Supports camera switching during stream, multiple quality levels (480p, 720p, 1080p), torch control
 */
class CameraStreamService : Service() {
    
    companion object {
        private const val TAG = "CameraStreamService"
        private const val NOTIFICATION_ID = 2002
        private const val CHANNEL_ID = "camera_stream_channel"
        
        // Intent actions
        const val ACTION_START_STREAM = "com.abuzahra.manager.START_CAMERA_STREAM"
        const val ACTION_STOP_STREAM = "com.abuzahra.manager.STOP_CAMERA_STREAM"
        const val ACTION_SWITCH_CAMERA = "com.abuzahra.manager.SWITCH_CAMERA"
        const val ACTION_TOGGLE_TORCH = "com.abuzahra.manager.TOGGLE_TORCH"
        const val ACTION_UPDATE_QUALITY = "com.abuzahra.manager.UPDATE_QUALITY"
        const val ACTION_PAUSE_STREAM = "com.abuzahra.manager.PAUSE_CAMERA_STREAM"
        const val ACTION_RESUME_STREAM = "com.abuzahra.manager.RESUME_CAMERA_STREAM"
        
        // Intent extras
        const val EXTRA_CONFIG = "config"
        const val EXTRA_CAMERA_ID = "camera_id"
        const val EXTRA_QUALITY = "quality"
        
        // Camera IDs
        const val CAMERA_FRONT = "front"
        const val CAMERA_BACK = "back"
        
        // Stream state
        private var currentStreamState: StreamState? = null
        
        /**
         * Get current stream state
         */
        fun getStreamState(): StreamState? = currentStreamState
        
        /**
         * Check if streaming is active
         */
        fun isStreaming(): Boolean = currentStreamState?.isActive ?: false
    }
    
    // Service scope for coroutines
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // Camera2 API
    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var cameraCaptureSession: CameraCaptureSession? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    // Camera state
    private var currentCameraId: String = ""
    private var frontCameraId: String = ""
    private var backCameraId: String = ""
    private var isTorchOn = false
    private var isStreaming = AtomicBoolean(false)
    private var isPaused = AtomicBoolean(false)
    
    // Configuration
    private var config: StreamConfig.Configuration = StreamConfig.Presets.cameraStream()
    
    // Encoders
    private var videoEncoder: VideoEncoder? = null
    private var audioEncoder: AudioEncoder? = null
    
    // WebSocket reconnection with exponential backoff
    private var wsReconnectAttempts = 0
    private val maxWsReconnectAttempts = 10
    private val wsBaseReconnectDelayMs = 1000L
    private val wsMaxReconnectDelayMs = 30000L
    
    // WebSocket client
    private var webSocket: WebSocket? = null
    private var okHttpClient: OkHttpClient? = null
    
    // Preview size
    private var previewSize: Size? = null
    
    // Statistics
    private var startTime = 0L
    private var totalBytesSent = 0L
    private var totalFramesEncoded = 0L
    
    // SharedPreferences
    private lateinit var prefs: SharedPreferences
    
    // ─── Background-camera overlay (1x1 SurfaceView via TYPE_APPLICATION_OVERLAY) ───
    // Android 9+ blocks background camera access. The trick: a 1x1 invisible
    // SurfaceView attached via WindowManager makes the system think the app
    // has an on-screen surface, so the camera is allowed to run in the
    // background. The overlay is created lazily when the app is NOT in the
    // foreground, and torn down when streaming stops.
    private var overlayView: SurfaceView? = null
    private var windowManager: WindowManager? = null
    @Volatile private var overlayHolderSurface: Surface? = null
    
    /**
     * Stream state data class
     */
    data class StreamState(
        val streamId: String,
        var isActive: Boolean,
        var isPaused: Boolean,
        val startTime: Long,
        var totalBytesSent: Long,
        var totalFramesEncoded: Long,
        var currentCamera: String,
        var isTorchOn: Boolean,
        var currentQuality: StreamConfig.Quality,
        var lastError: String? = null
    )
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        prefs = getSharedPreferences("camera_stream_prefs", Context.MODE_PRIVATE)
        
        // Initialize camera manager
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        
        // Find available cameras
        findAvailableCameras()
        
        // Initialize HTTP client
        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .build()
        
        Log.i(TAG, "CameraStreamService created. Front: $frontCameraId, Back: $backCameraId")
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Start as foreground service
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
        
        when (intent?.action) {
            ACTION_START_STREAM -> {
                val configJson = intent.getStringExtra(EXTRA_CONFIG)
                configJson?.let { try { config = com.google.gson.Gson().fromJson(it, StreamConfig.Configuration::class.java) } catch(_ : Exception) {} }
                
                val cameraId = intent.getStringExtra(EXTRA_CAMERA_ID) ?: CAMERA_BACK
                startStreaming(cameraId)
            }
            ACTION_STOP_STREAM -> stopStreaming()
            ACTION_SWITCH_CAMERA -> switchCamera()
            ACTION_TOGGLE_TORCH -> toggleTorch()
            ACTION_UPDATE_QUALITY -> {
                val qualityName = intent.getStringExtra(EXTRA_QUALITY)
                qualityName?.let { updateQuality(it) }
            }
            ACTION_PAUSE_STREAM -> pauseStreaming()
            ACTION_RESUME_STREAM -> resumeStreaming()
        }
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    /**
     * Find available camera IDs
     */
    @SuppressLint("MissingPermission")
    private fun findAvailableCameras() {
        try {
            val cameraIds = cameraManager?.cameraIdList ?: return
            
            for (id in cameraIds) {
                val characteristics = cameraManager?.getCameraCharacteristics(id)
                val facing = characteristics?.get(CameraCharacteristics.LENS_FACING)
                
                when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> frontCameraId = id
                    CameraCharacteristics.LENS_FACING_BACK -> backCameraId = id
                }
            }
            
            // Default to back camera
            currentCameraId = if (backCameraId.isNotEmpty()) backCameraId else frontCameraId
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to find cameras", e)
        }
    }
    
    /**
     * Start camera streaming
     */
    private fun startStreaming(cameraId: String) {
        if (isStreaming.get()) {
            Log.w(TAG, "Already streaming")
            return
        }
        
        // Select camera
        currentCameraId = when (cameraId) {
            CAMERA_FRONT -> frontCameraId
            CAMERA_BACK -> backCameraId
            else -> cameraId
        }
        
        if (currentCameraId.isEmpty()) {
            Log.e(TAG, "No camera available")
            updateStreamState(error = "No camera available")
            return
        }
        
        // ─── Background-camera overlay (1x1 SurfaceView) ───────────────
        // Android 9+ blocks background camera access. Create a 1x1 invisible
        // SurfaceView via WindowManager so the system thinks the app has an
        // on-screen surface, allowing the camera to run while backgrounded.
        // Safe to call unconditionally — it self-skips if already created or
        // if the app is in the foreground.
        createInvisibleOverlayIfNeeded()

        // Start background thread
        startBackgroundThread()
        
        // Initialize encoders
        if (!initEncoders()) {
            updateStreamState(error = "Failed to initialize encoders")
            cleanup()
            return
        }
        
        // Open camera BEFORE connecting to server
        if (!openCamera()) {
            updateStreamState(error = "Failed to open camera")
            cleanup()
            return
        }
        
        // Set streaming active NOW (before server connection)
        isStreaming.set(true)
        startTime = System.currentTimeMillis()
        
        // Initialize state
        currentStreamState = StreamState(
            streamId = config.streamId,
            isActive = true,
            isPaused = false,
            startTime = startTime,
            totalBytesSent = 0,
            totalFramesEncoded = 0,
            currentCamera = if (currentCameraId == frontCameraId) CAMERA_FRONT else CAMERA_BACK,
            isTorchOn = false,
            currentQuality = config.quality
        )
        
        updateNotification("Camera Streaming Active")
        
        // Connect to server ASYNC (non-blocking, retries in background)
        connectToServer()
        
        Log.i(TAG, "Camera streaming started: ${config.streamId}")
    }
    
    /**
     * Initialize encoders
     */
    private fun initEncoders(): Boolean {
        // Initialize video encoder
        videoEncoder = VideoEncoder(config)
        
        if (!videoEncoder!!.init()) {
            Log.e(TAG, "Failed to initialize video encoder")
            return false
        }
        
        videoEncoder!!.setEncodedDataCallback { frame ->
            onVideoFrameEncoded(frame)
        }
        
        // Initialize audio encoder if enabled
        if (config.audioEnabled) {
            audioEncoder = AudioEncoder(
                sampleRate = StreamConfig.AUDIO_SAMPLE_RATE,
                channelCount = StreamConfig.AUDIO_CHANNEL_COUNT,
                bitrate = config.audioBitrate
            )
            
            if (!audioEncoder!!.init()) {
                Log.w(TAG, "Failed to initialize audio encoder, continuing without audio")
                audioEncoder = null
            } else {
                audioEncoder!!.setEncodedDataCallback { frame ->
                    onAudioFrameEncoded(frame)
                }
            }
        }
        
        return true
    }
    
    /**
     * Start background thread for camera operations
     */
    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }
    
    /**
     * Stop background thread
     */
    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: InterruptedException) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }
    
    /**
     * Open camera device
     */
    @SuppressLint("MissingPermission")
    private fun openCamera(): Boolean {
        try {
            // Get supported preview size
            val characteristics = cameraManager?.getCameraCharacteristics(currentCameraId)
            val configMap = characteristics?.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            
            previewSize = chooseOptimalSize(
                configMap?.getOutputSizes(SurfaceTexture::class.java),
                config.width,
                config.height
            )
            
            // Update config with actual size
            previewSize?.let {
                config = config.copy(width = it.width, height = it.height)
            }
            
            // Open camera
            cameraManager?.openCamera(currentCameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    cameraDevice = camera
                    createCaptureSession()
                }
                
                override fun onDisconnected(camera: CameraDevice) {
                    camera.close()
                    cameraDevice = null
                }
                
                override fun onError(camera: CameraDevice, error: Int) {
                    camera.close()
                    cameraDevice = null
                    Log.e(TAG, "Camera error: $error")
                    updateStreamState(error = "Camera error: $error")
                }
            }, backgroundHandler)
            
            return true
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open camera", e)
            return false
        }
    }
    
    /**
     * Choose optimal preview size
     */
    private fun chooseOptimalSize(choices: Array<Size>?, targetWidth: Int, targetHeight: Int): Size? {
        if (choices == null || choices.isEmpty()) return null
        
        // Find sizes close to target
        val targetRatio = targetWidth.toDouble() / targetHeight
        
        return choices
            .filter { it.width >= 320 && it.height >= 240 }
            .minByOrNull { size ->
                val ratio = size.width.toDouble() / size.height
                val ratioDiff = Math.abs(ratio - targetRatio)
                val sizeDiff = Math.abs(size.width - targetWidth) + Math.abs(size.height - targetHeight)
                ratioDiff * 1000 + sizeDiff
            } ?: choices[0]
    }
    
    /**
     * Create camera capture session
     *
     * The capture session's output surface list now optionally includes the
     * 1x1 overlay surface (when present). Adding it forces the system to treat
     * the app as having a visible on-screen surface — required for background
     * camera access on Android 9+. The encoder surface remains the primary
     * output; the overlay surface is a "dummy" companion target.
     */
    private fun createCaptureSession() {
        val device = cameraDevice ?: return
        val encoderSurface = videoEncoder?.getInputSurface() ?: run {
            Log.e(TAG, "Cannot create capture session — encoder surface is null")
            return
        }

        // Build the list of output surfaces. Include the overlay surface if it
        // is ready and valid.
        val surfaces = mutableListOf<Surface>(encoderSurface)
        val overlaySurface = overlayHolderSurface
        if (overlaySurface != null && overlaySurface.isValid) {
            surfaces.add(overlaySurface)
            Log.d(TAG, "Capture session will include 1x1 overlay surface")
        }
        
        try {
            device.createCaptureSession(
                surfaces,
                object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        cameraCaptureSession = session
                        startPreview()
                    }
                    
                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        Log.e(TAG, "Capture session configuration failed")
                        updateStreamState(error = "Capture session failed")
                    }
                },
                backgroundHandler
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create capture session", e)
        }
    }
    
    /**
     * Start camera preview
     */
    private fun startPreview() {
        val session = cameraCaptureSession ?: return
        val surface = videoEncoder?.getInputSurface() ?: return
        
        try {
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder?.addTarget(surface)
            
            // Also target the overlay surface if it exists — the capture request
            // must reference every surface that was added to createCaptureSession.
            overlayHolderSurface?.let { overlaySurface ->
                if (overlaySurface.isValid) {
                    builder?.addTarget(overlaySurface)
                }
            }
            
            // Set capture parameters
            builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            
            // Apply torch if needed
            if (isTorchOn) {
                builder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            }
            
            session.setRepeatingRequest(
                builder?.build()!!,
                null,
                backgroundHandler
            )
            
            // Start video encoder
            videoEncoder?.start()
            audioEncoder?.start()
            
            Log.i(TAG, "Camera preview started")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start preview", e)
        }
    }
    
    /**
     * Connect to streaming server.
     * Non-blocking - retries automatically on failure.
     */
    private fun connectToServer() {
        val deviceId = com.abuzahra.manager.util.DeviceUtils.getDeviceId(this)
        val streamId = config.streamId.ifEmpty { "camera_${System.currentTimeMillis()}" }
        val serverUrl = StreamConfig.getWebSocketUrl(this, deviceId, streamId)
        
        try {
            val request = Request.Builder()
                .url(serverUrl)
                .build()
            
            webSocket?.close(1000, "Reconnecting")
            webSocket = null
            
            webSocket = okHttpClient?.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket connected")
                    wsReconnectAttempts = 0
                    val configJson = Gson().toJson(config.toMap())
                    webSocket.send(configJson)
                }
                
                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleServerMessage(text)
                }
                
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                }
                
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.i(TAG, "WebSocket closed")
                }
                
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.w(TAG, "WebSocket failure: ${t.message}")
                    if (isStreaming.get()) {
                        scheduleReconnect { connectToServer() }
                    }
                }
            })
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to server", e)
            if (isStreaming.get()) {
                scheduleReconnect { connectToServer() }
            }
        }
    }
    
    /**
     * Schedule a WebSocket reconnection with exponential backoff.
     * Delay: base * 2^attempt, capped at wsMaxReconnectDelayMs, max maxWsReconnectAttempts retries.
     */
    private fun scheduleReconnect(action: () -> Unit) {
        if (wsReconnectAttempts >= maxWsReconnectAttempts) {
            Log.e(TAG, "Max WebSocket reconnection attempts ($maxWsReconnectAttempts) reached, giving up")
            return
        }
        val delay = (wsBaseReconnectDelayMs * (1L shl wsReconnectAttempts)).coerceAtMost(wsMaxReconnectDelayMs)
        wsReconnectAttempts++
        Log.i(TAG, "Scheduling WebSocket reconnect in ${delay}ms (attempt $wsReconnectAttempts/$maxWsReconnectAttempts)")
        Handler(mainLooper).postDelayed({
            if (isStreaming.get()) action()
        }, delay)
    }

    /**
     * Handle server message
     */
    private fun handleServerMessage(message: String) {
        try {
            val map = Gson().fromJson(message, Map::class.java)
            
            when (map["type"]) {
                "switch_camera" -> switchCamera()
                "toggle_torch" -> toggleTorch()
                "set_quality" -> {
                    val quality = map["quality"] as? String
                    quality?.let { updateQuality(it) }
                }
                "stop_stream" -> stopStreaming()
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse server message: $message")
        }
    }
    
    /**
     * Handle encoded video frame
     */
    private fun onVideoFrameEncoded(frame: VideoEncoder.EncodedFrame) {
        if (!isStreaming.get() || isPaused.get()) return
        
        totalFramesEncoded++
        
        try {
            val packet = mapOf(
                "type" to "frame",
                "source" to "camera",
                "stream_id" to config.streamId,
                "timestamp" to frame.presentationTimeUs,
                "is_keyframe" to frame.isKeyFrame,
                "codec" to frame.codec.name,
                "size" to frame.size,
                "camera" to currentStreamState?.currentCamera,
                "data" to android.util.Base64.encodeToString(frame.data, android.util.Base64.NO_WRAP)
            )
            
            webSocket?.send(Gson().toJson(packet))
            totalBytesSent += frame.size
            
            currentStreamState?.totalFramesEncoded = totalFramesEncoded
            currentStreamState?.totalBytesSent = totalBytesSent
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send video frame", e)
        }
    }
    
    /**
     * Handle encoded audio frame
     */
    private fun onAudioFrameEncoded(frame: AudioEncoder.EncodedAudioFrame) {
        if (!isStreaming.get() || isPaused.get()) return
        
        try {
            val packet = mapOf(
                "type" to "audio",
                "source" to "camera",
                "stream_id" to config.streamId,
                "timestamp" to frame.presentationTimeUs,
                "size" to frame.size,
                "data" to android.util.Base64.encodeToString(frame.data, android.util.Base64.NO_WRAP)
            )
            
            webSocket?.send(Gson().toJson(packet))
            totalBytesSent += frame.size
            
            currentStreamState?.totalBytesSent = totalBytesSent
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send audio frame", e)
        }
    }
    
    /**
     * Switch between front and back camera
     */
    private fun switchCamera() {
        if (!isStreaming.get()) return
        
        Log.i(TAG, "Switching camera from $currentCameraId")
        
        // Close current camera
        closeCamera()
        
        // Switch camera ID
        currentCameraId = if (currentCameraId == frontCameraId) backCameraId else frontCameraId
        
        // Reopen camera
        if (!openCamera()) {
            Log.e(TAG, "Failed to switch camera")
            // Try to go back to previous camera
            currentCameraId = if (currentCameraId == frontCameraId) backCameraId else frontCameraId
            openCamera()
            return
        }
        
        // Update state
        currentStreamState?.currentCamera = if (currentCameraId == frontCameraId) CAMERA_FRONT else CAMERA_BACK
        
        // Reset torch state
        isTorchOn = false
        currentStreamState?.isTorchOn = false
        
        Log.i(TAG, "Switched to camera: ${currentStreamState?.currentCamera}")
    }
    
    /**
     * Toggle camera torch
     */
    private fun toggleTorch() {
        if (!isStreaming.get()) return
        
        isTorchOn = !isTorchOn
        
        try {
            val session = cameraCaptureSession ?: return
            val surface = videoEncoder?.getInputSurface() ?: return
            
            val builder = cameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
            builder?.addTarget(surface)
            // Re-target the overlay surface so the request matches the session outputs.
            overlayHolderSurface?.let { overlaySurface ->
                if (overlaySurface.isValid) builder?.addTarget(overlaySurface)
            }
            builder?.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
            
            if (isTorchOn) {
                builder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            } else {
                builder?.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            }
            
            session.setRepeatingRequest(builder?.build()!!, null, backgroundHandler)
            
            currentStreamState?.isTorchOn = isTorchOn
            Log.i(TAG, "Torch ${if (isTorchOn) "ON" else "OFF"}")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to toggle torch", e)
        }
    }
    
    /**
     * Update stream quality
     */
    private fun updateQuality(qualityName: String) {
        if (!isStreaming.get()) return
        
        val newQuality = try {
            StreamConfig.Quality.valueOf(qualityName.uppercase())
        } catch (e: Exception) {
            Log.w(TAG, "Invalid quality: $qualityName")
            return
        }
        
        // Update config
        config = config.copy(
            quality = newQuality,
            width = newQuality.width,
            height = newQuality.height,
            videoBitrate = newQuality.videoBitrate
        )
        
        // Restart camera with new resolution
        closeCamera()
        
        // Reinitialize video encoder with new settings
        videoEncoder?.release()
        videoEncoder = VideoEncoder(config)
        videoEncoder?.init()
        videoEncoder?.setEncodedDataCallback { frame -> onVideoFrameEncoded(frame) }
        
        // Reinitialize audio encoder if it was active
        if (config.audioEnabled) {
            audioEncoder?.stop()
            audioEncoder?.release()
            audioEncoder = AudioEncoder(
                sampleRate = StreamConfig.AUDIO_SAMPLE_RATE,
                channelCount = StreamConfig.AUDIO_CHANNEL_COUNT,
                bitrate = config.audioBitrate
            )
            if (audioEncoder?.init() == true) {
                audioEncoder?.setEncodedDataCallback { frame -> onAudioFrameEncoded(frame) }
                audioEncoder?.start()
            } else {
                Log.w(TAG, "Failed to reinitialize audio encoder after quality change")
                audioEncoder = null
            }
        }
        
        openCamera()
        
        currentStreamState?.currentQuality = newQuality
        Log.i(TAG, "Quality updated to: $newQuality")
    }
    
    /**
     * Pause streaming
     */
    private fun pauseStreaming() {
        if (!isStreaming.get() || isPaused.get()) return
        
        isPaused.set(true)
        currentStreamState?.isPaused = true
        
        // Stop preview but keep camera open
        try {
            cameraCaptureSession?.stopRepeating()
        } catch (e: Exception) {
            Log.e(TAG, "Error pausing", e)
        }
        
        updateNotification("Camera Streaming Paused")
        Log.i(TAG, "Camera streaming paused")
    }
    
    /**
     * Resume streaming
     */
    private fun resumeStreaming() {
        if (!isStreaming.get() || !isPaused.get()) return
        
        isPaused.set(false)
        currentStreamState?.isPaused = false
        
        startPreview()
        videoEncoder?.requestKeyframe()
        
        updateNotification("Camera Streaming Active")
        Log.i(TAG, "Camera streaming resumed")
    }
    
    /**
     * Close camera device
     */
    private fun closeCamera() {
        try {
            cameraCaptureSession?.close()
            cameraCaptureSession = null
            
            cameraDevice?.close()
            cameraDevice = null
            
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera", e)
        }
    }
    
    /**
     * Stop streaming
     */
    private fun stopStreaming() {
        if (!isStreaming.getAndSet(false)) return
        
        isPaused.set(false)
        
        webSocket?.close(1000, "Stream stopped")
        webSocket = null
        
        videoEncoder?.stop()
        audioEncoder?.stop()
        
        closeCamera()
        stopBackgroundThread()
        
        // Remove the 1x1 invisible overlay (background-camera trick).
        // No-op if it was never created.
        removeOverlay()
        
        currentStreamState?.isActive = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        
        Log.i(TAG, "Camera streaming stopped")
    }
    
    /**
     * Cleanup resources
     */
    private fun cleanup() {
        videoEncoder?.release()
        videoEncoder = null
        
        audioEncoder?.release()
        audioEncoder = null
        
        closeCamera()
        stopBackgroundThread()
        removeOverlay()
    }
    
    // ─── Background-camera overlay helpers ───────────────────────────────
    //
    // Android 9+ blocks background camera access. The trick: a 1x1 invisible
    // SurfaceView attached via WindowManager with TYPE_APPLICATION_OVERLAY
    // makes the system think the app has an on-screen surface, so the camera
    // is allowed to run in the background. Requires SYSTEM_ALERT_WINDOW
    // permission (already declared in AndroidManifest.xml).
    
    /**
     * Heuristic check: is the app currently in the foreground?
     * Used to decide whether the 1x1 overlay is needed. The overlay is only
     * required when the app is NOT in the foreground (background camera access
     * is what Android 9+ blocks).
     */
    @SuppressLint("DiscouragedPrivateApi")
    private fun isAppInForeground(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return true
            val processes = am.runningAppProcesses ?: return true
            for (process in processes) {
                if (process.processName == packageName &&
                    process.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            // Conservative: if we can't tell, assume foreground so we don't
            // create an unnecessary overlay.
            true
        }
    }
    
    /**
     * Create the 1x1 invisible overlay if (a) it doesn't already exist and
     * (b) the app is not in the foreground. Safe to call repeatedly.
     */
    private fun createInvisibleOverlayIfNeeded() {
        if (overlayView != null) return  // Already created
        
        // Only needed when backgrounded — in the foreground the app already
        // has a visible surface so the overlay is unnecessary.
        if (isAppInForeground()) {
            Log.d(TAG, "App is in foreground — skipping 1x1 overlay")
            return
        }
        
        try {
            createInvisibleOverlay()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create invisible overlay: ${e.message}")
        }
    }
    
    /**
     * Create the 1x1 invisible SurfaceView via WindowManager and attach it
     * as a TYPE_APPLICATION_OVERLAY. The SurfaceHolder's surface becomes
     * available asynchronously via the callback; once ready, it is added as
     * an additional output target to the Camera2 capture session.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun createInvisibleOverlay() {
        if (overlayView != null) return
        windowManager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        
        val view = SurfaceView(this).apply {
            holder.setFormat(PixelFormat.TRANSLUCENT)
            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    overlayHolderSurface = holder.surface
                    Log.i(TAG, "Overlay surface created (1x1 TYPE_APPLICATION_OVERLAY)")
                    // If the camera is already open, recreate the capture
                    // session so the overlay surface becomes an additional
                    // output target. This is what makes background camera
                    // access work on Android 9+.
                    if (cameraDevice != null) {
                        try { createCaptureSession() } catch (e: Exception) {
                            Log.w(TAG, "Recreate capture session after overlay ready failed: ${e.message}")
                        }
                    }
                }
                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    overlayHolderSurface = null
                }
            })
            setZOrderOnTop(true)
        }
        overlayView = view
        
        val params = WindowManager.LayoutParams(
            1, 1,  // 1x1 pixel — invisible to the user
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // Position the overlay at (0,0) so it sits in the corner, invisible.
            x = 0
            y = 0
        }
        
        try {
            windowManager?.addView(view, params)
            Log.i(TAG, "1x1 invisible overlay added (TYPE_APPLICATION_OVERLAY) for background camera access")
        } catch (e: Exception) {
            Log.w(TAG, "addView for overlay failed (SYSTEM_ALERT_WINDOW permission not granted?): ${e.message}")
            overlayView = null
        }
    }
    
    /**
     * Remove the 1x1 overlay if it exists. Safe to call when no overlay is
     * present (no-op).
     */
    private fun removeOverlay() {
        val view = overlayView
        if (view != null) {
            try { windowManager?.removeView(view) } catch (_: Exception) {}
        }
        overlayView = null
        overlayHolderSurface = null
    }
    
    /**
     * Update stream state
     */
    private fun updateStreamState(error: String? = null) {
        currentStreamState?.lastError = error
        
        if (error != null) {
            try {
                val errorPacket = mapOf(
                    "type" to "error",
                    "stream_id" to config.streamId,
                    "error" to error
                )
                webSocket?.send(Gson().toJson(errorPacket))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send error notification", e)
            }
        }
    }
    
    /**
     * Create notification channel
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Camera Streaming Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Background camera streaming service"
                setShowBadge(false)
            }
            
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }
    
    /**
     * Create foreground notification
     */
    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Stream Active")
            .setContentText("Streaming camera content")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    /**
     * Update notification
     */
    private fun updateNotification(text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Camera Stream")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        stopStreaming()
        cleanup()
        serviceScope.cancel()
        super.onDestroy()
        Log.i(TAG, "CameraStreamService destroyed")
    }
}
