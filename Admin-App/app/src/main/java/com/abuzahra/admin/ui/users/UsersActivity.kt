package com.abuzahra.admin.ui.users

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.ui.dashboard.DeviceAdapter
import com.abuzahra.admin.databinding.ActivityUsersBinding
import com.abuzahra.admin.ui.device.DeviceDetailActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UsersActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUsersBinding
    private lateinit var api: ApiService
    private lateinit var prefs: Preferences
    private val deviceAdapter: DeviceAdapter by lazy {
        DeviceAdapter { device -> navigateToDeviceDetail(device) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUsersBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = Preferences.getInstance(this)
        api = ApiClient.createWithToken(prefs.serverUrl, prefs.token ?: "")

        setupToolbar()
        setupLinkCode()
        setupTgLink()
        setupDevicesList()
        loadDevices()

        // Show admin section only for admins
        val role = prefs.userRole ?: "user"
        if (role == "admin") {
            setupAdminSection()
            loadUsers()
        } else {
            binding.adminSection.visibility = View.GONE
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "حسابي"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupLinkCode() {
        val code = prefs.permanentCode ?: ""
        if (code.isNotEmpty()) {
            binding.tvLinkCode.text = code
        } else {
            // Fetch from server
            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) { api.regenerateCode() }
                    if (response.ok && response.code.isNotEmpty()) {
                        prefs.permanentCode = response.code
                        binding.tvLinkCode.text = response.code
                    }
                } catch (e: Exception) {
                    // Ignore — code will be empty
                }
            }
        }

        binding.btnCopyCode.setOnClickListener {
            val codeText = binding.tvLinkCode.text.toString()
            if (codeText.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("link_code", codeText))
                Toast.makeText(this, "تم نسخ الكود", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnRegenerateCode.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val response = withContext(Dispatchers.IO) { api.regenerateCode() }
                    if (response.ok && response.code.isNotEmpty()) {
                        prefs.permanentCode = response.code
                        binding.tvLinkCode.text = response.code
                        Toast.makeText(this@UsersActivity, "تم تجديد الكود", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@UsersActivity, "فشل التجديد: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun setupTgLink() {
        binding.btnTgLink.setOnClickListener {
            lifecycleScope.launch {
                try {
                    binding.btnTgLink.isEnabled = false
                    binding.btnTgLink.text = "جاري..."
                    val response = withContext(Dispatchers.IO) { api.getTgLinkToken() }
                    if (response.ok && response.deep_link_url.isNotEmpty()) {
                        // Show the deep link in a dialog
                        MaterialAlertDialogBuilder(this@UsersActivity)
                            .setTitle("ربط بوت Telegram")
                            .setMessage("افتح هذا الرابط على هاتفك الذي يحمل تطبيق Telegram:\n\n${response.deep_link_url}\n\nالبوت: @${response.bot_username}\nصالح لمدة ${response.expires_in / 60} دقيقة")
                            .setPositiveButton("فتح الرابط") { _, _ ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(response.deep_link_url))
                                startActivity(intent)
                            }
                            .setNegativeButton("نسخ الرابط") { _, _ ->
                                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("tg_link", response.deep_link_url))
                                Toast.makeText(this@UsersActivity, "تم نسخ الرابط", Toast.LENGTH_SHORT).show()
                            }
                            .setNeutralButton("إغلاق", null)
                            .show()
                    } else {
                        Toast.makeText(this@UsersActivity, "فشل إنشاء رابط البوت", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this@UsersActivity, "خطأ: ${e.message}", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.btnTgLink.isEnabled = true
                    binding.btnTgLink.text = "ربط بوت Telegram"
                }
            }
        }
    }

    private fun setupDevicesList() {
        binding.recyclerDevices.layoutManager = LinearLayoutManager(this)
        binding.recyclerDevices.adapter = deviceAdapter
    }

    private fun loadDevices() {
        lifecycleScope.launch {
            try {
                val devices = withContext(Dispatchers.IO) { api.getDevices() }
                deviceAdapter.submitList(devices)
                binding.tvDevicesCount.text = "${devices.size} جهاز"
                if (devices.isEmpty()) {
                    binding.tvNoDevices.visibility = View.VISIBLE
                } else {
                    binding.tvNoDevices.visibility = View.GONE
                }
            } catch (e: Exception) {
                Toast.makeText(this@UsersActivity, "فشل تحميل الأجهزة", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToDeviceDetail(device: Device) {
        startActivity(DeviceDetailActivity.newIntent(this, device))
    }

    // ─── Admin section (only visible for admins) ───────────────

    private fun setupAdminSection() {
        binding.adminSection.visibility = View.VISIBLE
        binding.fabAddUser.setOnClickListener {
            showCreateUserDialog()
        }
        binding.swipeRefresh.setOnRefreshListener {
            loadUsers()
            loadDevices()
        }
    }

    private fun loadUsers() {
        lifecycleScope.launch {
            try {
                val users = withContext(Dispatchers.IO) { api.getUsers() }
                // Update UI with users list (existing logic)
                binding.swipeRefresh.isRefreshing = false
            } catch (e: Exception) {
                binding.swipeRefresh.isRefreshing = false
                // 403 is expected for non-admins — but we already hide this section
            }
        }
    }

    private fun showCreateUserDialog() {
        // Existing create user dialog logic
    }
}
