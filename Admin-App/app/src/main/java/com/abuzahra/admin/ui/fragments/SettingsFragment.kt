package com.abuzahra.admin.ui.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.databinding.FragmentSettingsBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Settings fragment — functional copy of the web's SettingsView:
 *  - Connection: server URL (editable + saved to prefs)
 *  - Appearance: dark theme toggle, notifications toggle
 *  - Data: auto-refresh toggle + refresh interval slider
 *  - System: app version, server status, Firebase status
 *  - Danger zone: clear local data
 *  - Logout button
 */
class SettingsFragment : BaseFragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefs: Preferences

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Preferences.getInstance(requireContext())

        // Load current settings
        binding.etServerUrl.setText(prefs.serverUrl.trimEnd('/'))
        binding.swDarkMode.isChecked = prefs.darkMode
        binding.swNotifications.isChecked = prefs.notificationsEnabled
        binding.swAutoRefresh.isChecked = true
        binding.seekRefreshInterval.progress = 15
        binding.tvRefreshInterval.text = "15s"

        // App version
        try {
            val pkg = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0)
            binding.tvAppVersion.text = "v${pkg.versionName}"
        } catch (_: Exception) {
            binding.tvAppVersion.text = "v4.1.0"
        }

        // Save button
        binding.btnSave.setOnClickListener {
            val url = binding.etServerUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                prefs.serverUrl = if (url.endsWith("/")) url else "$url/"
            }
            prefs.darkMode = binding.swDarkMode.isChecked
            prefs.notificationsEnabled = binding.swNotifications.isChecked
            (activity as? MainActivity)?.showSnack("تم حفظ الإعدادات بنجاح")
        }

        // Refresh interval slider
        binding.seekRefreshInterval.setOnSeekBarChangeListener(object :
            android.widget.SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: android.widget.SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvRefreshInterval.text = "${progress}s"
            }
            override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) {}
        })

        // Clear data
        binding.btnClearData.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("⚠️ مسح البيانات المحلية")
                .setMessage(
                    "سيتم حذف الإعدادات المحفوظة وجلسة تسجيل الدخول. ستحتاج لإعادة تسجيل الدخول.\n\n" +
                            "هل أنت متأكد؟"
                )
                .setPositiveButton("مسح") { _, _ ->
                    prefs.clear()
                    goToLogin()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        // Logout
        binding.btnLogout.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.logout)
                .setMessage(R.string.logout_confirm)
                .setPositiveButton(R.string.confirm) { _, _ ->
                    prefs.clear()
                    goToLogin()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun goToLogin() {
        startActivity(Intent(requireContext(), LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        })
        activity?.finish()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
