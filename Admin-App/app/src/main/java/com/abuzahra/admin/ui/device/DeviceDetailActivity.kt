package com.abuzahra.admin.ui.device

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

    // Debug log views
    private lateinit var debugLogContainer: LinearLayout
    private lateinit var debugLogScroll: ScrollView
    private lateinit var debugLogText: TextView
    private lateinit var debugToggleBtn: com.google.android.material.button.MaterialButton

    private var debugLogVisible = false

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
        setupDebugLogPanel()
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

    private fun setupDebugLogPanel() {
        // Create debug log panel at the bottom of the CoordinatorLayout
        debugLogContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(ContextCompat.getColor(this@DeviceDetailActivity, R.color.surface))
            elevation = 8f
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Toggle button row
        val toggleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(12, 8, 12, 4)
        }

        val toggleLabel = TextView(this).apply {
            text = "🔍 سجل الفحص"
            textSize = 13f
            setTextColor(ContextCompat.getColor(this@DeviceDetailActivity, R.color.text_secondary))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        debugToggleBtn = com.google.android.material.button.MaterialButton(this).apply {
            text = "عرض"
            textSize = 11f
            isAllCaps = false
            cornerRadius = 16
            setTextColor(ContextCompat.getColor(this@DeviceDetailActivity, R.color.primary))
            setOnClickListener { toggleDebugLog() }
        }

        toggleRow.addView(toggleLabel)
        toggleRow.addView(debugToggleBtn)
        debugLogContainer.addView(toggleRow)

        // Log content in a scrollable text
        debugLogScroll = ScrollView(this).apply {
            visibility = View.GONE
            isVerticalScrollBarEnabled = true
            setPadding(12, 0, 12, 8)
        }

        debugLogText = TextView(this).apply {
            textSize = 11f
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(ContextCompat.getColor(this@DeviceDetailActivity, R.color.text_primary))
            setLineSpacing(2f, 1f)
        }

        debugLogScroll.addView(debugLogText)
        debugLogContainer.addView(debugLogScroll)

        // Add to the root CoordinatorLayout at the bottom
        val coordinatorLayout = binding.coordinator as? android.widget.FrameLayout
        if (coordinatorLayout != null) {
            val lp = androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.MATCH_PARENT,
                androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams.WRAP_CONTENT
            )
            lp.gravity = Gravity.BOTTOM
            debugLogContainer.layoutParams = lp
            coordinatorLayout.addView(debugLogContainer)
        }
    }

    private fun toggleDebugLog() {
        debugLogVisible = !debugLogVisible
        debugLogScroll.visibility = if (debugLogVisible) View.VISIBLE else View.GONE
        debugToggleBtn.text = if (debugLogVisible) "إخفاء" else "عرض"
    }

    private fun updateDebugLog(logs: List<String>) {
        val sb = StringBuilder()
        for (log in logs) {
            sb.append(log).append("\n")
        }
        debugLogText.text = sb.toString()

        // Auto-scroll to top (newest)
        debugLogScroll.post {
            debugLogScroll.scrollTo(0, 0)
        }

        // Show the toggle if there are logs
        if (logs.isNotEmpty() && !debugLogVisible) {
            debugToggleBtn.text = "عرض (${logs.size})"
        }
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

        // Quick actions — use CORRECT server registry keys
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
                .setMessage("هل تريد إرسال أمر: ${commandDef.name}؟\n\nمفتاح الأمر: ${commandDef.key}")
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
            .setMessage("مفتاح الأمر: ${commandDef.key}")
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
                is Result.Loading -> {
                    binding.swipeRefresh.isRefreshing = true
                }
                is Result.Success -> {
                    binding.swipeRefresh.isRefreshing = false
                    Snackbar.make(binding.coordinator, result.data, Snackbar.LENGTH_SHORT).show()
                }
                is Result.Error -> {
                    binding.swipeRefresh.isRefreshing = false
                    if (result.code == 401) {
                        showSessionExpired()
                    } else {
                        Snackbar.make(binding.coordinator, result.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        // Debug log observer
        viewModel.debugLogs.observe(this) { logs ->
            updateDebugLog(logs)
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

    // Command parameter definitions — keys match server COMMAND_REGISTRY
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
        "show_notification" to listOf(
            ParamDef("title", "العنوان", "text"),
            ParamDef("message", "الرسالة", "multiline")
        ),
        "set_ringtone" to listOf(
            ParamDef("url", "رابط النغمة", "url")
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
        "block_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "unblock_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "close_app" to listOf(
            ParamDef("package", "اسم الحزمة", "text")
        ),
        "speak_text" to listOf(
            ParamDef("text", "النص", "multiline")
        ),
        "play_sound" to listOf(
            ParamDef("url", "رابط الصوت", "url")
        ),
        "set_stream_quality" to listOf(
            ParamDef("quality", "الجودة (low/medium/high)", "text", "medium")
        ),
        "change_passcode" to listOf(
            ParamDef("password", "كلمة المرور الجديدة", "text")
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