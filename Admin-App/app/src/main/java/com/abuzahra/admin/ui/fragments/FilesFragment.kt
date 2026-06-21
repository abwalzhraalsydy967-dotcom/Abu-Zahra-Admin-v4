package com.abuzahra.admin.ui.fragments

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.abuzahra.admin.MainActivity
import com.abuzahra.admin.R
import com.abuzahra.admin.data.api.Result
import com.abuzahra.admin.data.model.RemoteFile
import com.abuzahra.admin.databinding.FragmentFilesBinding
import com.abuzahra.admin.ui.adapters.FileAdapter
import com.abuzahra.admin.ui.dashboard.DashboardViewModel
import com.abuzahra.admin.ui.dashboard.DashboardViewModelFactory
import com.abuzahra.admin.util.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Files fragment — functional copy of the web's FileViewer:
 *  - List of files uploaded by devices (auto-expire after 1 hour)
 *  - Tap view → opens in external image/video/audio viewer
 *  - Tap download → saves to Downloads
 *  - Pull-to-refresh + manual refresh
 *  - Polling every 30s (matches web)
 */
class FilesFragment : BaseFragment() {

    private var _binding: FragmentFilesBinding? = null
    private val binding get() = _binding!!

    private val viewModel: DashboardViewModel by activityViewModels {
        DashboardViewModelFactory(Preferences.getInstance(requireContext()))
    }

    private val fileAdapter: FileAdapter by lazy {
        FileAdapter(
            onView = { file -> viewFile(file) },
            onDownload = { file -> downloadFile(file) }
        )
    }

    /** Polls for new uploaded files every 30s (matches web). */
    private val pollHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val pollRunnable = object : Runnable {
        override fun run() {
            viewModel.loadFiles()
            pollHandler.postDelayed(this, 30000)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentFilesBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.rvFiles.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = fileAdapter
        }

        binding.btnRefresh.setOnClickListener { viewModel.loadFiles() }
        binding.filesSwipe.setOnRefreshListener { viewModel.loadFiles() }

        observeViewModel()
        // Initial load
        viewModel.loadFiles()
    }

    private fun observeViewModel() {
        viewModel.files.observe(viewLifecycleOwner) { result ->
            binding.filesSwipe.isRefreshing = false
            when (result) {
                is Result.Loading -> Unit
                is Result.Success -> {
                    val files = result.data
                    fileAdapter.submitList(files)
                    binding.emptyState.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
                    binding.rvFiles.visibility = if (files.isEmpty()) View.GONE else View.VISIBLE
                    binding.tvCount.text = files.size.toString()
                    binding.tvSize.text = formatBytes(files.sumOf { it.size })
                }
                is Result.Error -> {
                    (activity as? MainActivity)?.showSnack(result.message)
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        var v = bytes.toDouble()
        var i = 0
        while (v >= 1024 && i < units.size - 1) {
            v /= 1024
            i++
        }
        return "${"%.1f".format(v)} ${units[i]}"
    }

    // ── File viewing / downloading ────────────────────────────────
    private fun viewFile(file: RemoteFile) {
        (activity as? MainActivity)?.showSnack("جارٍ تحميل الملف للعرض...")
        viewLifecycleOwner.lifecycleScope.launch {
            val path = downloadToCache(file) ?: run {
                (activity as? MainActivity)?.showSnack("تعذّر تحميل الملف — قد يكون منتهي الصلاحية")
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                requireContext(),
                "${requireContext().packageName}.fileprovider",
                File(path)
            )
            val mime = when (file.fileType.lowercase()) {
                "photo", "screenshot", "camera" -> "image/*"
                "video" -> "video/*"
                "audio" -> "audio/*"
                else -> "*/*"
            }
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                startActivity(Intent.createChooser(intent, "عرض الملف"))
            } catch (_: Exception) {
                (activity as? MainActivity)?.showSnack("لا يوجد تطبيق لعرض هذا النوع")
            }
        }
    }

    private fun downloadFile(file: RemoteFile) {
        (activity as? MainActivity)?.showSnack("جارٍ التنزيل: ${file.displayName}")
        viewLifecycleOwner.lifecycleScope.launch {
            val srcPath = downloadToCache(file) ?: run {
                (activity as? MainActivity)?.showSnack("تعذّر تنزيل الملف — قد يكون منتهي الصلاحية")
                return@launch
            }
            // Copy to Downloads/AbuZahra/
            val downloads = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "AbuZahra"
            ).apply { mkdirs() }
            val dest = File(downloads, file.displayName.ifEmpty { file.filename })
            File(srcPath).copyTo(dest, overwrite = true)
            (activity as? MainActivity)?.showSnack("تم التنزيل إلى Downloads/AbuZahra/${dest.name}")
        }
    }

    /**
     * Fetches the file via /api/files/{id} and writes it to cacheDir.
     * Returns the local file path or null on failure.
     */
    private suspend fun downloadToCache(file: RemoteFile): String? = withContext(Dispatchers.IO) {
        try {
            val prefs = Preferences.getInstance(requireContext())
            val serverUrl = prefs.serverUrl.trimEnd('/')
            val token = prefs.token ?: return@withContext null

            // Build URL
            val url = "$serverUrl/api/files/${Uri.encode(file.id)}"

            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val request = okhttp3.Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                val cacheDir = requireContext().cacheDir
                val outFile = File(cacheDir, "view_${file.id}_${file.displayName.ifEmpty { file.filename }}")
                body.byteStream().use { input ->
                    outFile.outputStream().use { output -> input.copyTo(output) }
                }
                return@withContext outFile.absolutePath
            }
        } catch (_: Exception) {
            null
        }
    }

    override fun onResume() {
        super.onResume()
        // Start polling for new uploads
        pollHandler.post(pollRunnable)
    }

    override fun onPause() {
        super.onPause()
        pollHandler.removeCallbacks(pollRunnable)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollHandler.removeCallbacks(pollRunnable)
        _binding = null
    }
}
