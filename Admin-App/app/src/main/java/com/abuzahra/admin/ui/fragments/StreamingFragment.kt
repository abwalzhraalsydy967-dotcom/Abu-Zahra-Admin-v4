package com.abuzahra.admin.ui.fragments

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.FragmentStreamingBinding
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

/**
 * Streaming fragment — functional copy of the web's StreamingView:
 *  - Device selector
 *  - 4 stream type cards (screen / front_camera / back_camera / audio)
 *  - Quality selector (480p / 720p / 1080p)
 *  - Start → "جارٍ الاقتران..." pulsing state (12s timeout)
 *    → send command → poll frames (300ms for video, 3000ms for audio)
 *    → live view
 *  - Live controls: stop, switch camera, screenshot
 *  - LIVE badge, FPS, latency, resolution
 */
class StreamingFragment : BaseFragment() {

    private var _binding: FragmentStreamingBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    enum class StreamType(val label: String, val startCmd: String, val stopCmd: String) {
        SCREEN("بث الشاشة", "start_screen_stream", "stop_screen_stream"),
        FRONT("الكاميرا الأمامية", "start_camera_stream", "stop_camera_stream"),
        BACK("الكاميرا الخلفية", "start_camera_stream", "stop_camera_stream"),
        AUDIO("بث الصوت", "start_audio_stream", "stop_audio_stream")
    }

    private enum class Status { IDLE, CONNECTING, ACTIVE, STOPPING, ERROR }

    private var status: Status = Status.IDLE
    private var streamType: StreamType? = null
    private var quality: String = "720p"
    private var fps = 0
    private var fpsCount = 0
    private var fpsLastReset = 0L
    private var connectingStartMs = 0L

    private val uiHandler = Handler(Looper.getMainLooper())

    /** Polls the latest frame from /api/stream/frame/{deviceId}?type=... */
    private val framePoller = object : Runnable {
        override fun run() {
            pollFrame()
            val delay = if (streamType == StreamType.AUDIO) 3000L else 300L
            uiHandler.postDelayed(this, delay)
        }
    }

