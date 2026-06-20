package com.abuzahra.admin.ui.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.abuzahra.admin.R
import com.abuzahra.admin.util.ImageLoader
import com.abuzahra.admin.util.Preferences
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import java.io.File

/**
 * VideoViewerActivity — fullscreen video viewer using the built-in
 * [VideoView]. Because the server's `/api/files/{id}` endpoint requires
 * Bearer authentication and VideoView cannot attach headers, we first
 * stream the bytes into a cache file via [ImageLoader.downloadToCache]
 * and then point the VideoView at the local file URI.
 *
 * For very large videos this is suboptimal, but for short clips captured
 * by the device (front/back camera, screen recording) it works well.
 */
class VideoViewerActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "VideoViewerActivity"
        const val EXTRA_FILE_ID = "extra_file_id"
        const val EXTRA_FILE_NAME = "extra_file_name"

        fun newIntent(context: Context, fileId: String, fileName: String = ""): Intent {
            return Intent(context, VideoViewerActivity::class.java).apply {
                putExtra(EXTRA_FILE_ID, fileId)
                putExtra(EXTRA_FILE_NAME, fileName)
            }
        }
    }

    private lateinit var prefs: Preferences
    private lateinit var videoView: VideoView
    private lateinit var loading: ProgressBar
    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_video_viewer)

        prefs = Preferences.getInstance(this)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        val name = intent.getStringExtra(EXTRA_FILE_NAME).orEmpty()
        toolbar.title = name.ifEmpty { "عارض الفيديو" }
        toolbar.setNavigationOnClickListener { finish() }

        videoView = findViewById(R.id.videoView)
        loading = findViewById(R.id.loading)
        tvError = findViewById(R.id.tvError)

        load()
    }

    private fun load() {
        val fileId = intent.getStringExtra(EXTRA_FILE_ID).orEmpty()
        if (fileId.isBlank()) {
            tvError.text = "معرّف الملف غير متوفر"
            tvError.visibility = View.VISIBLE
            return
        }
        loading.visibility = View.VISIBLE
        videoView.visibility = View.GONE

        lifecycleScope.launch {
            val ext = ".mp4"
            val cacheFile = File(cacheDir, "video_${fileId}$ext")
            val ok = try {
                ImageLoader.downloadToCache(
                    prefs.serverUrl, prefs.token ?: "", fileId, cacheFile
                )
            } catch (e: Exception) {
                Log.e(TAG, "Video download failed", e)
                false
            }
            loading.visibility = View.GONE
            if (ok && cacheFile.exists() && cacheFile.length() > 0) {
                videoView.visibility = View.VISIBLE
                videoView.setVideoURI(Uri.fromFile(cacheFile))
                videoView.setOnPreparedListener { mp ->
                    mp.isLooping = false
                    videoView.start()
                }
                videoView.setOnCompletionListener { finish() }
                videoView.setOnErrorListener { _, _, _ ->
                    tvError.text = "تعذر تشغيل الفيديو"
                    tvError.visibility = View.VISIBLE
                    videoView.visibility = View.GONE
                    true
                }
            } else {
                tvError.text = "تعذر تحميل الفيديو"
                tvError.visibility = View.VISIBLE
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if (::videoView.isInitialized) {
            videoView.stopPlayback()
        }
    }
}
