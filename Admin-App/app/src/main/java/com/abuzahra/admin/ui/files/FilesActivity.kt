package com.abuzahra.admin.ui.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiClient
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.ActivityFilesBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch as coroutineLaunch
import java.io.File

class FilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFilesBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    private var currentPath = "/"
    private var deviceId: String = ""

    private val fileAdapter: FileListAdapter by lazy {
        FileListAdapter(
            onFileClick = { file ->
                if (file.isDirectory) {
                    navigateToDirectory(file.path)
                }
            },
            onDownloadClick = { file ->
                downloadFile(file)
            },
            onParentClick = {
                navigateUp()
            },
            onFileOptionsClick = { file ->
                // Non-directory file was tapped → show 4-option dialog per
                // spec requirement #4: view content / download / send to
                // Telegram / save locally.
                showFileOptionsDialog(file)
            }
        )
    }

    // Use a simple approach for loading - we handle it directly since the FilesActivity needs a deviceId
    // We'll pick the first online device or ask user to select
    private val viewModel: FilesViewModel by viewModels {
        FilesViewModelFactory(prefs)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            openFilePicker()
        }
    }

    private val pickFileLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let { uploadFile(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupFab()
        setupRequestedFilesButton()
        observeViewModel()
        viewModel.loadDevices()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.files)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.swipeRefresh.setOnRefreshListener {
            if (deviceId.isNotBlank()) {
                viewModel.loadFiles(deviceId, currentPath)
            }
        }

        // Navigate button
        binding.etPath.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_GO) {
                val newPath = binding.etPath.text.toString().trim()
                if (newPath.isNotBlank()) {
                    navigateToDirectory(newPath)
                }
                true
            } else {
                false
            }
        }
    }

    private fun setupRequestedFilesButton() {
        binding.btnRequestedFiles.setOnClickListener {
            startActivity(Intent(this, RequestedFilesActivity::class.java))
        }
    }

    private fun setupRecyclerView() {
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@FilesActivity)
            adapter = fileAdapter
        }
    }

    private fun setupFab() {
        binding.fabUpload.setOnClickListener {
            if (deviceId.isBlank()) {
                Snackbar.make(
                    binding.root,
                    "لا يوجد جهاز محدد. يرجى الاتصال بجهاز أولاً.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(
                        this,
                        Manifest.permission.READ_MEDIA_IMAGES
                    ) != PackageManager.PERMISSION_GRANTED
                ) {
                    requestPermissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                } else {
                    openFilePicker()
                }
            } else {
                openFilePicker()
            }
        }
    }

    private fun openFilePicker() {
        pickFileLauncher.launch("*/*")
    }

    private fun uploadFile(uri: android.net.Uri) {
        Snackbar.make(binding.root, "جاري رفع الملف...", Snackbar.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}")
                inputStream?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                val result = ApiClient.uploadFile(
                    prefs.serverUrl,
                    prefs.token ?: "",
                    deviceId,
                    currentPath,
                    tempFile
                )

                when (result) {
                    is Result.Success -> {
                        runOnUiThread {
                            Snackbar.make(
                                binding.root,
                                "تم رفع الملف بنجاح",
                                Snackbar.LENGTH_SHORT
                            ).show()
                            viewModel.loadFiles(deviceId, currentPath)
                        }
                    }
                    is Result.Error -> {
                        runOnUiThread {
                            Snackbar.make(
                                binding.root,
                                result.message,
                                Snackbar.LENGTH_LONG
                            ).show()
                        }
                    }
                    else -> {}
                }

                tempFile.delete()
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "خطأ في رفع الملف: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun downloadFile(file: RemoteFile) {
        if (file.id.isBlank()) {
            Snackbar.make(
                binding.root,
                "لا يمكن تحميل هذا الملف مباشرة (معرّف الملف غير متوفر). استخدم زر «تحميل ملف» لإرسال أمر get_file للجهاز.",
                Snackbar.LENGTH_LONG
            ).show()
            return
        }
        Snackbar.make(binding.root, "جاري تحميل ${file.name}...", Snackbar.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            try {
                val api = prefs.getApiService()
                // Server route: GET /api/files/{file_id} with Authorization: Bearer <token>
                // The Bearer token is added automatically by the OkHttp interceptor
                // built into prefs.getApiService() (ApiClient.createWithToken).
                val url = "${prefs.serverUrl}api/files/${file.id}"
                val body = api.downloadFile(url)

                // Save to Downloads folder
                val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destFile = File(downloadsDir, file.name)

                body.byteStream().use { input ->
                    destFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "تم التحميل: ${destFile.absolutePath}",
                        Snackbar.LENGTH_LONG
                    ).setAction("فتح") {
                        openFile(destFile)
                    }.show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "فشل التحميل: ${e.message}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openFile(file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, contentResolver.getType(uri) ?: "*/*")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "لا يمكن فتح الملف", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun navigateToDirectory(path: String) {
        currentPath = path
        binding.etPath.setText(path)
        if (deviceId.isNotBlank()) {
            viewModel.loadFiles(deviceId, path)
        }
    }

    private fun navigateUp() {
        if (currentPath == "/") return
        val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
        navigateToDirectory(parent)
    }

    private fun observeViewModel() {
        viewModel.devices.observe(this) { devices ->
            if (devices.isEmpty()) {
                updateEmptyState(true)
                return@observe
            }
            if (deviceId.isBlank()) {
                // Auto-select first online device, or first device
                val target = devices.firstOrNull { it.isOnline } ?: devices.first()
                deviceId = target.id
                Snackbar.make(
                    binding.root,
                    "عرض ملفات: ${target.name.ifEmpty { target.model }}",
                    Snackbar.LENGTH_SHORT
                ).show()
                viewModel.loadFiles(deviceId, currentPath)
            }
        }

        viewModel.files.observe(this) { result ->
            binding.swipeRefresh.isRefreshing = false
            binding.loadingOverlay.visibility = View.GONE

            when (result) {
                is Result.Loading -> {
                    binding.loadingOverlay.visibility = View.VISIBLE
                }
                is Result.Success -> {
                    val sorted = result.data.sortedWith(
                        compareByDescending<RemoteFile> { it.isDirectory }
                            .thenBy { it.name.lowercase() }
                    )
                    fileAdapter.submitList(sorted)
                    fileAdapter.setShowParent(currentPath != "/")
                    updateEmptyState(sorted.isEmpty())
                }
                is Result.Error -> {
                    if (result.code == 401) {
                        showSessionExpired()
                    } else {
                        Snackbar.make(
                            binding.root,
                            result.message,
                            Snackbar.LENGTH_LONG
                        ).setAction(R.string.retry) {
                            viewModel.loadFiles(deviceId, currentPath)
                        }.show()
                    }
                    updateEmptyState(true)
                }
            }
        }
    }

    private fun showDevicePicker(devices: List<com.abuzahra.admin.data.model.Device>) {
        val names = devices.map { "${it.name.ifEmpty { it.model }} (${if (it.isOnline) "متصل" else "غير متصل"})" }
        MaterialAlertDialogBuilder(this)
            .setTitle("اختر جهازاً")
            .setItems(names.toTypedArray()) { _, which ->
                deviceId = devices[which].id
                viewModel.loadFiles(deviceId, currentPath)
            }
            .show()
    }

    /**
     * 4-option dialog shown when the user taps a non-directory file row.
     * Backs the spec requirement #4 (file viewer with view content /
     * download / send to Telegram / save locally options).
     *
     * The "view content" path sends the `get_file_content` command (added
     * to the server registry in this task) — the client app's FileExecutor
     * returns the text content of small text files (≤ 256 KB), or a
     * "binary file" marker for non-text content.
     */
    private fun showFileOptionsDialog(file: RemoteFile) {
        val options = arrayOf(
            "📄 عرض المحتوى",
            "⬇️ تحميل الملف",
            "✈️ إرسال للتيليجرام",
            "💾 حفظ محلياً"
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setMessage("المسار: ${file.path}\nالحجم: ${file.displaySize.ifEmpty { "غير معروف" }}")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> viewFileContent(file)
                    1 -> downloadFileViaCommand(file)
                    2 -> sendFileToTelegram(file)
                    3 -> saveFileLocally(file)
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Sends the `get_file_content` command to the device, waits for the
     * result, then displays the text content (or a "binary file" message)
     * in a MaterialAlertDialog.
     */
    private fun viewFileContent(file: RemoteFile) {
        if (deviceId.isBlank()) {
            Snackbar.make(binding.root, "لا يوجد جهاز محدد", Snackbar.LENGTH_SHORT).show()
            return
        }
        binding.swipeRefresh.isRefreshing = true
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            try {
                val api = prefs.getApiService()
                // Send get_file_content command and wait for result via
                // /api/web/device/files (which queues list_files-style
                // commands). For arbitrary commands we use sendCommand
                // then poll getCommands.
                val request = com.abuzahra.admin.data.api.SendCommandRequest(
                    "get_file_content",
                    mapOf("path" to file.path, "arg" to file.path)
                )
                val response = api.sendCommand(deviceId, request)
                if (!response.ok || response.command_id.isEmpty()) {
                    runOnUiThread {
                        binding.swipeRefresh.isRefreshing = false
                        Snackbar.make(
                            binding.root,
                            response.message.ifEmpty { "فشل إرسال الأمر" },
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    return@coroutineLaunch
                }
                // Poll for the result.
                val cmdId = response.command_id
                var resultText: String? = null
                var attempts = 0
                while (attempts < 15 && resultText == null) {
                    Thread.sleep(1500)
                    attempts++
                    val cmds = api.getCommands(deviceId)
                    val cmd = cmds.find { it.id == cmdId }
                    if (cmd != null) {
                        val status = cmd.status.lowercase()
                        if (status == "completed" || status == "success") {
                            resultText = cmd.result ?: ""
                            break
                        } else if (status == "failed" || status == "error") {
                            resultText = cmd.result ?: "فشل تنفيذ الأمر"
                            break
                        }
                    }
                }
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    if (resultText == null) {
                        Snackbar.make(binding.root, "انتهت مهلة الانتظار", Snackbar.LENGTH_LONG).show()
                    } else {
                        showFileContentDialog(file, resultText)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.swipeRefresh.isRefreshing = false
                    Snackbar.make(
                        binding.root,
                        "خطأ: ${e.message ?: e.javaClass.simpleName}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Shows the file content (returned by get_file_content) in a dialog.
     * Parses the JSON result and pretty-prints the "content" field, or
     * shows the "binary" / "truncated" / "error" message.
     */
    private fun showFileContentDialog(file: RemoteFile, rawResult: String) {
        val displayText = try {
            val json = com.google.gson.JsonParser.parseString(rawResult).asJsonObject
            when {
                json.has("error") -> "❌ خطأ: ${json.get("error").asString}"
                json.has("binary") && json.get("binary").asBoolean ->
                    "🚫 BIN\n${json.get("message")?.asString ?: "Binary file — preview not available"}"
                json.has("truncated") && json.get("truncated").asBoolean ->
                    "⚠️ ${json.get("message")?.asString ?: "File too large"}"
                json.has("content") -> {
                    buildString {
                        appendLine("📄 المحتوى:")
                        appendLine()
                        append(json.get("content").asString)
                    }
                }
                else -> rawResult
            }
        } catch (e: Exception) {
            rawResult
        }

        // Use a scrollable TextView inside an AlertDialog.
        val tv = android.widget.TextView(this).apply {
            text = displayText
            textSize = 12f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(48, 32, 48, 32)
            setTextIsSelectable(true)
            setTextColor(getColor(R.color.text_primary))
        }
        val scroll = android.widget.ScrollView(this).apply { addView(tv) }
        MaterialAlertDialogBuilder(this)
            .setTitle("محتوى: ${file.name}")
            .setView(scroll)
            .setPositiveButton(R.string.ok, null)
            .setNeutralButton("نسخ") { _, _ ->
                val clipboard = getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                        as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("content", displayText))
                Snackbar.make(binding.root, "تم النسخ", Snackbar.LENGTH_SHORT).show()
            }
            .show()
    }

    /**
     * Sends the `get_file` command to the device to stage the file for
     * download (the existing CommandResultActivity flow handles the
     * result display).
     */
    private fun downloadFileViaCommand(file: RemoteFile) {
        if (deviceId.isBlank()) {
            Snackbar.make(binding.root, "لا يوجد جهاز محدد", Snackbar.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            try {
                val api = prefs.getApiService()
                val request = com.abuzahra.admin.data.api.SendCommandRequest(
                    "get_file",
                    mapOf("path" to file.path, "arg" to file.path)
                )
                val response = api.sendCommand(deviceId, request)
                runOnUiThread {
                    if (response.ok && response.command_id.isNotEmpty()) {
                        Snackbar.make(
                            binding.root,
                            "⬇️ تم إرسال أمر تحميل الملف — سيظهر في «الملفات المطلوبة»",
                            Snackbar.LENGTH_LONG
                        ).show()
                        // Open CommandResultActivity to show execution progress.
                        startActivity(
                            com.abuzahra.admin.ui.device.CommandResultActivity.newIntent(
                                this@FilesActivity,
                                deviceId,
                                response.command_id,
                                "get_file"
                            )
                        )
                    } else {
                        Snackbar.make(
                            binding.root,
                            response.message.ifEmpty { "فشل إرسال أمر التحميل" },
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "خطأ: ${e.message ?: e.javaClass.simpleName}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Sends the `download_file` command which stages the file for
     * retrieval, then informs the user that the file is queued for
     * Telegram forwarding. The server's telegram bot picks up files
     * uploaded via /api/upload and forwards them to the user's linked
     * Telegram chat (see Task 16-BOT in worklog.md).
     */
    private fun sendFileToTelegram(file: RemoteFile) {
        if (deviceId.isBlank()) {
            Snackbar.make(binding.root, "لا يوجد جهاز محدد", Snackbar.LENGTH_SHORT).show()
            return
        }
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            try {
                val api = prefs.getApiService()
                val request = com.abuzahra.admin.data.api.SendCommandRequest(
                    "download_file",
                    mapOf("path" to file.path, "arg" to file.path)
                )
                val response = api.sendCommand(deviceId, request)
                runOnUiThread {
                    if (response.ok) {
                        Snackbar.make(
                            binding.root,
                            "✈️ تم إرسال الملف للتيليجرام — سيصلك في بوت Telegram المرتبط بحسابك",
                            Snackbar.LENGTH_LONG
                        ).show()
                    } else {
                        Snackbar.make(
                            binding.root,
                            response.message.ifEmpty { "فشل الإرسال" },
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(
                        binding.root,
                        "خطأ: ${e.message ?: e.javaClass.simpleName}",
                        Snackbar.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Saves the file's metadata + path to local app storage so it can
     * be retrieved later (even offline). The actual file content is not
     * downloaded — only the file's path, name, size, and timestamp are
     * recorded for future reference.
     */
    private fun saveFileLocally(file: RemoteFile) {
        val metadata = buildString {
            appendLine("{")
            appendLine("  \"name\": \"${file.name}\",")
            appendLine("  \"path\": \"${file.path}\",")
            appendLine("  \"size\": \"${file.displaySize}\",")
            appendLine("  \"is_directory\": ${file.isDirectory},")
            appendLine("  \"modified\": \"${file.modified ?: ""}\"")
            appendLine("}")
        }
        val path = com.abuzahra.admin.util.LocalDataStore.save(
            this,
            deviceId.ifEmpty { "unknown" },
            "file_meta_${file.name}",
            metadata
        )
        if (path != null) {
            Snackbar.make(
                binding.root,
                "💾 تم حفظ metadata الملف محلياً",
                Snackbar.LENGTH_LONG
            ).show()
        } else {
            Snackbar.make(binding.root, "فشل الحفظ المحلي", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    private fun showSessionExpired() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.session_expired)
            .setMessage("يرجى تسجيل الدخول مرة أخرى")
            .setPositiveButton(R.string.ok) { _, _ ->
                prefs.clear()
                startActivity(Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                })
                finish()
            }
            .setCancelable(false)
            .show()
    }

}