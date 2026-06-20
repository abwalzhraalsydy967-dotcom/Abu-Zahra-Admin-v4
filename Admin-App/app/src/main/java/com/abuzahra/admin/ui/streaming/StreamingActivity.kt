package com.abuzahra.admin.ui.streaming

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.view.animation.AnimationUtils
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.data.api.StreamFrameResponse
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.ActivityStreamingBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Live streaming viewer — FAST HTTP polling implementation.
 *
 * Flow:
 *   1. User taps «بدء البث».
 *   2. UI switches to STATE_CONNECTING ("جاري الاقتران بـ{type}..." + pulsing
 *      spinner overlay).
 *   3. We send the appropriate `start_*_stream` command ONCE to the device:
 *        screen        → start_screen_stream   {quality, fps}
 *        front_camera  → start_camera_stream   {camera: front, quality, fps}
 *        back_camera   → start_camera_stream   {camera: back,  quality, fps}
 *        audio         → start_audio_stream    {source: mic}
 *      The device's StreamService starts and pushes frames continuously via
 *      WebSocket to the server, which stores the latest frame at
 *      `/api/stream/frame/{device}?type=video|audio`.
 *   4. We poll that endpoint every 300 ms (FAST polling).
 *   5. When the first non-empty frame arrives we switch to STATE_LIVE
 *      ("🔴 بث مباشر"), show the red LIVE badge + FPS / frame counter,
 *      and decode each frame's base64 payload into a Bitmap on the
 *      ImageView.
 *   6. We keep polling at 300 ms for live updates.
 *   7. User taps «إيقاف» → we send the matching `stop_*_stream` command,
 *      cancel the polling job, and clear the UI.
 *
 * If no frame arrives within 15 s of connecting we surface a clear
 * "فشل الاتصال - تأكد من أن الجهاز متصل ووافق على الصلاحيات" error.
 */
class StreamingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreamingBinding
    private lateinit var device: Device
    private lateinit var api: ApiService

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Stream type selected by the user. Maps to the device's start_*_stream command. */
    private var streamType: StreamType = StreamType.SCREEN

    /** Quality preset selected by the user. Sent as the `quality` param. */
    private var qualityPreset: QualityPreset = QualityPreset.MEDIUM

    /** Coroutine job that polls /api/stream/frame every 300 ms. */
    private var pollJob: Job? = null

    /** Coroutine job that periodically refreshes the FPS / latency overlay. */
    private var statsRefreshJob: Job? = null

    /** Current state machine value. */
    @Volatile private var state: StreamState = StreamState.IDLE

    // ── Frame statistics ────────────────────────────────────────────
    @Volatile private var framesReceived: Long = 0L
    @Volatile private var audioBytesReceived: Long = 0L
    @Volatile private var lastFrameWallTime: Long = 0L
    @Volatile private var lastFrameTimestamp: String = ""
    @Volatile private var fpsWindowStart: Long = 0L
    @Volatile private var fpsWindowCount: Int = 0
    @Volatile private var displayedFps: Int = 0
    /** Wall-clock time when CONNECTING began — used for the 15 s timeout. */
    @Volatile private var connectStartTime: Long = 0L

    // ════════════════════════════════════════════════════════════════
    // Lifecycle
    // ════════════════════════════════════════════════════════════════

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStreamingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        device = intent.getSerializableExtra(EXTRA_DEVICE) as? Device ?: run {
            finish()
            return
        }

        val prefs = Preferences.getInstance(this)
        api = ApiClient.createWithToken(prefs.serverUrl, prefs.token ?: "")

        setupToolbar()
        setupViews()
        applyState(StreamState.IDLE)
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "البث المباشر"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.subtitle = device.name.ifEmpty { device.model }
        binding.toolbar.setNavigationOnClickListener { stopAndFinish() }
    }

    private fun setupViews() {
        // ── Stream type chips ──
        binding.chipScreen.setOnClickListener {
            streamType = StreamType.SCREEN
            selectStreamChip(binding.chipScreen)
        }
        binding.chipFrontCamera.setOnClickListener {
            streamType = StreamType.FRONT_CAMERA
            selectStreamChip(binding.chipFrontCamera)
        }
        binding.chipBackCamera.setOnClickListener {
            streamType = StreamType.BACK_CAMERA
            selectStreamChip(binding.chipBackCamera)
        }
        binding.chipAudio.setOnClickListener {
            streamType = StreamType.AUDIO
            selectStreamChip(binding.chipAudio)
        }
        binding.chipScreen.isChecked = true
        selectStreamChip(binding.chipScreen)

        // ── Quality chips ──
        binding.chipQualityLow.setOnClickListener {
            qualityPreset = QualityPreset.LOW
            selectQualityChip(binding.chipQualityLow)
        }
        binding.chipQualityMedium.setOnClickListener {
            qualityPreset = QualityPreset.MEDIUM
            selectQualityChip(binding.chipQualityMedium)
        }
        binding.chipQualityHigh.setOnClickListener {
            qualityPreset = QualityPreset.HIGH
            selectQualityChip(binding.chipQualityHigh)
        }
        binding.chipQualityUltra.setOnClickListener {
            qualityPreset = QualityPreset.ULTRA
            selectQualityChip(binding.chipQualityUltra)
        }
        binding.chipQualityMedium.isChecked = true
        selectQualityChip(binding.chipQualityMedium)

        // ── Start / Stop ──
        binding.btnStartStream.setOnClickListener { startStreaming() }
        binding.btnStopStream.setOnClickListener { stopStreaming() }
    }

    private fun selectStreamChip(selected: com.google.android.material.chip.Chip) {
        listOf(
            binding.chipScreen, binding.chipFrontCamera,
            binding.chipBackCamera, binding.chipAudio
        ).forEach { it.isChecked = (it == selected) }
    }

    private fun selectQualityChip(selected: com.google.android.material.chip.Chip) {
        listOf(
            binding.chipQualityLow, binding.chipQualityMedium,
            binding.chipQualityHigh, binding.chipQualityUltra
        ).forEach { it.isChecked = (it == selected) }
    }

    // ════════════════════════════════════════════════════════════════
    // Start / Stop
    // ════════════════════════════════════════════════════════════════

    private fun startStreaming() {
        if (state != StreamState.IDLE && state != StreamState.ERROR) return
        if (!device.isOnline) {
            Snackbar.make(binding.root, "الجهاز غير متصل", Snackbar.LENGTH_LONG).show()
            return
        }

        // Reset frame stats for this session.
        framesReceived = 0L
        audioBytesReceived = 0L
        lastFrameWallTime = 0L
        lastFrameTimestamp = ""
        fpsWindowCount = 0
        displayedFps = 0
        connectStartTime = System.currentTimeMillis()

        applyState(StreamState.CONNECTING)
        binding.tvConnectingText.text = "جاري الاقتران بـ ${streamType.label}..."
        binding.tvConnectingHint.text = when (streamType) {
            StreamType.AUDIO -> "يتم تشغيل خدمة البث الصوتي على الجهاز"
            else -> "يتم تشغيل خدمة البث وترميز الفيديو على الجهاز"
        }

        val (command, params) = buildStartCommand()
        val request = SendCommandRequest(command, params)

        // 1. Send the start command ONCE.
        // 2. As soon as the server accepts it, begin FAST polling.
        lifecycleScope.launch {
            try {
                api.sendCommand(device.id, request)
            } catch (e: Exception) {
                showError("فشل إرسال أمر البث: ${e.message ?: "خطأ غير معروف"}")
                return@launch
            }
            startFramePolling()
        }
    }

    /**
     * Build the (command, params) pair for the selected stream type + quality.
     *
     * The device's StreamExecutor accepts the following param keys:
     *   - quality: 480p|720p|1080p|1440p  (lowercase, per task spec)
     *   - fps:    15|30|60
     *   - camera: front|back   (start_camera_stream only)
     *   - source: mic           (start_audio_stream only)
     */
    private fun buildStartCommand(): Pair<String, Map<String, String>> {
        val quality = qualityPreset.deviceValue.lowercase()
        val fps = qualityPreset.fps.toString()
        return when (streamType) {
            StreamType.SCREEN ->
                "start_screen_stream" to mapOf("quality" to quality, "fps" to fps)
            StreamType.FRONT_CAMERA ->
                "start_camera_stream" to
                    mapOf("camera" to "front", "quality" to quality, "fps" to fps)
            StreamType.BACK_CAMERA ->
                "start_camera_stream" to
                    mapOf("camera" to "back", "quality" to quality, "fps" to fps)
            StreamType.AUDIO ->
                "start_audio_stream" to mapOf("source" to "mic")
        }
    }

    /**
     * Poll /api/stream/frame/{deviceId}?type=video|audio every [POLL_INTERVAL_MS]
     * until the activity is destroyed or the user stops the stream.
     *
     * - Skips frames whose server-side timestamp hasn't changed (avoids
     *   re-decoding the same JPEG).
     * - Switches to STATE_LIVE on the first non-empty frame.
     * - Times out after [CONNECT_TIMEOUT_MS] ms in CONNECTING with a clear
     *   error message.
     */
    private fun startFramePolling() {
        pollJob?.cancel()
        val pollType = if (streamType == StreamType.AUDIO) "audio" else "video"
        pollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive) {
                // Connecting timeout — no frame within 15 s.
                if (state == StreamState.CONNECTING &&
                    System.currentTimeMillis() - connectStartTime > CONNECT_TIMEOUT_MS
                ) {
                    withContext(Dispatchers.Main) {
                        showError(
                            "فشل الاتصال - تأكد من أن الجهاز متصل ووافق على الصلاحيات"
                        )
                    }
                    return@launch
                }
                if (state != StreamState.CONNECTING && state != StreamState.LIVE) return@launch

                try {
                    val frame = api.getStreamFrame(device.id, pollType)
                    if (frame.ok && frame.data.isNotEmpty() &&
                        frame.timestamp != lastFrameTimestamp
                    ) {
                        lastFrameTimestamp = frame.timestamp
                        // Decode the JPEG on the IO thread, then post the
                        // resulting Bitmap to the main thread for display.
                        val bitmap: Bitmap? =
                            if (streamType != StreamType.AUDIO) decodeBase64Bitmap(frame.data)
                            else null
                        withContext(Dispatchers.Main) {
                            onFrameReceived(frame, bitmap)
                        }
                    }
                } catch (_: Exception) {
                    // Network blips are expected during the connecting phase
                    // (the device may still be booting its StreamService).
                    // Stay quiet and keep polling.
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Decode a base64-encoded JPEG payload into a Bitmap. */
    private fun decodeBase64Bitmap(data: String): Bitmap? {
        return try {
            val bytes = Base64.decode(data, Base64.NO_WRAP)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Called on the main thread for every freshly-polled frame.
     *
     * - First frame: transitions CONNECTING → LIVE and starts the stats
     *   refresh loop.
     * - Subsequent frames: updates the ImageView (video) or byte counter
     *   (audio) and bumps the frame counter for FPS calculation.
     */
    private fun onFrameReceived(frame: StreamFrameResponse, bitmap: Bitmap?) {
        if (state == StreamState.CONNECTING) {
            applyState(StreamState.LIVE)
            startStatsRefresh()
        }
        if (state != StreamState.LIVE) return

        framesReceived++
        fpsWindowCount++
        lastFrameWallTime = System.currentTimeMillis()

        if (streamType == StreamType.AUDIO) {
            // AAC chunks can't be rendered as a bitmap — track bytes and
            // show the pulsing mic overlay (driven by renderState).
            val approxBytes = frame.data.length / 2
            audioBytesReceived += approxBytes.toLong()
            binding.tvFrameInfo.text = "AAC • ${approxBytes}B • ${frame.source.ifEmpty { "mic" }}"
            binding.tvFrameInfo.visibility = View.VISIBLE
            return
        }

        if (bitmap != null) {
            binding.imageView.visibility = View.VISIBLE
            binding.imageView.setImageBitmap(bitmap)
            binding.tvResolution.text = "${bitmap.width} × ${bitmap.height}"
            val approxBytes = frame.data.length / 2
            binding.tvFrameInfo.text = "JPEG • ${approxBytes}B"
            binding.tvFrameInfo.visibility = View.VISIBLE
        } else {
            // Decoding failed — the device may be pushing non-JPEG payloads
            // (e.g. raw H264 NALs). Surface an honest byte count instead of
            // leaving the viewport blank.
            val approxBytes = frame.data.length / 2
            binding.tvFrameInfo.text = "Frame • ${approxBytes}B • (غير قابل للعرض كصورة)"
            binding.tvFrameInfo.visibility = View.VISIBLE
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Stats refresh
    // ════════════════════════════════════════════════════════════════

    private fun startStatsRefresh() {
        statsRefreshJob?.cancel()
        fpsWindowStart = System.currentTimeMillis()
        fpsWindowCount = 0
        statsRefreshJob = lifecycleScope.launch {
            while (isActive && state == StreamState.LIVE) {
                delay(500)
                val now = System.currentTimeMillis()
                val elapsed = now - fpsWindowStart
                if (elapsed > 0) {
                    val instantFps = (fpsWindowCount * 1000.0 / elapsed).toInt()
                    // Smooth the displayed value to avoid jitter.
                    displayedFps = if (displayedFps == 0) instantFps
                    else (displayedFps * 0.6 + instantFps * 0.4).toInt()
                }
                fpsWindowStart = now
                fpsWindowCount = 0

                val latencyMs = if (lastFrameWallTime > 0)
                    (now - lastFrameWallTime).coerceAtLeast(0) else -1L
                mainHandler.post {
                    if (state != StreamState.LIVE) return@post
                    binding.tvFps.text = "FPS: $displayedFps"
                    binding.tvLatency.text = if (latencyMs >= 0)
                        "Latency: ${latencyMs}ms" else "Latency: -- ms"
                    binding.tvFrameCount.text = "Frames: $framesReceived"
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════
    // Stop
    // ════════════════════════════════════════════════════════════════

    private fun stopStreaming() {
        if (state == StreamState.IDLE) return
        applyState(StreamState.STOPPING)

        // Cancel polling + stats refresh.
        pollJob?.cancel()
        pollJob = null
        statsRefreshJob?.cancel()
        statsRefreshJob = null

        // Reset frame stats.
        framesReceived = 0L
        audioBytesReceived = 0L
        lastFrameWallTime = 0L
        lastFrameTimestamp = ""
        fpsWindowCount = 0
        displayedFps = 0

        // Clear the viewport.
        binding.imageView.setImageDrawable(null)
        binding.imageView.visibility = View.GONE

        // Send the matching stop command (best-effort, fire-and-forget).
        val stopCommand = when (streamType) {
            StreamType.SCREEN -> "stop_screen_stream"
            StreamType.FRONT_CAMERA, StreamType.BACK_CAMERA -> "stop_camera_stream"
            StreamType.AUDIO -> "stop_audio_stream"
        }
        lifecycleScope.launch {
            try {
                api.sendCommand(device.id, SendCommandRequest(stopCommand))
            } catch (_: Exception) {
                // Best-effort — the device's StreamService also self-stops
                // when the server stops receiving frames.
            }
        }

        applyState(StreamState.IDLE)
    }

    private fun stopAndFinish() {
        stopStreaming()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        pollJob?.cancel()
        statsRefreshJob?.cancel()
    }

    // ════════════════════════════════════════════════════════════════
    // State machine + UI
    // ════════════════════════════════════════════════════════════════

    private fun applyState(newState: StreamState) {
        state = newState
        // Run renderState synchronously when already on the main thread
        // (avoids a posted renderState clobbering UI updates made by the
        // caller — e.g. onFrameReceived flipping imageView to VISIBLE).
        if (Looper.myLooper() == Looper.getMainLooper()) {
            renderState(newState)
        } else {
            mainHandler.post { renderState(newState) }
        }
    }

    private fun renderState(s: StreamState) {
        // Default: hide everything, then re-show what's relevant.
        binding.connectingOverlay.visibility = View.GONE
        binding.audioOverlay.visibility = View.GONE
        binding.idleOverlay.visibility = View.GONE
        binding.imageView.visibility = View.GONE
        binding.surfaceView.visibility = View.GONE
        binding.liveBadgeContainer.visibility = View.GONE
        binding.statsOverlay.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        binding.tvFrameInfo.visibility = View.GONE
        binding.liveDot.clearAnimation()
        binding.audioPulseRing1.clearAnimation()
        binding.connectingOverlay.clearAnimation()

        when (s) {
            StreamState.IDLE -> {
                binding.idleOverlay.visibility = View.VISIBLE
                binding.tvStatus.text = "جاهز للبث"
                binding.tvStatus.setTextColor(
                    ContextCompat.getColor(this, R.color.text_secondary)
                )
                binding.btnStartStream.isEnabled = true
                binding.btnStopStream.isEnabled = false
                enableChipSelection(true)
            }
            StreamState.CONNECTING -> {
                binding.connectingOverlay.visibility = View.VISIBLE
                binding.progressBar.visibility = View.VISIBLE
                binding.tvStatus.text = "جاري الاقتران بـ ${streamType.label}..."
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                binding.btnStartStream.isEnabled = false
                binding.btnStopStream.isEnabled = true
                enableChipSelection(false)
                val pulse = AnimationUtils.loadAnimation(this, R.anim.pulse_connecting)
                binding.connectingOverlay.startAnimation(pulse)
            }
            StreamState.LIVE -> {
                binding.liveBadgeContainer.visibility = View.VISIBLE
                binding.statsOverlay.visibility = View.VISIBLE
                binding.tvStatus.text = "🔴 بث مباشر — ${streamType.label}"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.btnStartStream.isEnabled = false
                binding.btnStopStream.isEnabled = true
                enableChipSelection(false)
                // Blink the LIVE dot.
                val blink = AnimationUtils.loadAnimation(this, R.anim.pulse_live_dot)
                binding.liveDot.startAnimation(blink)
                if (streamType == StreamType.AUDIO) {
                    binding.audioOverlay.visibility = View.VISIBLE
                    val ring = AnimationUtils.loadAnimation(this, R.anim.pulse_audio_ring)
                    binding.audioPulseRing1.startAnimation(ring)
                    binding.tvFrameInfo.visibility = View.VISIBLE
                    binding.tvFrameInfo.text = "AAC • بانتظار الإطارات..."
                } else {
                    // imageView visibility will be flipped to VISIBLE by
                    // onFrameReceived() once the first bitmap is decoded.
                    binding.tvFrameInfo.visibility = View.VISIBLE
                    binding.tvFrameInfo.text = "JPEG • بانتظار الإطارات..."
                }
            }
            StreamState.STOPPING -> {
                binding.progressBar.visibility = View.VISIBLE
                binding.tvStatus.text = "تم إيقاف البث"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.warning))
                binding.btnStartStream.isEnabled = false
                binding.btnStopStream.isEnabled = false
                enableChipSelection(false)
            }
            StreamState.ERROR -> {
                binding.idleOverlay.visibility = View.VISIBLE
                binding.tvStatus.text = "تعذّر تشغيل البث"
                binding.tvStatus.setTextColor(ContextCompat.getColor(this, R.color.error))
                binding.btnStartStream.isEnabled = true
                binding.btnStopStream.isEnabled = false
                enableChipSelection(true)
            }
        }
    }

    private fun enableChipSelection(enabled: Boolean) {
        binding.chipScreen.isEnabled = enabled
        binding.chipFrontCamera.isEnabled = enabled
        binding.chipBackCamera.isEnabled = enabled
        binding.chipAudio.isEnabled = enabled
        binding.chipQualityLow.isEnabled = enabled
        binding.chipQualityMedium.isEnabled = enabled
        binding.chipQualityHigh.isEnabled = enabled
        binding.chipQualityUltra.isEnabled = enabled
    }

    private fun showError(message: String) {
        pollJob?.cancel()
        pollJob = null
        statsRefreshJob?.cancel()
        applyState(StreamState.ERROR)
        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
    }

    // ════════════════════════════════════════════════════════════════
    // Enums + constants
    // ════════════════════════════════════════════════════════════════

    private enum class StreamType(val label: String) {
        SCREEN("بث الشاشة"),
        FRONT_CAMERA("الكاميرا الأمامية"),
        BACK_CAMERA("الكاميرا الخلفية"),
        AUDIO("بث الصوت")
    }

    private enum class QualityPreset(val deviceValue: String, val fps: Int) {
        LOW("480P", 15),
        MEDIUM("720P", 30),
        HIGH("1080P", 30),
        ULTRA("1440P", 60)
    }

    private enum class StreamState {
        IDLE, CONNECTING, LIVE, STOPPING, ERROR
    }

    companion object {
        /** FAST polling interval — 300 ms. */
        private const val POLL_INTERVAL_MS = 300L
        /** Give up connecting if no frame arrives within 15 s. */
        private const val CONNECT_TIMEOUT_MS = 15_000L
        const val EXTRA_DEVICE = "extra_device"

        fun newIntent(context: Context, device: Device): Intent {
            return Intent(context, StreamingActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
            }
        }
    }
}
