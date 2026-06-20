package com.abuzahra.admin.ui.files

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.ApiException
import com.abuzahra.admin.data.model.Device
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.ActivityRequestedFilesBinding
import com.abuzahra.admin.ui.login.LoginActivity
import com.abuzahra.admin.util.Preferences
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import java.io.File

/**
 * Lists files that devices have uploaded to the server (photos, screenshots,
 * recordings, etc.) via GET /api/web/files?device_id=X.
 *
 * Features:
 * - List / grid view toggle.
 * - Search by filename.
 * - Filter by file type (All / Images / Videos / Audio / Files) and device.
 * - Sort by date (default) / name / size.
 * - Click an image file → fullscreen [ImageViewerActivity] with pinch-zoom.
 * - Click a video file → fullscreen [VideoViewerActivity].
 * - Click an audio file → [AudioPlayerDialogFragment] (play / pause / seek).
 * - Long-press → multi-select with Download-all / Delete-all actions.
 */
class RequestedFilesActivity : AppCompatActivity() {

    private enum class SortMode(val label: String) {
        DATE("التاريخ (الأحدث)"),
        NAME("الاسم"),
        SIZE("الحجم (الأكبر)")
    }

    private enum class TypeFilter(val label: String, val keys: Set<String>) {
        ALL("الكل", emptySet()),
        IMAGES("صور", setOf("photo", "camera", "screenshot", "image")),
        VIDEOS("فيديو", setOf("video")),
        AUDIO("صوت", setOf("audio")),
        FILES("ملفات", setOf("file"))
    }

    private lateinit var binding: ActivityRequestedFilesBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    private var devices: List<Device> = emptyList()
    private var selectedDeviceId: String? = null  // null = all devices
    private var allFiles: List<RemoteFile> = emptyList()

    private var currentTypeFilter: TypeFilter = TypeFilter.ALL
    private var currentSort: SortMode = SortMode.DATE
    private var currentQuery: String = ""

