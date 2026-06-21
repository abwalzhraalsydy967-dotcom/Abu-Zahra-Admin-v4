package com.abuzahra.admin.ui.fragments

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.CommandDefinitions
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.databinding.FragmentCommandsBinding
import com.abuzahra.admin.ui.adapters.CommandAdapter
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Commands fragment — functional copy of the web's CommandsView:
 *  - Device selector at top
 *  - Category chips (8 categories, single-select) — horizontal scroll
 *  - Real-time search filters commands by name/cmd
 *  - RecyclerView grid of ALL 100+ commands
 *  - Tap command → execute (send to server) → toast → navigate to Results
 *  - Commands with params → param dialog (text/number/select)
 *  - Dangerous commands (wipe_data, factory_reset) → confirmation dialog
 */
class CommandsFragment : BaseFragment() {

    private var _binding: FragmentCommandsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private val commandAdapter: CommandAdapter by lazy {
        CommandAdapter { cmd -> onCommandClick(cmd) }
    }

    private var selectedCategory: CommandDefinitions.Category? = null
    private var searchQuery: String = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCommandsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Grid layout — 2 columns on phones, 3 on tablets
        val span = if (resources.configuration.screenWidthDp >= 600) 3 else 2
        binding.rvCommands.apply {
            layoutManager = GridLayoutManager(requireContext(), span)
            adapter = commandAdapter
        }

        setupCategoryChips()

        binding.etCommandSearch.doOnTextChanged { text, _, _, _ ->
            searchQuery = text?.toString()?.trim()?.lowercase() ?: ""
            applyFilter()
        }

        binding.btnChangeDevice.setOnClickListener { showDevicePicker() }
        binding.cardDeviceSelected.setOnClickListener { showDevicePicker() }

