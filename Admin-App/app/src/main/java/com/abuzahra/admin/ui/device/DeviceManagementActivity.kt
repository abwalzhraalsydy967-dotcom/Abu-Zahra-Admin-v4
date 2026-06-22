package com.abuzahra.admin.ui.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.api.SendCommandRequest
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.ActivityDeviceManagementBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * DeviceManagementActivity — a dedicated section for managing the client
 * app and device. Each button sends a command to the device via the
 * existing /api/web/send_command endpoint (or, for "unlink", calls the
 * /api/web/unlink/{device_id} endpoint directly).
 *
 * Commands sent (server COMMAND_REGISTRY keys):
 *   - revoke_permissions        (revoke runtime permissions)
 *   - hide_app / show_app       (hide/show the client app icon)
 *   - restart_app               (restart the client app process)
 *   - clear_app_data            (clear app data — passes the package name)
 *   - disable_battery_optimization
 *   - anti_uninstall_on         (enable uninstall protection)
 *   - lock_phone                (lock device screen)
 *   - reboot                    (reboot the device)
 *
 * Device-link management:
 *   - DELETE /api/web/unlink/{device_id}
 */
class DeviceManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceManagementBinding
    private lateinit var device: Device
    private lateinit var api: ApiService
    private val preferences: Preferences by lazy { Preferences.getInstance(this) }

    // The package name of the client app — passed to clear_app_data
    // (the client's CommandExecutor expects a "package" parameter).
    private val clientPackage = "com.abuzahra.manager"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        device = intent.getSerializableExtra(EXTRA_DEVICE) as? Device ?: run {
            finish(); return
        }

        api = ApiClient.createWithToken(preferences.serverUrl, preferences.token ?: "")

        setupToolbar()
        setupDeviceInfo()
        setupButtons()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "إدارة الجهاز"
        supportActionBar?.subtitle = device.name.ifEmpty { device.model }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDeviceInfo() {
        binding.tvDeviceName.text = device.name.ifEmpty { device.model }
        binding.tvDeviceMeta.text = buildString {
            append("الموديل: ${device.model}")
            if (device.osVersion.isNotEmpty()) append("  •  ${device.osVersion}")
            append("\nالحالة: ${if (device.isOnline) "متصل" else "غير متصل"}")
            append("  •  البطارية: ${device.displayBattery}")
        }
    }

    private fun setupButtons() {
        // ─── App-level controls ───────────────────────────────
        binding.btnRevokePermissions.setOnClickListener {
            confirmAndSend("revoke_permissions", "إلغاء الصلاحيات",
                "سيتم إرسال أمر إلغاء صلاحيات تطبيق العميل. الجهاز قد يفقد الوصول لبعض الميزات. متابعة؟")
        }
        binding.btnHideApp.setOnClickListener {
            confirmAndSend("hide_app", "إخفاء التطبيق",
                "سيتم إخفاء أيقونة تطبيق العميل من قائمة التطبيقات. متابعة؟")
        }
        binding.btnShowApp.setOnClickListener {
            confirmAndSend("show_app", "إظهار التطبيق",
                "سيتم إظهار أيقونة تطبيق العميل مرة أخرى.")
        }
        binding.btnRestartApp.setOnClickListener {
            confirmAndSend("restart_app", "إعادة تشغيل التطبيق",
                "سيتم إعادة تشغيل تطبيق العميل. متابعة؟")
        }
        binding.btnClearAppData.setOnClickListener {
            confirmAndSend("clear_app_data", "مسح بيانات التطبيق",
                "سيتم مسح جميع بيانات تطبيق العميل (تسجيل الدخول، الإعدادات، ...). متابعة؟",
                extraParams = mapOf("package" to clientPackage))
        }
        binding.btnDisableBatteryOpt.setOnClickListener {
            confirmAndSend("disable_battery_optimization", "إيقاف تحسين البطارية",
                "سيُطلب من تطبيق العميل إيقاف تحسين البطارية لضمان عمل مستمر. متابعة؟")
        }
        binding.btnAntiUninstallOn.setOnClickListener {
            confirmAndSend("anti_uninstall_on", "تفعيل حماية الحذف",
                "سيتم تفعيل حماية الحذف لتطبيق العميل. متابعة؟")
        }

        // ─── Device-level controls ────────────────────────────
        binding.btnLockDevice.setOnClickListener {
            confirmAndSend("lock_phone", "قفل الجهاز",
                "سيتم قفل شاشة الجهاز فوراً. متابعة؟")
        }
        binding.btnRebootDevice.setOnClickListener {
            confirmAndSend("reboot", "إعادة تشغيل الجهاز",
                "سيتم إعادة تشغيل الجهاز. متابعة؟")
        }

        // ─── Link / unlink ────────────────────────────────────
        binding.btnUnlinkDevice.setOnClickListener {
            confirmUnlink()
        }
    }

    /**
     * Shows a confirmation dialog before sending a management command.
     * On confirm, sends the command via the existing sendCommand flow
     * (POST /api/web/send_command) and opens CommandResultActivity.
     */
    private fun confirmAndSend(
        commandKey: String,
        title: String,
        message: String,
        extraParams: Map<String, String> = emptyMap()
    ) {
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(R.string.confirm) { _, _ ->
                sendManagementCommand(commandKey, extraParams)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun sendManagementCommand(
        commandKey: String,
        extraParams: Map<String, String> = emptyMap()
    ) {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val request = SendCommandRequest(commandKey, extraParams)
                val response = withContext(Dispatchers.IO) {
                    api.sendCommand(device.id, request)
                }
                binding.progressBar.visibility = View.GONE

                if (response.ok) {
                    Snackbar.make(
                        binding.root,
                        "✅ تم إرسال أمر «$commandKey»",
                        Snackbar.LENGTH_SHORT
                    ).show()
                    // Open the result viewer to show execution progress.
                    if (response.command_id.isNotEmpty()) {
                        startActivity(
                            CommandResultActivity.newIntent(
                                this@DeviceManagementActivity,
                                device.id,
                                response.command_id,
                                commandKey
                            )
                        )
                    }
                } else {
                    val msg = response.message.ifEmpty { "فشل إرسال الأمر" }
                    Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
                }
            } catch (e: retrofit2.HttpException) {
                binding.progressBar.visibility = View.GONE
                val msg = when (e.code()) {
                    401 -> "انتهت الجلسة — يرجى تسجيل الدخول مرة أخرى"
                    403 -> "ليس لديك صلاحية لهذا الجهاز"
                    404 -> "الجهاز غير موجود"
                    else -> "خطأ الخادم: HTTP ${e.code()}"
                }
                if (e.code() == 401) showSessionExpired()
                else Snackbar.make(binding.root, msg, Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    "خطأ: ${e.message ?: e.javaClass.simpleName}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * Unlinks the device from the current user's account via
     * DELETE /api/web/unlink/{device_id}.
     */
    private fun confirmUnlink() {
        MaterialAlertDialogBuilder(this)
            .setTitle("🔗 فك ربط الجهاز")
            .setMessage("سيتم فك ربط الجهاز «${device.name.ifEmpty { device.model }}» من حسابك. " +
                    "لن تتمكن من إرسال أوامر إليه بعد الآن. " +
                    "يمكن إعادة الربط لاحقاً عبر كود الربط.\n\nهذا الإجراء لا يمكن التراجع عنه. متابعة؟")
            .setPositiveButton(R.string.confirm) { _, _ ->
                unlinkDevice()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun unlinkDevice() {
        binding.progressBar.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val ok = withContext(Dispatchers.IO) {
                    api.unlinkDevice(device.id)
                }
                binding.progressBar.visibility = View.GONE

                if (ok) {
                    MaterialAlertDialogBuilder(this@DeviceManagementActivity)
                        .setTitle("تم")
                        .setMessage("✅ تم فك ربط الجهاز بنجاح.")
                        .setPositiveButton(R.string.ok) { _, _ -> finish() }
                        .setCancelable(false)
                        .show()
                } else {
                    Snackbar.make(binding.root, "فشل فك الربط", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: retrofit2.HttpException) {
                binding.progressBar.visibility = View.GONE
                if (e.code() == 401) showSessionExpired()
                else Snackbar.make(
                    binding.root,
                    "خطأ الخادم: HTTP ${e.code()}",
                    Snackbar.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    "خطأ: ${e.message ?: e.javaClass.simpleName}",
                    Snackbar.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun showSessionExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired)
            .setMessage("يرجى تسجيل الدخول مرة أخرى")
            .setPositiveButton(R.string.ok) { _, _ ->
                preferences.clear()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }

    companion object {
        const val EXTRA_DEVICE = "extra_device"

        fun newIntent(context: Context, device: Device): Intent {
            return Intent(context, DeviceManagementActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
            }
        }
    }
}
