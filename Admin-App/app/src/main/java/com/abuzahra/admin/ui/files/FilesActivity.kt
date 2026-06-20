package com.abuzahra.admin.ui.files

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
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

    private enum class SortMode(val label: String) {
        NAME("الاسم"),
        SIZE_DESC("الحجم (الأكبر)"),
        DATE_DESC("التاريخ (الأحدث)")
    }

    private lateinit var binding: ActivityFilesBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    private var currentPath = "/"
    private var deviceId: String = ""
    private var currentSort: SortMode = SortMode.NAME
    private var actionMode: android.view.ActionMode? = null

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
            onLongClick = { _ -> },
            onSelectionToggle = { _ -> refreshActionMode() }
        )
    }

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

    private val actionModeCallback = object : android.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
            menu.add(0, R.id.action_download_selected, 0, "تحميل المحددد")
                .setIcon(R.drawable.ic_download)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            return true
        }

        override fun onPrepareActionMode(mode: android.view.ActionMode, menu: Menu): Boolean {
            mode.title = "تم تحديد ${fileAdapter.getSelectedCount()}"
            return true
        }

        override fun onActionItemClicked(mode: android.view.ActionMode, item: MenuItem): Boolean {
            return when (item.itemId) {
                R.id.action_download_selected -> {
                    downloadSelected()
                    mode.finish()
                    true
                }
                else -> false
            }
        }

        override fun onDestroyActionMode(mode: android.view.ActionMode) {
            fileAdapter.clearSelection()
            actionMode = null
        }
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
        binding.toolbar.setNavigationOnClickListener {
            if (fileAdapter.isInSelectionMode()) {
                fileAdapter.clearSelection()
                refreshActionMode()
            } else {
                finish()
            }
        }

        binding.swipeRefresh.setOnRefreshListener {
            if (deviceId.isNotBlank()) {
                viewModel.loadFiles(deviceId, currentPath)
            }
        }

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

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_files, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_sort -> {
                showSortDialog()
                true
            }
            R.id.action_select_all -> {
                fileAdapter.selectAll()
                refreshActionMode()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showSortDialog() {
        val labels = SortMode.values().map { it.label }.toTypedArray()
        val current = SortMode.values().indexOf(currentSort)
        MaterialAlertDialogBuilder(this)
            .setTitle("ترتيب حسب")
            .setSingleChoiceItems(labels, current) { dialog, which ->
                currentSort = SortMode.values()[which]
                dialog.dismiss()
                reSortCurrent()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun reSortCurrent() {
        // Re-fetch and apply sort (cheap on small lists).
        if (deviceId.isNotBlank()) {
            viewModel.loadFiles(deviceId, currentPath)
        }
    }

    private fun refreshActionMode() {
        if (fileAdapter.isInSelectionMode()) {
            if (actionMode == null) {
                actionMode = startSupportActionMode(actionModeCallback)
            }
            actionMode?.invalidate()
        } else {
            actionMode?.finish()
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
                val url = "${prefs.serverUrl}api/files/${file.id}"
                val body = api.downloadFile(url)

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

    /** Download every file currently selected in the multi-select mode. */
    private fun downloadSelected() {
        val paths = fileAdapter.getSelectedPaths()
        if (paths.isEmpty()) {
            Snackbar.make(binding.root, "لم يتم تحديد ملفات", Snackbar.LENGTH_SHORT).show()
            return
        }
        Snackbar.make(binding.root, "جاري تحميل ${paths.size} ملف...", Snackbar.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).coroutineLaunch {
            val api = prefs.getApiService()
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            var ok = 0
            var fail = 0
            try {
                val fresh = api.getFiles(deviceId, currentPath)
                for (f in fresh) {
                    if (f.path in paths) {
                        try {
                            val url = "${prefs.serverUrl}api/files/${f.id}"
                            val body = api.downloadFile(url)
                            val dest = File(downloadsDir, f.name)
                            body.byteStream().use { input ->
                                dest.outputStream().use { output -> input.copyTo(output) }
                            }
                            ok++
                        } catch (e: Exception) {
                            fail++
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Snackbar.make(binding.root, "فشل جلب الملفات: ${e.message}",
                        Snackbar.LENGTH_LONG).show()
                }
                return@coroutineLaunch
            }
            runOnUiThread {
                fileAdapter.clearSelection()
                refreshActionMode()
                Snackbar.make(binding.root,
                    "تم تحميل $ok ملف، فشل $fail",
                    Snackbar.LENGTH_LONG).show()
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
        updateBreadcrumbs(path)
        if (deviceId.isNotBlank()) {
            viewModel.loadFiles(deviceId, path)
        }
    }

    private fun navigateUp() {
        if (currentPath == "/") return
        val parent = currentPath.substringBeforeLast("/").ifEmpty { "/" }
        navigateToDirectory(parent)
    }

    /**
     * Build a row of clickable breadcrumb chips for [path]. Each chip navigates
     * to its segment when tapped. Paths like
     * `/storage/emulated/0/Download/sub` render as "/" > storage > emulated > 0 > Download > sub.
     */
    private fun updateBreadcrumbs(path: String) {
        val container = binding.breadcrumbContainer
        container.removeAllViews()
        if (path.isBlank()) return

        val inflater = LayoutInflater.from(this)
        val segments = path.split("/").filter { it.isNotEmpty() }

        // Root chip
        val rootChip = inflater.inflate(R.layout.item_breadcrumb, container, false) as android.widget.TextView
        rootChip.text = "/"
        rootChip.setOnClickListener { navigateToDirectory("/") }
        container.addView(rootChip)

        var acc = ""
        segments.forEachIndexed { index, seg ->
            acc = "$acc/$seg"

            val sep = android.widget.TextView(this).apply {
                text = ">"
                setTextColor(ContextCompat.getColor(this@FilesActivity, R.color.text_hint))
                setPadding(8, 0, 8, 0)
                textSize = 12f
            }
            container.addView(sep)

            val chip = inflater.inflate(R.layout.item_breadcrumb, container, false) as android.widget.TextView
            chip.text = seg
            val target = acc
            chip.setOnClickListener { navigateToDirectory(target) }
            // Highlight the last segment
            if (index == segments.lastIndex) {
                chip.setTextColor(ContextCompat.getColor(this, R.color.secondary))
                chip.setTypeface(null, android.graphics.Typeface.BOLD)
            }
            container.addView(chip)
        }

        // Auto-scroll to end so the deepest segment is visible
        binding.breadcrumbScroll.post {
            binding.breadcrumbScroll.fullScroll(View.FOCUS_RIGHT)
        }
    }

    private fun observeViewModel() {
        viewModel.devices.observe(this) { devices ->
            if (devices.isEmpty()) {
                updateEmptyState(true)
                return@observe
            }
            if (deviceId.isBlank()) {
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
                    val sorted = sortFiles(result.data)
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

    private fun sortFiles(files: List<RemoteFile>): List<RemoteFile> {
        // Always show directories first, then apply the chosen sort.
        return when (currentSort) {
            SortMode.NAME -> files.sortedWith(
                compareByDescending<RemoteFile> { it.isDirectory }
                    .thenBy { it.name.lowercase() }
            )
            SortMode.SIZE_DESC -> files.sortedWith(
                compareByDescending<RemoteFile> { it.isDirectory }
                    .thenByDescending { it.size }
            )
            SortMode.DATE_DESC -> files.sortedWith(
                compareByDescending<RemoteFile> { it.isDirectory }
                    .thenByDescending { it.modified ?: "" }
            )
        }
    }

    private fun updateEmptyState(isEmpty: Boolean) {
        binding.emptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (isEmpty) View.GONE else View.VISIBLE
    }

    override fun onBackPressed() {
        if (fileAdapter.isInSelectionMode()) {
            fileAdapter.clearSelection()
            refreshActionMode()
        } else {
            super.onBackPressed()
        }
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
