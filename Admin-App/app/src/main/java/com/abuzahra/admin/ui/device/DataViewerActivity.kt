package com.abuzahra.admin.ui.device

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.ApiService
import com.abuzahra.admin.data.api.StoredDataResponse
import com.abuzahra.admin.data.model.CommandDefinitions
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.ActivityDataViewerBinding
import com.abuzahra.admin.util.LocalDataStore
import com.abuzahra.admin.util.Preferences
import com.google.android.material.snackbar.Snackbar
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DataViewerActivity — displays the data currently stored in Firebase RTDB
 * for a given (device, type) pair, WITHOUT sending a fresh command to the
 * device. Backs the "عرض البيانات الحالية" choice in the data-command
 * choice dialog shown by DeviceDetailActivity.
 *
 * The data is fetched via GET /api/web/data/{device_id}?type=<type>.
 *
 * Flow:
 *  1. Loads from Firebase → pretty-prints as JSON in a TextView.
 *  2. User can: copy, save locally (SharedPreferences), or refresh.
 *  3. If empty, offers a "جلب بيانات جديدة" button → falls back to the
 *     normal sendCommand flow by sending the command via the API.
 */
class DataViewerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDataViewerBinding
    private lateinit var api: ApiService
    private lateinit var device: Device
    private lateinit var commandKey: String
    private lateinit var dataType: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDataViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        device = intent.getSerializableExtra(EXTRA_DEVICE) as? Device ?: run {
            finish(); return
        }
        commandKey = intent.getStringExtra(EXTRA_COMMAND_KEY) ?: run {
            finish(); return
        }
        dataType = CommandDefinitions.dataTypeForCommand(commandKey)
            ?: commandKey

        val prefs = Preferences.getInstance(this)
        api = ApiClient.createWithToken(prefs.serverUrl, prefs.token ?: "")

        setupToolbar()
        setupButtons()
        loadData()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        val cmdDef = CommandDefinitions.findByKey(commandKey)
        supportActionBar?.title = "عرض: ${cmdDef?.name ?: commandKey}"
        supportActionBar?.subtitle = device.name.ifEmpty { device.model }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupButtons() {
        binding.btnCopy.setOnClickListener {
            val text = binding.tvData.text.toString()
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("data", text))
                Toast.makeText(this, "تم النسخ", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSave.setOnClickListener {
            saveLocally()
        }

        binding.btnRefresh.setOnClickListener {
            loadData()
        }

        binding.btnFetchNew.setOnClickListener {
            // Fall back to sending the actual command to the device.
            // We simply finish() — the caller (DeviceDetailActivity) handles
            // the "fetch new" path when the user explicitly picks it, but if
            // the user got here with no data and changed their mind, we send
            // the command directly.
            val intent = Intent().apply {
                putExtra(EXTRA_REQUEST_FETCH_NEW, true)
                putExtra(EXTRA_COMMAND_KEY, commandKey)
            }
            setResult(RESULT_OK, intent)
            finish()
        }

        binding.btnRetry.setOnClickListener {
            loadData()
        }
    }

    private fun loadData() {
        showLoading()
        lifecycleScope.launch {
            try {
                val response = withContext(Dispatchers.IO) {
                    api.getStoredData(device.id, dataType)
                }
                if (response.ok) {
                    showData(response)
                } else {
                    showError(response.message.ifEmpty { "فشل تحميل البيانات" })
                }
            } catch (e: retrofit2.HttpException) {
                val code = e.code()
                val msg = when (code) {
                    401 -> "انتهت الجلسة — يرجى تسجيل الدخول مرة أخرى"
                    404 -> "الجهاز غير موجود"
                    else -> "خطأ الخادم: HTTP $code"
                }
                showError(msg)
            } catch (e: Exception) {
                Log.e(TAG, "loadData failed", e)
                showError("خطأ: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun showLoading() {
        binding.loadingView.visibility = View.VISIBLE
        binding.resultView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.GONE
    }

    private fun showData(response: StoredDataResponse) {
        binding.loadingView.visibility = View.GONE

        // Update header
        val cmdDef = CommandDefinitions.findByKey(commandKey)
        binding.tvTitle.text = "📊 ${cmdDef?.name ?: commandKey} — البيانات الحالية"
        val fetchedAt = if (response.fetched_at > 0) {
            SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
                .format(Date((response.fetched_at * 1000).toLong()))
        } else ""
        binding.tvMeta.text = "المصدر: Firebase RTDB  |  النوع: ${response.type}\nوقت الجلب: $fetchedAt"

        if (response.empty || response.data == null) {
            binding.emptyView.visibility = View.VISIBLE
            binding.resultView.visibility = View.GONE
            return
        }

        binding.resultView.visibility = View.VISIBLE
        binding.emptyView.visibility = View.GONE

        // Pretty-print the data as JSON.
        val prettyJson = try {
            val parsed = JsonParser.parseString(
                ApiClient.gson.toJson(response.data)
            )
            val prettyGson = GsonBuilder().setPrettyPrinting().create()
            prettyGson.toJson(parsed)
        } catch (e: Exception) {
            response.data.toString()
        }

        binding.tvData.text = prettyJson

        // Count items if it's a list
        val countText = when (val d = response.data) {
            is List<*> -> "عدد العناصر: ${d.size}"
            is Map<*, *> -> "عدد الحقول: ${d.size}"
            else -> "نوع البيانات: ${d?.javaClass?.simpleName ?: "غير معروف"}"
        }
        binding.tvCount.text = countText
    }

    private fun showError(message: String) {
        binding.loadingView.visibility = View.GONE
        binding.resultView.visibility = View.GONE
        binding.emptyView.visibility = View.GONE
        binding.errorView.visibility = View.VISIBLE
        binding.tvError.text = message
    }

    /**
     * Saves the current Firebase snapshot to local app storage so it
     * can be viewed later even when offline. Backed by [LocalDataStore].
     */
    private fun saveLocally() {
        val json = binding.tvData.text.toString()
        if (json.isBlank()) {
            Snackbar.make(binding.root, "لا توجد بيانات للحفظ", Snackbar.LENGTH_SHORT).show()
            return
        }
        val path = LocalDataStore.save(this, device.id, dataType, json)
        if (path != null) {
            Snackbar.make(
                binding.root,
                "💾 تم حفظ البيانات محلياً\n$path",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            Snackbar.make(binding.root, "فشل الحفظ المحلي", Snackbar.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val TAG = "DataViewerActivity"
        const val EXTRA_DEVICE = "extra_device"
        const val EXTRA_COMMAND_KEY = "extra_command_key"
        // Result-extras (sent back to DeviceDetailActivity when the user
        // picks "جلب بيانات جديدة" from inside this viewer).
        const val EXTRA_REQUEST_FETCH_NEW = "extra_request_fetch_new"

        fun newIntent(context: Context, device: Device, commandKey: String): Intent {
            return Intent(context, DataViewerActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
                putExtra(EXTRA_COMMAND_KEY, commandKey)
            }
        }
    }
}
