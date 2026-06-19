package com.abuzahra.admin.ui.files

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
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
 * recordings, etc.) via GET /api/web/files?device_id=X. These files auto-expire
 * on the server after 1 hour. The user can View (open in a system viewer) or
 * Download (save to Downloads) each file.
 *
 * The download URL is GET /api/files/{file_id} with a Bearer auth header
 * (added automatically by the OkHttp interceptor in ApiClient).
 */
class RequestedFilesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRequestedFilesBinding
    private val prefs: Preferences by lazy { Preferences.getInstance(this) }

    private var devices: List<Device> = emptyList()
    private var selectedDeviceId: String? = null  // null = all devices

    private val adapter: RequestedFileAdapter by lazy {
        RequestedFileAdapter(
            onViewClick = { file -> viewFile(file) },
            onDownloadClick = { file -> downloadFile(file) }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRequestedFilesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupRecyclerView()
        setupSwipeRefresh()

        loadDevicesAndFiles()
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "الملفات المطلوبة"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupRecyclerView() {
        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(this@RequestedFilesActivity)
            adapter = this@RequestedFilesActivity.adapter
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
                selectedDeviceId = null
                loadFiles()
            }
        }
        chipGroup.addView(allChip)

        devices.forEach { device ->
            val chip = Chip(this).apply {
                text = device.name.ifEmpty { device.model }
                isCheckable = true
                isChecked = selectedDeviceId == device.id
                setOnClickListener {
                    selectedDeviceId = device.id
                    loadFiles()
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
                    val files = response.files
                    adapter.submitList(files)
                    binding.emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
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
