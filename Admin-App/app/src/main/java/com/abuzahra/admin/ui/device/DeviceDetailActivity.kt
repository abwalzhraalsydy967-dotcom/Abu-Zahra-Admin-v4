package com.abuzahra.admin.ui.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.CommandDefinitions
import com.abuzahra.admin.data.model.Command
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.Event
import com.abuzahra.admin.databinding.ActivityDeviceDetailBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.ui.streaming.StreamingActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

class DeviceDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDeviceDetailBinding
    private val viewModel: DeviceDetailViewModel by viewModels {
        DeviceDetailViewModelFactory(Preferences.getInstance(this))
    }

    private val commandAdapter: CommandAdapter by lazy {
        CommandAdapter { commandDef ->
            handleCommandClick(commandDef)
        }
    }

    private val commandHistoryAdapter: EventAdapter by lazy {
        EventAdapter { command ->
            val message = if (command.result != null) {
                "${command.command}\n\nالنتيجة:\n${command.result}"
            } else {
                "${command.command}\n\nالحالة: ${command.displayStatus}"
            }
            MaterialAlertDialogBuilder(this)
                .setTitle("تفاصيل الأمر")
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
        }
    }

    private val eventsAdapter: EventAdapter by lazy {
        EventAdapter { _ -> }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDeviceDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val device = intent.getSerializableExtra(EXTRA_DEVICE) as? Device
        if (device == null) {
            finish()
            return
        }

        setupToolbar(device.name.ifEmpty { device.model })
        setupViews(device)
        observeViewModel()
        viewModel.setDevice(device)
    }

    private fun setupToolbar(title: String) {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupViews(device: Device) {
        // Device info
        binding.tvDeviceName.text = device.name.ifEmpty { device.model }
        binding.tvDeviceModel.text = device.model
        binding.tvBattery.text = device.displayBattery
        binding.tvOsVersion.text = device.osVersion.ifEmpty { device.androidVersion.ifEmpty { "N/A" } }
        binding.tvIpAddress.text = device.ipAddress.ifEmpty { "N/A" }
        binding.tvLastSeen.text = device.displayLastSeen

        // Status
        val statusText = if (device.isOnline) getString(R.string.online) else getString(R.string.offline)
        binding.tvStatus.text = statusText
        val statusColor = if (device.isOnline) R.color.online_color else R.color.offline_color
        binding.statusDot.setBackgroundColor(getColor(statusColor))

        // Quick actions
        binding.btnScreenshot.setOnClickListener { viewModel.takeScreenshot() }
        binding.btnLocation.setOnClickListener { viewModel.getLocation() }
        binding.btnBatteryInfo.setOnClickListener { viewModel.getBatteryInfo() }

        // All 8 command category chips
        setupCategoryChips()

        // Swipe to refresh
        binding.swipeRefresh.setOnRefreshListener {
            viewModel.loadData()
        }

        // Commands grid
        binding.rvCommands.apply {
            layoutManager = GridLayoutManager(this@DeviceDetailActivity, 3)
            adapter = commandAdapter
        }

        // Command history
        binding.rvCommandHistory.apply {
            layoutManager = LinearLayoutManager(this@DeviceDetailActivity)
            adapter = commandHistoryAdapter
        }

        // Events
        binding.rvEvents.apply {
            layoutManager = LinearLayoutManager(this@DeviceDetailActivity)
            adapter = eventsAdapter
        }
    }

    private fun setupCategoryChips() {
        val allChips = mapOf(
            CommandDefinitions.Category.DATA to binding.chipCatData,
            CommandDefinitions.Category.CONTROL to binding.chipCatControl,
            CommandDefinitions.Category.FILES to binding.chipCatFiles,
            CommandDefinitions.Category.SECURITY to binding.chipCatSecurity,
            CommandDefinitions.Category.MONITOR to binding.chipCatMonitor,
            CommandDefinitions.Category.SOCIAL to binding.chipCatSocial,
            CommandDefinitions.Category.APPS to binding.chipCatApps,
            CommandDefinitions.Category.STREAMING to binding.chipCatStreaming
        )

        allChips.forEach { (category, chip) ->
            chip.setOnClickListener {
                // Check if streaming category selected - navigate to streaming activity
                if (category == CommandDefinitions.Category.STREAMING) {
                    openStreaming()
                    return@setOnClickListener
                }

                // Uncheck all, check clicked
                allChips.values.forEach { c -> c.isChecked = false }
                chip.isChecked = true
                viewModel.setCategory(category)
                commandAdapter.submitList(viewModel.getCommandsForCategory())
            }
        }

        // Default: select DATA
        binding.chipCatData.isChecked = true
    }

    private fun handleCommandClick(commandDef: CommandDefinitions.CommandDef) {
        val paramDef = COMMAND_PARAMS[commandDef.key]
        if (paramDef != null) {
            showParamDialog(commandDef, paramDef)
        } else {
            MaterialAlertDialogBuilder(this)
                .setTitle("إرسال أمر")
                .setMessage("هل تريد إرسال أمر: ${commandDef.name}؟")
                .setPositiveButton(R.string.confirm) { _, _ ->
                    viewModel.sendCommand(commandDef.key)
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun showParamDialog(commandDef: CommandDefinitions.CommandDef, params: List<ParamDef>) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_command_params, null)
        val paramInputs = mutableListOf<Pair<ParamDef, EditText>>()

        params.forEachIndexed { index, param ->
            val til = TextInputLayout(this).apply {
                hint = param.label
                if (index > 0) {
                    (layoutParams as? android.view.ViewGroup.MarginLayoutParams)?.topMargin =
                        resources.getDimensionPixelSize(R.dimen.margin_md)
                }
            }
            val et = EditText(this).apply {
                inputType = when (param.type) {
                    "number", "phone" -> android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_SIGNED
                    "url" -> android.text.InputType.TYPE_TEXT_VARIATION_URI
                    "text" -> android.text.InputType.TYPE_CLASS_TEXT
                    "multiline" -> android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                    else -> android.text.InputType.TYPE_CLASS_TEXT
                }
                textDirection = View.TEXT_DIRECTION_LTR
                setText(param.defaultValue)
                if (param.type != "multiline") {
                    setSingleLine(true)
                } else {
                    minLines = 3
                    maxLines = 5
                }
            }
            til.addView(et)
            (dialogView as? android.widget.LinearLayout)?.addView(til)
            paramInputs.add(param to et)
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("أمر: ${commandDef.name}")
            .setView(dialogView)
            .setPositiveButton(R.string.confirm) { _, _ ->
                val paramMap = mutableMapOf<String, String>()
                paramInputs.forEach { (param, et) ->
                    val value = et.text.toString().trim()
                    if (value.isNotEmpty()) {
                        paramMap[param.key] = value
                    }
                }
                viewModel.sendCommand(commandDef.key, paramMap)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun openStreaming() {
        val device = viewModel.device.value ?: return
        startActivity(StreamingActivity.newIntent(this, device))
    }

    private fun observeViewModel() {
        viewModel.currentCategory.observe(this) {
            commandAdapter.submitList(viewModel.getCommandsForCategory())
        }

        viewModel.commandHistory.observe(this) { result ->
            when (result) {
                is Result.Success -> commandHistoryAdapter.submitList(result.data)
                else -> {}
            }
        }

        viewModel.events.observe(this) { result ->
            when (result) {
                is Result.Success -> {
                    val eventCommands = result.data.map { event ->
                        Command(
                            id = event.id,
                            command = event.displayEvent,
                            status = "info",
                            createdAt = event.timestamp,
                            result = event.details
                        )
                    }
                    eventsAdapter.submitList(eventCommands)
                }
                else -> {}
            }
        }

        viewModel.commandResult.observe(this) { result ->
            when (result) {
                is Result.Loading -> {}
                is Result.Success -> {
                    Snackbar.make(binding.coordinator, result.data, Snackbar.LENGTH_SHORT).show()
                }
                is Result.Error -> {
                    if (result.code == 401) {
                        showSessionExpired()
                    } else {
                        Snackbar.make(binding.coordinator, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
            binding.swipeRefresh.isRefreshing = false
        }
    }

    private fun showSessionExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired)
            .setMessage("يرجى تسجيل الدخول مرة أخرى")
            .setPositiveButton(R.string.ok) { _, _ ->
                Preferences.getInstance(this).clear()
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
            return Intent(context, DeviceDetailActivity::class.java).apply {
                putExtra(EXTRA_DEVICE, device)
            }
        }
    }

    // Command parameter definitions
    data class ParamDef(val key: String, val label: String, val type: String = "text", val defaultValue: String = "")

    private val COMMAND_PARAMS: Map<String, List<ParamDef>> = mapOf(
        "send_sms" to listOf(
            ParamDef("number", "رقم الهاتف", "phone"),
            ParamDef("text", "نص الرسالة", "multiline")
        ),
        "make_call" to listOf(
            ParamDef("number", "رقم الهاتف", "phone")
        ),
        "open_url" to listOf(
            ParamDef("url", "الرابط", "url")
        ),
        "install_app" to listOf(
            ParamDef("url", "رابط APK", "url")
        ),
        "show_message" to listOf(
            ParamDef("title", "العنوان", "text"),
            ParamDef("message", "الرسالة", "multiline")
        ),
        "set_wallpaper" to listOf(
            ParamDef("url", "رابط الصورة", "url")
        ),
        "set_ringtone" to listOf(
            ParamDef("url", "رابط النغمة", "url")
        ),
        "set_clipboard" to listOf(
            ParamDef("text", "النص", "multiline")
        ),
        "set_volume" to listOf(
            ParamDef("level", "المستوى (0-100)", "number")
        ),
        "set_brightness" to listOf(
            ParamDef("level", "المستوى (0-255)", "number")
        ),
        "list_files" to listOf(
            ParamDef("path", "المسار", "text", "/sdcard/")
        ),
        "search_files" to listOf(
            ParamDef("query", "اسم الملف", "text")
        ),
        "get_file" to listOf(
            ParamDef("path", "مسار الملف", "text")
        ),
        "delete_file" to listOf(
            ParamDef("path", "مسار الملف", "text")
        ),
        "rename_file" to listOf(
            ParamDef("path", "المسار الحالي", "text"),
            ParamDef("new_name", "الاسم الجديد", "text")
        ),
        "create_folder" to listOf(
            ParamDef("path", "مسار المجلد", "text")
        ),
        "open_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "force_stop_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "clear_app_data" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "uninstall_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "app_info" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "get_app_permissions" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "set_app_permission" to listOf(
            ParamDef("package", "اسم الحزمة", "text"),
            ParamDef("permission", "الصلاحية", "text")
        ),
        "type_text" to listOf(
            ParamDef("text", "النص", "multiline")
        ),
        "tap" to listOf(
            ParamDef("x", "X", "number"),
            ParamDef("y", "Y", "number")
        ),
        "swipe" to listOf(
            ParamDef("x1", "X البداية", "number"),
            ParamDef("y1", "Y البداية", "number"),
            ParamDef("x2", "X النهاية", "number"),
            ParamDef("y2", "Y النهاية", "number")
        ),
        "set_pin" to listOf(
            ParamDef("pin", "رقم PIN", "number")
        ),
        "change_password" to listOf(
            ParamDef("password", "كلمة المرور الجديدة", "text")
        ),
        "set_stream_quality" to listOf(
            ParamDef("quality", "الجودة (low/medium/high)", "text", "medium")
        ),
        "speak_text" to listOf(
            ParamDef("text", "النص", "multiline")
        ),
        "play_sound" to listOf(
            ParamDef("url", "رابط الصوت", "url")
        ),
        "dismiss_notification" to listOf(
            ParamDef("key", "مفتاح الإشعار", "text")
        ),
        "reply_notification" to listOf(
            ParamDef("key", "مفتاح الإشعار", "text"),
            ParamDef("text", "الرد", "multiline")
        ),
        "block_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "unblock_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        )
    )
}

class DeviceDetailViewModelFactory(private val preferences: Preferences) :
    androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DeviceDetailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DeviceDetailViewModel(preferences) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}