    private val adapter: RequestedFileAdapter by lazy {
        RequestedFileAdapter(
            onViewClick = { file -> openFile(file) },
            onDownloadClick = { file -> downloadFile(file) },
            onLongClick = { _ -> },
            onSelectionToggle = { _ -> updateSelectionTitle() }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Restore the persisted sort preference (defaults to DATE). Done
        // here (not in an init {} block) because `prefs` lazily calls
        // Preferences.getInstance(this) which needs the base context that
        // is only attached after construction.
        currentSort = runCatching {
            SortMode.valueOf(prefs.requestedFilesSortMode)
        }.getOrDefault(SortMode.DATE)

        super.onCreate(savedInstanceState)
        binding = ActivityRequestedFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSearch()
        setupTypeChips()
        setupSwipeRefresh()

        loadDevicesAndFiles()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "الملفات المطلوبة"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener {
            if (adapter.isInSelectionMode()) {
                adapter.clearSelection()
                updateSelectionTitle()
            } else {
                finish()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_requested_files, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_toggle_view -> {
                val newMode = if (adapter.getViewMode() == RequestedFileAdapter.ViewMode.LIST)
                    RequestedFileAdapter.ViewMode.GRID
                else
                    RequestedFileAdapter.ViewMode.LIST
                adapter.setViewMode(newMode)
                binding.rvFiles.layoutManager = when (newMode) {
                    RequestedFileAdapter.ViewMode.LIST -> LinearLayoutManager(this)
                    RequestedFileAdapter.ViewMode.GRID -> GridLayoutManager(this, 3)
                }
                item.icon = if (newMode == RequestedFileAdapter.ViewMode.GRID)
                    getDrawable(R.drawable.ic_list)
                else
                    getDrawable(R.drawable.ic_grid)
                true
            }
            R.id.action_sort -> {
                showSortDialog()
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
                // Persist the choice so it survives a process restart.
                prefs.requestedFilesSortMode = currentSort.name
                dialog.dismiss()
                applyFilterAndSort()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun setupRecyclerView() {
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@RequestedFilesActivity)
            adapter = this@RequestedFilesActivity.adapter
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                currentQuery = s?.toString()?.trim() ?: ""
                applyFilterAndSort()
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupTypeChips() {
        val group = binding.chipTypeFilter
        group.removeAllViews()
        TypeFilter.values().forEach { tf ->
            val chip = Chip(this).apply {
                text = tf.label
                isCheckable = true
                isChecked = tf == currentTypeFilter
                setOnClickListener {
                    if (isChecked) {
                        currentTypeFilter = tf
                        // Uncheck siblings
                        for (i in 0 until group.childCount) {
                            val c = group.getChildAt(i) as? Chip
                            c?.isChecked = c === this
                        }
                        applyFilterAndSort()
                    } else {
                        // Always keep one selected — re-select "الكل" if user un-checks
                        currentTypeFilter = TypeFilter.ALL
                        (group.getChildAt(0) as? Chip)?.isChecked = true
                        applyFilterAndSort()
                    }
                }
            }
            group.addView(chip)
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            loadFiles()
        }
    }

    private fun loadDevicesAndFiles() {
        binding.loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = prefs.getApiService()
                devices = api.getDevices()
                populateDeviceChips()
                loadFiles()
            } catch (e: HttpException) {
                if (e.code() == 401) showSessionExpired()
                else Snackbar.make(binding.root, "خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
                binding.loadingOverlay.visibility = View.GONE
            } catch (e: Exception) {
                Snackbar.make(binding.root, "خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
                binding.loadingOverlay.visibility = View.GONE
            }
        }
    }

    private fun populateDeviceChips() {
        val chipGroup = binding.chipDeviceFilter
        chipGroup.removeAllViews()

        // "All" chip
        val allChip = Chip(this).apply {
            text = "كل الأجهزة"
            isCheckable = true
            isChecked = selectedDeviceId == null
            setOnClickListener {
                if (isChecked) {
                    selectedDeviceId = null
                    for (i in 0 until chipGroup.childCount) {
                        val c = chipGroup.getChildAt(i) as? Chip
                        c?.isChecked = c === this
                    }
                    loadFiles()
                }
            }
        }
        chipGroup.addView(allChip)

        devices.forEach { device ->
            val chip = Chip(this).apply {
                text = device.name.ifEmpty { device.model }
                isCheckable = true
                isChecked = selectedDeviceId == device.id
                setOnClickListener {
                    if (isChecked) {
                        selectedDeviceId = device.id
                        for (i in 0 until chipGroup.childCount) {
                            val c = chipGroup.getChildAt(i) as? Chip
                            c?.isChecked = c === this
                        }
                        loadFiles()
                    }
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun loadFiles() {
        binding.loadingOverlay.visibility = View.VISIBLE
        lifecycleScope.launch {
            try {
                val api = prefs.getApiService()
                val response = api.getRequestedFiles(selectedDeviceId)
                if (response.ok) {
                    allFiles = response.files
                    applyFilterAndSort()
                } else {
                    Snackbar.make(binding.root, "فشل تحميل الملفات", Snackbar.LENGTH_LONG).show()
                }
            } catch (e: HttpException) {
                if (e.code() == 401) showSessionExpired()
                else Snackbar.make(binding.root, "خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
            } catch (e: ApiException) {
                Snackbar.make(binding.root, e.message ?: "خطأ", Snackbar.LENGTH_LONG).show()
            } catch (e: Exception) {
                Snackbar.make(binding.root, "خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
            } finally {
                binding.loadingOverlay.visibility = View.GONE
                binding.swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun applyFilterAndSort() {
        var filtered = allFiles

        // Type filter
        if (currentTypeFilter != TypeFilter.ALL) {
            filtered = filtered.filter { it.fileType.lowercase() in currentTypeFilter.keys }
        }

        // Search filter
        if (currentQuery.isNotBlank()) {
            val q = currentQuery.lowercase()
            filtered = filtered.filter { it.displayName.lowercase().contains(q) }
        }

        // Sort
        filtered = when (currentSort) {
            SortMode.DATE -> filtered.sortedByDescending { it.uploadedAt ?: "" }
            SortMode.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.SIZE -> filtered.sortedByDescending { it.size }
        }

        adapter.submitList(filtered)
        binding.emptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvFiles.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateSelectionTitle() {
        if (adapter.isInSelectionMode()) {
            supportActionBar?.title = "تم تحديد ${adapter.getSelectedCount()}"
        } else {
            supportActionBar?.title = "الملفات المطلوبة"
        }
    }

    /**
     * Open the appropriate viewer for a file based on its type:
     * image → ImageViewerActivity, video → VideoViewerActivity, audio →
     * AudioPlayerDialogFragment, anything else → system ACTION_VIEW.
     */
    private fun openFile(file: RemoteFile) {
        if (file.id.isBlank()) {
            Snackbar.make(binding.root, "معرّف الملف غير متوفر", Snackbar.LENGTH_LONG).show()
            return
        }
        when (file.fileType.lowercase()) {
            "photo", "camera", "screenshot", "image" -> {
                startActivity(ImageViewerActivity.newIntent(this, file.id, file.displayName))
            }
            "video" -> {
                startActivity(VideoViewerActivity.newIntent(this, file.id, file.displayName))
            }
            "audio" -> {
                AudioPlayerDialogFragment.newInstance(file.id, file.displayName)
                    .show(supportFragmentManager, "audio_player")
            }
            else -> {
                // Fallback: stream to cache, then ACTION_VIEW
                viewFile(file)
            }
        }
    }

    private fun viewFile(file: RemoteFile) {
        // Stream the file into a cache file then open with ACTION_VIEW.
        Snackbar.make(binding.root, "جاري فتح ${file.displayName}...", Snackbar.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = prefs.getApiService()
                val url = "${prefs.serverUrl}api/files/${file.id}"
                val body = api.downloadFile(url)

                val cacheDir = cacheDir
                val ext = suggestedExtension(file.fileType, file.filename)
                val cacheFile = File(cacheDir, "view_${file.id}${ext}")
                body.byteStream().use { input ->
                    cacheFile.outputStream().use { output -> input.copyTo(output) }
                }

                withContext(Dispatchers.Main) {
                    openCachedFile(cacheFile, file.fileType)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "فشل فتح الملف: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openCachedFile(file: File, fileType: String) {
        try {
            val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeTypeForType(fileType))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Snackbar.make(binding.root, "لا يمكن فتح الملف", Snackbar.LENGTH_SHORT).show()
        }
    }

    private fun downloadFile(file: RemoteFile) {
        if (file.id.isBlank()) {
            Snackbar.make(binding.root, "معرّف الملف غير متوفر", Snackbar.LENGTH_LONG).show()
            return
        }
        Snackbar.make(binding.root, "جاري تحميل ${file.displayName}...", Snackbar.LENGTH_SHORT).show()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val api = prefs.getApiService()
                val url = "${prefs.serverUrl}api/files/${file.id}"
                val body = api.downloadFile(url)

                val downloadsDir = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS
                )
                val ext = suggestedExtension(file.fileType, file.filename)
                val safeName = file.displayName + ext
                val destFile = File(downloadsDir, safeName)

                body.byteStream().use { input ->
                    destFile.outputStream().use { output -> input.copyTo(output) }
                }

                withContext(Dispatchers.Main) {
                    Snackbar.make(
                        binding.root,
                        "تم التحميل: ${destFile.absolutePath}",
                        Snackbar.LENGTH_LONG
                    ).setAction("فتح") { openCachedFile(destFile, file.fileType) }.show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Snackbar.make(binding.root, "فشل التحميل: ${e.message}", Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun suggestedExtension(fileType: String, filename: String): String {
        val lastDot = filename.lastIndexOf('.')
        if (lastDot >= 0 && lastDot < filename.length - 1) {
            return filename.substring(lastDot)  // includes the dot
        }
        return when (fileType.lowercase()) {
            "photo", "camera", "screenshot" -> ".jpg"
            "video" -> ".mp4"
            "audio" -> ".mp3"
            else -> ""
        }
    }

    private fun mimeTypeForType(fileType: String): String = when (fileType.lowercase()) {
        "photo", "camera", "screenshot" -> "image/jpeg"
        "video" -> "video/mp4"
        "audio" -> "audio/mpeg"
        "file" -> "*/*"
        else -> "*/*"
    }

    override fun onBackPressed() {
        if (adapter.isInSelectionMode()) {
            adapter.clearSelection()
            updateSelectionTitle()
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