    /** Times out the connecting state after 12s. */
    private val connectingTimeout = object : Runnable {
        override fun run() {
            if (status == Status.CONNECTING) {
                status = Status.ERROR
                showIdle("انتهى وقت الانتظار لأول إطار. تأكد من اتصال الجهاز وصلاحيات البث.")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStreamingBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnChangeDevice.setOnClickListener { showDevicePicker() }
        binding.cardDeviceSelected.setOnClickListener { showDevicePicker() }

        binding.qualityGroup.setOnCheckedStateChangeListener { group, _ ->
            quality = when (group.checkedChipId) {
                R.id.chip480 -> "480p"
                R.id.chip1080 -> "1080p"
                else -> "720p"
            }
        }

        binding.cardStreamScreen.setOnClickListener { startStream(StreamType.SCREEN) }
        binding.cardStreamFront.setOnClickListener { startStream(StreamType.FRONT) }
        binding.cardStreamBack.setOnClickListener { startStream(StreamType.BACK) }
        binding.cardStreamAudio.setOnClickListener { startStream(StreamType.AUDIO) }

        binding.btnStop.setOnClickListener { stopStream() }
        binding.btnSwitchCamera.setOnClickListener { switchCamera() }
        binding.btnScreenshot.setOnClickListener { saveScreenshot() }

        observeViewModel()
        updateSelectedDeviceCard(viewModel.selectedDevice.value)
    }

    private fun observeViewModel() {
        viewModel.selectedDevice.observe(viewLifecycleOwner) { device ->
            updateSelectedDeviceCard(device)
        }
    }

    private fun updateSelectedDeviceCard(device: Device?) {
        if (device == null) {
            binding.tvSelectedDeviceName.text = "لم يتم اختيار جهاز"
            binding.tvSelectedDeviceMeta.text = "اضغط لاختيار جهاز"
        } else {
            binding.tvSelectedDeviceName.text = device.name.ifEmpty { device.model }
            binding.tvSelectedDeviceMeta.text = buildString {
                if (device.brand.isNotEmpty()) append(device.brand)
                if (device.model.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(device.model)
                }
                append(" • ")
                append(if (device.isOnline) "متصل" else "غير متصل")
            }
        }
    }

    private fun showDevicePicker() {
        val devices = (viewModel.devices.value as? Result.Success)?.data ?: emptyList()
        if (devices.isEmpty()) {
            (activity as? MainActivity)?.showSnack(getString(R.string.select_device_first))
            (activity as? MainActivity)?.navigateToView(R.id.nav_devices)
            return
        }
        val labels = devices.map { d ->
            "${d.name.ifEmpty { d.model.ifEmpty { "جهاز" } }}  •  ${if (d.isOnline) "متصل" else "غير متصل"}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.devices)
            .setItems(labels) { _, which ->
                if (which in devices.indices) viewModel.selectDevice(devices[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Stream lifecycle ──────────────────────────────────────────
    private fun startStream(type: StreamType) {
        val device = viewModel.selectedDevice.value
        if (device == null) {
            (activity as? MainActivity)?.showSnack(getString(R.string.select_device_first))
            showDevicePicker()
            return
        }

        if (status == Status.ACTIVE || status == Status.CONNECTING) {
            (activity as? MainActivity)?.showSnack("بث نشط — أوقفه أولاً")
            return
        }

        streamType = type
        status = Status.CONNECTING
        connectingStartMs = System.currentTimeMillis()
        fpsCount = 0
        fpsLastReset = 0L

        // Show connecting overlay
        binding.idleState.visibility = View.GONE
        binding.liveHud.visibility = View.GONE
        binding.liveControls.visibility = View.GONE
        binding.ivFrame.visibility = View.GONE
        binding.connectingState.visibility = View.VISIBLE
        binding.tvConnectingLabel.text = "جارٍ الاقتران بـ${type.label}..."
        binding.tvConnectingMeta.text = "الجودة: $quality • الجهاز: ${device.name}"

        (activity as? MainActivity)?.showSnack("بدء البث: ${type.label}")

        // Send the start command + JPEG stream start in parallel
        viewLifecycleOwner.lifecycleScope.launch {
            // 1. Send the device-side command
            val params = when (type) {
                StreamType.BACK -> mapOf("camera" to "back", "quality" to quality)
                else -> mapOf("camera" to "front", "quality" to quality)
            }
            viewModel.sendCommand(device.id, type.startCmd, params)

            // 2. Tell server to begin polling JPEG frames (audio streams skip this)
            if (type != StreamType.AUDIO) {
                viewModel.startJpegStream(device.id, "video")
            }

            // 3. Start polling frames
            uiHandler.post(framePoller)
        }

        // 4. Schedule timeout
        uiHandler.postDelayed(connectingTimeout, 12000)
    }

    private fun pollFrame() {
        val device = viewModel.selectedDevice.value ?: return
        val type = streamType ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            val fetchType = if (type == StreamType.AUDIO) "audio" else "video"
            val frame = viewModel.fetchStreamFrame(device.id, fetchType) ?: return@launch

            if (frame.ok && frame.data.isNotEmpty()) {
                // Decode + display
                if (type != StreamType.AUDIO) {
                    try {
                        val bytes = Base64.decode(frame.data, Base64.DEFAULT)
                        val bmp: Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        if (bmp != null) {
                            binding.ivFrame.setImageBitmap(bmp)
                            binding.ivFrame.visibility = View.VISIBLE
                        }
                    } catch (_: Exception) { /* ignore decode errors */ }
                }

                // Latency
                val now = System.currentTimeMillis()
                val lat = now - (connectingStartMs)
                binding.tvLatency.text = "Latency: ${lat}ms"

                // FPS counter
                fpsCount++
                if (fpsLastReset == 0L) fpsLastReset = now
                val dt = (now - fpsLastReset) / 1000.0
                if (dt >= 1.0) {
                    fps = (fpsCount / dt).toInt()
                    fpsCount = 0
                    fpsLastReset = now
                    binding.tvFps.text = "FPS: $fps"
                }

                // Resolution: just show stream type for now (decoder parsing is heavy)
                binding.tvResolution.text = quality

                // First frame → transition to ACTIVE
                if (status == Status.CONNECTING) {
                    status = Status.ACTIVE
                    binding.connectingState.visibility = View.GONE
                    binding.liveHud.visibility = View.VISIBLE
                    binding.liveControls.visibility = View.VISIBLE
                    uiHandler.removeCallbacks(connectingTimeout)
                    (activity as? MainActivity)?.showSnack("البث مباشر الآن ✓")
                }
            }
        }
    }

    private fun stopStream() {
        val device = viewModel.selectedDevice.value ?: return
        val type = streamType ?: return

        status = Status.STOPPING
        uiHandler.removeCallbacks(framePoller)
        uiHandler.removeCallbacks(connectingTimeout)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.stopJpegStream(device.id)
            viewModel.sendCommand(device.id, type.stopCmd)
            resetToIdle()
            (activity as? MainActivity)?.showSnack("تم إيقاف البث")
        }
    }

    private fun switchCamera() {
        if (status != Status.ACTIVE) return
        val current = streamType ?: return
        val newType = if (current == StreamType.FRONT) StreamType.BACK else StreamType.FRONT
        streamType = newType
        val device = viewModel.selectedDevice.value ?: return

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.sendCommand(device.id, "switch_camera")
            viewModel.stopJpegStream(device.id)
            viewModel.startJpegStream(device.id, "video")
            binding.ivFrame.setImageDrawable(null)
            (activity as? MainActivity)?.showSnack("تم التبديل إلى ${newType.label}")
        }
    }

    private fun saveScreenshot() {
        binding.ivFrame.drawable?.let { drawable ->
            binding.ivFrame.buildDrawingCache()
            val bmp = binding.ivFrame.drawingCache
            if (bmp != null) {
                // Save to gallery
                try {
                    val filename = "screenshot_${System.currentTimeMillis()}.jpg"
                    val ctx = requireContext()
                    val fos = if (android.os.Build.VERSION.SDK_INT >= 29) {
                        val resolver = ctx.contentResolver
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(
                                android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                                android.os.Environment.DIRECTORY_PICTURES + "/AbuZahra"
                            )
                        }
                        resolver.insert(
                            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            values
                        )?.let { resolver.openOutputStream(it) }
                    } else {
                        @Suppress("DEPRECATION")
                        val dir = android.os.Environment.getExternalStoragePublicDirectory(
                            android.os.Environment.DIRECTORY_PICTURES
                        )
                        val file = java.io.File(dir, "AbuZahra/$filename")
                        file.parentFile?.mkdirs()
                        java.io.FileOutputStream(file)
                    }
                    fos?.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    (activity as? MainActivity)?.showSnack("تم حفظ اللقطة في المعرض")
                } catch (e: Exception) {
                    (activity as? MainActivity)?.showSnack("خطأ في حفظ اللقطة: ${e.message}")
                }
            }
        }
    }

    private fun resetToIdle() {
        status = Status.IDLE
        streamType = null
        binding.connectingState.visibility = View.GONE
        binding.liveHud.visibility = View.GONE
        binding.liveControls.visibility = View.GONE
        binding.ivFrame.visibility = View.GONE
        binding.idleState.visibility = View.VISIBLE
        binding.ivFrame.setImageDrawable(null)
    }

    private fun showIdle(message: String) {
        resetToIdle()
        (activity as? MainActivity)?.showSnack(message)
    }

    override fun onPause() {
        super.onPause()
        // Auto-stop the stream when leaving (matches web's cleanup-on-unmount)
        if (status == Status.ACTIVE || status == Status.CONNECTING) {
            stopStream()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        uiHandler.removeCallbacks(framePoller)
        uiHandler.removeCallbacks(connectingTimeout)
        _binding = null
    }
}