        observeViewModel()
        updateSelectedDeviceCard(viewModel.selectedDevice.value)
    }

    private fun setupCategoryChips() {
        binding.chipCategory.removeAllViews()

        // "All" chip
        val allChip = Chip(requireContext()).apply {
            text = "الكل (${CommandDefinitions.totalCommands})"
            isCheckable = true
            isChecked = true
            setOnClickListener {
                selectedCategory = null
                checkOnly(this)
                applyFilter()
            }
        }
        binding.chipCategory.addView(allChip)

        for (cat in CommandDefinitions.Category.entries) {
            val count = CommandDefinitions.commandsByCategory[cat]?.size ?: 0
            val chip = Chip(requireContext()).apply {
                text = "${cat.displayName} ($count)"
                isCheckable = true
                setOnClickListener {
                    selectedCategory = cat
                    checkOnly(this)
                    applyFilter()
                }
            }
            binding.chipCategory.addView(chip)
        }
    }

    private fun checkOnly(activeChip: Chip) {
        for (i in 0 until binding.chipCategory.childCount) {
            val c = binding.chipCategory.getChildAt(i) as? Chip ?: continue
            c.isChecked = c === activeChip
        }
    }

    private fun applyFilter() {
        val all = CommandDefinitions.commandsByCategory.values.flatten()
        val source = if (selectedCategory != null) {
            CommandDefinitions.commandsByCategory[selectedCategory] ?: emptyList()
        } else {
            all
        }
        val filtered = if (searchQuery.isBlank()) source else source.filter { def ->
            def.name.lowercase().contains(searchQuery) ||
                    def.key.lowercase().contains(searchQuery)
        }
        commandAdapter.submitList(filtered)
        binding.tvCommandCount.text = "${filtered.size} أمر متاح"
    }

    private fun observeViewModel() {
        viewModel.selectedDevice.observe(viewLifecycleOwner) { device ->
            updateSelectedDeviceCard(device)
        }
    }

    private fun updateSelectedDeviceCard(device: Device?) {
        if (device == null) {
            binding.tvSelectedDeviceName.text = "لم يتم اختيار جهاز"
            binding.tvSelectedDeviceMeta.text = "اضغط لاختيار جهاز"
        } else {
            binding.tvSelectedDeviceName.text = device.name.ifEmpty { device.model }
            binding.tvSelectedDeviceMeta.text = buildString {
                if (device.brand.isNotEmpty()) append(device.brand)
                if (device.model.isNotEmpty()) {
                    if (isNotEmpty()) append(" • ")
                    append(device.model)
                }
                append(" • ")
                append(if (device.isOnline) "متصل" else "غير متصل")
            }
        }
    }

    private fun showDevicePicker() {
        val devices = (viewModel.devices.value as? Result.Success)?.data ?: emptyList()
        if (devices.isEmpty()) {
            (activity as? MainActivity)?.showSnack(getString(R.string.select_device_first))
            (activity as? MainActivity)?.navigateToView(R.id.nav_devices)
            return
        }
        val labels = devices.map { d ->
            "${d.name.ifEmpty { d.model.ifEmpty { "جهاز" } }}  •  ${if (d.isOnline) "متصل" else "غير متصل"}"
        }.toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.devices)
            .setItems(labels) { _, which ->
                if (which in devices.indices) viewModel.selectDevice(devices[which])
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    // ── Command execution ─────────────────────────────────────────
    private fun onCommandClick(cmd: CommandDefinitions.CommandDef) {
        val device = viewModel.selectedDevice.value
        if (device == null) {
            (activity as? MainActivity)?.showSnack(getString(R.string.select_device_first))
            showDevicePicker()
            return
        }

        // Dangerous commands require confirmation
        if (cmd.key == "wipe_data" || cmd.key == "factory_reset") {
            showDangerousConfirm(cmd, device)
            return
        }

        // Commands with params
        if (hasParams(cmd.key)) {
            showParamDialog(cmd, device)
            return
        }

        // Plain command — send immediately
        executeCommand(device, cmd.key)
    }

    private fun hasParams(key: String): Boolean = when (key) {
        "set_volume", "set_brightness", "set_ringtone", "speak_text",
        "show_notification", "open_url", "send_sms", "make_call",
        "open_app", "close_app", "install_app", "uninstall_app",
        "block_app", "unblock_app", "clear_app_data", "force_stop_app",
        "list_files", "search_files", "get_file", "delete_file",
        "change_passcode", "set_stream_quality" -> true
        else -> false
    }

    private fun showDangerousConfirm(cmd: CommandDefinitions.CommandDef, device: Device) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("⚠️ تأكيد الأمر الخطير")
            .setMessage(
                "أنت على وشك تنفيذ أمر قد يؤدي إلى فقدان البيانات نهائياً.\n\n" +
                        "الأمر: ${cmd.name}\n" +
                        "الجهاز: ${device.name.ifEmpty { device.model }}\n\n" +
                        "هذا الإجراء لا يمكن التراجع عنه. هل أنت متأكد؟"
            )
            .setPositiveButton("تأكيد التنفيذ") { _, _ ->
                executeCommand(device, cmd.key)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Build a parameter dialog based on the command key. Mirrors the web's
     * `paramFields` definitions in commands.ts.
     */
    private fun showParamDialog(cmd: CommandDefinitions.CommandDef, device: Device) {
        val context = requireContext()
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }

        val fields = paramFieldsFor(cmd.key)
        val inputs = mutableMapOf<String, Any>()

        for (field in fields) {
            val label = android.widget.TextView(context).apply {
                text = field.label + if (field.required) " *" else ""
                setTextColor(requireContext().getColor(R.color.text_secondary))
                textSize = 13f
            }
            container.addView(label)

            if (field.type == "select") {
                val spinner = Spinner(context).apply {
                    adapter = ArrayAdapter(
                        context,
                        android.R.layout.simple_spinner_dropdown_item,
                        field.options
                    )
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 24
                spinner.layoutParams = lp
                container.addView(spinner)
                inputs[field.key] = spinner
            } else {
                val et = EditText(context).apply {
                    hint = field.placeholder
                    inputType = if (field.type == "number") InputType.TYPE_CLASS_NUMBER
                    else InputType.TYPE_CLASS_TEXT
                    setTextColor(requireContext().getColor(R.color.text_primary))
                    setHintTextColor(requireContext().getColor(R.color.text_hint))
                    backgroundTintList = android.content.res.ColorStateList.valueOf(
                        requireContext().getColor(R.color.secondary)
                    )
                }
                val lp = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                lp.bottomMargin = 24
                et.layoutParams = lp
                container.addView(et)
                inputs[field.key] = et
            }
        }

        MaterialAlertDialogBuilder(context)
            .setTitle("${emojiFor(cmd.key)} ${cmd.name}")
            .setView(container)
            .setPositiveButton("إرسال") { _, _ ->
                val params = mutableMapOf<String, String>()
                for ((key, view) in inputs) {
                    val value = when (view) {
                        is EditText -> view.text.toString().trim()
                        is Spinner -> (view.selectedItem ?: "").toString()
                        else -> ""
                    }
                    params[key] = value
                }
                executeCommand(device, cmd.key, params)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Param field definitions matching the web's commands.ts paramFields.
     */
    private data class Field(
        val key: String,
        val label: String,
        val type: String, // "text" | "number" | "select"
        val placeholder: String = "",
        val required: Boolean = false,
        val options: List<String> = emptyList()
    )

    private fun paramFieldsFor(key: String): List<Field> = when (key) {
        "set_volume" -> listOf(Field("level", "مستوى الصوت", "number", "0-100", required = true))
        "set_brightness" -> listOf(Field("level", "مستوى السطوع", "number", "0-255", required = true))
        "set_ringtone" -> listOf(Field("url", "رابط النغمة", "text", "https://", required = true))
        "speak_text" -> listOf(Field("text", "النص", "text", "أدخل النص", required = true))
        "show_notification" -> listOf(
            Field("title", "العنوان", "text", "عنوان الإشعار", required = true),
            Field("text", "النص", "text", "نص الإشعار", required = true)
        )
        "open_url" -> listOf(Field("url", "الرابط", "text", "https://", required = true))
        "send_sms" -> listOf(
            Field("number", "الرقم", "text", "رقم الهاتف", required = true),
            Field("message", "الرسالة", "text", "نص الرسالة", required = true)
        )
        "make_call" -> listOf(Field("number", "الرقم", "text", "رقم الهاتف", required = true))
        "open_app", "close_app", "uninstall_app", "block_app",
        "unblock_app", "clear_app_data", "force_stop_app" ->
            listOf(Field("package", "اسم الحزمة", "text", "com.example.app", required = true))
        "install_app" -> listOf(Field("url", "رابط APK", "text", "https://", required = true))
        "list_files", "get_file", "delete_file" ->
            listOf(Field("path", "المسار", "text", "/storage/emulated/0/", required = false))
        "search_files" -> listOf(Field("query", "اسم الملف", "text", "ابحث عن ملف...", required = true))
        "change_passcode" -> listOf(
            Field("old_pin", "الرمز الحالي", "text", "الرمز القديم", required = true),
            Field("new_pin", "الرمز الجديد", "text", "الرمز الجديد", required = true)
        )
        "set_stream_quality" -> listOf(
            Field("quality", "الجودة", "select", required = true,
                options = listOf("480p", "720p", "1080p", "1440p"))
        )
        else -> emptyList()
    }

    private fun emojiFor(key: String): String {
        // Mirror of CommandAdapter.emojiFor — duplicated here for dialog titles.
        // (See CommandAdapter.kt for the full table.)
        val map = mapOf(
            "sms" to "💬", "calls" to "📞", "contacts" to "👤", "location" to "📍",
            "screenshot" to "📸", "front_camera" to "🤳", "back_camera" to "📷",
            "record_audio" to "🎙️", "record_screen" to "🎬", "reboot" to "🔄",
            "wipe_data" to "💣", "factory_reset" to "⚠️"
        )
        return map[key] ?: "⚡"
    }

    private fun executeCommand(
        device: Device,
        command: String,
        params: Map<String, String> = emptyMap()
    ) {
        (activity as? MainActivity)?.showSnack("جارٍ إرسال الأمر: $command")
        viewModel.sendCommand(device.id, command, params)
        // After sending, navigate to Results so the user sees the result
        view?.postDelayed({
            (activity as? MainActivity)?.navigateToView(R.id.nav_results)
        }, 600)
    }

    override fun onResume() {
        super.onResume()
        // Refresh the command list (in case the user just selected a device)
        applyFilter()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
