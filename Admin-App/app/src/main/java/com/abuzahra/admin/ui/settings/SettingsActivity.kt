package com.abuzahra.admin.ui.settings

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.databinding.ActivitySettingsBinding
import com.abuzahra.admin.ui.dashboard.DashboardActivity
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        loadPreferences()
        setupListeners()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.settings)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun loadPreferences() {
        // Server URL
        binding.etServerUrl.setText(prefs.serverUrl)

        // Notification toggles
        binding.switchNotifications.isChecked = prefs.notificationsEnabled
        binding.switchOnlineNotif.isChecked = prefs.onlineNotifications
        binding.switchOfflineNotif.isChecked = prefs.offlineNotifications
        binding.switchEventNotif.isChecked = prefs.eventNotifications

        // Update child switch enabled states based on master
        updateNotificationSwitchesEnabled(prefs.notificationsEnabled)

        // Dark mode
        binding.switchDarkMode.isChecked = prefs.darkMode
    }

    private fun setupListeners() {
        // Server URL - save on focus lost
        binding.etServerUrl.setOnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                saveServerUrl()
            }
        }
        binding.tilServerUrl.setEndIconOnClickListener {
            saveServerUrl()
            Toast.makeText(this, "تم حفظ رابط الخادم", Toast.LENGTH_SHORT).show()
        }

        // Master notifications toggle
        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefs.notificationsEnabled = isChecked
            updateNotificationSwitchesEnabled(isChecked)
        }

        // Individual notification toggles
        binding.switchOnlineNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.onlineNotifications = isChecked
        }
        binding.switchOfflineNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.offlineNotifications = isChecked
        }
        binding.switchEventNotif.setOnCheckedChangeListener { _, isChecked ->
            prefs.eventNotifications = isChecked
        }

        // Dark mode toggle
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            prefs.darkMode = isChecked
            Toast.makeText(this, "سيتم تطبيق الوضع الداكن عند إعادة فتح التطبيق", Toast.LENGTH_SHORT).show()
        }

        // Logout button
        binding.btnLogout.setOnClickListener {
            showLogoutDialog()
        }
    }

    private fun saveServerUrl() {
        val url = binding.etServerUrl.text.toString().trim()
        if (url.isNotEmpty() && url != prefs.serverUrl) {
            prefs.serverUrl = if (url.endsWith("/")) url else "$url/"
            Toast.makeText(this, "تم حفظ رابط الخادم", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateNotificationSwitchesEnabled(enabled: Boolean) {
        binding.switchOnlineNotif.isEnabled = enabled
        binding.switchOfflineNotif.isEnabled = enabled
        binding.switchEventNotif.isEnabled = enabled
    }

    private fun showLogoutDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.logout)
            .setMessage(R.string.logout_confirm)
            .setPositiveButton(R.string.confirm) { _, _ ->
                prefs.clear()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    override fun onPause() {
        super.onPause()
        // Save server URL when leaving the screen
        saveServerUrl()
    }

    override fun onSupportNavigateUp(): Boolean {
        // Navigate back to dashboard
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
        return true
    }
}